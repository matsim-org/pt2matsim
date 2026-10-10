package org.matsim.pt2matsim.osm;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinks;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinksUtils;
import org.matsim.lanes.*;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmLaneData;

/**
 * Multimodal OSM conversion with turning lanes and audited restriction sequences.
 * Inspired by SignalsAndLanesOsmNetworkReader's turn-tag/outgoing-link matching.
 * Lane connections are mode-independent; network restrictions remain mode-specific.
 * Conditional restrictions and exact receiving-lane constraints cannot be encoded
 * in standard MATSim lanes and are skipped with an audit record.
 */
public class OsmNetworkWithLanesConverter extends OsmMultimodalNetworkConverter {
    private static final Logger LOG = LogManager.getLogger(OsmNetworkWithLanesConverter.class);
    private final LaneConversionReport report;
    private final List<Rule> rules = new ArrayList<>();
    private final Set<Id<Osm.Node>> inferredJunctions = new HashSet<>();
    private final Set<Id<Osm.Relation>> skippedRelations = new HashSet<>();
    private final Map<Id<Osm.Relation>, String> relationDetails = new HashMap<>();
    private final List<Osm.Relation> connectivity = new ArrayList<>();
    private final Map<Id<Osm.Way>, List<Id<Osm.Node>>> geometry = new HashMap<>();
    private Lanes lanes;
    private boolean converted;
    private final Set<String> auditedDirectionalCounts = new HashSet<>();
    private final Map<String, Double> derivedDirectionalCounts = new HashMap<>();
    private final Set<String> auditedLaneCountArrays = new HashSet<>();
    private final Set<String> ignoredReservedLaneArrays = new HashSet<>();
    private long pathExpansions;
    private static final Set<String> RESTRICTION_VALUES = Set.of("no_left_turn", "no_right_turn", "no_straight_on", "no_u_turn",
            "only_left_turn", "only_right_turn", "only_straight_on", "only_u_turn", "no_entry", "no_exit");

    private record Rule(Id<Osm.Relation> id, List<Id<Osm.Way>> from,
                        List<Id<Osm.Way>> via, List<Id<Osm.Way>> to,
                        Id<Osm.Node> node, Map<String, String> tags) { }

    public OsmNetworkWithLanesConverter(OsmData data) {
        super(data);
        report = data instanceof OsmLaneData laneData ? laneData.getReport() : new LaneConversionReport();
    }
    public Lanes getLanes() { return lanes; }
    public LaneConversionReport getReport() { return report; }

    @Override
    protected void onDirectionalLaneCountInference(Osm.Way way, boolean forward, double total,
                                                   double opposite, double shared, double inferred, boolean valid) {
        String direction = forward ? "forward" : "backward";
        if (valid) derivedDirectionalCounts.put(way.getId() + ":" + direction, inferred);
        if (auditedDirectionalCounts.add(way.getId() + ":" + direction))
            report.add(valid ? "directionalLaneCountsDerived" : "directionalLaneCountInferenceRejected", way.getId(), "",
                    direction + ": total=" + total + " - opposite=" + opposite + " - shared=" + shared
                            + " = " + inferred + (valid ? "; reserved lanes included in directional total" : "; retain legacy fallback"));
    }

    @Override
    protected void onLaneCountFromTags(Osm.Way way, boolean forward, int count, String evidence) {
        String direction = forward ? "forward" : "backward";
        if (count > 0) derivedDirectionalCounts.put(way.getId() + ":" + direction, (double) count);
        if (auditedLaneCountArrays.add(way.getId() + ":" + direction))
            report.add(count > 0 ? "laneCountsInferredFromArrays" : "laneCountArrayInferenceRejected", way.getId(), "",
                    direction + ": " + evidence + (count > 0 ? "; motor lanes=" + count : "; retain configured fallback"));
    }

