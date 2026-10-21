package org.matsim.pt2matsim.hafas.lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KantenReader {
    private static final Logger log = LogManager.getLogger(KantenReader.class);

    public static void readKanten(String kantenFile, Map<String, Coord> streckenpunkte, Network network, Charset encodingCharset) throws IOException {
        File file = new File(kantenFile);


        NetworkFactory factory = network.getFactory();
        int linkCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encodingCharset))) {
            String line;
            String fromNodeId = null;
            String toNodeId = null;
            boolean bidirectional = false;
            List<String> currentGeomPoints = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("*F") || line.startsWith("*TP")) continue;

                if (line.startsWith("*G ")) {
                    String[] parts = line.split(" +");
                    for (int i = 1; i < parts.length; i++) {
                        currentGeomPoints.add(parts[i]);
                    }
                } else {
                    if (fromNodeId != null && toNodeId != null) {
                        createLinks(fromNodeId, toNodeId, currentGeomPoints, bidirectional, streckenpunkte, network, factory);
                        linkCount++;
                    }

                    String[] parts = line.split(" +");
                    if (parts.length >= 2) {
                        fromNodeId = parts[0];
                        toNodeId = parts[1];
                        bidirectional = parts.length >= 3 && parts[2].equalsIgnoreCase("B");
                        currentGeomPoints.clear();
                    }
                }
            }
            if (fromNodeId != null && toNodeId != null) {
                createLinks(fromNodeId, toNodeId, currentGeomPoints, bidirectional, streckenpunkte, network, factory);
                linkCount++;
            }
        }
        log.info("Created links for {} kanten.", linkCount);
    }

    private static void createLinks(String fromId, String toId, List<String> geomPoints, boolean bidirectional, Map<String, Coord> streckenpunkte, Network network, NetworkFactory factory) {
        List<String> sequence = new ArrayList<>();
        sequence.add(fromId);
        sequence.addAll(geomPoints);
        sequence.add(toId);

        for (String ptId : sequence) {
            Id<Node> nodeId = Id.createNodeId(ptId);
            if (!network.getNodes().containsKey(nodeId)) {
                Coord coord = streckenpunkte.get(ptId);
                if (coord != null) {
                    network.addNode(factory.createNode(nodeId, coord));
                }
            }
        }

        for (int i = 0; i < sequence.size() - 1; i++) {
            String ptA = sequence.get(i);
            String ptB = sequence.get(i + 1);

            Node nodeA = network.getNodes().get(Id.createNodeId(ptA));
            Node nodeB = network.getNodes().get(Id.createNodeId(ptB));

            if (nodeA != null && nodeB != null && !nodeA.getId().equals(nodeB.getId())) {
                Id<Link> linkId = Id.createLinkId(nodeA.getId().toString() + "_" + nodeB.getId().toString());
                if (!network.getLinks().containsKey(linkId)) {
                    Link link = factory.createLink(linkId, nodeA, nodeB);
                    link.setLength(CoordUtils.calcEuclideanDistance(nodeA.getCoord(), nodeB.getCoord()));
                    link.setFreespeed(15.0);
                    network.addLink(link);
                }
                if (bidirectional) {
                    Id<Link> reverseLinkId = Id.createLinkId(nodeB.getId().toString() + "_" + nodeA.getId().toString());
                    if (!network.getLinks().containsKey(reverseLinkId)) {
                        Link reverseLink = factory.createLink(reverseLinkId, nodeB, nodeA);
                        reverseLink.setLength(CoordUtils.calcEuclideanDistance(nodeB.getCoord(), nodeA.getCoord()));
                        reverseLink.setFreespeed(15.0);
                        network.addLink(reverseLink);
                    }
                }
            }
        }
    }
}
