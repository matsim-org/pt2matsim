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

import com.opencsv.CSVWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.lib.*;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public final class GtfsTools {

	private GtfsTools() {
	}

	/**
	 * @return the day of the feed on which the most trips occur
	 */
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

	/**
	 * @return the day of the feed on which the most services occur
	 */
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

	/**
	 * @return a map that stores the services occuring on each date of the feed
	 */
	public static Map<LocalDate, Set<Service>> getServicesOnDates(GtfsFeed feed) {
		Map<LocalDate, Set<Service>> servicesOnDate = new HashMap<>();

		for(Service service : feed.getServices().values()) {
			for(LocalDate day : service.getCoveredDays()) {
				MapUtils.getSet(day, servicesOnDate).add(service);
			}
		}
		return servicesOnDate;
	}

	/**
	 * @return a map that stores the trips occuring on each date of the feed
	 */
	public static Map<LocalDate, Set<Trip>> getTripsOndates(GtfsFeed feed) {
		Map<LocalDate, Set<Trip>> tripsOnDate = new HashMap<>();

		for(Service service : feed.getServices().values()) {
			for(LocalDate day : service.getCoveredDays()) {
				MapUtils.getSet(day, tripsOnDate).addAll(service.getTrips().values());
			}
		}
		return tripsOnDate;
	}

	/**
	 * Experimental class to write stop_times.txt from a (filtered) collection of trips. stop_times.txt is
	 * usually the largest file.
	 */
	public static void writeStopTimes(Collection<Trip> trips, String folder) throws IOException {
		CSVWriter stopTimesWriter = new CSVWriter(new FileWriter(folder + GtfsDefinitions.Files.STOP_TIMES.fileName), ',');
		String[] header = GtfsDefinitions.Files.STOP_TIMES.columns;
		stopTimesWriter.writeNext(header, true);

		for(Trip trip : trips) {
			for(StopTime stopTime : trip.getStopTimes()) {
				// TRIP_ID, STOP_SEQUENCE, ARRIVAL_TIME, DEPARTURE_TIME, STOP_ID
				String[] line = new String[header.length];
				line[0] = stopTime.getTrip().getId();
				line[1] = String.valueOf(stopTime.getSequencePosition());
				line[2] = Time.writeTime(stopTime.getArrivalTime());
				line[3] = Time.writeTime(stopTime.getDepartureTime());
				line[4] = stopTime.getStop().getId();

				stopTimesWriter.writeNext(line);
			}
		}
		stopTimesWriter.close();
	}

	/**
	 * Experimental class to write stops.txt (i.e. after filtering for one date)
	 */
	public static void writeStops(Collection<Stop> stops, String folder) throws IOException {
		CSVWriter stopsWriter = new CSVWriter(new FileWriter(folder + GtfsDefinitions.Files.STOPS.fileName), ',');
		String[] header = GtfsDefinitions.Files.STOPS.columns;
		stopsWriter.writeNext(header, true);
		for(Stop stop : stops) {
			// STOP_ID, STOP_LON, STOP_LAT, STOP_NAME
			String[] line = new String[header.length];
			line[0] = stop.getId();
			line[1] = String.valueOf(stop.getLon());
			line[2] = String.valueOf(stop.getLat());
			line[3] = stop.getName();
			stopsWriter.writeNext(line);
		}
		stopsWriter.close();
	}

	/**
	 * Experimental class to write trips.txt (i.e. after filtering for one date)
	 */
	public static void writeTrips(Collection<Trip> trips, String folder) throws IOException {
		CSVWriter tripsWriter = new CSVWriter(new FileWriter(folder + GtfsDefinitions.Files.TRIPS.fileName), ',');
		String[] header = GtfsDefinitions.Files.TRIPS.columns;
		tripsWriter.writeNext(header, true);
		for(Trip trip : trips) {
			// ROUTE_ID, TRIP_ID, SERVICE_ID
			String[] line = new String[header.length];
			line[0] = trip.getRoute().getId();
			line[1] = trip.getId();
			line[2] = trip.getService().getId();
			tripsWriter.writeNext(line);
		}
		tripsWriter.close();
	}

	/**
	 * @return Array of Coords with the minimal South-West and the
	 * maximal North-East Coordinates (WGS84!)
	 */
	public static Coord[] getExtent(GtfsFeed feed) {
		double maxE = 0;
		double maxN = 0;
		double minS = Double.MAX_VALUE;
		double minW = Double.MAX_VALUE;

		for(Stop stop : feed.getStops().values()) {
			if(stop.getLon() > maxE) {
				maxE = stop.getLon();
			}
			if(stop.getLat() > maxN) {
				maxN = stop.getLat();
			}
			if(stop.getLon() < minW) {
				minW = stop.getLon();
			}
			if(stop.getLat() < minS) {
				minS = stop.getLat();
			}
		}

		return new Coord[]{new Coord(minW, minS), new Coord(maxE, maxN)};
	}

	/**
	 * @return a map that stores all trips for each stop on the given date
	 */
	public Map<Stop, Set<Trip>> getTripsForStops(GtfsFeed feed, LocalDate extractDate) {
		Map<Stop, Set<Trip>> tripsForStop = new HashMap<>();
		for(Trip trip : feed.getTrips().values()) {
			if(trip.getService().runsOnDate(extractDate)) {
				for(StopTime stop : trip.getStopTimes()) {
					MapUtils.getSet(stop.getStop(), tripsForStop).add(trip);
				}
			}
		}
		return tripsForStop;
	}
}
