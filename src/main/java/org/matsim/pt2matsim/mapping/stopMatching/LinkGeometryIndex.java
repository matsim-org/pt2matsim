package org.matsim.pt2matsim.mapping.stopMatching;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.*;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.config.*;

/** Directed road shapes and a spatial index independent of endpoint proximity. */
public final class LinkGeometryIndex {
    private final Network network;
    private final Map<Id<Link>, LineString> shapes = new HashMap<>();
    private final Map<Id<TransitStopFacility>, Map<Id<Link>, Id<Link>>> preferred = new HashMap<>();
    private final GeometryFactory factory = new GeometryFactory();
    private STRtree index;
    private long fallbackShapes;

    public LinkGeometryIndex(Network network, String geometryFile) {
        this.network = network;
        if (geometryFile != null && !geometryFile.isBlank()) read(geometryFile);
        rebuild();
    }

    private void read(String file) {
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8))) {
            List<String> header = Arrays.asList(Objects.requireNonNull(reader.readNext(), "Empty geometry CSV"));
            int idColumn = header.indexOf("LinkId"), shapeColumn = header.indexOf("Geometry");
            if (idColumn < 0 || shapeColumn < 0) throw new IllegalArgumentException("Geometry CSV needs LinkId and Geometry columns");
            WKTReader wkt = new WKTReader(factory);
            String[] row;
            while ((row = reader.readNext()) != null) {
                Link link = network.getLinks().get(Id.createLinkId(row[idColumn]));
                if (link == null) continue;
                Geometry parsed;
                try { parsed = wkt.read(row[shapeColumn]); }
                catch (ParseException | IllegalArgumentException exception) { continue; } // Degenerate exporter rows use endpoint fallback.
                if (!(parsed instanceof LineString line) || line.isEmpty()) continue;
                Coordinate from = coordinate(link.getFromNode().getCoord()), to = coordinate(link.getToNode().getCoord());
                if (line.getCoordinateN(0).distance(from) > .1 || line.getCoordinateN(line.getNumPoints() - 1).distance(to) > .1)
                    throw new IllegalArgumentException("Geometry endpoints/direction do not match link " + link.getId() + "; use the network's CRS and matching geometry export");
                shapes.put(link.getId(), line);
            }
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        catch (CsvValidationException exception) { throw new IllegalArgumentException("Invalid geometry CSV", exception); }
    }

    public static Coordinate coordinate(Coord coord) { return new Coordinate(coord.getX(), coord.getY()); }

    public LineString shape(Link link) {
        return shapes.computeIfAbsent(link.getId(), id -> factory.createLineString(new Coordinate[] {
                coordinate(link.getFromNode().getCoord()), coordinate(link.getToNode().getCoord())}));
    }

    public void rebuild() {
        index = new STRtree();
        for (Link link : network.getLinks().values()) {
            if (!shapes.containsKey(link.getId())) fallbackShapes++;
            index.insert(shape(link).getEnvelopeInternal(), link);
        }
        index.build();
    }

    public double distance(Link link, Coord coord) {
        return shape(link).distance(factory.createPoint(coordinate(coord)));
    }

    public boolean rightSide(Link link, Coord coord) {
        LengthIndexedLine indexed = new LengthIndexedLine(shape(link));
        double position = indexed.project(coordinate(coord)), length = shape(link).getLength();
        Coordinate a = indexed.extractPoint(Math.max(0, position - .1)), b = indexed.extractPoint(Math.min(length, position + .1));
        return (b.x - a.x) * (coord.getY() - a.y) - (b.y - a.y) * (coord.getX() - a.x) < 0;
    }

    public SortedMap<Double, Set<Link>> closest(TransitStopFacility stop, Set<String> modes,
                                               String scheduleMode, PublicTransitMappingConfigGroup config) {
        double radius = config.getMaxLinkCandidateDistance();
        int maximum = config.getNLinkThreshold();
        boolean strict = false;
        var parameters = config.getModeSpecificRules() ? config.getParameterSetForMode(scheduleMode) : null;
        if (parameters != null) {
            radius = parameters.getMaximumSearchDistance(); maximum = parameters.getNumberOfLinkCandidates();
            strict = parameters.getImposeStrictLinksRule();
        }
        Envelope envelope = new Envelope(coordinate(stop.getCoord())); envelope.expandBy(radius);
        SortedMap<Double, Set<Link>> distances = new TreeMap<>();
        Map<Id<Link>, Id<Link>> choices = preferred.getOrDefault(stop.getId(), Map.of());
        for (Object item : index.query(envelope)) {
            Link link = (Link) item;
            if (Collections.disjoint(link.getAllowedModes(), modes)) continue;
            Object parent = link.getAttributes().getAttribute(StopLinkPreparation.ORIGINAL_LINK);
            Id<Link> expected = choices.get(parent == null ? link.getId() : Id.createLinkId(parent.toString()));
            if (expected != null && !expected.equals(link.getId())) continue;
            double distance = distance(link, stop.getCoord());
            if (distance <= radius) distances.computeIfAbsent(distance, ignored -> new TreeSet<>(Comparator.comparing(l -> l.getId().toString()))).add(link);
        }
        SortedMap<Double, Set<Link>> result = new TreeMap<>();
        int count = 0; double softLimit = radius;
        for (var entry : distances.entrySet()) {
            if (count >= maximum && (strict || entry.getKey() > softLimit)) break;
            result.put(entry.getKey(), entry.getValue()); count += entry.getValue().size();
            if (count >= maximum && softLimit == radius) softLimit = entry.getKey() * config.getCandidateDistanceMultiplier();
        }
        return result;
    }

    void replace(Id<Link> parent, List<Link> parts, List<LineString> partShapes,
                 Map<Id<TransitStopFacility>, Integer> stopParts) {
        shapes.remove(parent);
        for (int i = 0; i < parts.size(); i++) shapes.put(parts.get(i).getId(), partShapes.get(i));
        stopParts.forEach((stop, part) -> preferred.computeIfAbsent(stop, ignored -> new HashMap<>()).put(parent, parts.get(part).getId()));
    }

    public long getFallbackShapes() { return fallbackShapes; }

    public void write(String file) {
        if (file == null) return;
        try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(Path.of(file), StandardCharsets.UTF_8))) {
            writer.writeNext(new String[] {"LinkId", "Geometry"});
            WKTWriter wkt = new WKTWriter();
            for (Link link : network.getLinks().values()) writer.writeNext(new String[] {link.getId().toString(), wkt.write(shape(link))});
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
}
