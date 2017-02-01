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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.pt2matsim.osm.parser.TagFilter;

import java.util.*;

/**
 * @author polettif
 */
public class OsmDataImpl implements OsmData {

	private final static Logger log = Logger.getLogger(OsmData.class);

	private final Map<Id<Osm.Node>, Osm.Node> nodes = new HashMap<>();
	private final Map<Id<Osm.Way>, Osm.Way> ways = new HashMap<>();
	private final Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

	private final Map<Long, Osm.ParsedNode> parsedNodes = new HashMap<>();
	private final Map<Long, Osm.ParsedRelation> parsedRelations = new HashMap<>();
	private final Map<Long, Osm.ParsedWay> parsedWays = new HashMap<>();

	// node extent
	private double minX = Double.MAX_VALUE;
	private double minY = Double.MAX_VALUE;
	private double maxX = Double.MIN_VALUE;
	private double maxY = Double.MIN_VALUE;

	private QuadTree<Osm.Node> quadTree;

	// Filters
	private TagFilter nodeFilter;
	private TagFilter relationFilter;
	private TagFilter wayFilter;
	private TagFilter filter = new TagFilter(Osm.Element.NODE);

	public OsmDataImpl(TagFilter... filter) {
		for(TagFilter f : filter) {
			this.filter.mergeFilter(f);
		}
	}

	public void buildMap() {
		quadTree = new QuadTree<>(minX, minY, maxX, maxY);

		// create nodes
		for(Osm.ParsedNode pn : parsedNodes.values()) {
			Osm.Node newNode = new Osm.Node(pn.id, pn.coord, pn.tags);
			quadTree.put(newNode.getCoord().getX(), newNode.getCoord().getY(), newNode);
			if(nodes.put(newNode.getId(), newNode) != null) {
				throw new RuntimeException("Node id " + newNode.getId() + "already exists on map");
			}
		}

		// create ways
		for(Osm.ParsedWay pw : parsedWays.values()) {
			List<Osm.Node> nodeList = new ArrayList<>();
			for(Long id : pw.nodes) {
				nodeList.add(nodes.get(Id.create(id, Osm.Node.class)));
			}

			Osm.Way newWay = new Osm.Way(pw.id, nodeList, pw.tags);
			if(ways.put(newWay.getId(), newWay) != null) {
				throw new RuntimeException("Way id " + newWay.getId() + "already exists on map");
			}

			// add way to nodes
			for(Osm.Node n : nodeList) {
				n.addWay(newWay);
			}
		}

		// create relations
		for(Osm.ParsedRelation pr : parsedRelations.values()) {
			Osm.Relation newRel = new Osm.Relation(pr.id, pr.tags);
			if(relations.put(newRel.getId(), newRel) != null) {
				throw new RuntimeException("Relation id " + newRel.getId() + "already exists on map");
			}
		}

		// add relation members
		for(Osm.ParsedRelation pr : parsedRelations.values()) {

			Osm.Relation currentRel = relations.get(Id.create(pr.id, Osm.Relation.class));

			Map<Osm.OsmElement, String> memberRoles = new HashMap<>();
			List<Osm.OsmElement> memberList = new ArrayList<>();
			for(Osm.ParsedRelationMember pMember : pr.members) {
				Osm.OsmElement member = null;
				switch(pMember.type) {
					case NODE:
						member = nodes.get(Id.create(pMember.refId, Osm.Node.class));
						break;
					case WAY:
						member = ways.get(Id.create(pMember.refId, Osm.Way.class));
						break;
					case RELATION:
						member = relations.get(Id.create(pMember.refId, Osm.Relation.class));
						break;
				}
				// relation member might be outside of map area
				if(member != null) {
					memberList.add(member);
					memberRoles.put(member, pMember.role);
				}

				// add relations to nodes/ways/relations
				for(Osm.OsmElement e : memberList) {
					e.addRelation(currentRel);
				}
			}
			currentRel.setMembers(memberList, memberRoles);
		}

		// clear memory
		parsedNodes.clear();
		parsedWays.clear();
		parsedRelations.clear();
	}

	@Override
	public Map<Id<Osm.Node>, Osm.Node> getNodes() {
		return nodes;
	}

	@Override
	public Map<Id<Osm.Way>, Osm.Way> getWays() {
		return ways;
	}

	@Override
	public Map<Id<Osm.Relation>, Osm.Relation> getRelations() {
		return relations;
	}

	@Override
	public void removeNode(Id<Osm.Node> id) {
		Osm.Node n = nodes.get(id);
		for(Osm.Way w : n.getWays().values()) {
			w.getNodes().remove(n);
		}
		removeMemberFromRelations(n);
		nodes.remove(id);
	}

	@Override
	public void removeWay(Id<Osm.Way> id) {
		Osm.Way w = ways.get(id);
		for(Osm.Node n : w.getNodes()) {
			n.getWays().remove(w.getId());
		}
		removeMemberFromRelations(w);
		ways.remove(id);
	}

	@Override
	public void removeRelation(Id<Osm.Relation> id) {
		Osm.Relation rel = relations.get(id);

		for(Osm.OsmElement e : rel.getMembers()) {
			e.getRelations().remove(rel.getId());
		}

		removeMemberFromRelations(rel);
	}

	private void removeMemberFromRelations(Osm.OsmElement e) {
		for(Osm.Relation r : new HashSet<>(relations.values())) {
			if(r.getMembers().contains(e)) {
				r.getMembers().remove(e);
			}
		}
	}

	@Override
	public void handleNode(Osm.ParsedNode node) {
		if(nodeFilter == null || nodeFilter.matches(node.tags)) {
			parsedNodes.put(node.id, node);

			updateExtent(node.coord);
		}
	}

	private void updateExtent(Coord c) {
		if(c.getX() < minX) minX = c.getX();
		if(c.getY() < minY) minY = c.getY();
		if(c.getX() > maxX) maxX = c.getX();
		if(c.getY() > maxY) maxY = c.getY();
	}

	// todo combine filters

	@Override
	public void handleRelation(Osm.ParsedRelation relation) {
		if(relationFilter == null || relationFilter.matches(relation.tags)) {
			parsedRelations.put(relation.id, relation);
		}
	}

	@Override
	public void handleWay(Osm.ParsedWay way) {
		if(wayFilter == null || wayFilter.matches(way.tags)) {
			parsedWays.put(way.id, way);
		}
	}
}
