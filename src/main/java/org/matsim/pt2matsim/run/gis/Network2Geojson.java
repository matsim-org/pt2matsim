/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

import org.apache.log4j.Logger;
import org.geojson.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.tools.GeojsonTools;
import org.matsim.pt2matsim.tools.NetworkTools;

import java.util.ArrayList;
import java.util.List;

/**
 * @author polettif
 */
public class Network2Geojson {

	/**
	 * Converts a network to geojson files (nodes and links)
	 *
	 * @param args [0] network coordinate system (coordinates are transformed to WGS84)
	 * 			   [1] input network file
	 *             [2] output nodes file
	 *             [3] output links file
	 */
	public static void main(String[] args) {
		if(args.length == 3) {
			run(args[0], args[1], args[2], null);
		} else if(args.length == 4) {
			run(args[0], args[1], args[2], args[3]);
		} else {
			throw new RuntimeException("Incorrect number of arguments " + args.length);
		}
	}

	public static void run(String networkFile, String nodesOutputFile, String linksOutputFile, String networkCoordSys) {
		run(networkCoordSys, NetworkTools.readNetwork(networkFile), nodesOutputFile, linksOutputFile);
	}

	public static void run(String networkCoordSys, Network network, String nodesOutputFile, String linksOutputFile) {
		Network2Geojson n2g = new Network2Geojson(networkCoordSys, network);
		n2g.convertNodes(nodesOutputFile);
		n2g.convertLinks(linksOutputFile);
	}

	protected static Logger log = Logger.getLogger(Network2Geojson.class);
	private final CoordinateTransformation ct;
	private Network network;

	public Network2Geojson(String networkCoordSystem, Network network) {
		this.ct = networkCoordSystem == null ? new IdentityTransformation() : TransformationFactory.getCoordinateTransformation(networkCoordSystem, TransformationFactory.WGS84);
		this.network = network;
	}

	public void convertNodes(String nodesOutputFile) {
		FeatureCollection featureCollection = new FeatureCollection();

		for(Node node : network.getNodes().values()) {
			Feature f = GeojsonTools.createPointFeature(ct.transform(node.getCoord()));
			f.setProperty("id", node.getId().toString());
//			f.setProperty("inLinks", MiscUtils.collectionToString(node.getInLinks().values()));
//			f.setProperty("outLinks", MiscUtils.collectionToString(node.getOutLinks().values()));
			featureCollection.add(f);
		}

		GeojsonTools.writeFeatureCollectionToFile(featureCollection, nodesOutputFile);
		log.info("Nodes file (" + nodesOutputFile + ") written.");
	}

	public void convertLinks(String linksOutputFile) {
		FeatureCollection linkFeatures = new FeatureCollection();

		for(Link link : network.getLinks().values()) {
			Feature f = createLineFeature(link);
			f.setProperty("id", link.getId().toString());
			f.setProperty("length", link.getLength());
			f.setProperty("freespeed", link.getFreespeed());
			f.setProperty("capacity", link.getCapacity());
			f.setProperty("lanes", link.getNumberOfLanes());
			f.setProperty("fromNode", link.getFromNode().getId().toString());
			f.setProperty("toNode", link.getToNode().getId().toString());
			f.setProperty("modes", CollectionUtils.setToString(link.getAllowedModes()));
			linkFeatures.add(f);
		}

		GeojsonTools.writeFeatureCollectionToFile(linkFeatures, linksOutputFile);
		log.info("Links file (" + linksOutputFile + ") written.");
	}

	private Feature createLineFeature(Link link) {
		List<Coord> list = new ArrayList<>();
		list.add(ct.transform(link.getFromNode().getCoord()));
		list.add(ct.transform(link.getToNode().getCoord()));
		return GeojsonTools.createLineFeature(list);
	}

}
