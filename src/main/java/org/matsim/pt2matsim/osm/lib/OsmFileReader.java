/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.misc.Counter;
import org.xml.sax.Attributes;

import java.util.*;

/**
 * Based on mrieser / Senozon AG
 */
public class OsmFileReader extends MatsimXmlParser {

	private final OsmData osmData;
	private final Counter nodeCounter = new Counter("node ");
	private final Counter wayCounter = new Counter("way ");
	private final Counter relationCounter = new Counter("relation ");
	private ParsedNode currentNode = null;
	private ParsedWay currentWay = null;
	private ParsedRelation currentRelation = null;

	public OsmFileReader(OsmData osmData) {
		super();
		this.osmData = osmData;
		this.setValidating(false);
	}

	@Override
	public void startTag(final String name, final Attributes atts, final Stack<String> context) {
		if ("node".equals(name)) {
			long id = Long.parseLong(atts.getValue("id"));
			double lat = Double.parseDouble(atts.getValue("lat"));
			double lon = Double.parseDouble(atts.getValue("lon"));
			this.currentNode = new ParsedNode(id, new Coord(lon, lat));
		} else if ("way".equals(name)) {
			this.currentWay = new ParsedWay(Long.parseLong(atts.getValue("id")));
		} else if ("relation".equals(name)) {
			String id = StringCache.get(atts.getValue("id"));
			this.currentRelation = new ParsedRelation(Long.parseLong(id));
		} else if ("nd".equals(name)) {
			if (this.currentWay != null) {
				this.currentWay.nodes.add(Long.valueOf(atts.getValue("ref")));
			}
		} else if ("tag".equals(name)) {
			if (this.currentNode != null) {
				this.currentNode.tags.put(StringCache.get(atts.getValue("k")), StringCache.get(atts.getValue("v")));
			} else if (this.currentWay != null) {
				this.currentWay.tags.put(StringCache.get(atts.getValue("k")), StringCache.get(atts.getValue("v")));
			} else if (this.currentRelation != null) {
				this.currentRelation.tags.put(StringCache.get(atts.getValue("k")), StringCache.get(atts.getValue("v")));
			}
		} else if ("member".equals(name)) {
			if (this.currentRelation != null) {
				Osm.ElementType type = null;
				String lcType = atts.getValue("type").toLowerCase(Locale.ROOT);
				if ("node".equals(lcType)) {
					type = Osm.ElementType.NODE;
				} else if ("way".equals(lcType)) {
					type = Osm.ElementType.WAY;
				} else if ("relation".equals(lcType)) {
					type = Osm.ElementType.RELATION;
				}
				this.currentRelation.members.add(new ParsedRelationMember(type, Long.parseLong(atts.getValue("ref")), StringCache.get(atts.getValue("role"))));
			}
		}
	}

	@Override
	public void endTag(final String name, final String content, final Stack<String> context) {
		if ("node".equals(name)) {
			this.nodeCounter.incCounter();
			this.osmData.handleParsedNode(this.currentNode);
			this.currentNode = null;
		} else if ("way".equals(name)) {
			this.wayCounter.incCounter();
			this.osmData.handleParsedWay(this.currentWay);
			this.currentWay = null;
		} else if ("relation".equals(name)) {
			this.relationCounter.incCounter();
			this.osmData.handleParsedRelation(this.currentRelation);
			this.currentRelation = null;
		} else if ("osm".equals(name)) {
			osmData.buildMap(); // finalize osmData
			this.nodeCounter.printCounter();
			this.wayCounter.printCounter();
			this.relationCounter.printCounter();
		}
	}

	public static class ParsedNode implements Osm.Element {
		public final long id;
		public final Coord coord;
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public ParsedNode(final long id, final Coord coord) {
			this.id = id;
			this.coord = coord;
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
		public Osm.ElementType getType() {
			return Osm.ElementType.NODE;
		}
	}

	public static class ParsedWay implements Osm.Element {
		public final long id;
		public final List<Long> nodes = new ArrayList<>(6);
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public ParsedWay(final long id) {
			this.id = id;
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
		public Osm.ElementType getType() {
			return Osm.ElementType.WAY;
		}
	}

	public static class ParsedRelation implements Osm.Element {
		public final long id;
		public final List<ParsedRelationMember> members = new ArrayList<>(8);
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public ParsedRelation(final long id) {
			this.id = id;
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
		public Osm.ElementType getType() {
			return Osm.ElementType.RELATION;
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
}