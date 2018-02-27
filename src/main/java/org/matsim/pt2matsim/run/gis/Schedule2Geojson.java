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
	 * to geojson files. The output folder contains files for: transitRoutes, stopFacilities, refLinks, networkNodes*, networkLinks*.
	 * <br/><br/>
	 * * if network is given
	 *
	 * @param args [0] coordinate reference system (coordinates are transformed to WGS84)
	 *             [1] output folder
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
	 * to GIS shape files.
	 *
	 * @param crs          coordinate reference system (EPSG:*, coordinates are transformed to WGS83)
	 * @param outputFolder output folder
	 * @param scheduleFile input schedule file
	 * @param networkFile  input network file (<tt>null</tt> if not available)
	 */
	public static void run(String crs, String outputFolder, String scheduleFile, String networkFile) {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(scheduleFile);
		Network network = networkFile == null ? null : NetworkTools.readNetwork(networkFile);

		Schedule2Geojson s2s = new Schedule2Geojson(crs, schedule, network);

		s2s.routes2Polylines(outputFolder + "transitRoutes.geojson", network != null);
		s2s.stopFacilities2Points(outputFolder + "stopFacilities.geojson");
		s2s.stopRefLinks2Polylines(outputFolder + "refLinks.geojson");
		s2s.convertNetwork(outputFolder);
	}

	public static void run(String crs, String outputFolder, TransitSchedule schedule, Network network) {
		Schedule2Geojson s2s = new Schedule2Geojson(crs, schedule, network);

		s2s.routes2Polylines(outputFolder + "transitRoutes.geojson", network != null);
		s2s.stopFacilities2Points(outputFolder + "stopFacilities.geojson");
		s2s.stopRefLinks2Polylines(outputFolder + "refLinks.geojson");
		s2s.convertNetwork(outputFolder);
	}

	private static final Logger log = Logger.getLogger(Schedule2Geojson.class);
	private final TransitSchedule schedule;
	private final Network network;
	private final String crs;

	private final CoordinateTransformation ct;

	private Map<TransitStopFacility, Set<Id<TransitRoute>>> routesOnStopFacility = new HashMap<>();

	public Schedule2Geojson(String originalCoordRefSys, final TransitSchedule schedule, final Network network) {
		this.crs = originalCoordRefSys;
		this.ct = originalCoordRefSys == null ? new IdentityTransformation() : TransformationFactory.getCoordinateTransformation(originalCoordRefSys, TransformationFactory.WGS84);
		this.schedule = schedule;
		this.network = network;
	}

	/**
	 * Converts reference links to polylines.
	 */
	public void stopRefLinks2Polylines(String outputFile) {
		FeatureCollection lineFeatures = new FeatureCollection();

		for(TransitStopFacility stopFacility : schedule.getFacilities().values()) {
			if(stopFacility.getLinkId() != null) {
				Link refLink = network.getLinks().get(stopFacility.getLinkId());

				List<Coord> coords = new ArrayList<>();
				coords.add(this.ct.transform(refLink.getFromNode().getCoord()));
				coords.add(this.ct.transform(refLink.getToNode().getCoord()));

				Feature lf = GeojsonTools.createLineFeature(coords);
				lf.setProperty("id", stopFacility.getId().toString());
				lf.setProperty("name", stopFacility.getName());
				lf.setProperty("linkId", stopFacility.getLinkId().toString());
				lf.setProperty("postAreaId", stopFacility.getStopPostAreaId());
				lf.setProperty("isBlocking", stopFacility.getIsBlockingLane());
				lineFeatures.add(lf);
			}
		}

		GeojsonTools.writeFeatureCollectionToFile(lineFeatures, outputFile);
	}


	/**
	 * Converts the stop facilities to points.
	 */
	public void stopFacilities2Points(String pointOutputFile) {
		FeatureCollection pointFeatures = new FeatureCollection();

		for(TransitStopFacility stopFacility : schedule.getFacilities().values()) {
			Coord stopCoord = stopFacility.getCoord();


			Feature pf = GeojsonTools.createPointFeature(ct.transform(stopCoord));
			pf.setProperty("id", stopFacility.getId().toString());
			pf.setProperty("name", stopFacility.getName());
			pf.setProperty("postAreaId", stopFacility.getStopPostAreaId());
			pf.setProperty("isBlocking", stopFacility.getIsBlockingLane());

			if(stopFacility.getLinkId() != null) pf.setProperty("linkId", stopFacility.getLinkId().toString());

			if(routesOnStopFacility.get(stopFacility) != null) {
				pf.setProperty("routes", CollectionUtils.idSetToString(routesOnStopFacility.get(stopFacility)));
			}
			pointFeatures.add(pf);
		}

		GeojsonTools.writeFeatureCollectionToFile(pointFeatures, pointOutputFile);
	}

	/**
	 * Converts the transit routes to polylines
	 */
	public void routes2Polylines(String outputFile, boolean useNetworkLinks) {
		FeatureCollection features = new FeatureCollection();

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {

				for(TransitRouteStop stop : transitRoute.getStops()) {
					MapUtils.getSet(stop.getStopFacility(), routesOnStopFacility).add(transitRoute.getId());
				}

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

				Feature f = GeojsonTools.createLineFeature(coords);
				f.setProperty("line", transitLine.getId().toString());
				f.setProperty("route", transitRoute.getId().toString());
				f.setProperty("mode", transitRoute.getTransportMode());
				f.setProperty("descr", transitRoute.getDescription());
				f.setProperty("simLength", simLength);
				features.add(f);
			}
		}

		GeojsonTools.writeFeatureCollectionToFile(features, outputFile);
	}

	/**
	 * Converts the network to a geojson. Calls {@link org.matsim.utils.gis.matsim2esri.network.Links2ESRIShape}
	 */
	private void convertNetwork(String outputNetworkFile) {
		Network2Geojson n2s = new Network2Geojson(this.crs, network);
		n2s.writeNetwork(outputNetworkFile);
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