    @Override
    protected void onInconsistentReservedLaneArray(Osm.Way way, String key, String value, int motorSlots, double declared) {
        if (ignoredReservedLaneArrays.add(way.getId() + ":" + key))
            report.add("inconsistentReservedLaneArraysIgnored", way.getId(), "",
                    key + "=" + value + "; motor slots=" + motorSlots + "; declared directional lanes=" + declared
                            + "; ignore this inconsistent array; retain valid count tags and opposite-direction car access");
    }

    @Override
    public void convert(OsmConverterConfigGroup config) {
        if (converted) throw new IllegalStateException("Use a new converter for each conversion");
        converted = true;
        if (!config.parseTurnRestrictions) throw new IllegalArgumentException("Lane conversion requires parseTurnRestrictions=true");
        try {
            snapshotRelations();
            osmData.getWays().values().forEach(w -> geometry.put(w.getId(), w.getNodes().stream().map(Osm.Node::getId).toList()));
            super.convert(config);
            // Rebuild after cleaning/derived mode creation: cleaning must never relax surviving restrictions.
            disallowedNextLinks.clear();
            network.getLinks().values().forEach(NetworkUtils::removeDisallowedNextLinks);
            applyRestrictions(true);
            for (var e : disallowedNextLinks.entrySet()) NetworkUtils.setDisallowedNextLinks(network.getLinks().get(e.getKey()), e.getValue());
            validateConnectivity();
            generateLanes();
            if (!DisallowedNextLinksUtils.isValid(network)) throw new IllegalStateException("Invalid restriction sequences after lane conversion");
            LOG.info("Lane conversion counts: {}", report.getCounts());
        } catch (RuntimeException failure) {
            report.recordError(failure);
            throw failure;
        }
    }

