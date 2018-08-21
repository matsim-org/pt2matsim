/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.run.gis;

import org.apache.log4j.Logger;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.GeojsonTools;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.*;

/**
 * Converts a MATSim Transit Schedule to a ESRI shape file.
 *
 * @author polettif
 */
public class Schedule2Geojson {

	/**
	 * Converts the given schedule based on the given network
	 * to a geojson file that contains the stop facilities and transit routes.
	 * <br/><br/>
	 *
	 * @param args [0] coordinate reference system (coordinates are transformed to WGS84)
	 *             [1] output schedule geojson file
	 *             [2] input schedule
	 *             [3] input network (optional)
	 */
	public static void main(final String[] args) {
		if(args.length == 3) {
			run(args[0], args[1], args[2], null);
		} else if(args.length == 4) {
			run(args[0], args[1], args[2], args[3]);
		} else {
			throw new RuntimeException("Incorrect number of arguments");
		}
	}

	/**
	 * Converts the given schedule based on the given network
	 * to a geojson file containing the stops and transit routes.
	 *
	 * @param crs          coordinate reference system (EPSG:*, coordinates are transformed to WGS84)
	 * @param outputFile   output schedule folder
	 * @param scheduleFile input schedule file
	 * @param networkFile  input network file (<tt>null</tt> if not available)
	 */
	public static void run(String crs, String outputFile, String scheduleFile, String networkFile) {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(scheduleFile);
		Network network = networkFile == null ? null : NetworkTools.readNetwork(networkFile);

		Schedule2Geojson s2s = new Schedule2Geojson(crs, schedule, network);
		s2s.writeSchedule(outputFile);
	}

	public static void run(TransitSchedule schedule, String outputFile) {
		Schedule2Geojson s2s = new Schedule2Geojson(null, schedule, null);
		s2s.writeSchedule(outputFile);
	}

	public static void run(String crs, TransitSchedule schedule, String outputFile) {
		Schedule2Geojson s2s = new Schedule2Geojson(crs, schedule, null);
		s2s.writeSchedule(outputFile);
	}

	public static void run(String crs, TransitSchedule schedule, Network network, String outputFile) {
		Schedule2Geojson s2s = new Schedule2Geojson(crs, schedule, network);
		s2s.writeSchedule(outputFile);
	}

	private static final Logger log = Logger.getLogger(Schedule2Geojson.class);
	private final TransitSchedule schedule;
	private final Network network;
	private final CoordinateTransformation ct;
	private Map<TransitStopFacility, Set<Id<TransitRoute>>> routesOnStopFacility = new HashMap<>();

	private FeatureCollection transitRouteFeatures = new FeatureCollection();
	private FeatureCollection stopFacilityFeatures = new FeatureCollection();
	private FeatureCollection stopRefLinks = new FeatureCollection();
	private FeatureCollection scheduleFeatureCollection;

	public Schedule2Geojson(String originalCoordRefSys, final TransitSchedule schedule) {
		this.ct = originalCoordRefSys == null ? new IdentityTransformation() : TransformationFactory.getCoordinateTransformation(originalCoordRefSys, TransformationFactory.WGS84);
		this.schedule = schedule;
		this.network = null;

		convertTransitRoutes(false);
		convertStopFacilities();
		combineFeatures();
	}

	public Schedule2Geojson(String originalCoordRefSys, final TransitSchedule schedule, final Network network) {
		this.ct = originalCoordRefSys == null ? new IdentityTransformation() : TransformationFactory.getCoordinateTransformation(originalCoordRefSys, TransformationFactory.WGS84);
		this.schedule = schedule;
		this.network = network;

		convertTransitRoutes(network != null);
		convertStopFacilities();
		combineFeatures();
	}

	public void writeSchedule(String outputFile) {
		GeojsonTools.writeFeatureCollectionToFile(this.scheduleFeatureCollection, outputFile);
	}

	public void writeStopFacilities(String outputFile) {
		GeojsonTools.writeFeatureCollectionToFile(this.stopFacilityFeatures, outputFile);
	}

	public void writeTransitRoutes(String outputFile) {
		GeojsonTools.writeFeatureCollectionToFile(this.transitRouteFeatures, outputFile);
	}

	public void writeStopRefLinks(String outputFile) {
		GeojsonTools.writeFeatureCollectionToFile(this.stopRefLinks, outputFile);
	}

	private void combineFeatures() {
		this.scheduleFeatureCollection = new FeatureCollection();
		this.scheduleFeatureCollection.addAll(transitRouteFeatures.getFeatures());
		this.scheduleFeatureCollection.addAll(stopFacilityFeatures.getFeatures());
		// TODO set refLink as network attribute
	}

