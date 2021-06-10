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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreator;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreatorStandard;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersFactory;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersStandard;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoSchedule;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoScheduleImpl;
import org.matsim.pt2matsim.plausibility.StopFacilityHistogram;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.List;
import java.util.Set;

/**
 * References an unmapped transit schedule to a network. Combines
 * finding link sequences for TransitRoutes and referencing
 * TransitStopFacilities to link. Calculates the least cost path
 * from the transit route's first to its last stop with the constraint
 * that the path must contain a link candidate of every stop.<p/>
 * <p>
 * Additional stop facilities are created if a stop facility has more
 * than one plausible link. Artificial links are added to the network
 * if no path can be found.</p>
 * <p>
 * {@link LinkCandidateCreator} is applied to find link candidates and
 * {@link ScheduleRouters} to find the shortest paths on the network.
 * </p>
 *
 * @author polettif
 */
public class PTMapper {

	protected static Logger log = Logger.getLogger(PTMapper.class);
	private final PseudoSchedule pseudoSchedule = new PseudoScheduleImpl();
	private Network network;
	private TransitSchedule schedule;

	public static void mapScheduleToNetwork(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
		if(config.getInputNetworkFile() != null) {
			log.warn("The input network file set in PublicTransitMappingConfigGroup is ignored");
		}
		if(config.getInputScheduleFile() != null) {
			log.warn("The input schedule file set in PublicTransitMappingConfigGroup is ignored");
		}
		new PTMapper(schedule, network).run(config);
	}

	/**
	 * The provided schedule is expected to contain the stops sequence and
	 * the stop facilities each transit route. The routes will be newly routed,
	 * any former routes will be overwritten. Changes are done on the schedule and
	 * network provided here.
	 * <p/>
	 *
	 * @param schedule which will be newly routed.
	 * @param network  schedule is mapped to this network, is modified
	 */
	public PTMapper(TransitSchedule schedule, Network network) {
		this.schedule = schedule;
		this.network = network;
	}

	public void run(PublicTransitMappingConfigGroup config) {
		run(config, null, null);
	}
	
	/**
	 * Maps the schedule to the network with parameters defined in config
	 */
	public void run(PublicTransitMappingConfigGroup config, LinkCandidateCreator linkCandidateCreator, ScheduleRoutersFactory scheduleRoutersFactory) {
		// use defaults
		if(linkCandidateCreator == null) {
			linkCandidateCreator = new LinkCandidateCreatorStandard(schedule, network,
					config.getNLinkThreshold(),
					config.getCandidateDistanceMultiplier(),
					config.getMaxLinkCandidateDistance(),
					config.getTransportModeAssignment());
		}
		
		if(scheduleRoutersFactory == null) {
			scheduleRoutersFactory = new ScheduleRoutersStandard.Factory(schedule, network, config.getTransportModeAssignment(), config.getTravelCostType(), config.getRoutingWithCandidateDistance());
		}

		run(linkCandidateCreator,
			scheduleRoutersFactory,
			config.getNumOfThreads(), config.getMaxTravelCostFactor(),
			config.getScheduleFreespeedModes(), config.getModesToKeepOnCleanUp(),
			config.getRemoveNotUsedStopFacilities());
	}

	/**
	 * Maps the schedule to the network
	 */
	public void run(LinkCandidateCreator linkCandidates, ScheduleRoutersFactory scheduleRoutersFactory, int numThreads, double maxTravelCostFactor, Set<String> scheduleFreespeedModes, Set<String> modesToKeepOnCleanup, boolean removeNotUsedStopFacilities) {
		if(schedule == null) throw new RuntimeException("No schedule defined!");
		if(network == null) throw new RuntimeException("No network defined!");

		if(linkCandidates == null) throw new RuntimeException("No LinkCandidates defined!");
		if(scheduleRoutersFactory == null) throw new RuntimeException("No ScheduleRoutersFactory defined!");

		if(ScheduleTools.idsContainChildStopString(schedule)) {
			throw new RuntimeException("Some stopFacility ids contain the string \"" + PublicTransitMappingStrings.SUFFIX_CHILD_STOP_FACILITIES + "\"! Schedule cannot be mapped.");
		}

		PTMapperTools.setLogLevels();

		if(schedule.getTransitLines().size() == 0) {
			throw new IllegalArgumentException("No transit lines available in schedule");
		}
		if(schedule.getFacilities().size() == 0) {
			throw new IllegalArgumentException("No stop facilities available in schedule");
		}

		log.info("======================================");
		log.info("Mapping transit schedule to network...");

		/*
		  Some schedule statistics
		 */
		int nStopFacilities = schedule.getFacilities().size();
		int nTransitRoutes = 0;
		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			nTransitRoutes += transitLine.getRoutes().size();
		}