    private void snapshotRelations() {
        for (Osm.Relation r : osmData.getRelations().values()) {
            String type = r.getTags().get("type");
            if (!"restriction".equals(type) && !"connectivity".equals(type) && (type == null || !type.startsWith("restriction:"))) {
                report.add("ignoredNonNetworkRelations", r.getId(), "", "type=" + type + "; tags=" + r.getTags());
                continue;
            }
            relationDetails.put(r.getId(), "tags=" + r.getTags() + "; members=" + r.getMembers().stream()
                    .map(m -> m.getClass().getSimpleName() + " " + ((org.matsim.api.core.v01.Identifiable<?>) m).getId()
                            + " roles=" + r.getMemberRoles(m)).toList());
            try {
                if ("connectivity".equals(r.getTags().get("type"))) {
                    long nodeCount = r.getMembers().stream().filter(e -> e instanceof Osm.Node && r.getMemberRoles(e).contains("via")).count();
                    if (members(r, "from").size() != 1 || members(r, "to").size() != 1
                            || nodeCount != 1 && members(r, "via").isEmpty() || !r.getTags().containsKey("connectivity")) {
                        throw relationError(r.getId(), "Malformed connectivity relation");
                    }
                    connectivity.add(r);
                    continue;
                }
                Map<String, String> tags = normalizeRestrictionTags(r);
                List<Id<Osm.Way>> from = members(r, "from"), via = members(r, "via"), to = members(r, "to");
                List<Osm.Node> nodes = r.getMembers().stream().filter(e -> e instanceof Osm.Node && r.getMemberRoles(e).contains("via"))
                        .map(e -> (Osm.Node) e).toList();
                if (nodes.isEmpty() && via.isEmpty() && from.size() == 1 && to.size() == 1) {
                    Set<Id<Osm.Node>> incomingNodes = osmData.getWays().get(from.get(0)).getNodes().stream()
                            .map(Osm.Node::getId).collect(java.util.stream.Collectors.toSet());
                    nodes = osmData.getWays().get(to.get(0)).getNodes().stream()
                            .filter(n -> incomingNodes.contains(n.getId())).distinct().toList();
                    if (nodes.size() != 1) {
                        report.add("relationsSkippedUnresolvedJunction", r.getId(), "", "Missing via member; found " + nodes.size()
                                + " shared nodes between from way " + from.get(0) + " and to way " + to.get(0)
                                + "; ignored restriction " + r.getTags());
                        continue;
                    }
                    inferredJunctions.add(nodes.get(0).getId());
                    report.add("inferredRestrictionJunctions", r.getId(), "", "Missing via member; inferred unique shared node " + nodes.get(0).getId());
                }
                if (from.isEmpty() || to.isEmpty() || (nodes.size() != 1 && via.isEmpty()) || (!nodes.isEmpty() && !via.isEmpty())) {
                    report.add("relationsSkippedUnresolvedJunction", r.getId(), "", "Missing or conflicting junction members; from="
                            + from + ", viaWays=" + via + ", viaNodes=" + nodes.stream().map(Osm.Node::getId).toList()
                            + ", to=" + to + "; ignored restriction " + r.getTags());
                    continue;
                }
                if (tags.keySet().stream().noneMatch(k -> k.equals("restriction") || k.startsWith("restriction:"))) {
                    throw relationError(r.getId(), "Missing restriction tag");
                }
                for (var e : tags.entrySet()) if (e.getKey().equals("restriction") || e.getKey().startsWith("restriction:") && !e.getKey().endsWith(":conditional")) {
                    if (e.getKey().equals("restriction:bicycle") && Set.of("give_way", "stop").contains(e.getValue())) {
                        report.add("priorityRulesNotSimulated", r.getId(), "", e.getKey() + "=" + e.getValue()
                                + ": bicycle movement remains available; yielding/stopping behavior is not simulated");
                        continue;
                    }
                    if (!RESTRICTION_VALUES.contains(e.getValue()) && !e.getValue().equals("none")) {
                        report.add("unsupportedRestrictionTags", r.getId(), "", "Unknown " + e
                                + "; preserve supported overrides for other modes; " + relationDetails.get(r.getId()));
                    }
                    if (RESTRICTION_VALUES.contains(e.getValue()) && (from.size() > 1 && !e.getValue().equals("no_entry")
                            || to.size() > 1 && !e.getValue().equals("no_exit"))) {
                        report.add("inferredRestrictionCardinalities", r.getId(), "", e + ": apply to each represented incoming way; "
                                + "treat destinations as prohibited targets for no_* or allowed alternatives for only_*; " + relationDetails.get(r.getId()));
                    }
                }
                if (tags.keySet().stream().anyMatch(k -> Set.of("day_on", "day_off", "hour_on", "hour_off").contains(k))) throw relationError(r.getId(), "Legacy time-dependent restriction is not representable in static lanes");
                if (tags.entrySet().stream().noneMatch(e -> (e.getKey().equals("restriction") || e.getKey().startsWith("restriction:"))
                        && RESTRICTION_VALUES.contains(e.getValue()))) {
                    if (tags.keySet().stream().anyMatch(k -> k.startsWith("restriction") && k.endsWith(":conditional"))) {
                        skipRelation(r.getId(), "reading relation", "Conditional restrictions need a time-aware model; no unconditional turn rule available");
                    } else if (tags.entrySet().stream().anyMatch(e -> (e.getKey().equals("restriction") || e.getKey().startsWith("restriction:"))
                            && !RESTRICTION_VALUES.contains(e.getValue()) && !Set.of("none", "give_way", "stop").contains(e.getValue()))) {
                        skipRelation(r.getId(), "reading relation", "Unsupported restriction values; no supported unconditional turn rule available");
                    }
                    continue;
                }
                if (!nodes.isEmpty()) inferredJunctions.add(nodes.get(0).getId());
                rules.add(new Rule(r.getId(), from, via, to, nodes.isEmpty() ? null : nodes.get(0).getId(), tags));
            } catch (IllegalArgumentException unsupported) {
                skipRelation(r.getId(), "reading relation", unsupported.getMessage());
            }
        }
    }

