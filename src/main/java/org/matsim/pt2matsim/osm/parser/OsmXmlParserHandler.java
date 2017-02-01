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

package org.matsim.pt2matsim.osm.parser;

import org.matsim.api.core.v01.Coord;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.parser.handler.OsmNodeHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmRelationHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmWayHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler to read out osm data (nodes, ways and relations).
 *
 * NOTE: Just stores the primitive data. Nodes/ways/relations are not
 * referenced to each other.
 *
 * It is possible to add filters to reduce overhead.
 *
 * @author polettif
 */
public class OsmXmlParserHandler implements OsmNodeHandler, OsmRelationHandler, OsmWayHandler {
	
	private TagFilter nodeFilter;
	private TagFilter wayFilter;
	private TagFilter relationFilter;

	private final Map<Long, Osm.ParsedNode> nodes = new HashMap<>();
	private final Map<Long, Osm.ParsedRelation> relations = new HashMap<>();
	private final Map<Long, Osm.ParsedWay> ways = new HashMap<>();

	// node extent
	public double minX = Double.MAX_VALUE;
	public double minY = Double.MAX_VALUE;
	public double maxX = Double.MIN_VALUE;
	public double maxY = Double.MIN_VALUE;

	public OsmXmlParserHandler() {
	}

	public OsmXmlParserHandler(TagFilter... filters) {
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

	public Map<Long, Osm.ParsedWay> getWays() {
		return ways;
	}

	public Map<Long, Osm.ParsedNode> getNodes() {
		return nodes;
	}

	public Map<Long, Osm.ParsedRelation> getRelations() {
		return relations;
	}

	@Override
	public void handleNode(Osm.ParsedNode node) {
		if(nodeFilter == null || nodeFilter.matches(node.tags)) {
			nodes.put(node.id, node);

			updateExtent(node.coord);
		}
	}

	private void updateExtent(Coord c) {
		if(c.getX() < minX) minX = c.getX();
		if(c.getY() < minY) minY = c.getY();
		if(c.getX() > maxX) maxX = c.getX();
		if(c.getY() > maxY) maxY = c.getY();
	}

	@Override
	public void handleRelation(Osm.ParsedRelation relation) {
		if(relationFilter == null || relationFilter.matches(relation.tags)) {
			relations.put(relation.id, relation);
		}
	}

	@Override
	public void handleWay(Osm.ParsedWay way) {
		if(wayFilter == null || wayFilter.matches(way.tags)) {
			ways.put(way.id, way);
		}
	}
}

