package org.matsim.pt2matsim.osm;

import java.util.*;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinks;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;

/** Shared turn-arrow matching and audited movement inference, extracted from the lanes converter. */
final class OsmTurnMovementLogic {

    record UTurnRule(List<Id<Osm.Way>> from, List<Id<Osm.Way>> to, Id<Osm.Node> node, Map<String, String> tags) { }
    record Movements(List<Integer> physical, List<Set<Id<Link>>> destinations, List<Integer> motorPositions) { }
    private final Network network;
    private final OsmData osmData;
    private final Map<Id<Link>, Id<Osm.Way>> osmIds;
    private final Map<Id<Link>, Boolean> linkForward;
    private final Map<Id<Osm.Way>, List<Id<Link>>> wayLinkMap;
    private final Map<Id<Osm.Way>, List<Id<Osm.Node>>> geometry;
    private final LaneConversionReport report;
    private final Map<String, Double> derivedDirectionalCounts;
    private final Set<String> ignoredReservedLaneArrays;
    private final Map<Id<Link>, Map<Id<Link>, Set<String>>> uTurnModeExemptions = new HashMap<>();
    private final Set<Id<Link>> ambiguousPhysicalLanes = new HashSet<>();
    private final Set<Id<Link>> ambiguousTurnLinks = new HashSet<>();
    private final Map<Id<Link>, List<Integer>> motorLanePositions = new HashMap<>();
    private final Set<Id<Link>> inferredReservedPositionLinks = new HashSet<>();

    OsmTurnMovementLogic(OsmMultimodalNetworkConverter converter,
                         Map<Id<Osm.Way>, List<Id<Osm.Node>>> geometry, LaneConversionReport report,
                         Map<String, Double> derivedDirectionalCounts, Set<String> ignoredReservedLaneArrays) {
        network = converter.network;
        osmData = converter.osmData;
        osmIds = converter.osmIds;
        linkForward = converter.linkForward;
        wayLinkMap = converter.wayLinkMap;
        this.geometry = geometry;
        this.report = report;
        this.derivedDirectionalCounts = derivedDirectionalCounts;
        this.ignoredReservedLaneArrays = ignoredReservedLaneArrays;
    }

