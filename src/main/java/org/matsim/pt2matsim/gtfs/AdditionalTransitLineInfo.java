/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2024 by the members listed in the COPYING,        *
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
package org.matsim.pt2matsim.gtfs;

import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions;
import org.matsim.pt2matsim.gtfs.lib.Route;

/**
 * Simple container class for useful but not necessary info extracted from the gtfs schedule
 * 
 * @author tkohl (Royal2Flush)
 */
public class AdditionalTransitLineInfo {
	public static final String INFO_COLUMN_ID = "lineId";
	public static final String INFO_COLUMN_SHORTNAME = "shortName";
	public static final String INFO_COLUMN_LONGNAME = "longName";
	public static final String INFO_COLUMN_TYPE = "type";
	public static final String INFO_COLUMN_DESCRIPTION = "description";
	public static final String INFO_COLUMN_AGENCY_ID = "agencyId";
	public static final String INFO_COLUMN_AGENCY_NAME = "agencyName";
	public static final String INFO_COLUMN_AGENCY_URL = "agencyURL";
	public static final String INFO_COLUMN_NUM_TRANSIT_ROUTES = "numTransitRoutes";
	public static final String INFO_COLUMN_NUM_TOTAL_DEPARTURES = "totalDepartures";
	
	private final String id;
	private final String shortName;
	private final String longName;

	private final GtfsDefinitions.RouteType routeType;
	private final String routeDescription;
	private final String agencyId;
	private final String agencyName;
	private final String agencyURL;
	private int numberOfTransitRoutes = 0;
	private int totalNumberOfDepartures = 0;
	
	AdditionalTransitLineInfo(Route gtfsRoute) {
		this.id = gtfsRoute.getId();
		this.shortName = gtfsRoute.getShortName();
		this.longName = gtfsRoute.getLongName();
		this.routeType = gtfsRoute.getRouteType();
		this.routeDescription = gtfsRoute.getDescription();
		this.agencyId = gtfsRoute.getAgency().getId();
		this.agencyName = gtfsRoute.getAgency().getAgencyName();
		this.agencyURL = gtfsRoute.getAgency().getAgencyUrl();
	}
	
	void countRoute(int numRouteDepartures) {
		this.numberOfTransitRoutes++;
		this.totalNumberOfDepartures += numRouteDepartures;
	}
	
	public String getId() {
		return id;
	}
	
	public String getShortName() {
		return this.shortName;
	}

	public String getLongName() {
		return longName;
	}

	public GtfsDefinitions.RouteType getRouteType() {
		return routeType;
	}

	public String getRouteDescription() {
		return routeDescription;
	}

	public String getAgencyId() {
		return agencyId;
	}

	public String getAgencyName() {
		return agencyName;
	}

	public String getAgencyURL() {
		return agencyURL;
	}

	public int getNumberOfTransitRoutes() {
		return numberOfTransitRoutes;
	}

	public int getTotalNumberOfDepartures() {
		return totalNumberOfDepartures;
	}
}
