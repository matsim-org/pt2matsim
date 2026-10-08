package org.matsim.pt2matsim.mapping.stopMatching;

import com.opencsv.CSVWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.turnRestrictions.*;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.tools.NetworkTools;

/** Optional stop projection/splitting, independent of lane-file conversion. */
public final class StopLinkPreparation {
    public static final String ORIGINAL_LINK = "pt2matsim:stopMatchingOriginalLink";
    public static final String PART_INDEX = "pt2matsim:stopMatchingPartIndex";
    public static final String PART_COUNT = "pt2matsim:stopMatchingPartCount";
    public static final String ORIGINAL_LENGTH = "pt2matsim:stopMatchingOriginalLength";
    public static final String END_FRACTION = "pt2matsim:stopMatchingEndFraction";
    private static final double EPSILON = .001;

    private StopLinkPreparation() { }

    public static LinkGeometryIndex prepare(Network network, TransitSchedule schedule, PublicTransitMappingConfigGroup config) {
        LinkGeometryIndex geometry = new LinkGeometryIndex(network, config.getInputNetworkGeometryFile());
        Map<String, Long> counts = new TreeMap<>();
        List<String[]> details = new ArrayList<>();
        Map<Id<Link>, TreeMap<Double, Set<Id<TransitStopFacility>>>> projections = new TreeMap<>(Comparator.comparing(Id::toString));
        Set<String> visited = new HashSet<>();
        if (config.getSplitLinksAtStops()) {
            for (var line : schedule.getTransitLines().values()) for (var route : line.getRoutes().values()) {
                String mode = route.getTransportMode();
                if (!config.getStopLinkSplittingModes().contains(mode)) continue;
                Set<String> networkModes = config.getTransportModeAssignment().getOrDefault(mode, Set.of());
                for (var stop : route.getStops()) {
                    TransitStopFacility facility = stop.getStopFacility();
                    if (!visited.add(mode + ":" + facility.getId())) continue;
                    var candidates = geometry.closest(facility, networkModes, mode, config);
                    if (candidates.isEmpty()) {
                        counts.merge("stopsWithoutEligibleRoadCandidates", 1L, Long::sum);
                        details.add(new String[] {"noEligibleRoadCandidate", "", facility.getId().toString(), "mode=" + mode + "; no eligible road geometry within configured distance"});
                    }
                    for (var group : candidates.values()) for (Link link : group) {
                        if (link.getAllowedModes().contains("artificial")) continue;
                        if (link.getAttributes().getAttribute(ORIGINAL_LINK) != null)
                            throw new IllegalArgumentException("Stop splitting expects an unsplit input network: " + link.getId());
                        LineString shape = geometry.shape(link);
                        double position = new LengthIndexedLine(shape).project(LinkGeometryIndex.coordinate(facility.getCoord()));
                        if (position <= EPSILON || position >= shape.getLength() - EPSILON) continue;
                        projections.computeIfAbsent(link.getId(), ignored -> new TreeMap<>())
                                .computeIfAbsent(position, ignored -> new TreeSet<>(Comparator.comparing(Id::toString))).add(facility.getId());
                    }
                }
            }
        }
        // Capture restrictions before replacing links; source IDs stay on the
        // final piece, where the original junction decisions still occur.
        Map<Id<Link>, DisallowedNextLinks> restrictions = new HashMap<>();
        if (!projections.isEmpty()) for (Link link : network.getLinks().values()) {
            var rules = NetworkUtils.getDisallowedNextLinks(link);
            if (rules != null) restrictions.put(link.getId(), rules.copy());
        }
        Map<Id<Link>, List<Id<Link>>> partsByParent = new HashMap<>();
        for (var entry : projections.entrySet()) {
            Link parent = network.getLinks().get(entry.getKey());
            LineString shape = geometry.shape(parent);
            LengthIndexedLine indexed = new LengthIndexedLine(shape);
            // Merge numerically coincident projections, not different stops
            // several metres apart. Very short pieces are audited explicitly.
            TreeMap<Double, Set<Id<TransitStopFacility>>> cuts = new TreeMap<>();
            for (var projection : entry.getValue().entrySet()) {
                Double last = cuts.isEmpty() ? null : cuts.lastKey();
                if (last != null && projection.getKey() - last < EPSILON) cuts.get(last).addAll(projection.getValue());
                else cuts.put(projection.getKey(), new TreeSet<>(projection.getValue()));
            }
            List<Double> positions = new ArrayList<>(); positions.add(0.0); positions.addAll(cuts.keySet()); positions.add(shape.getLength());
            List<Node> nodes = new ArrayList<>(); nodes.add(parent.getFromNode());
            for (int i = 1; i + 1 < positions.size(); i++) {
                Id<Node> id = Id.createNodeId("stopMatch:" + parent.getId() + ":" + i);
                if (network.getNodes().containsKey(id)) throw new IllegalArgumentException("Split node ID collision: " + id);
                Coordinate point = indexed.extractPoint(positions.get(i));
                Node node = network.getFactory().createNode(id, new Coord(point.x, point.y));
                network.addNode(node); nodes.add(node);
            }
            nodes.add(parent.getToNode());
            List<Link> parts = new ArrayList<>(); List<LineString> shapes = new ArrayList<>();
            network.removeLink(parent.getId());
            for (int i = 0; i + 1 < positions.size(); i++) {
                Id<Link> id = i + 2 == positions.size() ? parent.getId() : Id.createLinkId("stopMatch:" + parent.getId() + ":" + (i + 1));
                if (network.getLinks().containsKey(id)) throw new IllegalArgumentException("Split link ID collision: " + id);
                Link part = network.getFactory().createLink(id, nodes.get(i), nodes.get(i + 1));
                double fraction = (positions.get(i + 1) - positions.get(i)) / shape.getLength();
                part.setLength(parent.getLength() * fraction); part.setFreespeed(parent.getFreespeed());
                part.setCapacity(parent.getCapacity()); part.setNumberOfLanes(parent.getNumberOfLanes());
                part.setAllowedModes(new HashSet<>(parent.getAllowedModes()));
                parent.getAttributes().getAsMap().forEach(part.getAttributes()::putAttribute);
                NetworkUtils.removeDisallowedNextLinks(part);
                part.getAttributes().putAttribute(ORIGINAL_LINK, parent.getId().toString());
                part.getAttributes().putAttribute(PART_INDEX, i);
                part.getAttributes().putAttribute(PART_COUNT, positions.size() - 1);
                part.getAttributes().putAttribute(ORIGINAL_LENGTH, parent.getLength());
                part.getAttributes().putAttribute(END_FRACTION, positions.get(i + 1) / shape.getLength());
                network.addLink(part); parts.add(part);
                shapes.add((LineString) indexed.extractLine(positions.get(i), positions.get(i + 1)));
                if (part.getLength() < 1) {
                    counts.merge("subMetreFragments", 1L, Long::sum);
                    details.add(new String[] {"subMetreFragment", id.toString(), "", "length=" + part.getLength()});
                }
            }
            Map<Id<TransitStopFacility>, Integer> stopParts = new HashMap<>();
            int part = 0;
            for (var cut : cuts.entrySet()) {
                for (Id<TransitStopFacility> stop : cut.getValue()) {
                    stopParts.put(stop, part);
                    TransitStopFacility facility = schedule.getFacilities().get(stop);
                    if (parent.getId().equals(facility.getLinkId())) facility.setLinkId(parts.get(part).getId());
                    details.add(new String[] {"projectedStop", parent.getId().toString(), stop.toString(),
                            "fragment=" + parts.get(part).getId() + "; offset=" + cut.getKey()});
                }
                part++;
            }
            geometry.replace(parent.getId(), parts, shapes, stopParts);
            partsByParent.put(parent.getId(), parts.stream().map(Link::getId).toList());
            counts.merge("splitOriginalLinks", 1L, Long::sum);
            counts.merge("addedLinks", (long) parts.size() - 1, Long::sum);
            counts.merge("projectedStopAssignments", (long) stopParts.size(), Long::sum);
        }
        for (var source : restrictions.entrySet()) {
            DisallowedNextLinks updated = new DisallowedNextLinks();
            for (var mode : source.getValue().getAsMap().entrySet()) for (var sequence : mode.getValue()) {
                List<Id<Link>> expanded = new ArrayList<>();
                for (int i = 0; i < sequence.size(); i++) {
                    List<Id<Link>> parts = partsByParent.getOrDefault(sequence.get(i), List.of(sequence.get(i)));
                    // A forbidden final link means forbidden entry into its
                    // first piece, even if the route ends at an interior stop.
                    expanded.addAll(i + 1 == sequence.size() ? List.of(parts.get(0)) : parts);
                }
                updated.addDisallowedLinkSequence(mode.getKey(), expanded);
            }
            NetworkUtils.setDisallowedNextLinks(network.getLinks().get(source.getKey()), updated);
        }
        if (!DisallowedNextLinksUtils.isValid(network)) throw new IllegalArgumentException("Invalid restrictions after stop splitting");
        geometry.rebuild();
        counts.put("endpointGeometryFallbacks", geometry.getFallbackShapes());
        LogManager.getLogger(StopLinkPreparation.class).info("Stop matching preparation: {}", counts);
        if (config.getOutputPreparedNetworkFile() != null) NetworkTools.writeNetwork(network, config.getOutputPreparedNetworkFile());
        if (config.getStopMatchingReportFile() != null) {
            try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(Path.of(config.getStopMatchingReportFile()), StandardCharsets.UTF_8))) {
                writer.writeNext(new String[] {"category", "linkId", "stopId", "detail"});
                for (var count : counts.entrySet()) writer.writeNext(new String[] {"COUNT", count.getKey(), "", count.getValue().toString()});
                for (String[] detail : details) writer.writeNext(detail);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
        return geometry;
    }
}