    private Map<String, String> normalizeRestrictionTags(Osm.Relation relation) {
        Map<String, String> tags = new HashMap<>(relation.getTags());
        String type = tags.get("type");
        if (type.startsWith("restriction:")) {
            String key = type;
            String generic = tags.remove("restriction");
            if (generic != null) tags.putIfAbsent(key, generic);
            tags.put("type", "restriction");
            report.add("normalizedRestrictionTags", relation.getId(), "", "Normalized legacy " + type + " to a mode-specific rule; " + relationDetails.get(relation.getId()));
        }
        for (String key : new ArrayList<>(tags.keySet())) {
            if (key.endsWith(":conditional")) {
                report.add("conditionalTagsNotSimulated", relation.getId(), "", key + "=" + tags.get(key)
                        + "; only affected modes are omitted; " + relationDetails.get(relation.getId()));
            } else if (key.startsWith("restriction:") && tags.get(key).equals("no")) {
                tags.put(key, "none");
                report.add("normalizedRestrictionTags", relation.getId(), "", key + "=no interpreted as no turn restriction for this mode; "
                        + relationDetails.get(relation.getId()));
            }
        }
        return Map.copyOf(tags);
    }

    private void skipRelation(Id<Osm.Relation> id, String stage, String reason) {
        if (skippedRelations.add(id)) report.add("relationsSkippedUnsupported", id, "", "stage=" + stage + "; reason=" + reason
                + "; " + relationDetails.getOrDefault(id, ""));
    }

    private static List<Id<Osm.Way>> members(Osm.Relation r, String role) {
        return r.getMembers().stream().filter(e -> e instanceof Osm.Way && r.getMemberRoles(e).contains(role))
                .map(e -> ((Osm.Way) e).getId()).distinct().toList();
    }

    @Override protected boolean preserveNode(Osm.Node node) {
        return inferredJunctions.contains(node.getId())
                || node.getRelations().values().stream().anyMatch(r -> "restriction".equals(r.getTags().get("type")) || "connectivity".equals(r.getTags().get("type")));
    }

    @Override protected void attachTurnRestrictionsAsDisallowedNextLinks() {
        // Replace the original matching, which cannot resolve parallel reserved-lane links.
        for (Link link : new ArrayList<>(network.getLinks().values())) {
            if (!Double.isFinite(link.getNumberOfLanes()) || link.getNumberOfLanes() <= 0 || link.getNumberOfLanes() > 256
                    || !Double.isFinite(link.getLength()) || link.getLength() <= 0
                    || !Double.isFinite(link.getCapacity()) || link.getCapacity() <= 0
                    || !Double.isFinite(link.getFreespeed()) || link.getFreespeed() <= 0) {
                Osm.Way way = osmData.getWays().get(osmIds.get(link.getId()));
                report.add("unusableNetworkLinksRemoved", osmIds.get(link.getId()), link.getId(), "Removed link: lanes="
                        + link.getNumberOfLanes() + ", length=" + link.getLength() + ", capacity=" + link.getCapacity()
                        + ", freespeed=" + link.getFreespeed() + "; tags=" + (way == null ? "{}" : way.getTags()));
                network.removeLink(link.getId());
            }
        }
        network.getLinks().values().forEach(l -> l.getAttributes().removeAttribute("OsmTurnRestriction"));
        applyRestrictions(false);
    }

    @Override protected void cleanNetwork() {
        Set<Id<Link>> before = new HashSet<>(network.getLinks().keySet());
        super.cleanNetwork();
        before.removeAll(network.getLinks().keySet());
        for (Id<Link> removed : before) report.count("linksRemovedByCleaning");
    }

