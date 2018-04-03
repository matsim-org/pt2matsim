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
	 * Converts a network to a geojson file
	 *
	 * @param args [0] network coordinate system (coordinates are transformed to WGS84, use WGS84 for no transformation)
	 * 			   [1] input network file
	 *             [2] output network file (contains only links if nodes file is given)
	 *             [3] output nodes file (optional)
	 */
	public static void main(String[] args) {
		if(args.length == 4) {
			run(args[0], args[1], args[2], args[3]);
		} else if(args.length == 3) {
			run(args[0], args[1], args[2]);
		} else {
			throw new RuntimeException("Incorrect number of arguments " + args.length);
		}
	}

	public static void run(Network network, String outputFile) {
		run(null, network, outputFile);
	}

	public static void run(String networkCoordSys, String networkFile, String linksOutputFile, String nodesOutputFile) {
		run(networkCoordSys, NetworkTools.readNetwork(networkFile), linksOutputFile, nodesOutputFile);
	}

	public static void run(String networkCoordSys, String networkFile, String networkOutputFile) {
		run(networkCoordSys, NetworkTools.readNetwork(networkFile), networkOutputFile);
	}

	public static void run(String networkCoordSys, Network network, String linksOutputFile, String nodesOutputFile) {
		Network2Geojson n2g = new Network2Geojson(networkCoordSys, network);
		n2g.writeLinks(linksOutputFile);
		if(nodesOutputFile != null) {
			n2g.writeNodes(nodesOutputFile);
		}
	}

	public static void run(String networkCoordSys, Network network, String networkOutputFile) {
		Network2Geojson n2g = new Network2Geojson(networkCoordSys, network);
		n2g.writeNetwork(networkOutputFile);
	}


	protected static Logger log = Logger.getLogger(Network2Geojson.class);
	private final CoordinateTransformation ct;
	private Network network;
	private FeatureCollection linkFeatures = new FeatureCollection();
	private FeatureCollection nodeFeatures = new FeatureCollection();
	private FeatureCollection networkFeatureCollection;

	public Network2Geojson(String networkCoordSystem, Network network) {
		this.ct = networkCoordSystem == null ? new IdentityTransformation() : TransformationFactory.getCoordinateTransformation(networkCoordSystem, TransformationFactory.WGS84);
		this.network = network;
		convertLinks();
		convertNodes();
		combineFeatures();
	}

	public void writeNetwork(String networkOutputFile) {
		GeojsonTools.writeFeatureCollectionToFile(networkFeatureCollection, networkOutputFile);
		log.info("Network file (" + networkOutputFile + ") written.");
	}

	public void writeLinks(String linkOutputFile) {
		GeojsonTools.writeFeatureCollectionToFile(linkFeatures, linkOutputFile);
		log.info("Network file (" + linkOutputFile + ") written.");
	}

	public void writeNodes(String nodeOutputFile) {
		GeojsonTools.writeFeatureCollectionToFile(nodeFeatures, nodeOutputFile);
		log.info("Network file (" + nodeOutputFile + ") written.");
	}

	private void combineFeatures() {
		this.networkFeatureCollection = new FeatureCollection();
		this.networkFeatureCollection.addAll(linkFeatures.getFeatures());
		this.networkFeatureCollection.addAll(nodeFeatures.getFeatures());
	}

	private void convertNodes() {
		for(Node node : network.getNodes().values()) {
			Feature f = GeojsonTools.createPointFeature(ct.transform(node.getCoord()));
			f.setProperty("id", node.getId().toString());
//			f.setProperty("inLinks", MiscUtils.collectionToString(node.getInLinks().values()));
//			f.setProperty("outLinks", MiscUtils.collectionToString(node.getOutLinks().values()));
			this.nodeFeatures.add(f);
		}
	}

	private void convertLinks() {
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
			this.linkFeatures.add(f);
		}
	}

	private Feature createLineFeature(Link link) {
		List<Coord> list = new ArrayList<>();
		list.add(ct.transform(link.getFromNode().getCoord()));
		list.add(ct.transform(link.getToNode().getCoord()));
		return GeojsonTools.createLineFeature(list);
	}

}
