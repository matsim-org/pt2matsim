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

import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.parser.handler.OsmHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmNodeHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmRelationHandler;
import org.matsim.pt2matsim.osm.parser.handler.OsmWayHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Based on OsmParser by mrieser / Senozon AG
 */
@Deprecated
public class OsmParser {

//	private static final Logger log = Logger.getLogger(OsmParser.class);

	private final List<OsmHandler> handlers = new ArrayList<>();

	private static CoordinateTransformation transformation = new IdentityTransformation();

	public OsmParser() {
	}

	public OsmParser(CoordinateTransformation ct) {
		transformation = ct;
	}

	public void addHandler(final OsmHandler handler) {
		this.handlers.add(handler);
	}

	public void run(final String filename) throws UncheckedIOException {
		OsmHandler distributor = new DataDistributor(this.handlers);
		if (filename.toLowerCase(Locale.ROOT).endsWith(".osm.pbf")) {
//			log.error("*.osm.pbf are not supported. Use *.osm (xml format) instead.");
		} else {
			new OsmXmlParser(distributor).readFile(filename);
		}
	}

	private static class DataDistributor implements OsmNodeHandler, OsmWayHandler, OsmRelationHandler {

		private final List<OsmNodeHandler> nodeHandlers = new ArrayList<>();
		private final List<OsmWayHandler> wayHandlers = new ArrayList<>();
		private final List<OsmRelationHandler> relHandlers = new ArrayList<>();

		DataDistributor(final List<OsmHandler> handlers) {
			for (OsmHandler h : handlers) {
				if (h instanceof OsmNodeHandler) {
					this.nodeHandlers.add((OsmNodeHandler) h);
				}
				if (h instanceof OsmWayHandler) {
					this.wayHandlers.add((OsmWayHandler) h);
				}
				if (h instanceof OsmRelationHandler) {
					this.relHandlers.add((OsmRelationHandler) h);
				}
			}
		}

		@Override
		public void handleNode(final Osm.Node node) {
			for (OsmNodeHandler handler : this.nodeHandlers) {
				handler.handleNode(node);
			}
		}

		@Override
		public void handleWay(final Osm.Way way) {
			for (OsmWayHandler handler : this.wayHandlers) {
				handler.handleWay(way);
			}
		}

		@Override
		public void handleRelation(final Osm.Relation relation) {
			for (OsmRelationHandler handler : this.relHandlers) {
				handler.handleRelation(relation);
			}
		}

	}
}
