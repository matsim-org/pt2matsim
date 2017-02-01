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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementations of different OSM elements
 *
 * @author polettif
 */
public class OsmImpl {
	public static class ParsedNode {
		public final long id;
		public final Coord coord;
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public ParsedNode(final long id, final Coord coord) {
			this.id = id;
			this.coord = coord;
		}
	}

	public static class ParsedWay {
		public final long id;
		public final List<Long> nodes = new ArrayList<>(6);
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public ParsedWay(final long id) {
			this.id = id;
		}
	}

	public static class ParsedRelation {
		public final long id;
		public final List<ParsedRelationMember> members = new ArrayList<>(8);
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public ParsedRelation(final long id) {
			this.id = id;
		}
	}

	public static class ParsedRelationMember {
		public final Osm.ElementType type;
		public final long refId;
		public final String role;

		public ParsedRelationMember(final Osm.ElementType type, final long refId, final String role) {
			this.type = type;
			this.refId = refId;
			this.role = role;
		}
	}

	/**
	 * OSM node
	 */
	public static class OsmNode implements Osm.Node {

		private final Id<Osm.Node> id;
		private final Coord coord;
		private final Map<String, String> tags;

		private Map<Id<Osm.Way>, Osm.Way> ways = new HashMap<>();
		private Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

		public OsmNode(final long id, final Coord coord, Map<String, String> tags) {
			this.id = Id.create(id, Osm.Node.class);
			this.coord = coord;
			this.tags = tags;
		}

		@Override
		public Id<Osm.Node> getId() {
			return id;
		}

		public Coord getCoord() {
			return coord;
		}

		@Override
		public Map<String, String> getTags() {
			return tags;
		}

		@Override
		public String getValue(String key) {
			return tags.get(key);
		}

		/*pckg*/ void addRelation(Osm.Relation rel) {
			relations.put(rel.getId(), rel);
		}

		@Override
		public Map<Id<Osm.Relation>, Osm.Relation> getRelations() {
			return relations;
		}

		public Map<Id<Osm.Way>, Osm.Way> getWays() {
			return ways;
		}

		/*pckg*/ void addWay(Osm.Way way) {
			ways.put(way.getId(), way);
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(!(obj instanceof Osm.Node))
				return false;

			Osm.Node other = (Osm.Node) obj;
			return getId().equals(other.getId());
		}

		@Override
		public int hashCode() {
			return getId().hashCode();
		}
	}

	/**
	 * OSM way
	 */
	public static class OsmWay implements Osm.Way {

		private final Id<Osm.Way> id;
		private final List<Osm.Node> nodes;
		private final Map<String, String> tags;

		private boolean isUsed = true;

		private Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

		public OsmWay(long id, List<Osm.Node> nodes, Map<String, String> tags) {
			this.id = Id.create(id, Osm.Way.class);
			this.nodes =  nodes;
			this.tags = tags;
		}

		@Override
		public Id<Osm.Way> getId() {
			return id;
		}

		public List<Osm.Node> getNodes() {
			return nodes;
		}

		@Override
		public Map<String, String> getTags() {
			return tags;
		}

		@Override
		public String getValue(String key) {
			return tags.get(key);
		}

		/*pckg*/ void addRelation(Osm.Relation rel) {
			relations.put(rel.getId(), rel);
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(!(obj instanceof OsmWay))
				return false;

			OsmWay other = (OsmWay) obj;
			return getId().equals(other.getId());
		}

		@Override
		public int hashCode() {
			return getId().hashCode();
		}

		public Map<Id<Osm.Relation>, Osm.Relation> getRelations() {
			return relations;
		}
	}

	/**
	 * OSM relation
	 */
	public static class OsmRelation implements Osm.Relation {

		private final Id<Osm.Relation> id;
		private List<Osm.Element> members;
		private final Map<String, String> tags;
		private Map<Osm.Element, String> memberRoles;

		private Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

		public OsmRelation(long id, Map<String, String> tags) {
			this.id = Id.create(id, Osm.Relation.class);
			this.tags = tags;
		}

		@Override
		public Id<Osm.Relation> getId() {
			return id;
		}

		public List<Osm.Element> getMembers() {
			return members;
		}

		@Override
		public Map<String, String> getTags() {
			return tags;
		}

		@Override
		public String getValue(String key) {
			return tags.get(key);
		}

		/*pckg*/ void addRelation(Osm.Relation currentRel) {
			relations.put(currentRel.getId(), currentRel);
		}

		@Override
		public Map<Id<Osm.Relation>, Osm.Relation> getRelations() {
			return relations;
		}

		public String getMemberRole(Osm.Element member) {
			return memberRoles.get(member);
		}

		/*pckg*/ void setMembers(List<Osm.Element> memberList, Map<Osm.Element, String> memberRoles) {
			if(members != null) {
				throw new IllegalArgumentException("OsmRelation members have already been set");
			}
			this.members = memberList;
			this.memberRoles = memberRoles;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(!(obj instanceof OsmRelation))
				return false;

			OsmRelation other = (OsmRelation) obj;
			return getId().equals(other.getId());
		}

		@Override
		public int hashCode() {
			return getId().hashCode();
		}
	}
}
