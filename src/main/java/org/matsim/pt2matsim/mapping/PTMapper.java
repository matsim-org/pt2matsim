/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.pt2matsim.mapping;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreator;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreatorUnique;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersTransportMode;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoSchedule;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoScheduleImpl;
import org.matsim.pt2matsim.plausibility.StopFacilityHistogram;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.List;

/**
 * References an unmapped transit schedule to a network. Combines
 * finding link sequences for TransitRoutes and referencing
 * TransitStopFacilities to link. Calculates the least cost path
 * from the transit route's first to its last stop with the constraint
 * that the path must contain a link candidate of every stop.<p/>
 *
 * Additional stop facilities are created if a stop facility has more
 * than one plausible link. Artificial links are added to the network
 * if no path can be found.
 *
 * @author polettif
 */
public class PTMapper {

	protected static Logger log = Logger.getLogger(PTMapper.class);
	private final PseudoSchedule pseudoSchedule = new PseudoScheduleImpl();
	private PublicTransitMappingConfigGroup config;
	private Network network;
	private TransitSchedule schedule;
	private LinkCandidateCreator linkCandidates;
	private ScheduleRouters scheduleRouters;

	/**
	 * Use this constructor if you just want to use the config for mapping parameters.
	 * The provided schedule is expected to contain the stops sequence and
	 * the stop facilities each transit route. The routes will be newly routed,
	 * any former routes will be overwritten. Changes are done on the schedule
	 * network provided here.
	 * <p/>
	 *
	 * @param config a PublicTransitMapping config that defines all parameters used
	 *               for mapping.
	 * @param schedule which will be newly routed.
	 * @param network schedule is mapped to this network, is modified
	 */
	public PTMapper(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network, MapperModule... modules) {
		this.config = config;
		this.schedule = schedule;
		this.network = network;

		for(MapperModule m : modules) {
			if(m instanceof LinkCandidateCreator) {	this.linkCandidates = (LinkCandidateCreator) m;	}
			if(m instanceof ScheduleRouters) {	this.scheduleRouters = (ScheduleRouters) m;	}
		}

		// assign defaults
		if(this.linkCandidates == null) {	this.linkCandidates = new LinkCandidateCreatorUnique(schedule, network, this.config);	}
		if(this.scheduleRouters == null) {	this.scheduleRouters = new ScheduleRoutersTransportMode(this.config, schedule, network, false);	}
	}

	private static void setLogLevels() {
		Logger.getLogger(org.matsim.core.router.Dijkstra.class).setLevel(Level.ERROR); // suppress no route found warnings
		Logger.getLogger(Network.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.network.filter.NetworkFilterManager.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessEuclidean.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessLandmarks.class).setLevel(Level.WARN);
	}

