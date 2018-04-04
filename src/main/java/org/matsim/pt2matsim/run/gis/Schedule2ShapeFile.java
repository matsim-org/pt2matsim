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

package org.matsim.pt2matsim.run.gis;

import com.vividsolutions.jts.geom.Coordinate;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;

/**
 * Converts a MATSim Transit Schedule to a ESRI shape file.
 *
 * @author polettif
 */
public class Schedule2ShapeFile {

	private static final Logger log = Logger.getLogger(Schedule2ShapeFile.class);
	private final TransitSchedule schedule;
	private final Network network;
	private final String crs;
	private Map<TransitStopFacility, Set<Id<TransitRoute>>> routesOnStopFacility = new HashMap<>();

	public Schedule2ShapeFile(String crs, final TransitSchedule schedule, final Network network) {
		this.schedule = schedule;
		this.network = network;
		this.crs = crs;
	}

	/**
	 * Converts the given schedule based on the given network
	 * to GIS shape files.
	 *
	 * @param args [0] coordinate reference system (EPSG:*)
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
	 * @param outputFolder output folder
	 * @param crs          coordinate reference system (EPSG:*)
	 * @param scheduleFile input schedule file
	 * @param networkFile  input network file (<tt>null</tt> if not available)
	 */
	public static void run(String crs, String outputFolder, String scheduleFile, String networkFile) {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(scheduleFile);
		Network network = networkFile == null ? null : NetworkTools.readNetwork(networkFile);

		Schedule2ShapeFile s2s = new Schedule2ShapeFile(crs, schedule, network);

		s2s.routes2Polylines(outputFolder + "transitRoutes.shp", network != null);
		s2s.stopFacilities2Points(outputFolder + "stopFacilities.shp");
		s2s.stopRefLinks2Polylines(outputFolder + "refLinks.shp");
		s2s.convertNetwork(outputFolder);
	}

	public static void run(String crs, String outputFolder, TransitSchedule schedule, Network network) {
		Schedule2ShapeFile s2s = new Schedule2ShapeFile(crs, schedule, network);

		s2s.routes2Polylines(outputFolder + "transitRoutes.shp", network != null);
		s2s.stopFacilities2Points(outputFolder + "stopFacilities.shp");
		s2s.stopRefLinks2Polylines(outputFolder + "refLinks.shp");
		s2s.convertNetwork(outputFolder);
	}

	/**
	 * Converts reference links to polylines.
	 */
	public void stopRefLinks2Polylines(String outputFile) {
		Collection<SimpleFeature> lineFeatures = new ArrayList<>();

		PolylineFeatureFactory polylineFeatureFactory = new PolylineFeatureFactory.Builder()
				.setName("StopFacilities")
				.setCrs(MGC.getCRS(crs))
				.addAttribute("id", String.class)
				.addAttribute("name", String.class)
				.addAttribute("linkId", String.class)
				.addAttribute("postAreaId", String.class)
				.addAttribute("isBlocking", Boolean.class)
				.addAttribute("routes", String.class)
				.create();

		for(TransitStopFacility stopFacility : schedule.getFacilities().values()) {
			if(stopFacility.getLinkId() != null) {
				Link refLink = network.getLinks().get(stopFacility.getLinkId());

				Coordinate[] coordinates = new Coordinate[2];
				try {
					coordinates[0] = MGC.coord2Coordinate(refLink.getFromNode().getCoord());
				} catch (Exception e) {
					e.printStackTrace();
				}
				coordinates[1] = MGC.coord2Coordinate(refLink.getToNode().getCoord());

				SimpleFeature lf = polylineFeatureFactory.createPolyline(coordinates);
				lf.setAttribute("id", stopFacility.getId().toString());
				lf.setAttribute("name", stopFacility.getName());
				lf.setAttribute("linkId", stopFacility.getLinkId().toString());
				lf.setAttribute("postAreaId", stopFacility.getStopAreaId());
				lf.setAttribute("isBlocking", stopFacility.getIsBlockingLane());
				lineFeatures.add(lf);
			}
		}

		ShapeFileWriter.writeGeometries(lineFeatures, outputFile);
	}


