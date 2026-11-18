package org.matsim.pt2matsim.osm;

import java.util.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt2matsim.osm.lib.Osm;

/** Interpret complete lane-to-branch evidence at an ordinary two-exit fork. */
final class OsmForkEvidence {
    private OsmForkEvidence() { }

    record Branch(Link link, Osm.Way way, boolean forward, double angle) { }

    // Only project explicit incoming lane -> outgoing way pairs. MATSim lanes do not identify receiving lanes.
    static List<Set<Id<Link>>> connectivity(Osm.Way from, String via, List<Integer> slots, List<Branch> branches) {
        List<Set<Id<Link>>> lanes = new ArrayList<>();
        for (int slot : slots) lanes.add(new TreeSet<>());
        Set<Id<Link>> represented = new HashSet<>();
        for (Osm.Relation relation : from.getRelations().values()) {
            if (!"connectivity".equals(relation.getValue("type")) || !relation.getMemberRoles(from).contains("from")) continue;
            var nodes = relation.getMembers().stream().filter(e -> relation.getMemberRoles(e).contains("via")).toList();
            if (nodes.size() != 1 || !(nodes.getFirst() instanceof Osm.Node node) || !node.getId().toString().equals(via)) continue;
            if (relation.getMembers().size() != 3 || relation.getTags().containsKey("except")
                    || relation.getTags().keySet().stream().anyMatch(k -> k.startsWith("connectivity:") || k.contains("conditional")))
                return List.of();
            var targets = branches.stream().filter(b -> relation.getMemberRoles(b.way()).contains("to")).toList();
            if (targets.size() != 1) return List.of();
            Id<Link> exit = targets.getFirst().link().getId();
            if (!represented.add(exit)) return List.of();
            for (String pair : relation.getTags().getOrDefault("connectivity", "").split("\\|", -1)) {
                if (!pair.matches("[1-9][0-9]*:[1-9][0-9]*(,[1-9][0-9]*)*")) return List.of();
                int colon = pair.indexOf(':');
                try {
                    int lane = slots.indexOf(Integer.parseInt(pair.substring(0, colon)) - 1);
                    if (lane < 0 || !lanes.get(lane).add(exit)) return List.of();
                    for (String receiver : pair.substring(colon + 1).split(",")) {
                        if (Integer.parseInt(receiver) > targets.getFirst().link().getNumberOfLanes()) return List.of();
                    }
                } catch (NumberFormatException invalidIndex) {
                    return List.of();
                }
            }
        }
        return represented.size() == 2 && lanes.stream().noneMatch(Set::isEmpty) ? lanes : List.of();
    }

    static List<Set<Id<Link>>> signs(List<Integer> slots, int slotCount, String[] names, String[] refs, List<Branch> branches) {
        List<Set<Id<Link>>> result = new ArrayList<>();
        if (names.length != slotCount && refs.length != slotCount) return List.of();
        Set<Id<Link>> covered = new HashSet<>();
        List<Set<String>> branchNames = branches.stream().map(b -> labels(b, "destination")).toList();
        List<Set<String>> branchRefs = branches.stream().map(b -> labels(b, "destination:ref")).toList();
        for (int slot : slots) {
            Set<Id<Link>> byName = namedExits(names, slotCount, slot, branches, branchNames);
            Set<Id<Link>> byRef = namedExits(refs, slotCount, slot, branches, branchRefs);
            if (!byName.isEmpty() && !byRef.isEmpty() && !byName.equals(byRef)) return List.of();
            Set<Id<Link>> matches = byName.isEmpty() ? byRef : byName;
            if (matches.isEmpty()) return List.of();
            result.add(matches);
            covered.addAll(matches);
        }
        return covered.size() == 2 ? result : List.of();
    }

    private static Set<String> labels(Branch branch, String key) {
        Set<String> result = new HashSet<>();
        String suffix = branch.forward() ? ":forward" : ":backward";
        for (String field : List.of(key, key + ":lanes")) {
            String value = branch.way().getTags().getOrDefault(field + suffix, branch.way().getTags().getOrDefault(field, ""));
            result.addAll(tokens(value));
        }
        if (key.equals("destination:ref") && result.isEmpty()) result.addAll(tokens(branch.way().getTags().getOrDefault("ref", "")));
        return result;
    }

    private static Set<String> tokens(String value) {
        Set<String> labels = new HashSet<>();
        for (String s : value.split("[;|]")) {
            String token = s.strip().toLowerCase(Locale.ROOT).replace(" ", "");
            if (!token.isEmpty() && !token.equals("none")) labels.add(token);
        }
        return labels;
    }

    private static Set<Id<Link>> namedExits(String[] values, int slotCount, int slot, List<Branch> branches, List<Set<String>> labels) {
        Set<Id<Link>> matches = new TreeSet<>();
        if (values.length == slotCount) for (String token : tokens(values[slot])) {
            boolean first = labels.getFirst().contains(token), second = labels.getLast().contains(token);
            if (first != second) matches.add(branches.get(first ? 0 : 1).link().getId());
        }
        return matches;
    }

    // Resolve uncertain geometry only when one same-class road continues the approach's name or route.
    static List<Set<Id<Link>>> roadContinuation(Osm.Way from, List<String> arrows, Branch left, Branch right) {
        boolean l = continues(from, left.way()), r = continues(from, right.way());
        if (l == r || !arrows.contains("through")) return List.of();
        List<Set<Id<Link>>> result = new ArrayList<>();
        for (String lane : arrows) {
            Set<Id<Link>> targets = new TreeSet<>();
            for (String token : lane.split(";")) {
                boolean through = token.strip().equals("through");
                targets.add((through == l ? left : right).link().getId());
            }
            result.add(targets);
        }
        return result.stream().flatMap(Set::stream).distinct().count() == 2 ? result : List.of();
    }

    private static boolean continues(Osm.Way from, Osm.Way to) {
        if (!Objects.equals(from.getValue("highway"), to.getValue("highway"))) return false;
        String name = from.getValue("name");
        return name != null && !name.isBlank() && name.equals(to.getValue("name"))
                || !Collections.disjoint(tokens(from.getTags().getOrDefault("ref", "")), tokens(to.getTags().getOrDefault("ref", "")));
    }

    // A shared lane can serve both groups between the exclusive left and right lanes.
    static List<Set<Id<Link>>> orderedArrows(List<String> arrows, Branch left, Branch right) {
        List<Set<String>> groups = new ArrayList<>();
        Set<String> directions = new HashSet<>();
        for (String arrow : arrows) {
            Set<String> lane = new HashSet<>();
            for (String token : arrow.split(";", -1)) {
                String side = switch (token.strip()) {
                    case "left", "slight_left" -> "left";
                    case "through" -> "through";
                    case "right", "slight_right" -> "right";
                    default -> "";
                };
                if (side.isEmpty()) return List.of();
                lane.add(side);
            }
            directions.addAll(lane);
            groups.add(lane);
        }
        if (directions.size() != 2) return List.of();
        String first = directions.contains("left") ? "left" : "through";
        String last = directions.contains("right") ? "right" : "through";
        int previous = 0;
        List<Set<Id<Link>>> result = new ArrayList<>();
        for (Set<String> lane : groups) {
            int order = lane.size() == 2 ? 1 : lane.contains(first) ? 0 : 2;
            if (order < previous) return List.of();
            previous = order;
            Set<Id<Link>> exits = new TreeSet<>();
            if (lane.contains(first)) exits.add(left.link().getId());
            if (lane.contains(last)) exits.add(right.link().getId());
            result.add(exits);
        }
        return result;
    }
}
