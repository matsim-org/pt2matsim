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

package org.matsim.pt2matsim.osm.parser;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.xml.sax.Attributes;

import java.util.Locale;
import java.util.Stack;

/**
 * @author mrieser / Senozon AG
 */
public class OsmFileReader extends MatsimXmlParser {

//	todo cleanup
//	private OsmNodeHandler nodeHandler;
//	private OsmWayHandler wayHandler;
//	private OsmRelationHandler relHandler;

	private OsmData osmData;

	private Osm.ParsedNode currentNode = null;
	private Osm.ParsedWay currentWay = null;
	private Osm.ParsedRelation currentRelation = null;
	private final Counter nodeCounter = new Counter("node ");
	private final Counter wayCounter = new Counter("way ");
	private final Counter relationCounter = new Counter("relation ");

	public OsmFileReader(OsmData osmData) {
		super();
		this.osmData = osmData;
		this.setValidating(false);
	}

	@Override
	public void startTag(final String name, final Attributes atts, final Stack<String> context) {
//		if ("node".equals(name) & this.nodeHandler != null) {
		if ("node".equals(name)) {
			long id = Long.parseLong(atts.getValue("id"));
			double lat = Double.parseDouble(atts.getValue("lat"));
			double lon = Double.parseDouble(atts.getValue("lon"));
			this.currentNode = new Osm.ParsedNode(id, new Coord(lon, lat));
//		} else if ("way".equals(name) & this.wayHandler != null) {
		} else if ("way".equals(name)) {
			this.currentWay = new Osm.ParsedWay(Long.parseLong(atts.getValue("id")));
//		} else if ("relation".equals(name) & this.relHandler != null) {
		} else if ("relation".equals(name)) {
			String id = StringCache.get(atts.getValue("id"));
			this.currentRelation = new Osm.ParsedRelation(Long.parseLong(id));
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
				Osm.Element type = null;
				String lcType = atts.getValue("type").toLowerCase(Locale.ROOT);
				if ("node".equals(lcType)) {
					type = Osm.Element.NODE;
				} else if ("way".equals(lcType)) {
					type = Osm.Element.WAY;
				} else if ("relation".equals(lcType)) {
					type = Osm.Element.RELATION;
				}
				this.currentRelation.members.add(new Osm.ParsedRelationMember(type, Long.parseLong(atts.getValue("ref")), StringCache.get(atts.getValue("role"))));
			}
		}
	}

	@Override
	public void endTag(final String name, final String content, final Stack<String> context) {
//		if ("node".equals(name) & this.nodeHandler != null) {
		if ("node".equals(name)) {
			this.nodeCounter.incCounter();
			this.osmData.handleNode(this.currentNode);
			this.currentNode = null;
//		} else if ("way".equals(name) & this.wayHandler != null) {
		} else if ("way".equals(name)) {
			this.wayCounter.incCounter();
			this.osmData.handleWay(this.currentWay);
			this.currentWay = null;
//		} else if ("relation".equals(name) & this.relHandler != null) {
		} else if ("relation".equals(name)) {
			this.relationCounter.incCounter();
			this.osmData.handleRelation(this.currentRelation);
			this.currentRelation = null;
		} else if ("osm".equals(name)) {
			osmData.buildMap(); // finalize osmData
			this.nodeCounter.printCounter();
			this.wayCounter.printCounter();
			this.relationCounter.printCounter();
		}
	}

}