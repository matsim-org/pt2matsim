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
    private final Set<Id<Link>> ambiguousPhysicalLanes = new HashSet<>();
    private final Set<Id<Link>> ambiguousTurnLinks = new HashSet<>();
    private final Map<Id<Link>, List<Integer>> motorLanePositions = new HashMap<>();
    private final Map<Id<Link>, Map<Id<Link>, Set<String>>> uTurnModeExemptions = new HashMap<>();
    private final Set<String> auditedDirectionalCounts = new HashSet<>();
    private final Map<String, Double> derivedDirectionalCounts = new HashMap<>();
    private final Set<Id<Link>> inferredReservedPositionLinks = new HashSet<>();
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
            indexUTurnModeExemptions();
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
        return restrictionForMode(tags, mode, new HashSet<>());
    }

    private String restrictionForMode(Map<String, String> tags, String mode, Set<String> visited) {
        if (!visited.add(mode)) throw new IllegalArgumentException("Cyclic derived mode definition involving " + mode);
        List<String> aliases = switch (mode) {
            case "car" -> List.of("motorcar", "motor_vehicle", "vehicle");
            case "taxi" -> List.of("taxi", "psv", "motorcar", "motor_vehicle", "vehicle");
            case "bus", "pt" -> List.of("bus", "psv", "motor_vehicle", "vehicle");
            case "bike" -> List.of("bicycle", "vehicle");
            case "truck" -> List.of("hgv", "motor_vehicle", "vehicle");
            case "motorcycle" -> List.of("motorcycle", "motor_vehicle", "vehicle");
            case "walk" -> List.of("foot");
            default -> List.of(mode);
        };
        Set<String> except = Arrays.stream(tags.getOrDefault("except", "").split(";")).map(String::trim).collect(Collectors.toSet());
        if (except.contains(mode) || aliases.stream().anyMatch(except::contains)) return null;
        checkConditionalMode(tags, mode, mode);
        if (tags.containsKey("restriction:" + mode)) return turnRestrictionValue(tags.get("restriction:" + mode));
        if (aliases.equals(List.of(mode))) {
            Set<String> sources = config.getParameterSets(OsmConverterConfigGroup.RoutableSubnetworkParams.SET_NAME).stream()
                    .map(p -> (OsmConverterConfigGroup.RoutableSubnetworkParams) p).filter(p -> p.subnetworkMode.equals(mode))
                    .flatMap(p -> p.allowedTransportModes.stream()).filter(source -> !source.equals(mode)).collect(Collectors.toSet());
            if (!sources.isEmpty()) {
                Set<String> sourceRules = new HashSet<>();
                for (String source : sources) sourceRules.add(restrictionForMode(tags, source, new HashSet<>(visited)));
                if (sourceRules.size() > 1) throw new IllegalArgumentException("Derived mode " + mode
                        + " combines incompatible source-mode restrictions; specify restriction:" + mode + " explicitly");
                return sourceRules.iterator().next();
            }
        }
        for (String alias : aliases) {
            checkConditionalMode(tags, alias, mode);
            if (tags.containsKey("restriction:" + alias)) return turnRestrictionValue(tags.get("restriction:" + alias));
        }
        if (tags.containsKey("restriction:conditional") && !mode.equals("walk")) {
            throw new IllegalArgumentException("Conditional generic restriction for mode " + mode + " requires a time-aware model");
        }
        String generic = tags.get("restriction");
        return mode.equals("walk") || generic == null ? null : turnRestrictionValue(generic);
    }

    private static void checkConditionalMode(Map<String, String> tags, String alias, String mode) {
        if (tags.containsKey("restriction:" + alias + ":conditional") || tags.containsKey(alias + ":conditional")) {
            throw new IllegalArgumentException("Conditional rule for " + alias + " affects mode " + mode + "; requires a time-aware model");
        }
    }

    private static String turnRestrictionValue(String value) {
        if (Set.of("give_way", "stop", "none").contains(value)) return null;
        if (!RESTRICTION_VALUES.contains(value)) throw new IllegalArgumentException("Unsupported mode-specific restriction value " + value);
        return value;
    }

    private void generateLanes() {
        lanes = LanesUtils.createLanesContainer();
        for (Link link : network.getLinks().values().stream().sorted(Comparator.comparing(l -> l.getId().toString())).toList()) {
            Osm.Way way = osmData.getWays().get(osmIds.get(link.getId()));
            if (way == null || !way.getTags().containsKey("highway")) continue;
            if (!Double.isFinite(link.getNumberOfLanes()) || link.getNumberOfLanes() <= 0 || link.getNumberOfLanes() > 256
                    || !Double.isFinite(link.getLength()) || link.getLength() <= 0
                    || !Double.isFinite(link.getCapacity()) || link.getCapacity() <= 0) {
                throw new IllegalArgumentException("Invalid length, capacity or lane count on OSM way " + way.getId() + ", link " + link.getId());
            }
            List<Link> eligible = link.getToNode().getOutLinks().values().stream().map(l -> (Link) l)
                    .filter(out -> movementAllowed(link, out)).sorted(Comparator.comparing(l -> l.getId().toString())).toList();
            if (eligible.isEmpty()) {
                report.add("deadEndLinks", way.getId(), link.getId(), "No legal outgoing movement; no forbidden turn reopened");
                continue;
            }
            List<Integer> physical = physicalLanes(link, way);
            List<String> turnTags = turnTags(link, way, physical.size());
            Set<Id<Link>> onlyExitUTurns = new HashSet<>();
            eligible = applyInferredUTurnPolicy(link, way, eligible, physical, turnTags, onlyExitUTurns);
            List<Set<Id<Link>>> destinations = new ArrayList<>();
            boolean junction = eligible.stream().map(out -> osmIds.get(out.getId())).distinct().count() > 1;
            boolean atWayEnd = atWayEnd(link, way);
            boolean inferred = false;
            for (int i = 0; i < physical.size(); i++) {
                Set<Id<Link>> targets = new TreeSet<>();
                String tag = turnTags == null ? "" : turnTags.get(physical.get(i));
                List<Link> fallback = fallbackMovements(link, eligible, tag);
                if (!atWayEnd && !junction) {
                    eligible.stream().filter(out -> !out.getToNode().equals(link.getFromNode())).forEach(out -> targets.add(out.getId()));
                } else if (!tag.isBlank() && !tag.equals("none")) {
                    for (String direction : tag.split(";")) {
                        // Neighbouring car and bus exits may have different angles.
                        // Select each mode's best legal exit before combining lanes.
                        Set<Link> matches = new LinkedHashSet<>();
                        for (String mode : link.getAllowedModes()) {
                            DisallowedNextLinks restrictions = NetworkUtils.getDisallowedNextLinks(link);
                            List<Link> modeExits = eligible.stream().filter(out -> out.getAllowedModes().contains(mode))
                                    .filter(out -> restrictions == null || !restrictions.getDisallowedLinkSequences(mode).contains(List.of(out.getId())))
                                    .toList();
                            matches.addAll(matchTurn(link, modeExits, direction.trim()));
                        }
                        if (matches.isEmpty()) {
                            List<Link> physicalOutgoing = link.getToNode().getOutLinks().values().stream().map(l -> (Link) l)
                                    .filter(out -> out.getAllowedModes().stream().anyMatch(link.getAllowedModes()::contains)).toList();
                            if (!matchTurn(link, physicalOutgoing, direction.trim()).isEmpty()) {
                                report.add("conflictingTurnIndications", way.getId(), link.getId(), "Lane " + (physical.get(i) + 1)
                                        + ": " + direction + " conflicts with supported restrictions; infer legal outgoing movements");
                            }
                            inferred = true;
                            report.add("inferredLaneMovements", way.getId(), link.getId(), "Lane " + (physical.get(i) + 1) + ": cannot resolve " + direction + "; use legal outgoing movements, preferring non-U-turn exits");
                            fallback.forEach(out -> targets.add(out.getId()));
                        } else matches.forEach(out -> targets.add(out.getId()));
                    }
                } else {
                    inferred = true;
                    fallback.forEach(out -> targets.add(out.getId()));
                    report.add("inferredLaneMovements", way.getId(), link.getId(), "Lane " + (physical.get(i) + 1) + ": missing turn indications; use legal outgoing movements");
                }
                if (targets.isEmpty()) {
                    inferred = true;
                    fallback.forEach(out -> targets.add(out.getId()));
                    report.add("inferredLaneMovements", way.getId(), link.getId(), "Lane " + (physical.get(i) + 1)
                            + ": no matched continuation; use legal outgoing movements");
                }
                destinations.add(targets);
            }
            // Shared lane destinations must retain an exit needed by a mode
            // whose other exits are prohibited, even if another mode matched an arrow.
            Set<Id<Link>> served = destinations.stream().flatMap(Set::stream).collect(Collectors.toSet());
            for (Id<Link> target : onlyExitUTurns) if (!served.contains(target)) {
                inferred = true;
                destinations.get(0).add(target);
                report.add("uTurnOnlyExitLaneConnectionsRestored", way.getId(), link.getId(),
                        "Retain outgoing link " + target + " on first motor lane: only legal exit for a represented mode");
            }
            if (inferred || ambiguousTurnLinks.contains(link.getId()) || inferredReservedPositionLinks.contains(link.getId()))
                report.count("linksWithInferredConnections");
            Set<Id<Link>> covered = destinations.stream().flatMap(Set::stream).collect(Collectors.toSet());
            for (Link out : eligible) if (!covered.contains(out.getId())) {
                report.add("unservedLegalMovements", way.getId(), link.getId(), "Turn indications/connectivity provide no lane to outgoing link " + out.getId());
                // Routing must agree with the lane topology; do not route vehicles to a nonexistent lane movement.
                DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(link);
                if (dnl == null) {
                    dnl = new DisallowedNextLinks();
                    NetworkUtils.setDisallowedNextLinks(link, dnl);
                }
                for (String mode : link.getAllowedModes()) if (out.getAllowedModes().contains(mode)) dnl.addDisallowedLinkSequence(mode, List.of(out.getId()));
            }
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
                if (motorLanePositions.containsKey(link.getId()))
                    lane.getAttributes().putAttribute("osmMotorLaneIndex", motorLanePositions.get(link.getId()).indexOf(physical.get(i)) + 1);
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

    private boolean movementAllowed(Link from, Link out) {
        for (String mode : from.getAllowedModes()) if (out.getAllowedModes().contains(mode)) {
            DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(from);
            if (dnl == null || !dnl.getDisallowedLinkSequences(mode).contains(List.of(out.getId()))) return true;
        }
        return false;
    }

    private boolean movementAllowed(Link from, Link out, String mode) {
        if (!from.getAllowedModes().contains(mode) || !out.getAllowedModes().contains(mode)) return false;
        DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(from);
        return dnl == null || !dnl.getDisallowedLinkSequences(mode).contains(List.of(out.getId()));
    }

    private boolean immediateUTurn(Link from, Link out) {
        return !from.getFromNode().equals(from.getToNode())
                && out.getToNode().equals(from.getFromNode())
                && Objects.equals(osmIds.get(from.getId()), osmIds.get(out.getId()))
                && !Objects.equals(linkForward.get(from.getId()), linkForward.get(out.getId()));
    }

    private boolean turningFacility(Link link) {
        Osm.Node node = osmData.getNodes().get(Id.create(link.getToNode().getId().toString(), Osm.Node.class));
        return node != null && Set.of("turning_circle", "turning_loop", "mini_roundabout")
                .contains(node.getTags().getOrDefault("highway", ""));
    }

    private void indexUTurnModeExemptions() {
        // A signed no-U-turn relation with an explicit mode exemption is
        // stronger evidence than the missing-arrow inference default.
        for (Rule rule : rules) {
            if (skippedRelations.contains(rule.id()) || !"no_u_turn".equals(rule.tags().get("restriction"))
                    || rule.node() == null || !rule.via().isEmpty()) continue;
            for (Id<Osm.Way> fromWay : rule.from()) {
                for (Id<Link> id : wayLinkMap.getOrDefault(fromWay, List.of())) {
                    Link from = network.getLinks().get(id);
                    if (from == null || !from.getToNode().getId().toString().equals(rule.node().toString())) continue;
                    for (Link out : from.getToNode().getOutLinks().values()) {
                        if (!rule.to().contains(osmIds.get(out.getId())) || !immediateUTurn(from, out)) continue;
                        for (String mode : from.getAllowedModes()) {
                            if (Set.of("bike", "bicycle", "walk", "foot").contains(mode) || !movementAllowed(from, out, mode)) continue;
                            try {
                                if (restrictionForMode(rule.tags(), mode) == null)
                                    uTurnModeExemptions.computeIfAbsent(from.getId(), key -> new HashMap<>())
                                            .computeIfAbsent(out.getId(), key -> new TreeSet<>()).add(mode);
                            } catch (IllegalArgumentException unsupported) {
                                // Unsupported/time-dependent mode rules are already audited.
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean uTurnModeExempt(Link from, Link out, String mode) {
        Map<Id<Link>, Set<String>> targets = uTurnModeExemptions.get(from.getId());
        return targets != null && targets.getOrDefault(out.getId(), Set.of()).contains(mode);
    }

    private static boolean reverseIndication(String tag) {
        return tag != null && Arrays.stream(tag.split(";")).anyMatch(t -> t.trim().equals("reverse"));
    }

    private List<Link> applyInferredUTurnPolicy(Link link, Osm.Way way, List<Link> eligible,
                                               List<Integer> physical, List<String> turns, Set<Id<Link>> onlyExitTargets) {
        boolean explicit = turns != null && physical.stream().anyMatch(i -> reverseIndication(turns.get(i)));
        boolean missing = turns == null || physical.stream().anyMatch(i -> turns.get(i).isBlank() || turns.get(i).equals("none"));
        boolean facility = turningFacility(link), excluded = false;
        // Snapshot legal alternatives before adding inferred restrictions.
        Map<String, List<Link>> modeExits = new TreeMap<>();
        for (String mode : link.getAllowedModes()) modeExits.put(mode,
                eligible.stream().filter(out -> movementAllowed(link, out, mode)).toList());
        for (Link out : eligible) {
            if (!immediateUTurn(link, out)) continue;
            List<String> banned = new ArrayList<>();
            Map<String, List<String>> exceptions = new TreeMap<>();
            for (var entry : modeExits.entrySet()) {
                String mode = entry.getKey();
                if (!entry.getValue().contains(out)) continue;
                String reason = null;
                if (Set.of("bike", "bicycle", "walk", "foot").contains(mode)) reason = "uTurnNonMotorExceptions";
                else if (uTurnModeExempt(link, out, mode)) reason = "uTurnModeExemptionExceptions";
                else if (explicit) reason = "uTurnExplicitArrowExceptions";
                else if (facility) reason = "uTurnTurningFacilityExceptions";
                else if (entry.getValue().stream().allMatch(exit -> immediateUTurn(link, exit))) {
                    reason = "uTurnOnlyLegalExitExceptions";
                    onlyExitTargets.add(out.getId());
                } else if (missing) banned.add(mode);
                if (reason != null) exceptions.computeIfAbsent(reason, key -> new ArrayList<>()).add(mode);
            }
            if (!banned.isEmpty()) {
                DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(link);
                if (dnl == null) { dnl = new DisallowedNextLinks(); NetworkUtils.setDisallowedNextLinks(link, dnl); }
                for (String mode : banned) {
                    dnl.addDisallowedLinkSequence(mode, List.of(out.getId()));
                    report.count("inferredUTurnModeBans");
                }
                report.add("inferredUTurnMovementsExcluded", way.getId(), link.getId(),
                        "Outgoing link " + out.getId() + "; modes=" + banned
                                + "; immediate return on same OSM segment; alternative legal exits exist; missing turn arrows");
                excluded = true;
            }
            for (var exception : exceptions.entrySet()) report.add(exception.getKey(), way.getId(), link.getId(),
                    "Outgoing link " + out.getId() + "; modes=" + exception.getValue());
        }
        if (excluded) report.count("linksWithInferredUTurnExclusions");
        return eligible.stream().filter(out -> movementAllowed(link, out)).toList();
    }

    private List<Integer> physicalLanes(Link link, Osm.Way way) {
        List<? extends Link> directionLinks = wayLinkMap.getOrDefault(way.getId(), List.of()).stream().map(network.getLinks()::get)
                .filter(Objects::nonNull).filter(l -> l.getFromNode().equals(link.getFromNode()) && l.getToNode().equals(link.getToNode()))
                .filter(l -> Objects.equals(linkForward.get(l.getId()), linkForward.get(link.getId()))).toList();
        List<String> turns = turnTags(link, way, -1);
        int total = turns == null ? Math.max(1, (int) Math.ceil(directionLinks.stream().mapToDouble(Link::getNumberOfLanes).sum())) : turns.size();
        if (turns == null) {
            Double derived = derivedDirectionalCounts.get(way.getId() + ":" + (linkForward.get(link.getId()) ? "forward" : "backward"));
            if (derived != null) total = Math.max(1, (int) Math.ceil(derived));
            String count = way.getTags().get("lanes:" + (linkForward.get(link.getId()) ? "forward" : "backward"));
            if (count == null && directionalTag(way, "lanes", linkForward.get(link.getId())) != null) count = way.getTags().get("lanes");
            if (count != null) try { total = Math.max(1, (int) Math.ceil(Double.parseDouble(count))); }
            catch (NumberFormatException ignored) { /* Original converter already reports malformed counts. */ }
            // Access arrays can include bicycle slots omitted from lanes=*.
            for (String key : LANE_ACCESS_KEYS) {
                String value = directionalTag(way, key, linkForward.get(link.getId()));
                if (value != null) total = Math.max(total, value.split("\\|", -1).length);
            }
        }
        Set<String> motorModes = directionLinks.stream()
                .flatMap(l -> l.getAllowedModes().stream()).filter(m -> !Set.of("bike", "bicycle", "walk", "foot").contains(m))
                .collect(Collectors.toSet());
        Map<String, List<String>> accessTags = new LinkedHashMap<>();
        for (String key : LANE_ACCESS_KEYS) {
            String value = directionalTag(way, key, linkForward.get(link.getId()));
            if (value == null) continue;
            List<String> values = Arrays.asList(value.split("\\|", -1));
            if (values.size() != total) {
                report.add("laneAccessTagCountMismatches", way.getId(), link.getId(), key + " has " + values.size() + " slots; expected " + total);
            } else accessTags.put(key, values);
        }
        List<Integer> motorPositions = new ArrayList<>(), excluded = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            final int slot = i;
            // A designated bicycle lane is not by itself a motor-vehicle ban.
            if (motorModes.isEmpty() || motorModes.stream().anyMatch(mode -> laneAccessibleToMotorMode(accessTags, slot, mode))) motorPositions.add(i);
            else excluded.add(i);
        }
        boolean dedicated = link.getId().toString().endsWith("_spec");
        List<Integer> reserved = new ArrayList<>();
        for (String key : List.of("bus:lanes", "psv:lanes", "taxi:lanes")) {
            String value = directionalTag(way, key, linkForward.get(link.getId()));
            if (value == null) continue;
            String[] access = value.split("\\|", -1);
            if (access.length != total) {
                report.add("laneTagCountMismatches", way.getId(), link.getId(), key + " length differs from turn/count lanes");
                continue;
            }
            for (int i = 0; i < total; i++) if (Set.of("designated", "only", "exclusive").contains(access[i])) reserved.add(i);
            break;
        }
        boolean split = directionLinks.stream().anyMatch(l -> l.getId().toString().endsWith("_spec"));
        if (split && reserved.isEmpty()) {
            int count = (int) Math.ceil(directionLinks.stream().filter(l -> l.getId().toString().endsWith("_spec"))
                    .mapToDouble(Link::getNumberOfLanes).sum());
            Set<String> reservedModes = directionLinks.stream().filter(l -> l.getId().toString().endsWith("_spec"))
                    .flatMap(l -> l.getAllowedModes().stream()).filter(mode -> !mode.equals("pt")).collect(Collectors.toSet());
            Set<String> generalModes = directionLinks.stream().filter(l -> !l.getId().toString().endsWith("_spec"))
                    .flatMap(l -> l.getAllowedModes().stream()).filter(mode -> !mode.equals("pt")).collect(Collectors.toSet());
            List<Integer> exclusive = motorPositions.stream().filter(slot -> reservedModes.stream()
                    .anyMatch(mode -> laneAccessibleToMotorMode(accessTags, slot, mode)))
                    .filter(slot -> generalModes.stream().noneMatch(mode -> laneAccessibleToMotorMode(accessTags, slot, mode))).toList();
            if (!generalModes.isEmpty() && exclusive.size() == count) {
                reserved.addAll(exclusive);
                report.add("reservedLanePositionsFromAccess", way.getId(), link.getId(),
                        "Reserved motor slots " + reserved.stream().map(i -> i + 1).toList()
                                + " identified from exclusive mode-access arrays; tags=" + accessTags);
            }
            if (reserved.isEmpty() && count > 0 && count == motorPositions.size()) {
                reserved.addAll(motorPositions);
                report.add("reservedLanePositionsFromCounts", way.getId(), link.getId(),
                        "All motor slots reserved: directional count=" + motorPositions.size() + "; dedicated count=" + count);
            } else if (reserved.isEmpty() && count > 0 && count < motorPositions.size()) {
                reserved.addAll(motorPositions.subList(motorPositions.size() - count, motorPositions.size()));
                inferredReservedPositionLinks.add(link.getId());
                report.add("inferredReservedLanePositions", way.getId(), link.getId(),
                        "No designated reserved slots; infer rightmost motor slots " + reserved.stream().map(i -> i + 1).toList()
                                + " from dedicated count=" + count + "; assume right-side driving");
            }
        }
        List<Integer> selected = new ArrayList<>();
        for (int i : motorPositions) if (!split || reserved.isEmpty() || dedicated == reserved.contains(i)) selected.add(i);
        int expected = Math.max(1, (int) Math.ceil(link.getNumberOfLanes()));
        if (selected.size() != expected || (split && reserved.isEmpty())) {
            ambiguousPhysicalLanes.add(link.getId());
            report.add(split ? "inferredReservedLanePositions" : "laneTagCountMismatches", way.getId(), link.getId(), "Cannot identify physical lanes reliably; ignore lane-specific turn tags for this link");
            return java.util.stream.IntStream.range(0, expected).boxed().toList();
        }
        if (!excluded.isEmpty()) {
            motorLanePositions.put(link.getId(), List.copyOf(motorPositions));
            for (int slot : excluded) report.add("nonMotorLaneSlotsExcluded", way.getId(), link.getId(),
                    "Raw OSM slot " + (slot + 1) + " excludes represented motor modes " + motorModes + "; aligned access tags=" + accessTags);
            report.add("motorLaneLayoutsResolved", way.getId(), link.getId(), "Motor slots=" + motorPositions.stream().map(i -> i + 1).toList()
                    + "; selected slots=" + selected.stream().map(i -> i + 1).toList() + "; preserve matching turn entries");
        }
        return selected;
    }

    private static final List<String> LANE_ACCESS_KEYS = List.of("access:lanes", "vehicle:lanes", "motor_vehicle:lanes",
            "motorcar:lanes", "hgv:lanes", "psv:lanes", "bus:lanes", "taxi:lanes", "bicycle:lanes", "cycleway:lanes");

    private static boolean laneAccessibleToMotorMode(Map<String, List<String>> tags, int slot, String mode) {
        List<String> keys = new ArrayList<>(List.of("access:lanes", "vehicle:lanes", "motor_vehicle:lanes"));
        switch (mode) {
            case "car", "car_passenger" -> keys.add("motorcar:lanes");
            case "truck", "hgv" -> keys.add("hgv:lanes");
            case "bus", "pt" -> { keys.add("psv:lanes"); keys.add("bus:lanes"); }
            case "taxi" -> { keys.add("psv:lanes"); keys.add("taxi:lanes"); }
            default -> { }
        }
        String access = "yes";
        for (String key : keys) {
            List<String> values = tags.get(key);
            if (values != null && !values.get(slot).isBlank()) access = values.get(slot).trim();
        }
        return !access.equals("no");
    }

    private List<String> turnTags(Link link, Osm.Way way, int selectedCount) {
        String value = directionalTag(way, "turn:lanes", linkForward.get(link.getId()));
        if (value == null) return null;
        List<String> tags = Arrays.asList(value.split("\\|", -1));
        if (selectedCount >= 0 && ambiguousPhysicalLanes.contains(link.getId())) return null;
        return tags;
    }

    private String directionalTag(Osm.Way way, String key, boolean forward) {
        String directionalKey = key + (forward ? ":forward" : ":backward");
        if (ignoredReservedLaneArrays.contains(way.getId() + ":" + directionalKey)) return null;
        String directional = way.getTags().get(directionalKey);
        if (directional != null) return directional;
        String oneway = way.getTags().get("oneway");
        boolean isOneway = Set.of("yes", "1", "true", "-1").contains(oneway == null ? "" : oneway) || "roundabout".equals(way.getTags().get("junction"));
        return isOneway ? way.getTags().get(key) : null;
    }

    private boolean atWayEnd(Link link, Osm.Way way) {
        List<Id<Osm.Node>> nodes = geometry.get(way.getId());
        return link.getToNode().getId().toString().equals(nodes.get(linkForward.get(link.getId()) ? nodes.size() - 1 : 0).toString());
    }

    private List<Link> matchTurn(Link link, List<Link> eligible, String indication) {
        Double desired = switch (indication) {
            case "through" -> 0d;
            case "left" -> 90d;
            case "slight_left" -> 35d;
            case "sharp_left" -> 135d;
            case "right" -> -90d;
            case "slight_right" -> -35d;
            case "sharp_right" -> -135d;
            case "reverse" -> 180d;
            default -> null;
        };
        if (desired == null) return List.of();
        double best = Double.POSITIVE_INFINITY;
        List<Link> result = new ArrayList<>();
        for (Link out : eligible) {
            double angle = angle(link, out);
            boolean reverse = isReverseMovement(link, out, angle);
            // A return to the approach link is a U-turn, regardless of the sign
            // of its angle. Only an explicit reverse arrow may match it.
            if (desired == 180 ? !reverse : reverse) continue;
            // Forks can have shallow left/right branches. A 15-degree cutoff
            // incorrectly discards those branches (e.g. Wehntalerstrasse).
            if (desired > 0 && desired < 180 && angle <= 1e-6 || desired < 0 && angle >= -1e-6) continue;
            if (desired == 0 && Math.abs(angle) > 60) continue;
            double distance = Math.abs(angle - desired);
            if (desired == 180) distance = 180 - Math.abs(angle);
            if (distance < best - 1e-6) { best = distance; result.clear(); result.add(out); }
            else if (Math.abs(distance - best) < 1e-6) result.add(out);
        }
        if (result.stream().map(out -> osmIds.get(out.getId())).distinct().count() > 1) {
            ambiguousTurnLinks.add(link.getId());
            report.add("ambiguousTurnMatches", osmIds.get(link.getId()), link.getId(), indication + " matches multiple outgoing ways; retain all matches");
        }
        return result;
    }

    private List<Link> fallbackMovements(Link link, List<Link> eligible, String tag) {
        if (reverseIndication(tag)) return eligible;
        if (tag.isBlank() || tag.equals("none")) {
            // An explicit reverse arrow on another lane does not grant this
            // untagged lane a U-turn. Keep mode-specific sole exits and facilities.
            return eligible.stream().filter(out -> !immediateUTurn(link, out) || turningFacility(link)
                    || link.getAllowedModes().stream().anyMatch(mode -> movementAllowed(link, out, mode)
                    && (uTurnModeExempt(link, out, mode) || Set.of("bike", "bicycle", "walk", "foot").contains(mode)
                    || eligible.stream().filter(exit -> movementAllowed(link, exit, mode)).allMatch(exit -> immediateUTurn(link, exit)))))
                    .toList();
        }
        List<Link> ordinary = eligible.stream().filter(out -> !isReverseMovement(link, out, angle(link, out))).toList();
        // If the extract leaves only a legal U-turn, keep the existing audited
        // missing-arrow fallback policy rather than silently disconnecting it.
        return ordinary.isEmpty() ? eligible : ordinary;
    }

    private static boolean isReverseMovement(Link from, Link out, double angle) {
        return out.getToNode().equals(from.getFromNode()) || Math.abs(angle) >= 150;
    }

    private double angle(Link from, Link out) {
        double[] a = tangent(from, true), b = tangent(out, false);
        return Math.toDegrees(Math.atan2(a[0] * b[1] - a[1] * b[0], a[0] * b[0] + a[1] * b[1]));
    }

    private double[] tangent(Link link, boolean atEnd) {
        List<Id<Osm.Node>> nodes = geometry.get(osmIds.get(link.getId()));
        String endpoint = (atEnd ? link.getToNode() : link.getFromNode()).getId().toString();
        int index = -1;
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).toString().equals(endpoint)) { index = i; break; }
        int sign = linkForward.get(link.getId()) ? 1 : -1;
        int adjacent = index + (atEnd ? -sign : sign);
        if (index >= 0 && adjacent >= 0 && adjacent < nodes.size()) {
            var end = osmData.getNodes().get(nodes.get(index)).getCoord();
            var next = osmData.getNodes().get(nodes.get(adjacent)).getCoord();
            double dx = atEnd ? end.getX() - next.getX() : next.getX() - end.getX();
            double dy = atEnd ? end.getY() - next.getY() : next.getY() - end.getY();
            if (dx != 0 || dy != 0) return new double[]{dx, dy};
        }
        return new double[]{link.getToNode().getCoord().getX() - link.getFromNode().getCoord().getX(), link.getToNode().getCoord().getY() - link.getFromNode().getCoord().getY()};
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