	/**
	 * Converts the stop facilities to points.
	 */
	public void stopFacilities2Points(String pointOutputFile) {
		Collection<SimpleFeature> pointFeatures = new ArrayList<>();

		PointFeatureFactory pointFeatureFactory = new PointFeatureFactory.Builder()
				.setName("StopFacilities")
				.setCrs(MGC.getCRS(crs))
				.addAttribute("id", String.class)
				.addAttribute("name", String.class)
				.addAttribute("linkId", String.class)
				.addAttribute("postAreaId", String.class)
				.addAttribute("isBlocking", Boolean.class)
				.addAttribute("routes", String.class)
				.create();

		for(TransitStopFacility stopFacility : schedule.getFacilities().values()) {

			SimpleFeature pf = pointFeatureFactory.createPoint(MGC.coord2Coordinate(stopFacility.getCoord()));
			pf.setAttribute("id", stopFacility.getId().toString());
			pf.setAttribute("name", stopFacility.getName());
			pf.setAttribute("postAreaId", stopFacility.getStopAreaId());
			pf.setAttribute("isBlocking", stopFacility.getIsBlockingLane());

			if(stopFacility.getLinkId() != null) pf.setAttribute("linkId", stopFacility.getLinkId().toString());

			if(routesOnStopFacility.get(stopFacility) != null) {
				pf.setAttribute("routes", CollectionUtils.idSetToString(routesOnStopFacility.get(stopFacility)));
			}
			pointFeatures.add(pf);
		}

		ShapeFileWriter.writeGeometries(pointFeatures, pointOutputFile);
	}

	/**
	 * Converts the transit routes to polylines
	 */
	public void routes2Polylines(String outputFile, boolean useNetworkLinks) {
		Collection<SimpleFeature> features = new ArrayList<>();

		PolylineFeatureFactory ff = new PolylineFeatureFactory.Builder()
				.setName("TransitRoutes")
				.setCrs(MGC.getCRS(crs))
				.addAttribute("line", String.class)
				.addAttribute("route", String.class)
				.addAttribute("mode", String.class)
				.addAttribute("simLength", Double.class)
				.addAttribute("descr", String.class)
				.create();

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {

				for(TransitRouteStop stop : transitRoute.getStops()) {
					MapUtils.getSet(stop.getStopFacility(), routesOnStopFacility).add(transitRoute.getId());
				}

				Coordinate[] coordinates;
				double simLength = 0.0;
				if(useNetworkLinks) {
					coordinates = getCoordinatesFromRoute(transitRoute);
					if(coordinates == null) {
						log.warn("No links found for route " + transitRoute.getId() + " on line " + transitLine.getId());
					}
					simLength = getRouteLength(transitRoute);
				} else {
					coordinates = getCoordinatesFromStopFacilities(transitRoute);
				}

				SimpleFeature f = ff.createPolyline(coordinates);
				f.setAttribute("line", transitLine.getId().toString());
				f.setAttribute("route", transitRoute.getId().toString());
				f.setAttribute("mode", transitRoute.getTransportMode());
				f.setAttribute("descr", transitRoute.getDescription());
				f.setAttribute("simLength", simLength);
				features.add(f);
			}
		}

		ShapeFileWriter.writeGeometries(features, outputFile);
	}

	/**
	 * Converts the network to a shapefile. Calls {@link org.matsim.utils.gis.matsim2esri.network.Links2ESRIShape}
	 */
	private void convertNetwork(String outputFolder) {
		Network2ShapeFile n2s = new Network2ShapeFile(crs, network);
		n2s.convertNodes(outputFolder + "networkNodes.shp");
		n2s.convertLinks(outputFolder + "networkLinks.shp");
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
	private Coordinate[] getCoordinatesFromRoute(TransitRoute transitRoute) {
		List<Coordinate> coordList = new ArrayList<>();
		List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(transitRoute);

		if(linkIds.size() > 0) {
			for(Id<Link> linkId : linkIds) {
				if(network.getLinks().containsKey(linkId)) {
					coordList.add(MGC.coord2Coordinate(network.getLinks().get(linkId).getFromNode().getCoord()));
				} else {
					throw new IllegalArgumentException("Link " + linkId + " not found in network");
				}
			}
			coordList.add(MGC.coord2Coordinate(network.getLinks().get(linkIds.get(linkIds.size() - 1)).getToNode().getCoord()));
			Coordinate[] coordinates = new Coordinate[coordList.size()];
			return coordList.toArray(coordinates);
		}
		return null;
	}

	/**
	 * @return an array of coordinates from stop facilities. Can be used to create straight lines between stops
	 */
	private Coordinate[] getCoordinatesFromStopFacilities(TransitRoute transitRoute) {
		List<Coordinate> coordList = new ArrayList<>();

		for(TransitRouteStop trs : transitRoute.getStops()) {
			coordList.add(MGC.coord2Coordinate(trs.getStopFacility().getCoord()));
		}

		Coordinate[] coordinates = new Coordinate[coordList.size()];
		return coordList.toArray(coordinates);
	}

}