package org.matsim.pt2matsim.osm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.Osm.Node;
import org.matsim.pt2matsim.osm.lib.Osm.Way;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Export the full link geometry as WKT LineStrings for GIS software and Simunto
 * Via. Even if Via does not require the start end end node of each link to be
 * present in the LineString we export it anyways so this file is a
 * representation of the full network.
 */
public class LinkGeometryExporter {

	private final char SEPARATOR = ',';
	private Map<Id<Link>, LinkDefinition> linkDefinitions = new TreeMap<>();

	public void addLinkDefinition(Id<Link> linkId, LinkDefinition definition) {
		linkDefinitions.put(linkId, definition);
	}

	public void onlyKeepGeometryForTheseLinks(Collection<Id<Link>> keepLinkIds) {
		Collection<Id<Link>> toBeRemoved = linkDefinitions.keySet().parallelStream() //
				.filter(id -> !keepLinkIds.contains(id)) //
				.collect(Collectors.toSet());
		toBeRemoved.forEach(id -> linkDefinitions.remove(id));
	}

	public void writeToFile(Path outputPath) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
			writer.write("LinkId" + SEPARATOR + "Geometry\n");
			for (Entry<Id<Link>, LinkDefinition> entry : linkDefinitions.entrySet()) {
				Optional<String> wkt = toWkt(entry.getValue());
				if (wkt.isPresent()) {
					writer.write(String.format("%s%s\"%s\"\n", entry.getKey(), SEPARATOR, wkt.get()));
				}
			}
		}
	}

	private static Optional<String> toWkt(LinkDefinition linkDefinition) {
		int fromIndex = linkDefinition.way.getNodes().indexOf(linkDefinition.fromNode);
		int toIndex = linkDefinition.way.getNodes().indexOf(linkDefinition.toNode);
		if (fromIndex < 0 || toIndex < 0) {
			return Optional.empty();
		}

		List<Osm.Node> geometryNodes;
		if (fromIndex < toIndex) {
			geometryNodes = linkDefinition.way.getNodes().subList(fromIndex, toIndex + 1);
		} else {
			geometryNodes = Lists.reverse(linkDefinition.way.getNodes().subList(toIndex, fromIndex + 1));
		}

		return Optional.of(toWktLinestring(geometryNodes));
	}

	private static String toWktLinestring(List<Osm.Node> nodes) {
		List<String> coords = nodes.stream().map(node -> {
			Coord coord = node.getCoord();
			return String.format(Locale.ROOT, "%.5f %.5f", coord.getX(), coord.getY());
		}).collect(Collectors.toList());
		return "LINESTRING(" + Joiner.on(',').join(coords) + ")";
	}

	public static class LinkDefinition {
		public final Osm.Node fromNode;
		public final Osm.Node toNode;
		public final Osm.Way way;

		public LinkDefinition(Node fromNode, Node toNode, Way way) {
			this.fromNode = fromNode;
			this.toNode = toNode;
			this.way = way;
		}
	}

}