	/**
	 * Reads the schedule and network file specified in the PublicTransitMapping
	 * config and maps the schedule to the network. Writes the output files as
	 * well if defined in config. The mapping parameters defined in the config
	 * are used.
	 */
	public void run() {
		if(schedule == null) {
			throw new RuntimeException("No schedule defined!");
		} else if(network == null) {
			throw new RuntimeException("No network defined!");
		}

		if(ScheduleTools.idsContainChildStopString(schedule)) {
			throw new RuntimeException("Some stopFacility ids contain the string \"" + PublicTransitMappingStrings.SUFFIX_CHILD_STOP_FACILITIES + "\"! Schedule cannot be mapped.");
		}

		setLogLevels();
		config.loadParameterSets();

		log.info("======================================");
		log.info("Mapping transit schedule to network...");

		/**
		 * Some schedule statistics
		 * Check link candidate params and mode routing assignment
		 */
		int nStopFacilities = schedule.getFacilities().size();
		int nTransitRoutes = 0;
		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				nTransitRoutes++;
				String scheduleMode = transitRoute.getTransportMode();
				if(!config.getModeRoutingAssignment().containsKey(scheduleMode)) {
					throw new IllegalArgumentException("No mode routing assignment for schedule mode " + scheduleMode);
				}
				if(!config.getLinkCandidateCreatorParams().containsKey(scheduleMode)) {
					throw new IllegalArgumentException("No link candidate creator parameters assignment for schedule mode " + scheduleMode);
				}
			}
		}

		/** [1]
		 * Create a separate network for all schedule modes and
		 * initiate routers.
		 */
		log.info("==============================================");
		log.info("Creating network routers for transit routes...");
		scheduleRouters.load();


		/** [2]
		 * Load the closest links and create LinkCandidates. StopFacilities
		 * with no links within search radius are given a dummy loop link right
		 * on their coordinates. Each Link Candidate is a possible new stop facility
		 * after PseudoRouting.
		 */
		log.info("===========================");
		log.info("Creating link candidates...");
		linkCandidates.load();


		/** [3]
		 * PseudoRouting
		 * Initiate and start threads, calculate PseudoTransitRoutes
		 * for all transit routes.
		 */
		log.info("==================================");
		log.info("Calculating pseudoTransitRoutes... ("+nTransitRoutes+" transit routes in "+schedule.getTransitLines().size()+" transit lines)");

		// initiate pseudoRouting
		int numThreads = config.getNumOfThreads() > 0 ? config.getNumOfThreads() : 1;
		PseudoRouting[] pseudoRoutingRunnables = new PseudoRouting[numThreads];
		for(int i = 0; i < numThreads; i++) {
			pseudoRoutingRunnables[i] = new PseudoRoutingImpl(config, scheduleRouters, linkCandidates);
		}
		// spread transit lines on runnables
		int thr = 0;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			pseudoRoutingRunnables[thr++ % numThreads].addTransitLineToQueue(transitLine);
		}

		Thread[] threads = new Thread[numThreads];
		// start pseudoRouting
		for(int i = 0; i < numThreads; i++) {
			threads[i] = new Thread(pseudoRoutingRunnables[i]);
			threads[i].start();
		}
		for(Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}


		/** [4]
		 * Collect artificial links from threads and add them to network.
		 * Collect pseudoSchedules from threads.
		 */
		log.info("=====================================");
		log.info("Adding artificial links to network...");
		for(PseudoRouting prt : pseudoRoutingRunnables) {
			prt.addArtificialLinks(network);
			pseudoSchedule.mergePseudoSchedule(prt.getPseudoSchedule());
		}


		/** [5]
		 * Replace the parent stop facilities in each transitRoute's routeProfile
		 * with child StopFacilities. Add the new transitRoutes to the schedule.
		 */
		log.info("==========================================================================================");
		log.info("Replacing parent StopFacilities in schedule, creating link sequences for transit routes...");
		pseudoSchedule.createFacilitiesAndLinkSequences(schedule);

		/** [6]
		 * Now that all lines have been routed, it is possible that a route passes
		 * a link closer to a stop facility than its referenced link.
		 */
		log.info("================================");
		log.info("Pulling child stop facilities...");
		int nPulled = 1;
		while(nPulled != 0) {
			nPulled = PTMapperTools.pullChildStopFacilitiesTogether(this.schedule, this.network);
		}

		/** [7]
		 * After all lines are created, clean the schedule and network. Removing
		 * not used transit links includes removing artificial links that
		 * needed to be added to the network for routing purposes.
		 */
		log.info("=============================");
		log.info("Clean schedule and network...");
		cleanScheduleAndNetwork();

		/** [8]
		 * Validate the schedule
		 */
		log.info("======================");
		log.info("Validating schedule...");
		printValidateSchedule();

		log.info("==================================================");
		log.info("= Mapping transit schedule to network completed! =");
		log.info("==================================================");

		/**
		 * Statistics
		 */
		printStatistics(nStopFacilities);
	}

	private void cleanScheduleAndNetwork() {
		// might have been set higher during pseudo routing
		NetworkTools.resetLinkLength(network, PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE);

		// changing the freespeed of the artificial links (value is used in simulations)
		ScheduleTools.setFreeSpeedBasedOnSchedule(network, schedule, config.getScheduleFreespeedModes());

		// Remove unnecessary parts of schedule
		ScheduleCleaner.removeNotUsedTransitLinks(schedule, network, config.getModesToKeepOnCleanUp(), true);
		if(config.getRemoveNotUsedStopFacilities()) ScheduleCleaner.removeNotUsedStopFacilities(schedule);

		// change the network transport modes
		ScheduleTools.assignScheduleModesToLinks(schedule, network);
		if(config.getCombinePtModes()) {
			NetworkTools.replaceNonCarModesWithPT(network);
		} else if(config.getAddPtMode()) {
			ScheduleTools.addPTModeToNetwork(schedule, network);
		}
	}

	/**
	 * Log the result of the schedule validator
	 */
	private void printValidateSchedule() {
		TransitScheduleValidator.ValidationResult validationResult = TransitScheduleValidator.validateAll(schedule, network);
		if(validationResult.isValid()) {
			log.info("Schedule appears valid!");
		} else {
			log.warn("Schedule is NOT valid!");
		}
		if(validationResult.getErrors().size() > 0) {
			log.info("Validation errors:");
			for(String e : validationResult.getErrors()) {
				log.info(e);
			}
		}
		if(validationResult.getWarnings().size() > 0) {
			log.info("Validation warnings:");
			for(String w : validationResult.getWarnings()) {
				log.info(w);
			}
		}
	}

	/**
	 * Print some basic mapping statistics.
	 */
	private void printStatistics(int inputNStopFacilities) {
		int nArtificialLinks = 0;
		for(Link l : network.getLinks().values()) {
			if(l.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE)) {
				nArtificialLinks++;
			}
		}
		int withoutArtificialLinks = 0;
		int nRoutes = 0;
		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				nRoutes++;
				boolean routeHasArtificialLink = false;
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(transitRoute);
				for(Id<Link> linkId : linkIds) {
					if(network.getLinks().get(linkId).getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE)) {
						routeHasArtificialLink = true;
					}
				}
				if(!routeHasArtificialLink) {
					withoutArtificialLinks++;
				}
			}
		}

		StopFacilityHistogram histogram = new StopFacilityHistogram(schedule);

		log.info("");
		log.info("    Artificial Links:");
		log.info("       created  " + nArtificialLinks);
		log.info("    Stop Facilities:");
		log.info("       total input   " + inputNStopFacilities);
		log.info("       total output  " + schedule.getFacilities().size());
		log.info("       diff.         " + (schedule.getFacilities().size() - inputNStopFacilities));
		log.info("    Child Stop Facilities:");
		log.info("       median nr created   " + String.format("%.0f", histogram.median()));
		log.info("       average nr created  " + String.format("%.2f", histogram.average()));
		log.info("       max nr created      " + String.format("%.0f", histogram.max()));
		log.info("    Transit Routes:");
		log.info("       total routes in schedule         " + nRoutes);
		log.info("       routes without artificial links  " + withoutArtificialLinks);
		log.info("");
		log.info("    Run PlausibilityCheck for further analysis");
		log.info("");
		log.info("==================================================");
	}

	public Config getConfig() {
		Config configAll = ConfigUtils.createConfig();
		configAll.addModule(config);
		return configAll;
	}

	public void setConfig(Config config) {
		this.config = ConfigUtils.addOrGetModule(config, PublicTransitMappingConfigGroup.GROUP_NAME, PublicTransitMappingConfigGroup.class);
	}

	public TransitSchedule getSchedule() {
		return schedule;
	}

	public void setSchedule(TransitSchedule schedule) {
		this.schedule = schedule;
	}

	public Network getNetwork() {
		return network;
	}

	public void setNetwork(Network network) {
		this.network = network;
	}
}
