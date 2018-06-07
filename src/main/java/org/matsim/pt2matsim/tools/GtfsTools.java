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
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author polettif
 */
public final class GtfsTools {

	private GtfsTools() {
	}

	public static void writeShapesToGeojson(GtfsFeed feed, String file) {
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(feed.getCurrentCoordSystem(), TransformationFactory.WGS84);
		FeatureCollection features = new FeatureCollection();
		for(RouteShape routeShape : feed.getShapes().values()) {
			List<Coord> coords = ShapeTools.transformCoords(ct, routeShape.getCoords());
			Feature lineFeature = GeojsonTools.createLineFeature(coords);
			lineFeature.setProperty("id", routeShape.getId().toString());
			features.add(lineFeature);
		}
		GeojsonTools.writeFeatureCollectionToFile(features, file);
	}

	/**
	 * @return the day of the feed on which the most trips occur
	 */
	public static LocalDate getDayWithMostTrips(GtfsFeed feed) {
		LocalDate busiestDate = null;
		int maxTrips = 0;

		Map<LocalDate, Integer> nTripsOnDate = new TreeMap<>();
		for(Service service : feed.getServices().values()) {
			for(LocalDate day : service.getCoveredDays()) {
				MapUtils.addToInteger(day, nTripsOnDate, 1, service.getTrips().size());
			}
		}
		for(Map.Entry<LocalDate, Integer> entry : nTripsOnDate.entrySet()) {
			if(entry.getValue() > maxTrips) {
				maxTrips = entry.getValue();
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

		Map<LocalDate, Integer> nServicesOnDate = new TreeMap<>();
		for(Service service : feed.getServices().values()) {
			for(LocalDate day : service.getCoveredDays()) {
				MapUtils.addToInteger(day, nServicesOnDate, 1, 1);
			}
		}
		for(Map.Entry<LocalDate, Integer> entry : nServicesOnDate.entrySet()) {
			if(entry.getValue() > maxService) {
				maxService = entry.getValue();
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
	public static void writeStops(Collection<Stop> stops, String path) throws IOException {
		CSVWriter stopsWriter = new CSVWriter(new FileWriter(path + GtfsDefinitions.Files.STOPS.fileName), ',');
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
	public static void writeTrips(Collection<Trip> trips, String path) throws IOException {
		CSVWriter tripsWriter = new CSVWriter(new FileWriter(path + GtfsDefinitions.Files.TRIPS.fileName), ',');
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
	 * Experimental class to write transfers.txt (i.e. after creating additional walk transfer)
	 */
	public static void writeTransfers(Collection<Transfer> transfers, String path) throws IOException {
		CSVWriter transfersWiter = new CSVWriter(new FileWriter(path + GtfsDefinitions.Files.TRANSFERS.fileName), ',');
		String[] columns = GtfsDefinitions.Files.TRANSFERS.columns;
		String[] optionalColumns = GtfsDefinitions.Files.TRANSFERS.optionalColumns;
		String[] header = Stream.concat(Arrays.stream(columns), Arrays.stream(optionalColumns)).toArray(String[]::new);
		transfersWiter.writeNext(header, true);
		for(Transfer transfer : transfers) {
			// FROM_STOP_ID, TO_STOP_ID, TRANSFER_TYPE, (MIN_TRANSFER_TIME)
			String[] line = new String[header.length];
			line[0] = transfer.getFromStopId();
			line[1] = transfer.getToStopId();
			line[2] = String.valueOf(transfer.getTransferType().index);
			String minTransferTime = (transfer.getMinTransferTime() != null ? transfer.getMinTransferTime().toString() : "");
			line[3] = minTransferTime;
			transfersWiter.writeNext(line);
		}
		transfersWiter.close();
	}

	/**
	 * @return Array of minW, minS, maxE, maxN (WGS84)
	 */
	public static double[] getExtent(GtfsFeed feed) {
		double maxE = Double.MIN_VALUE;
		double maxN = Double.MIN_VALUE;
		double minN = Double.MAX_VALUE;
		double minE = Double.MAX_VALUE;

		for(Stop stop : feed.getStops().values()) {
			if(stop.getLon() > maxE) {
				maxE = stop.getLon();
			}
			if(stop.getLat() > maxN) {
				maxN = stop.getLat();
			}
			if(stop.getLon() < minE) {
				minE = stop.getLon();
			}
			if(stop.getLat() < minN) {
				minN = stop.getLat();
			}
		}

		return new double[]{minE, minN, maxE, maxN};
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

	/**
	 * Filters trips.txt and stop_times.txt for the given date and time span. Writes the filtered
	 * file to output folder. All other files are copied from the input folder.
	 */
	public static void filterAndCopyFeed(String inputFolder, String outputFolder, LocalDate date, double startTime, double endTime) {
		Set<Trip> cutTrips = GtfsTools.getTripsOndates(new GtfsFeedImpl(inputFolder)).get(date);

		Set<Trip> cutTripsTime = new HashSet<>();
		for(Trip t : cutTrips) {
			int time = t.getStopTimes().last().getArrivalTime();
			if(time > startTime && time < endTime) {
				cutTripsTime.add(t);
			}
		}
		try {
			new File(outputFolder).mkdir();

			GtfsTools.writeStopTimes(cutTripsTime, outputFolder);
			GtfsTools.writeTrips(cutTripsTime, outputFolder);

			for(GtfsDefinitions.Files f : GtfsDefinitions.Files.values()) {
				if(!f.equals(GtfsDefinitions.Files.STOP_TIMES) && !f.equals(GtfsDefinitions.Files.TRIPS)) {
					File source = new File(inputFolder + f.fileName);
					if(source.exists()) {
						Files.copy(source.toPath(), new File(outputFolder +f.fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