		/* [1]
		  PseudoRouting
		  Initiate and start threads, calculate PseudoTransitRoutes
		  for all transit routes.
		 */
		log.info("==================================");
		log.info("Calculating pseudoTransitRoutes... (" + nTransitRoutes + " transit routes in " + schedule.getTransitLines().size() + " transit lines)");

		Progress progress = new Progress(nTransitRoutes, "Calculating pseudoTransitRoutes ...");
		
		// initiate pseudoRouting
		PseudoRouting[] pseudoRoutingRunnables = new PseudoRouting[numThreads];
		for(int i = 0; i < numThreads; i++) {
			pseudoRoutingRunnables[i] = new PseudoRoutingImpl(scheduleRoutersFactory, linkCandidates, maxTravelCostFactor, progress);
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
				throw new RuntimeException(e);
			}
		}


		/* [2]
		  Collect artificial links from threads and add them to network.
		  Collect pseudoSchedules from threads.
		 */
		log.info("=====================================");
		log.info("Adding artificial links to network...");
		for(PseudoRouting prt : pseudoRoutingRunnables) {
			prt.addArtificialLinks(network);
			pseudoSchedule.mergePseudoSchedule(prt.getPseudoSchedule());
		}


		/* [3]
		  Replace the parent stop facilities in each transitRoute's routeProfile
		  with child StopFacilities. Add the new transitRoutes to the schedule.
		 */
		log.info("==========================================================================================");
		log.info("Replacing parent StopFacilities in schedule, creating link sequences for transit routes...");
		pseudoSchedule.createFacilitiesAndLinkSequences(schedule);

		/* [4]
		  Now that all lines have been routed, it is possible that a route passes
		  a link closer to a stop facility than its referenced link.
		 */
		log.info("================================");
		log.info("Pulling child stop facilities...");
		int nPulled = 1;
		while(nPulled != 0) {
			nPulled = PTMapperTools.pullChildStopFacilitiesTogether(this.schedule, this.network);
		}

		/* [5] */
		log.info("==========================================");
		log.info("Add transfers for child stop facilities...");
		PTMapperTools.addTransfersForChildStopFacilities(this.schedule);

		/* [6]
		  After all lines are created, clean the schedule and network. Removing
		  not used transit links includes removing artificial links that
		  needed to be added to the network for routing purposes.
		 */
		log.info("=============================");
		log.info("Clean schedule and network...");
		cleanScheduleAndNetwork(scheduleFreespeedModes, modesToKeepOnCleanup, removeNotUsedStopFacilities);

		/* [7]
		  Validate the schedule
		 */
		log.info("======================");
		log.info("Validating schedule...");
		printValidateSchedule();

		log.info("==================================================");
		log.info("= Mapping transit schedule to network completed! =");
		log.info("==================================================");

		/*
		  Statistics
		 */
		printStatistics(nStopFacilities);
	}

	private void cleanScheduleAndNetwork(Set<String> scheduleFreespeedModes, Set<String> modesToKeepOnCleanup, boolean removeNotUsedStopFacilities) {
		NetworkTools.resetLinkLength(network, PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE);

		// changing the freespeed of the artificial links (value is used in simulations)
		ScheduleTools.setFreeSpeedBasedOnSchedule(network, schedule, scheduleFreespeedModes);

		// Remove unnecessary parts of schedule
		ScheduleCleaner.removeNotUsedTransitLinks(schedule, network, modesToKeepOnCleanup, true);
		if(removeNotUsedStopFacilities) {
			ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		}
		ScheduleCleaner.removeNotUsedMinimalTransferTimes(schedule);

		// change the network transport modes
		ScheduleTools.assignScheduleModesToLinks(schedule, network);
		// ScheduleTools.addLoopLinkAtRouteStart(schedule, network);
		// NetworkTools.replaceNonCarModesWithPT(network);
		// ScheduleTools.addPTModeToNetwork(schedule, network);
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

	public TransitSchedule getSchedule() {
		return schedule;
	}

	public Network getNetwork() {
		return network;
	}
}