	/**
	 * Converts the stop facilities to points.
	 */
	private void convertStopFacilities() {
		for(TransitStopFacility stopFacility : schedule.getFacilities().values()) {
			Coord stopCoord = stopFacility.getCoord();

			Feature pf = GeojsonTools.createPointFeature(ct.transform(stopCoord));
			pf.setProperty("stopFacilityId", stopFacility.getId().toString());
			pf.setProperty("stopFacilityName", stopFacility.getName());
			pf.setProperty("stopFacilityPostAreaId", stopFacility.getStopAreaId());
			pf.setProperty("stopFacilityIsBlocking", stopFacility.getIsBlockingLane());

			if(stopFacility.getLinkId() != null) pf.setProperty("stopFacilityLinkId", stopFacility.getLinkId().toString());

			if(routesOnStopFacility.get(stopFacility) != null) {
				pf.setProperty("stopFacilityTransitRoutes", CollectionUtils.idSetToString(routesOnStopFacility.get(stopFacility)));
			}
			stopFacilityFeatures.add(pf);

			// convert stop ref links (not written to combined file)
			if(stopFacility.getLinkId() != null && this.network != null) {
				Link refLink = network.getLinks().get(stopFacility.getLinkId());

				List<Coord> coords = new ArrayList<>();
				coords.add(this.ct.transform(refLink.getFromNode().getCoord()));
				coords.add(this.ct.transform(refLink.getToNode().getCoord()));

				Feature lf = GeojsonTools.createLineFeature(coords);
				lf.setProperty("id", stopFacility.getId().toString());
				lf.setProperty("name", stopFacility.getName());
				lf.setProperty("linkId", stopFacility.getLinkId().toString());
				lf.setProperty("postAreaId", stopFacility.getStopAreaId());
				lf.setProperty("isBlocking", stopFacility.getIsBlockingLane());
				stopRefLinks.add(lf);
			}
		}
	}

	/**
	 * Converts the transit routes to polylines
	 */
	private void convertTransitRoutes(boolean useNetworkLinks) {
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {

				for(TransitRouteStop stop : transitRoute.getStops()) {
					MapUtils.getSet(stop.getStopFacility(), routesOnStopFacility).add(transitRoute.getId());
				}

				// create coordinates
				List<Coord> coords;
				double simLength = 0.0;
				if(useNetworkLinks) {
					coords = getCoordFromRoute(transitRoute);
					if(coords.size() == 0) {
						log.warn("No links found for route " + transitRoute.getId() + " on line " + transitLine.getId());
					}
					simLength = getRouteLength(transitRoute);
				} else {
					coords = getCoordsFromStopFacilities(transitRoute);
				}

				// departures
				Set<String> deps = new TreeSet<>();
				for(Departure departure : transitRoute.getDepartures().values()) {
					deps.add(Time.writeTime(departure.getDepartureTime()));
				}

				Feature f = GeojsonTools.createLineFeature(coords);
				f.setProperty("transitLineId", transitLine.getId().toString());
				f.setProperty("transitLineName", transitLine.getName());
				f.setProperty("transitRouteId", transitRoute.getId().toString());
				f.setProperty("transportMode", transitRoute.getTransportMode());
				f.setProperty("transitRouteDescription", transitRoute.getDescription());
				f.setProperty("transitRouteSimLength", simLength);
				f.setProperty("departures", CollectionUtils.setToString(deps));
				transitRouteFeatures.add(f);
			}
		}
	}

	/**
	 * @return the sum of all link lenghts of a transit route
	 */
	private double getRouteLength(TransitRoute transitRoute) {
		double length = 0;
		for(Link l : NetworkTools.getLinksFromIds(network, ScheduleTools.getTransitRouteLinkIds(transitRoute))) {
			length += l.getLength();
		}
		return length;
	}

	/**
	 * @return the coordinates of the transit route
	 */
	private List<Coord> getCoordFromRoute(TransitRoute transitRoute) {
		List<Coord> coordList = new ArrayList<>();
		List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(transitRoute);

		if(linkIds.size() > 0) {
			for(Id<Link> linkId : linkIds) {
				if(network.getLinks().containsKey(linkId)) {
					Coord coord = network.getLinks().get(linkId).getFromNode().getCoord();
					coordList.add(ct.transform(coord));
				} else {
					throw new IllegalArgumentException("Link " + linkId + " not found in network");
				}
			}
			Coord finalCoord = network.getLinks().get(linkIds.get(linkIds.size() - 1)).getToNode().getCoord();
			coordList.add(ct.transform(finalCoord));
		}
		return coordList;
	}

	/**
	 * @return an array of coordinates from stop facilities. Can be used to create straight lines between stops
	 */
	private List<Coord> getCoordsFromStopFacilities(TransitRoute transitRoute) {
		List<Coord> coordList = new ArrayList<>();
		for(TransitRouteStop trs : transitRoute.getStops()) {
			Coord coord = trs.getStopFacility().getCoord();
			coordList.add(ct.transform(coord));
		}
		return coordList;
	}

}