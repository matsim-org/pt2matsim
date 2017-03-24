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

package org.matsim.pt2matsim.tools;

import org.matsim.core.utils.collections.MapUtils;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.lib.Service;
import org.matsim.pt2matsim.gtfs.lib.Trip;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public final class GtfsTools {

	private GtfsTools() {
	}

	public static LocalDate getDayWithMostTrips(GtfsFeed feed) {
		LocalDate busiestDate = null;
		int maxTrips = 0;
		for(Map.Entry<LocalDate, Set<Trip>> entry : getTripsOndates(feed).entrySet()) {
			if(entry.getValue().size() > maxTrips) {
				maxTrips = entry.getValue().size();
				busiestDate = entry.getKey();
			}
		}
		return busiestDate;
	}

	public static LocalDate getDayWithMostServices(GtfsFeed feed) {
		LocalDate busiestDate = null;
		int maxService = 0;
		for(Map.Entry<LocalDate, Set<Service>> entry : getServicesOnDates(feed).entrySet()) {
			if(entry.getValue().size() > maxService) {
				maxService = entry.getValue().size();
				busiestDate = entry.getKey();
			}
		}
		return busiestDate;

	}

	public static Map<LocalDate, Set<Service>> getServicesOnDates(GtfsFeed feed) {
		Map<LocalDate, Set<Service>> servicesOnDate = new HashMap<>();

		for(Service service : feed.getServices().values()) {
			for(LocalDate day : service.getCoveredDays()) {
				MapUtils.getSet(day, servicesOnDate).add(service);
			}
		}
		return servicesOnDate;
	}

	public static Map<LocalDate, Set<Trip>> getTripsOndates(GtfsFeed feed) {
		Map<LocalDate, Set<Trip>> tripsOnDate = new HashMap<>();

		for(Service service : feed.getServices().values()) {
			for(LocalDate day : service.getCoveredDays()) {
				MapUtils.getSet(day, tripsOnDate).addAll(service.getTrips().values());
			}
		}
		return tripsOnDate;
	}
}