    private void applyRestrictions(boolean afterCleaning) {
        for (Rule rule : rules) {
            if (skippedRelations.contains(rule.id())) continue;
            List<? extends Link> incoming = rule.from().stream().flatMap(w -> wayLinkMap.getOrDefault(w, List.of()).stream())
                    .map(network.getLinks()::get).filter(Objects::nonNull)
                    .filter(l -> rule.node() == null || l.getToNode().getId().toString().equals(rule.node().toString())).toList();
            Set<String> modes = incoming.stream().flatMap(l -> l.getAllowedModes().stream()).collect(Collectors.toCollection(TreeSet::new));
            int matchedModes = 0, failedModes = 0;
            String lastFailure = "";
            for (String mode : modes) {
                try {
                    String restriction = restrictionForMode(rule.tags(), mode);
                    if (restriction == null) continue;
                    pathExpansions = 0;
                    Map<Id<Link>, DisallowedNextLinks> pending = new HashMap<>();
                    int matched = buildModeRestrictions(rule, incoming, mode, restriction, pending);
                    if (matched == 0) throw relationError(rule.id(), "Could not match restriction to a complete directed network path");
                    // Commit this mode only after all its approaches and paths succeeded.
                    for (var e : pending.entrySet()) {
                        DisallowedNextLinks target = disallowedNextLinks.computeIfAbsent(e.getKey(), k -> new DisallowedNextLinks());
                        for (var sequence : e.getValue().getDisallowedLinkSequences(mode)) target.addDisallowedLinkSequence(mode, sequence);
                    }
                    matchedModes++;
                    if (afterCleaning) report.count("restrictionModeApplications");
                } catch (IllegalArgumentException unsupported) {
                    failedModes++;
                    lastFailure = unsupported.getMessage();
                    if (afterCleaning) report.add("restrictionModesSkippedUnsupported", rule.id(), mode,
                            "stage=matching final network; mode=" + mode + "; reason=" + lastFailure + "; " + relationDetails.get(rule.id()));
                }
            }
            if (afterCleaning) {
                if (matchedModes > 0) {
                    report.count("restrictionRelationsApplied");
                    if (failedModes > 0) report.add("restrictionRelationsPartiallyApplied", rule.id(), "",
                            "Applied for " + matchedModes + " modes; omitted for " + failedModes + " modes; " + relationDetails.get(rule.id()));
                } else if (failedModes > 0) {
                    skipRelation(rule.id(), "matching final network", lastFailure);
                } else {
                    report.add("restrictionRelationsOutsideFinalNetwork", rule.id(), "", "No applicable incoming movement survives conversion/cleaning");
                }
            }
        }
        disallowedNextLinks.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private int buildModeRestrictions(Rule rule, List<? extends Link> incoming, String mode, String restriction,
                                      Map<Id<Link>, DisallowedNextLinks> pending) {
        int matched = 0;
        for (Link from : incoming) {
            if (!from.getAllowedModes().contains(mode)) continue;
            List<List<Link>> paths = new ArrayList<>();
            for (Id<Osm.Way> to : rule.to()) {
                List<Id<Osm.Way>> stages = new ArrayList<>(rule.via());
                stages.add(to);
                findPaths(from, from.getToNode(), stages, 0, mode, new ArrayList<>(), new HashSet<>(), paths);
            }
            if (rule.from().equals(rule.to()) && rule.via().isEmpty()) {
                boolean uturn = restriction.endsWith("u_turn");
                paths.removeIf(p -> uturn != p.get(0).getToNode().equals(from.getFromNode()));
            }
            if (paths.isEmpty()) {
                boolean startsVia = rule.node() != null || from.getToNode().getOutLinks().values().stream()
                        .anyMatch(out -> rule.via().get(0).equals(osmIds.get(out.getId())) && out.getAllowedModes().contains(mode));
                if (restriction.startsWith("only_") && startsVia) {
                    throw relationError(rule.id(), "An only_* target is unavailable for mode " + mode + " but its incoming link survives");
                }
                continue;
            }
            matched++;
            DisallowedNextLinks dnl = pending.computeIfAbsent(from.getId(), k -> new DisallowedNextLinks());
            if (restriction.startsWith("no_")) {
                for (List<Link> path : paths) addSequence(dnl, mode, path.stream().map(Link::getId).toList(), rule.id());
            } else {
                Map<List<Id<Link>>, Set<Id<Link>>> continuations = new HashMap<>();
                for (List<Link> path : paths) for (int i = 0; i < path.size(); i++) {
                    List<Id<Link>> prefix = path.subList(0, i).stream().map(Link::getId).toList();
                    continuations.computeIfAbsent(prefix, k -> new HashSet<>()).add(path.get(i).getId());
                }
                for (var e : continuations.entrySet()) {
                    Node junction = e.getKey().isEmpty() ? from.getToNode() : network.getLinks().get(e.getKey().get(e.getKey().size() - 1)).getToNode();
                    for (Link out : junction.getOutLinks().values()) if (out.getAllowedModes().contains(mode) && !e.getValue().contains(out.getId())) {
                        List<Id<Link>> banned = new ArrayList<>(e.getKey());
                        banned.add(out.getId());
                        addSequence(dnl, mode, banned, rule.id());
                    }
                }
            }
        }
        return matched;
    }

    private static void addSequence(DisallowedNextLinks dnl, String mode, List<Id<Link>> sequence, Object relation) {
        if (new HashSet<>(sequence).size() != sequence.size()) throw relationError(relation, "A repeating-link restriction cannot be encoded by DisallowedNextLinks");
        dnl.addDisallowedLinkSequence(mode, sequence);
    }

    private void findPaths(Link from, Node node, List<Id<Osm.Way>> stages, int stage, String mode,
                           List<Link> prefix, Set<Id<Link>> visited, List<List<Link>> result) {
        if (++pathExpansions > 100000 || result.size() > 4096 || prefix.size() > 256) throw new IllegalArgumentException("Restriction path expansion exceeded limit at link " + from.getId());
        for (Link out : node.getOutLinks().values()) {
            if (!out.getAllowedModes().contains(mode) || visited.contains(out.getId())) continue;
            if (!prefix.isEmpty() && out.getToNode().equals(prefix.get(prefix.size() - 1).getFromNode())) continue;
            if (!stages.get(stage).equals(osmIds.get(out.getId()))) continue;
            prefix.add(out); visited.add(out.getId());
            if (stage == stages.size() - 1) result.add(List.copyOf(prefix));
            else {
                findPaths(from, out.getToNode(), stages, stage + 1, mode, prefix, visited, result);
                findPaths(from, out.getToNode(), stages, stage, mode, prefix, visited, result);
            }
            visited.remove(out.getId()); prefix.remove(prefix.size() - 1);
        }
    }

    private String restrictionForMode(Map<String, String> tags, String mode) {
        return new OsmRestrictionModeResolver(config).restrictionForMode(tags, mode);
    }

    // Detailed lanes apply the same policy once, after their enhanced relation handling.
    @Override protected void addAdditionalTurnRestrictions() { }

    private void generateLanes() {
        lanes = LanesUtils.createLanesContainer();
        var logic = new OsmTurnMovementLogic(this, geometry, report, derivedDirectionalCounts,
                ignoredReservedLaneArrays);
        logic.indexUTurnModeExemptions(rules.stream().filter(rule -> !skippedRelations.contains(rule.id())
                && rule.node() != null && rule.via().isEmpty())
                .map(rule -> new OsmTurnMovementLogic.UTurnRule(rule.from(), rule.to(), rule.node(), rule.tags())).toList(), config);
        for (Link link : network.getLinks().values().stream().sorted(Comparator.comparing(l -> l.getId().toString())).toList()) {
            Osm.Way way = osmData.getWays().get(osmIds.get(link.getId()));
            if (way == null || !way.getTags().containsKey("highway")) continue;
            if (!Double.isFinite(link.getNumberOfLanes()) || link.getNumberOfLanes() <= 0 || link.getNumberOfLanes() > 256
                    || !Double.isFinite(link.getLength()) || link.getLength() <= 0
                    || !Double.isFinite(link.getCapacity()) || link.getCapacity() <= 0) {
                throw new IllegalArgumentException("Invalid length, capacity or lane count on OSM way " + way.getId() + ", link " + link.getId());
            }
            OsmTurnMovementLogic.Movements movements = logic.resolve(link, way);
            if (movements == null) continue;
            List<Integer> physical = movements.physical();
            List<Set<Id<Link>>> destinations = movements.destinations();
            LanesToLinkAssignment assignment = lanes.getFactory().createLanesToLinkAssignment(link.getId());
            Lane original = lanes.getFactory().createLane(Id.create(link.getId() + ".ol", Lane.class));
            original.setStartsAtMeterFromLinkEnd(link.getLength());
            original.setNumberOfRepresentedLanes(link.getNumberOfLanes());
            LanesUtils.calculateAndSetCapacity(original, false, link, network);
            assignment.addLane(original);
            for (int i = 0; i < destinations.size(); i++) {
                Lane lane = lanes.getFactory().createLane(Id.create(link.getId() + "." + (i + 1), Lane.class));
                lane.setStartsAtMeterFromLinkEnd(Math.min(50, link.getLength() / 2));
                lane.setNumberOfRepresentedLanes(link.getNumberOfLanes() / destinations.size());
                // OSM lane indices increase left to right; MATSim alignment does too.
                lane.setAlignment(2 * i - (destinations.size() - 1));
                lane.getAttributes().putAttribute("osmLaneIndex", physical.get(i) + 1);
                if (movements.motorPositions() != null)
                    lane.getAttributes().putAttribute("osmMotorLaneIndex", movements.motorPositions().indexOf(physical.get(i)) + 1);
                destinations.get(i).forEach(lane::addToLinkId);
                LanesUtils.calculateAndSetCapacity(lane, true, link, network);
                assignment.addLane(lane); original.addToLaneId(lane.getId());
            }
            lanes.addLanesToLinkAssignment(assignment);
            // Exercise MATSim's actual model builder, including lane tree consistency.
            LanesUtils.createLanes(link, assignment);
            report.count("linksWithLanes");
            report.count("assumedLaneLengths");
        }
    }

    private void validateConnectivity() {
        // Standard lanes cannot constrain the receiving lane across a junction.
        // Audit unsupported receiving-lane constraints and use ordinary inferred lanes.
        for (Osm.Relation relation : connectivity) {
            boolean applicable = members(relation, "from").stream().flatMap(w -> wayLinkMap.getOrDefault(w, List.of()).stream())
                    .map(network.getLinks()::get).filter(Objects::nonNull).anyMatch(link -> relation.getMembers().stream()
                            .anyMatch(e -> e instanceof Osm.Node && relation.getMemberRoles(e).contains("via")
                                    && ((Osm.Node) e).getId().toString().equals(link.getToNode().getId().toString())));
            if (applicable) {
                skipRelation(relation.getId(), "lane connectivity", "Exact receiving-lane connectivity is not representable in standard MATSim lanes");
                continue;
            }
            if (relation.getMembers().stream().anyMatch(e -> e instanceof Osm.Way && relation.getMemberRoles(e).contains("via"))) {
                skipRelation(relation.getId(), "lane connectivity", "Via-way connectivity is not representable in standard MATSim lanes");
                continue;
            }
            report.add("connectivityRelationsOutsideFinalNetwork", relation.getId(), "", "No incoming movement survives conversion/cleaning");
        }
    }

    private static IllegalArgumentException relationError(Object id, String message) {
        return new IllegalArgumentException("OSM relation " + id + ": " + message);
    }
}
