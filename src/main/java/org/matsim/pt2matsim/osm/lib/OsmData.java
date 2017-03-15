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
 * OSM data is read via {@link OsmFileReader}. After reading the file
 * new elements cannot be added directly (indirectly via handlers).
 *
 * @author polettif
 */
public interface OsmData {

	Map<Id<Osm.Node>, Osm.Node> getNodes();
	Map<Id<Osm.Way>, Osm.Way> getWays();
	Map<Id<Osm.Relation>, Osm.Relation> getRelations();

	/**
	 * Creates the node/way/relation objects from parsed data
	 * and connects them. Called in {@link OsmFileReader}
	 */
	void buildMap();


	/**
	 * Defines how a node should be handled in {@link OsmFileReader}
	 */
	void handleParsedNode(OsmFileReader.ParsedNode parsedNode);

	/**
	 * Defines how a way should be handled in {@link OsmFileReader}
	 */
	void handleParsedWay(OsmFileReader.ParsedWay parsedWay);

	/**
	 * Defines how a relation should be handled in {@link OsmFileReader}
	 */
	void handleParsedRelation(OsmFileReader.ParsedRelation parsedRelation);


	/**
	 * Removes the node from the osm data set. The node is removed from ways
	 * and relations as well. This might lead to inconsistencies.
	 */
	void removeNode(Id<Osm.Node> id);

	/**
	 * Removes the node from the osm data set. The way is removed from relations
	 * as well which might lead to inconsistencies.
	 */
	void removeWay(Id<Osm.Way> id);

	/**
	 * Removes the relation from the osm data set. The relation's members are not removed.
	 */
	void removeRelation(Id<Osm.Relation> id);
}
