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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementations of different OSM elements
 *
 * @author polettif
 */
public class OsmImpl {

	/**
	 * OSM node
	 */
	public static class Node implements Osm.Node {

		private final long osmId;
		private final Id<Osm.Node> id;
		private final Coord coord;
		private final Map<String, String> tags;

		private Map<Id<Osm.Way>, Osm.Way> ways = new HashMap<>();
		private Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

		public Node(final long id, final Coord coord, Map<String, String> tags) {
			this.osmId = id;
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

		@Override
		public long getOsmId() {
			return osmId;
		}

		@Override
		public Osm.ElementType getType() {
			return Osm.ElementType.NODE;
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
	public static class Way implements Osm.Way {

		private final long osmId;
		private final Id<Osm.Way> id;
		private final List<Osm.Node> nodes;
		private final Map<String, String> tags;

		private Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

		public Way(long id, List<Osm.Node> nodes, Map<String, String> tags) {
			this.osmId = id;
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

		@Override
		public long getOsmId() {
			return osmId;
		}

		@Override
		public Osm.ElementType getType() {
			return Osm.ElementType.WAY;
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
			if(!(obj instanceof Way))
				return false;

			Way other = (Way) obj;
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
	public static class Relation implements Osm.Relation {

		private final long osmId;
		private final Id<Osm.Relation> id;
		private List<Osm.Element> members;
		private final Map<String, String> tags;
		private Map<Osm.Element, String> memberRoles;

		private Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

		public Relation(long id, Map<String, String> tags) {
			this.osmId = id;
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

		@Override
		public long getOsmId() {
			return osmId;
		}

		@Override
		public Osm.ElementType getType() {
			return Osm.ElementType.RELATION;
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
				throw new IllegalArgumentException("Relation members have already been set");
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
			if(!(obj instanceof Relation))
				return false;

			Relation other = (Relation) obj;
			return getId().equals(other.getId());
		}

		@Override
		public int hashCode() {
			return getId().hashCode();
		}
	}
}
