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
 * @author polettif
 */
public class OsmDataImpl implements OsmData{

	private final Map<Long, Osm.Node> nodes;
	private final Map<Long, Osm.Relation> relations;
	private final Map<Long, Osm.Way> ways;


	public OsmDataImpl(String osmFile) {
		TagFilter[] filters = TagFilter.getDefaultPTFilter();

		OsmParser parser = new OsmParser();
		OsmParserHandler handler = new OsmParserHandler(filters);
		parser.addHandler(handler);
		parser.run(osmFile);

		this.ways = handler.getWays();
		this.nodes = handler.getNodes();
		this.relations = handler.getRelations();
	}

	@Override
	public Map<Long, Osm.Node> getNodes() {
		return nodes;
	}

	@Override
	public Map<Long, Osm.Relation> getRelations() {
		return relations;
	}

	@Override
	public Map<Long, Osm.Way> getWays() {
		return ways;
	}
}
