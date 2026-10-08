package org.matsim.pt2matsim.lanes;

import java.util.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup.TravelCostType;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.PTMapperTools;

/** Bounded Dijkstra with restriction-prefix states, including the destination link. */
final class TurnRestrictedPathFinder {
    private record State(Id<Node> node, Set<List<Id<Link>>> pending) { }
    private record Label(State state, double cost, Label previous, Link via) { }

    private static Set<List<Id<Link>>> advance(Network network, Set<List<Id<Link>>> pending, Id<Link> id, String mode) {
        Set<List<Id<Link>>> next = new HashSet<>();
        for (var sequence : pending) if (sequence.getFirst().equals(id)) {
            if (sequence.size() == 1) return null;
            next.add(List.copyOf(sequence.subList(1, sequence.size())));
        }
        Link link = network.getLinks().get(id);
        if (link != null) {
            var restrictions = NetworkUtils.getDisallowedNextLinks(link);
            if (restrictions != null) next.addAll(restrictions.getDisallowedLinkSequences(mode));
        }
        return Set.copyOf(next);
    }

    static int firstForbiddenEnd(Network network, List<Id<Link>> path, String mode) {
        Set<List<Id<Link>>> pending = Set.of();
        for (int i = 0; i < path.size(); i++) {
            pending = advance(network, pending, path.get(i), mode);
            if (pending == null) return i;
        }
        return -1;
    }

    /** Returns intermediate link IDs; history includes the source candidate link. */
    static List<Id<Link>> find(Network network, List<Id<Link>> history, Link target, String mode,
            TravelCostType costType, double maxCost) {
        Set<List<Id<Link>>> pending = Set.of();
        for (Id<Link> id : history) {
            pending = advance(network, pending, id, mode);
            if (pending == null) throw new IllegalArgumentException("Route prefix already violates a restriction");
        }
        Link source = network.getLinks().get(history.getLast());
        if (source == null || target == null) return null;
        State start = new State(source.getToNode().getId(), pending);
        var queue = new PriorityQueue<Label>(Comparator.comparingDouble(Label::cost));
        var costs = new HashMap<State, Double>();
        costs.put(start, 0.0); queue.add(new Label(start, 0, null, null));
        while (!queue.isEmpty()) {
            Label label = queue.poll();
            if (label.cost > costs.getOrDefault(label.state, Double.POSITIVE_INFINITY)) continue;
            if (label.cost > maxCost) return null;
            if (label.state.node.equals(target.getFromNode().getId())
                    && advance(network, label.state.pending, target.getId(), mode) != null) {
                List<Id<Link>> result = new ArrayList<>();
                for (Label at = label; at.via != null; at = at.previous) result.add(at.via.getId());
                Collections.reverse(result); return result;
            }
            for (Link link : network.getNodes().get(label.state.node).getOutLinks().values()) {
                if (!link.getAllowedModes().contains(mode)
                        || link.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE)) continue;
                var nextPending = advance(network, label.state.pending, link.getId(), mode);
                if (nextPending == null) continue;
                double cost = label.cost + PTMapperTools.calcTravelCost(link, costType);
                if (!Double.isFinite(cost) || cost > maxCost) continue;
                State next = new State(link.getToNode().getId(), nextPending);
                if (cost < costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    costs.put(next, cost); queue.add(new Label(next, cost, label, link));
                }
            }
        }
        return null;
    }
}