    void indexUTurnModeExemptions(List<UTurnRule> rules, OsmConverterConfigGroup config) {
        var resolver = new OsmRestrictionModeResolver(config);
        // A signed no-U-turn relation with an explicit mode exemption is
        // stronger evidence than the missing-arrow inference default.
        for (UTurnRule rule : rules) {
            if (!"no_u_turn".equals(rule.tags().get("restriction"))
                    || rule.node() == null) continue;
            for (Id<Osm.Way> fromWay : rule.from()) {
                for (Id<Link> id : wayLinkMap.getOrDefault(fromWay, List.of())) {
                    Link from = network.getLinks().get(id);
                    if (from == null || !from.getToNode().getId().toString().equals(rule.node().toString())) continue;
                    for (Link out : from.getToNode().getOutLinks().values()) {
                        if (!rule.to().contains(osmIds.get(out.getId())) || !immediateUTurn(from, out)) continue;
                        for (String mode : from.getAllowedModes()) {
                            if (Set.of("bike", "bicycle", "walk", "foot").contains(mode) || !movementAllowed(from, out, mode)) continue;
                            try {
                                if (resolver.restrictionForMode(rule.tags(), mode) == null)
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

    Movements resolve(Link link, Osm.Way way) {
    List<Link> eligible = link.getToNode().getOutLinks().values().stream().map(l -> (Link) l)
            .filter(out -> movementAllowed(link, out)).sorted(Comparator.comparing(l -> l.getId().toString())).toList();
    if (eligible.isEmpty()) {
        report.add("deadEndLinks", way.getId(), link.getId(), "No legal outgoing movement; no forbidden turn reopened");
        return null;
    }
    List<Integer> physical = physicalLanes(link, way);
    List<String> turnTags = turnTags(link, way, physical.size());
    boolean atWayEnd = atWayEnd(link, way);
    boolean junction = eligible.stream().map(out -> osmIds.get(out.getId())).distinct().count() > 1;
    if (!atWayEnd && junction && turnTags != null) {
        report.add("interiorWayArrowsDeferred", way.getId(), link.getId(),
                "Interior junction: the way's downstream arrows do not uniquely identify movements here");
        turnTags = null;
    }
    Set<Id<Link>> onlyExitUTurns = new HashSet<>();
    eligible = applyInferredUTurnPolicy(link, way, eligible, physical, turnTags, onlyExitUTurns);
    List<Set<Id<Link>>> destinations = new ArrayList<>();
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
    if (junction && !ambiguousPhysicalLanes.contains(link.getId())
            && matchFork(link, way, eligible, physical, turnTags, destinations, atWayEnd)) inferred = true;
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
        return new Movements(physical, destinations, motorLanePositions.get(link.getId()));
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

    private boolean matchFork(Link link, Osm.Way way, List<Link> eligible, List<Integer> slots, List<String> tags,
                              List<Set<Id<Link>>> destinations, boolean atWayEnd) {
        if (!atWayEnd && (way.getRelations().values().stream().noneMatch(r -> "connectivity".equals(r.getValue("type")))
                || link.getToNode().getInLinks().values().stream().filter(l -> way.getId().equals(osmIds.get(l.getId()))).count() != 1))
            return false;
        if (slots.size() < 2 || eligible.size() < 2 || eligible.size() > 3) return false;
        List<OsmForkEvidence.Branch> branches = new ArrayList<>(2);
        for (Link out : eligible) {
            double angle = angle(link, out);
            if (isReverseMovement(link, out, angle)) continue;
            Osm.Way target = osmData.getWays().get(osmIds.get(out.getId()));
            if (target == null) return false;
            branches.add(new OsmForkEvidence.Branch(out, target, linkForward.get(out.getId()), angle));
        }
        if (branches.size() != 2) return false;
        branches.sort(Comparator.comparingDouble(OsmForkEvidence.Branch::angle).reversed());
        var left = branches.getFirst();
        var right = branches.getLast();
        if (left.way().equals(right.way()) || left.link().getToNode().equals(right.link().getToNode())) return false;
        var both = Set.of(left.link().getId(), right.link().getId());
        // Lane permissions are shared across modes. Leave any explicit or sole-exit U-turn on its lane.
        if (destinations.stream().anyMatch(lane -> !both.containsAll(lane))) return false;

        List<Set<Id<Link>>> assignment = OsmForkEvidence.connectivity(way, link.getToNode().getId().toString(), slots, branches);
        String reason = "OSM connectivity";
        if (!atWayEnd && assignment.isEmpty()) return false;
        if (assignment.isEmpty()) {
            int slotCount = tags != null ? tags.size() : slots.getLast() + 1;
            assignment = OsmForkEvidence.signs(slots, slotCount, destinationLanes(link, way, "destination:lanes"),
                    destinationLanes(link, way, "destination:ref:lanes"), branches);
            if (!assignment.isEmpty() && tags != null && slots.stream().anyMatch(i -> tags.get(i).contains(";"))) {
                var ordered = OsmForkEvidence.orderedArrows(slots.stream().map(tags::get).toList(), left, right);
                for (int i = 0; i < slots.size(); i++) if (tags.get(slots.get(i)).contains(";")) {
                    assignment.get(i).addAll(destinations.get(i));
                    if (!ordered.isEmpty()) assignment.get(i).addAll(ordered.get(i));
                }
            }
            reason = "lane destinations";
        }
        if (assignment.isEmpty()) {
            if (tags == null || destinations.stream().flatMap(Set::stream).distinct().count() == 2) return false;
            // At least one branch must be a plausible through exit. Ordinary crossings,
            // sharp turns and turn-only approaches keep the existing indication matching.
            if (Math.abs(left.angle()) >= 90 || Math.abs(right.angle()) >= 90
                    || Math.min(Math.abs(left.angle()), Math.abs(right.angle())) > 60) return false;
            List<String> arrows = slots.stream().map(i -> tags.get(i).strip()).toList();
            assignment = OsmForkEvidence.orderedArrows(arrows, left, right);
            reason = "ordered lane groups";
            boolean bothStraight = arrows.stream().allMatch("through"::equals)
                    && Math.max(Math.abs(left.angle()), Math.abs(right.angle())) <= 60;
            if (assignment.isEmpty() && !bothStraight) return false;
            if (bothStraight || left.angle() - right.angle() < 1e-6
                    || downstreamAngle(link, left.link()) <= downstreamAngle(link, right.link()) + 1e-6) {
                List<Set<Id<Link>>> continuation = OsmForkEvidence.roadContinuation(way, arrows, left, right);
                if (continuation.isEmpty()) {
                    assignment = Collections.nCopies(slots.size(), both);
                    reason = "ambiguous fork; retain both branches";
                } else {
                    assignment = continuation;
                    reason = "through road identity";
                }
            }
        }
        if (assignment.equals(destinations)) return false;
        for (int i = 0; i < slots.size(); i++) destinations.set(i, new TreeSet<>(assignment.get(i)));
        report.add("forkLaneAssignments", way.getId(), link.getId(), reason + ": " + assignment);
        return true;
    }

    private String[] destinationLanes(Link link, Osm.Way way, String key) {
        String value = directionalTag(way, key, linkForward.get(link.getId()));
        return value == null ? new String[0] : value.split("\\|", -1);
    }

    // Confirm the side ordering along the outgoing road as well as at the first OSM segment.
    private double downstreamAngle(Link from, Link out) {
        List<Id<Osm.Node>> nodes = geometry.get(osmIds.get(out.getId()));
        var origin = out.getFromNode().getCoord();
        var end = origin;
        double remaining = Math.min(30.0, out.getLength());
        int i = nodes.indexOf(Id.create(out.getFromNode().getId().toString(), Osm.Node.class));
        int step = linkForward.get(out.getId()) ? 1 : -1;
        double dx = 0, dy = 0;
        while (i >= 0 && i + step >= 0 && i + step < nodes.size() && remaining > 0) {
            i += step;
            var next = osmData.getNodes().get(nodes.get(i)).getCoord();
            double x = next.getX() - end.getX(), y = next.getY() - end.getY(), length = Math.hypot(x, y);
            if (length > 0) {
                double fraction = Math.min(1, remaining / length);
                dx += x * fraction;
                dy += y * fraction;
                remaining -= length;
            }
            end = next;
            if (nodes.get(i).toString().equals(out.getToNode().getId().toString())) break;
        }
        double[] incoming = tangent(from, true);
        return Math.toDegrees(Math.atan2(incoming[0] * dy - incoming[1] * dx, incoming[0] * dx + incoming[1] * dy));
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

}
