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

package org.matsim.pt2matsim.editor;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.IOException;

/**
 * Executes the BasicScheduleEditor with the given schedule, network and
 * command file. Experimental!
 *
 * @author polettif
 */
public class RunScheduleEditor {

	protected static Logger log = Logger.getLogger(RunScheduleEditor.class);

	/**
	 * Loads the schedule and network, then executes all commands in the commands csv file.
	 *
	 * Possible Commands:
	 * - Reroute TransitRoute via new Link
	 * 		["rerouteViaLink"] [TransitLineId] [TransitRouteId] [oldLinkId] [newLinkId]
	 *
	 * - Reroute TransitRoute from a given stop facility
	 * 		["rerouteFromStop"] [TransitLineId] [TransitRouteId] [fromStopId] [newLinkId]
	 *
	 * - Changes the referenced link of a stopfacility. Effectively creates a new child stop facility.
	 * 		["changeRefLink"] [StopFacilityId] [newlinkId]
	 * 		["changeRefLink"] [TransitLineId] [TransitRouteId] [ParentId] [newlinkId]
	 * 		["changeRefLink"] ["allTransitRoutesOnLink"] [linkId] [ParentId] [newlinkId]
	 *
	 * - Add a link to the network. Uses the attributes (freespeed, nr of lanes, transportModes)
	 *   of the attributeLink.
	 * 		[addLink] [linkId] [fromNodeId] [toNodeId] [attributeLinkId]
	 *
	 * - Refreshes the given transit route (reroute all paths between referenced stop facility links)
	 * 		[refreshTransitRoute] [transitLineId] [transitRouteId]
	 *
	 * // Comment
	 *
	 * @param args [0] schedule file
	 *             [1] network file
	 *             [2] command csv file
	 *             [3] output schedule file (optional if input file should be overwritten)
	 *             [4] output network file (optional if input file should be overwritten)
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if(args.length == 5) {
			run(args[0], args[1], args[2], args[3], args[4]);
		} else if(args.length == 3) {
			run(args[0], args[1], args[2], args[0], args[1]);
		} else {
			throw new IllegalArgumentException("Wrong number of input arguments.");
		}
	}

	public static void run(String scheduleFile, String networkFile, String commandFile, String outputScheduleFile, String outputNetworkFile) throws IOException {
		setLogLevels();
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(scheduleFile);
		Network network = NetworkTools.readNetwork(networkFile);

		ScheduleEditor scheduleEditor = new BasicScheduleEditor(schedule, network);
		scheduleEditor.parseCommandCsv(commandFile);

		ScheduleTools.assignScheduleModesToLinks(schedule, network);
		log.info("Writing schedule and network to file...");
		new TransitScheduleWriter(schedule).writeFile(outputScheduleFile);
		new NetworkWriter(network).write(outputNetworkFile);
	}

	public static void run(String scheduleFile, String networkFile, String commandFile) throws IOException {
		run(scheduleFile, networkFile, commandFile, scheduleFile, networkFile);
	}

	private static void setLogLevels() {
		Logger.getLogger(org.matsim.core.router.Dijkstra.class).setLevel(Level.ERROR); // suppress no route found warnings
		Logger.getLogger(Network.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.api.core.v01.network.Node.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.api.core.v01.network.Link.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.utils.io.MatsimXmlParser.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.utils.io.MatsimFileTypeGuesser.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.network.filter.NetworkFilterManager.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessEuclidean.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessLandmarks.class).setLevel(Level.WARN);
	}

}