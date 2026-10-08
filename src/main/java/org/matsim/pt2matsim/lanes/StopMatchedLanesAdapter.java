package org.matsim.pt2matsim.lanes;

import java.util.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.lanes.*;
import org.matsim.pt2matsim.mapping.stopMatching.StopLinkPreparation;
import org.matsim.pt2matsim.osm.LaneConversionReport;

/** Moves original junction lanes onto the final stop-split road fragment. */
public final class StopMatchedLanesAdapter {
    private StopMatchedLanesAdapter() { }

    public static void adapt(Network preparedNetwork, Lanes lanes, LaneConversionReport report) {
        Map<Id<Link>, List<Link>> fragments = new HashMap<>();
        for (Link link : preparedNetwork.getLinks().values()) {
            Object parent = link.getAttributes().getAttribute(StopLinkPreparation.ORIGINAL_LINK);
            if (parent != null) fragments.computeIfAbsent(Id.createLinkId(parent.toString()), ignored -> new ArrayList<>()).add(link);
        }
        fragments.values().forEach(parts -> parts.sort(Comparator.comparingInt(l ->
                ((Number) l.getAttributes().getAttribute(StopLinkPreparation.PART_INDEX)).intValue())));
        for (var assignment : lanes.getLanesToLinkAssignments().values()) {
            for (Lane lane : assignment.getLanes().values()) if (lane.getToLinkIds() != null) {
                List<Id<Link>> remapped = lane.getToLinkIds().stream().map(id ->
                        fragments.containsKey(id) ? fragments.get(id).getFirst().getId() : id).distinct().toList();
                lane.getToLinkIds().clear(); remapped.forEach(lane::addToLinkId);
            }
            List<Link> parts = fragments.get(assignment.getLinkId());
            if (parts == null) continue;
            Link last = parts.getLast();
            if (!last.getId().equals(assignment.getLinkId())) throw new IllegalArgumentException("Original junction link ID is not on final fragment");
            Set<Id<Lane>> children = new HashSet<>();
            for (Lane lane : assignment.getLanes().values()) if (lane.getToLaneIds() != null) children.addAll(lane.getToLaneIds());
            List<Lane> roots = assignment.getLanes().values().stream().filter(l -> !children.contains(l.getId())).toList();
            if (roots.size() != 1) throw new IllegalArgumentException("Expected one original entry lane on " + assignment.getLinkId());
            Lane root = roots.getFirst(); root.setStartsAtMeterFromLinkEnd(last.getLength());
            double furthest = assignment.getLanes().values().stream().filter(l -> l != root)
                    .mapToDouble(Lane::getStartsAtMeterFromLinkEnd).max().orElse(0);
            double scale = furthest >= last.getLength() ? last.getLength() * .5 / furthest : 1;
            for (Lane lane : assignment.getLanes().values()) if (lane != root && scale < 1) {
                double previous = lane.getStartsAtMeterFromLinkEnd();
                lane.setStartsAtMeterFromLinkEnd(previous * scale);
                report.add("stopMatchingLaneLengthsAdjusted", "", last.getId(), "lane=" + lane.getId()
                        + "; startsAt=" + previous + " -> " + lane.getStartsAtMeterFromLinkEnd()
                        + "; junction lane sections shortened to fit final stop fragment; capacity/represented lanes retained");
            }
            LanesUtils.createLanes(last, assignment);
            report.add("stopMatchingLaneAssignmentsAdapted", "", last.getId(), "fragments=" + parts.size()
                    + "; junction assignment retained on final fragment; internal fragments allow continuation only");
        }
    }
}
