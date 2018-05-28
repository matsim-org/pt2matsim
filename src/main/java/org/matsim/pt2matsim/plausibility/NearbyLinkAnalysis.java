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

package org.matsim.pt2matsim.plausibility;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.tools.CsvTools;
import org.matsim.pt2matsim.tools.MiscUtils;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Class to analyse the distance between stop facilities and links.
 * Results (averages/quantiles) can be used to adjust link candidate parameters.
 *
 * @author polettif
 */
public class NearbyLinkAnalysis {

	/** Example code/paths **/
	public static void main(String[] args) {
		String base = "zvv/";
		String inputNetworkFile = base + "network/network_unmapped.xml.gz";
		String inputScheduleFile = base + "mts/schedule_unmapped.xml.gz";

		Network n = NetworkTools.readNetwork(inputNetworkFile);
		TransitSchedule s = ScheduleTools.readTransitSchedule(inputScheduleFile);

		NearbyLinkAnalysis nba = new NearbyLinkAnalysis(s, n);
		nba.printDistances(20);
		nba.writeDistancesCsv(24, "bus", base + "linkDistances.csv");
	}

	private final Collection<PublicTransitStop> stops = new HashSet<>();

	public NearbyLinkAnalysis(TransitSchedule schedule, Network network) {
		for(TransitLine line : schedule.getTransitLines().values()) {
			for(TransitRoute route : line.getRoutes().values()) {
				for(TransitRouteStop stop : route.getStops()) {
					stops.add(new PublicTransitStop(stop.getStopFacility(), route.getTransportMode()));
				}
			}
		}

		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

		for(PublicTransitStop stop : stops) {
			Map<Double, Set<Link>> sortedLinks = NetworkTools.findClosestLinks(network, stop.getStopFacility().getCoord(), 500, config.getTransportModeAssignment().get(stop.getMode()));
			stop.addLinks(sortedLinks);
		}
	}

	/**
	 * Writes the distances for the 1st to the n-th link for each
	 * stop to to a csv file.
	 */
	public void writeDistancesCsv(int nLinks, String scheduleMode, String file) {
		Map<Tuple<Integer, Integer>, String> csv = new HashMap<>();

		csv.put(new Tuple<>(1, 1), "stop");
		// header
		for(int k=2; k<= nLinks+1; k++) {
			csv.put(new Tuple<>(1, k), "link."+(k-1));
		}

		int i = 2;
		for(PublicTransitStop stop : stops) {
			if(stop.getMode().equals(scheduleMode)) {
				csv.put(new Tuple<>(i, 1), stop.getStopFacility().getId().toString());
				int j = 2;
				for(NearbyLink link : stop.getLinks()) {
					csv.put(new Tuple<>(i, j), Double.toString(link.distance));
					j++;
					if(j > nLinks) {
						break;
					}
				}
				i++;
			}
		}

		try {
			CsvTools.writeToFile(CsvTools.convertToCsvLines(csv, ';'), file);
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write the quantile distances for the 1st to the n-th link for all stops to the console.
	 *
	 */
	public void printDistances(int nLinks) {
		SortedMap<Integer, SortedSet<NearbyLink>> distanceForNthLink = new TreeMap<>();

		for(PublicTransitStop stop : stops) {
			int n=1;
			for(NearbyLink nbl : stop.getLinks()) {
				if(n > nLinks) break;
				MiscUtils.getSortedSet(n, distanceForNthLink).add(nbl);
				n++;
			}
		}

		System.out.println("link" + (nLinks > 9 ? " " : "") + " \t Q25  \t Q50  \t Q75  \t Q85  \t Q95  ");

		for(Map.Entry<Integer, SortedSet<NearbyLink>> e : distanceForNthLink.entrySet()) {
			Object[] values = e.getValue().toArray();
			int m = values.length;

			double val;
			System.out.print("#  " + e.getKey());
			val = ((NearbyLink) values[(int) (0.25 * m)]).distance; System.out.format("\t"+(val < 10 ? " ": "" )+"%.1f",val);
			val = ((NearbyLink) values[(int) (0.50 * m)]).distance; System.out.format("\t"+(val < 10 ? " ": "" )+"%.1f",val);
			val = ((NearbyLink) values[(int) (0.75 * m)]).distance; System.out.format("\t"+(val < 10 ? " ": "" )+"%.1f",val);
			val = ((NearbyLink) values[(int) (0.85 * m)]).distance; System.out.format("\t"+(val < 10 ? " ": "" )+"%.1f",val);
			val = ((NearbyLink) values[(int) (0.95 * m)]).distance; System.out.format("\t"+(val < 10 ? " ": "" )+"%.1f",val);
			System.out.print("\n");
		}
	}

	/**
	 * Wrapper class to store distance and stop for a link.
	 */
	private class NearbyLink implements Comparable<NearbyLink> {

		private final PublicTransitStop stop;
		private final Link link;
		private final double distance;

		private NearbyLink(PublicTransitStop stop, Link link, double distance) {
			this.stop = stop;
			this.link = link;
			this.distance = distance;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			NearbyLink that = (NearbyLink) o;

			if(Double.compare(that.distance, distance) != 0) return false;
			if(!stop.equals(that.stop)) return false;
			return link.equals(that.link);
		}

		@Override
		public int hashCode() {
			int result;
			long temp;
			result = stop.hashCode();
			result = 31 * result + link.hashCode();
			temp = Double.doubleToLongBits(distance);
			result = 31 * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public int compareTo(NearbyLink o) {
			return distance < o.distance ? -1 : 1;
		}

		public String getId() {
			return stop.getStopFacility().getId().toString()+".link"+link.getId().toString();
		}
	}


	/**
	 * Wrapper class to separate facilities for different modes
	 */
	private class PublicTransitStop {

		private final TransitStopFacility stopFacility;
		private final String mode;
		private SortedSet<NearbyLink> nearbyLinks = new TreeSet<>();

		private

		PublicTransitStop(TransitStopFacility stopFacility, String mode) {
			this.stopFacility = stopFacility;
			this.mode = mode;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			PublicTransitStop that = (PublicTransitStop) o;

			if(!stopFacility.equals(that.stopFacility)) return false;
			return mode != null ? mode.equals(that.mode) : that.mode == null;
		}

		@Override
		public int hashCode() {
			int result = stopFacility.hashCode();
			result = 31 * result + (mode != null ? mode.hashCode() : 0);
			return result;
		}

		public TransitStopFacility getStopFacility() {
			return stopFacility;
		}

		public void addLinks(Map<Double, Set<Link>> sortedLinks) {
			for(Map.Entry<Double, Set<Link>> e : sortedLinks.entrySet()) {
				if(e.getKey() < 500) {
					for(Link l : e.getValue()) {
						this.nearbyLinks.add(new NearbyLink(this, l, e.getKey()));
					}
				}
			}
		}

		public SortedSet<NearbyLink> getLinks() {
			return nearbyLinks;
		}

		public String getMode() {
			return mode;
		}
	}

}
