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

import org.matsim.pt2matsim.osm.parser.OsmParser;

import java.util.Map;

/**
 * Interface to load an osm-xml file. Is used by the osm
 * network converter (similar to GtfsFeed and GtfsConverter)
 *
 * @author polettif
 */
public interface OsmData {

	Map<Long, OsmParser.OsmNode> getNodes();

	Map<Long, OsmParser.OsmRelation> getRelations();

	Map<Long, OsmParser.OsmWay> getWays();
}
