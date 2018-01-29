/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt2matsim.run.gis;

import com.vividsolutions.jts.geom.Coordinate;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Class to convert nodes and links to a network
 *
 * @author polettif
 */
public class Network2ShapeFile {

	private final String crs;
	private final Network network;

	public Network2ShapeFile(String crs, Network network) {
		this.crs = crs;
		this.network = network;
	}

	/**
	 * Converts a network to ESRI shape files (nodes and links)
	 *
	 * @param args [0] coordinate reference system (EPSG:*)
	 *             [1] input network
	 *             [2] output nodes file
	 *             [3] output links file
	 */
	public static void main(final String[] args) {
		if(args.length == 4) {
			run(args[0], args[1], args[2], args[3]);
		} else {
			throw new RuntimeException("Incorrect number of arguments");
		}
	}

	public static void run(String coordRefSys, String networkFile, String nodesOutputFile, String linksOutputFile) {
		run(coordRefSys, NetworkTools.readNetwork(networkFile), nodesOutputFile, linksOutputFile);
	}

	public static void run(String coordRefSys, Network network, String nodesOutputFile, String linksOutputFile) {
		Network2ShapeFile n2s = new Network2ShapeFile(coordRefSys, network);
		n2s.convertNodes(nodesOutputFile);
		n2s.convertLinks(linksOutputFile);
	}

	// todo use custom maps Map<Id<Link>, Object> for attributes (e.g. transit lines)

	public void convertNodes(String nodesOutputFile) {
		Collection<SimpleFeature> nodeFeatures = new ArrayList<>();
		PointFeatureFactory pointFeatureFactory = new PointFeatureFactory.Builder()
				.setName("nodes")
				.setCrs(MGC.getCRS(crs))
				.addAttribute("id", String.class)
				.addAttribute("inLinks", Double.class)
				.addAttribute("outLinks", Double.class)
				.create();

		for(Node node : network.getNodes().values()) {
			SimpleFeature f = pointFeatureFactory.createPoint(MGC.coord2Coordinate(node.getCoord()));
			f.setAttribute("id", node.getId());
			f.setAttribute("inLinks", node.getInLinks());
			f.setAttribute("outLinks", node.getOutLinks());
			nodeFeatures.add(f);
		}

		ShapeFileWriter.writeGeometries(nodeFeatures, nodesOutputFile);
	}

	public void convertLinks(String linksOutputFile) {
		Collection<SimpleFeature> linkFeatures = new ArrayList<>();
		PolylineFeatureFactory linkFactory = new PolylineFeatureFactory.Builder()
				.setName("links")
				.setCrs(MGC.getCRS(crs))
				.addAttribute("id", String.class)
				.addAttribute("length", Double.class)
				.addAttribute("freespeed", Double.class)
				.addAttribute("capacity", Double.class)
				.addAttribute("lanes", Double.class)
				.addAttribute("modes", String.class)
				.addAttribute("fromNode", String.class)
				.addAttribute("toNode", String.class)
				.create();

		for(Link link : network.getLinks().values()) {
			SimpleFeature f = linkFactory.createPolyline(getCoordinates(link));
			f.setAttribute("id", link.getId());
			f.setAttribute("length", link.getLength());
			f.setAttribute("freespeed", link.getFreespeed());
			f.setAttribute("capacity", link.getCapacity());
			f.setAttribute("lanes", link.getNumberOfLanes());
			f.setAttribute("fromNode", link.getFromNode());
			f.setAttribute("toNode", link.getToNode());
			f.setAttribute("modes", CollectionUtils.setToString(link.getAllowedModes()));
			linkFeatures.add(f);
		}

		ShapeFileWriter.writeGeometries(linkFeatures, linksOutputFile);
	}

	private Coordinate[] getCoordinates(Link link) {
		Coordinate[] coordinates = new Coordinate[2];
		coordinates[0] = MGC.coord2Coordinate(link.getFromNode().getCoord());
		coordinates[1] = MGC.coord2Coordinate(link.getToNode().getCoord());
		return coordinates;
	}
}
