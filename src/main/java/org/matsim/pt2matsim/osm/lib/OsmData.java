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

package org.matsim.pt2matsim.osm.lib;

import org.matsim.api.core.v01.Id;

import java.util.Map;

/**
 * Interface to load an osm-xml file. Is used by the osm
 * network converter (similar to GtfsFeed and GtfsConverter)
 *
 * Should be called OSMMap since it represents the map/network
 * but that's a bit redundant
 *
 * @author polettif
 */
public interface OsmData {

	Map<Id<Osm.Node>, Osm.Node> getNodes();
	Map<Id<Osm.Way>, Osm.Way> getWays();
	Map<Id<Osm.Relation>, Osm.Relation> getRelations();

	void removeNode(Id<Osm.Node> id);
	void removeWay(Id<Osm.Way> id);
	void removeRelation(Id<Osm.Relation> id);

	/**
	 * Creates the node/way/relation objects and connects them.
	 */
	void buildMap();

	void handleRelation(OsmFileReader.ParsedRelation parsedRelation);
	void handleWay(OsmFileReader.ParsedWay parsedWay);
	void handleNode(OsmFileReader.ParsedNode parsedNode);
}
