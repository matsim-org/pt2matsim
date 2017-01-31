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

import org.matsim.pt2matsim.osm.parser.handler.OsmNodeHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmRelationHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmWayHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler to read out osm data (nodes, ways and relations). Just stores the data.
 *
 * It is possible to add filters to reduce overhead.
 *
 * @author polettif
 */
public class OsmParserHandler implements OsmNodeHandler, OsmRelationHandler, OsmWayHandler {
	
	private TagFilter nodeFilter;
	private TagFilter wayFilter;
	private TagFilter relationFilter;

	private final Map<Long, Osm.Node> nodes = new HashMap<>();
	private final Map<Long, Osm.Relation> relations = new HashMap<>();
	private final Map<Long, Osm.Way> ways = new HashMap<>();

	public OsmParserHandler() {
	}

	public OsmParserHandler(TagFilter... filters) {
		for(TagFilter filter : filters) {
			switch(filter.getTag()) {
				case NODE:
					if(this.nodeFilter == null) {
						this.nodeFilter = filter;
					} else {
						this.nodeFilter.mergeFilter(filter);
					}
					break;
				case WAY:
					if(wayFilter == null) {
						wayFilter = filter;
					} else {
						wayFilter.mergeFilter(filter);
					}
					break;
				case RELATION:
					if(relationFilter == null) {
						relationFilter = filter;
					} else {
						relationFilter.mergeFilter(filter);
					}
					break;
			}
		}
	}

	public Map<Long, Osm.Way> getWays() {
		return ways;
	}

	public Map<Long, Osm.Node> getNodes() {
		return nodes;
	}

	public Map<Long, Osm.Relation> getRelations() {
		return relations;
	}

	@Override
	public void handleNode(Osm.Node node) {
		if(nodeFilter == null || nodeFilter.matches(node.tags)) {
			nodes.put(node.id, node);
		}
	}

	@Override
	public void handleRelation(Osm.Relation relation) {
		if(relationFilter == null || relationFilter.matches(relation.tags)) {
			relations.put(relation.id, relation);
		}
	}

	@Override
	public void handleWay(Osm.Way way) {
		if(wayFilter == null || wayFilter.matches(way.tags)) {
			ways.put(way.id, way);
		}
	}
}

