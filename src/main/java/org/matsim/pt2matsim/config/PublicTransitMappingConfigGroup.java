/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.config;

import com.opencsv.CSVReader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.internal.MatsimParameters;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.run.PublicTransitMapper;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Config Group used by {@link PublicTransitMapper}. Defines parameters for
 * mapping public transit to a network.
 *
 * @author polettif
 */
public class PublicTransitMappingConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUP_NAME = "PublicTransitMapping";

	// param names
	private static final String MODES_TO_KEEP_ON_CLEAN_UP = "modesToKeepOnCleanUp";
	private static final String NODE_SEARCH_RADIUS = "nodeSearchRadius";
	private static final String TRAVEL_COST_TYPE = "travelCostType";
	private static final String MAX_TRAVEL_COST_FACTOR = "maxTravelCostFactor";
	private static final String NETWORK_FILE = "networkFile";
	private static final String SCHEDULE_FILE = "scheduleFile";
	private static final String OUTPUT_NETWORK_FILE = "outputNetworkFile";
	private static final String OUTPUT_SCHEDULE_FILE = "outputScheduleFile";
	private static final String OUTPUT_STREET_NETWORK_FILE = "outputStreetNetworkFile";
	private static final String SCHEDULE_FREESPEED_MODES = "scheduleFreespeedModes";
	private static final String NUM_OF_THREADS = "numOfThreads";
	private static final String REMOVE_NOT_USED_STOP_FACILITIES = "removeNotUsedStopFacilities";

	private static final String N_LINK_THRESHOLD = "nLinkThreshold";
	private static final String CANDIDATE_DISTANCE_MULTIPLIER = "candidateDistanceMultiplier";
	private static final String MAX_LINK_CANDIDATE_DISTANCE = "maxLinkCandidateDistance";

	// default values
	private Map<String, Set<String>> modeRoutingAssignment = new HashMap<>();

	private Set<String> scheduleFreespeedModes = PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE_AS_SET;
	private Set<String> modesToKeepOnCleanUp = new HashSet<>();
	private double maxTravelCostFactor = 5.0;
	private int numOfThreads = 2;
	private double nodeSearchRadius = 500;
	private boolean removeNotUsedStopFacilities = true;
	private boolean combinePtModes = false;
	private boolean addPtMode = false;
	private String networkFile = null;
	private String scheduleFile = null;
	private String outputNetworkFile = null;
	private String outputStreetNetworkFile = null;
	private String outputScheduleFile = null;
	private TravelCostType travelCostType = TravelCostType.linkLength;

	private int nLinkThreshold = 6;
	private double maxLinkCandidateDistance = 90;
	private double candiateDistanceMulitplier = 1.6;

	public PublicTransitMappingConfigGroup() {
		super(GROUP_NAME);
	}

	/**
	 * @return a new default public transit mapping config
	 */
	public static PublicTransitMappingConfigGroup createDefaultConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");

		ModeRoutingAssignment mraBus = new ModeRoutingAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		ModeRoutingAssignment mraRail = new ModeRoutingAssignment("rail");
		mraRail.setNetworkModesStr("rail,light_rail");
		config.addParameterSet(mraBus);
		config.addParameterSet(mraRail);

		return config;
	}

	@Override
	public final Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(MODES_TO_KEEP_ON_CLEAN_UP,
				"All links that do not have a transit route on them are removed, except the ones \n" +
				"\t\tlisted in this set (typically only car). Separated by comma.");
		map.put(TRAVEL_COST_TYPE,
				"Defines which link attribute should be used for routing. Possible values \"" + TravelCostType.linkLength + "\" (default) \n" +
				"\t\tand \"" + TravelCostType.travelTime + "\".");
		map.put(NODE_SEARCH_RADIUS,
				"Defines the radius [meter] from a stop facility within nodes are searched. Values up to 2000 don't \n" +
				"\t\thave any significant impact on performance.");
		map.put(SCHEDULE_FREESPEED_MODES,
				"After the schedule has been mapped, the free speed of links can be set according to the necessary travel \n" +
				"\t\ttimes given by the transit schedule. The freespeed of a link is set to the minimal value needed by all \n" +
				"\t\ttransit routes passing using it. This is performed for \"" + PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE + "\" automatically, additional \n" +
				"\t\tmodes (rail is recommended) can be added, separated by commas.");
		map.put(MAX_TRAVEL_COST_FACTOR,
				"If all paths between two stops have a [travelCost] > [" + MAX_TRAVEL_COST_FACTOR + "] * [minTravelCost], \n" +
				"\t\tan artificial link is created. If " + TRAVEL_COST_TYPE + " is " + TravelCostType.travelTime + "\n" +
				"\t\tminTravelCost is the travelTime between stops from schedule. If " + TRAVEL_COST_TYPE + " is \n" +
				"\t\t" + TravelCostType.linkLength + " minTravel cost is the beeline distance.");
		map.put(NUM_OF_THREADS,
				"Defines the number of numOfThreads that should be used for pseudoRouting. Default: 2.");
		map.put(NETWORK_FILE, "Path to the input network file. Not needed if PTMapper is called within another class.");
		map.put(SCHEDULE_FILE, "Path to the input schedule file. Not needed if PTMapper is called within another class.");
		map.put(OUTPUT_NETWORK_FILE, "Path to the output network file. Not needed if PTMapper is used within another class.");
		map.put(OUTPUT_STREET_NETWORK_FILE, "Path to the output car only network file. The input multimodal map is filtered. \n" +
				"\t\tNot needed if PTMapper is used within another class.");
		map.put(OUTPUT_SCHEDULE_FILE, "Path to the output schedule file. Not needed if PTMapper is used within another class.");
		map.put(REMOVE_NOT_USED_STOP_FACILITIES,
				"If true, stop facilities that are not used by any transit route are removed from the schedule. Default: true");

		// link candidates
		map.put(CANDIDATE_DISTANCE_MULTIPLIER,
				"After " + N_LINK_THRESHOLD + " link candidates have been found, additional link \n" +
				"\t\t\tcandidates within [" + CANDIDATE_DISTANCE_MULTIPLIER + "] * [distance to the Nth link] are added to the set.\n" +
				"\t\t\tMust be >= 1.");
		map.put(N_LINK_THRESHOLD,
				"Number of link candidates considered for all stops, depends on accuracy of stops and desired \n" +
				"\t\t\tperformance. Somewhere between 4 and 10 seems reasonable for bus stops, depending on the accuracy of the stop \n" +
				"\t\t\tfacility coordinates and performance desires. Default: " + nLinkThreshold);
		map.put(MAX_LINK_CANDIDATE_DISTANCE,
				"The maximal distance [meter] a link candidate is allowed to have from the stop facility. No link candidate\n" +
				"\t\t\tbeyond this distance are added.");
		return map;
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch(type) {
			case ModeRoutingAssignment.SET_NAME:
				return new ModeRoutingAssignment();
			default:
				throw new IllegalArgumentException("Unknown parameterset name!");
		}
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		super.addParameterSet(set);
		loadParameterSets();
	}

	/**
	 * Loads the parameter sets for ModeRoutingAssignment, LinkCandidateCreator and ManualLinkCandidates for
	 * easier access
	 */
	private void loadParameterSets() {
		for(ConfigGroup e : this.getParameterSets(PublicTransitMappingConfigGroup.ModeRoutingAssignment.SET_NAME)) {
			ModeRoutingAssignment mra = (ModeRoutingAssignment) e;
			modeRoutingAssignment.put(mra.getScheduleMode(), mra.getNetworkModes());
		}
	}

	/**
	 * References transportModes from the schedule (key) and the
	 * allowed modeRouting of a link from the network (value). <p/>
	 * <p/>
	 * Schedule transport modeRouting should be in gtfs categories:
	 * <ul>
	 * <li>0 - Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area.</li>
	 * <li>1 - Subway, Metro. Any underground rail system within a metropolitan area.</li>
	 * <li>2 - Rail. Used for intercity or long-distance travel.</li>
	 * <li>3 - Bus. Used for short- and long-distance bus routes.</li>
	 * <li>4 - Ferry. Used for short- and long-distance boat service.</li>
	 * <li>5 - Cable car. Used for street-level cable cars where the cable runs beneath the car.</li>
	 * <li>6 - Gondola, Suspended cable car. Typically used for aerial cable cars where the car is suspended from the cable.</li>
	 * <li>7 - Funicular. Any rail system designed for steep inclines.</li>
	 * </ul>
	 */

	public Map<String, Set<String>> getModeRoutingAssignment() {
		return modeRoutingAssignment;
	}

	public void setModeRoutingAssignment(Map<String, Set<String>> modeRoutingAssignment) {
		this.modeRoutingAssignment = modeRoutingAssignment;
	}

	/**
	 * All links that do not have a transit route on them are removed, except
	 * the ones listed in this set (typically only car).
	 */

	public Set<String> getModesToKeepOnCleanUp() {
		return this.modesToKeepOnCleanUp;
	}

	public void setModesToKeepOnCleanUp(Set<String> modesToKeepOnCleanUp) {
		this.modesToKeepOnCleanUp = modesToKeepOnCleanUp;
	}

	@StringSetter(MODES_TO_KEEP_ON_CLEAN_UP)
	private void setModesToKeepOnCleanUp(String modesToKeepOnCleanUp) {
		if(modesToKeepOnCleanUp == null) {
			this.modesToKeepOnCleanUp = null;
			return;
		}
		for(String mode : modesToKeepOnCleanUp.split(",")) {
			this.modesToKeepOnCleanUp.add(mode.trim());
		}
	}

	@StringGetter(MODES_TO_KEEP_ON_CLEAN_UP)
	private String getModesToKeepOnCleanUpString() {
		String ret = "";
		if(modesToKeepOnCleanUp != null) {
			for(String mode : modesToKeepOnCleanUp) {
				ret += "," + mode;
			}
		}
		return this.modesToKeepOnCleanUp == null ? null : ret.substring(1);
	}

	/**
	 *
	 */
	@StringGetter(REMOVE_NOT_USED_STOP_FACILITIES)
	public boolean getRemoveNotUsedStopFacilities() {
		return removeNotUsedStopFacilities;
	}

	@StringSetter(REMOVE_NOT_USED_STOP_FACILITIES)
	public void setRemoveNotUsedStopFacilities(boolean v) {
		this.removeNotUsedStopFacilities = v;
	}

	/**
	 * Defines the radius [meter] from a stop facility within nodes are searched.
	 * Mainly a maximum value for performance.
	 */
	@StringGetter(NODE_SEARCH_RADIUS)
	public double getNodeSearchRadius() {
		return nodeSearchRadius;
	}

	@StringSetter(NODE_SEARCH_RADIUS)
	public void setNodeSearchRadius(double nodeSearchRadius) {
		this.nodeSearchRadius = nodeSearchRadius;
	}

	/**
	 * Threads
	 */
	@StringGetter(NUM_OF_THREADS)
	public int getNumOfThreads() {
		return numOfThreads;
	}

	@StringSetter(NUM_OF_THREADS)
	public void setNumOfThreads(int numOfThreads) {
		this.numOfThreads = numOfThreads;
	}

	/**
	 *
	 */
	@StringGetter(TRAVEL_COST_TYPE)
	public TravelCostType getTravelCostType() {
		return travelCostType;
	}

	@StringSetter(TRAVEL_COST_TYPE)
	public void setTravelCostType(TravelCostType type) {
		this.travelCostType = type;
	}

	/**
	 * If all paths between two stops have a length > maxTravelCostFactor * beelineDistance,
	 * an artificial link is created.
	 */
	@StringGetter(MAX_TRAVEL_COST_FACTOR)
	public double getMaxTravelCostFactor() {
		return maxTravelCostFactor;
	}


	@StringSetter(MAX_TRAVEL_COST_FACTOR)
	public void setMaxTravelCostFactor(double maxTravelCostFactor) {
		if(maxTravelCostFactor < 1) {
			throw new RuntimeException("maxTravelCostFactor cannnot be less than 1!");
		}
		this.maxTravelCostFactor = maxTravelCostFactor;
	}

	/**
	 *
	 */
	@StringGetter(SCHEDULE_FREESPEED_MODES)
	public String getScheduleFreespeedModesStr() {
		return "";
	}

	@StringSetter(SCHEDULE_FREESPEED_MODES)
	public void setScheduleFreespeedModesStr(String modes) {
		this.scheduleFreespeedModes.addAll(CollectionUtils.stringToSet(modes));
	}

	public Set<String> getScheduleFreespeedModes() {
		return scheduleFreespeedModes;
	}

	public void setScheduleFreespeedModes(Set<String> modes) {
		this.scheduleFreespeedModes.addAll(modes);
	}


	/**
	 * Params for filepaths
	 */
	@StringGetter(NETWORK_FILE)
	public String getNetworkFileStr() {
		return this.networkFile == null ? "" : this.networkFile;
	}

	public String getNetworkFile() {
		return this.networkFile;
	}

	@StringSetter(NETWORK_FILE)
	public void setNetworkFile(String networkFile) {
		this.networkFile = networkFile.equals("") ? null : networkFile;
	}

	@StringGetter(SCHEDULE_FILE)
	public String getScheduleFileStr() {
		return this.scheduleFile == null ? "" : this.scheduleFile;
	}

	public String getScheduleFile() {
		return this.scheduleFile;
	}

	@StringSetter(SCHEDULE_FILE)
	public void setScheduleFile(String scheduleFile) {
		this.scheduleFile = scheduleFile.equals("") ? null : scheduleFile;
	}

	@StringGetter(OUTPUT_NETWORK_FILE)
	public String getOutputNetworkFile() {
		return this.outputNetworkFile == null ? "" : this.outputNetworkFile;
	}

	@StringSetter(OUTPUT_NETWORK_FILE)
	public String setOutputNetworkFile(String outputNetwork) {
		final String old = this.outputNetworkFile;
		this.outputNetworkFile = outputNetwork;
		return old;
	}

	@StringGetter(OUTPUT_STREET_NETWORK_FILE)
	public String getOutputStreetNetworkFileStr() {
		return this.outputStreetNetworkFile == null ? "" : this.outputStreetNetworkFile;
	}

	public String getOutputStreetNetworkFile() {
		return this.outputStreetNetworkFile;
	}

	@StringSetter(OUTPUT_STREET_NETWORK_FILE)
	public void setOutputStreetNetworkFile(String outputStreetNetworkFile) {
		this.outputStreetNetworkFile = outputStreetNetworkFile.equals("") ? null : outputStreetNetworkFile;
	}

	public String getOutputScheduleFile() {
		return this.outputScheduleFile;
	}

	@StringGetter(OUTPUT_SCHEDULE_FILE)
	public String getOutputScheduleFileStr() {
		return this.outputScheduleFile == null ? "" : this.outputScheduleFile;
	}

	@StringSetter(OUTPUT_SCHEDULE_FILE)
	public String setOutputScheduleFile(String outputSchedule) {
		final String old = this.outputScheduleFile;
		this.outputScheduleFile = outputSchedule;
		return old;
	}

	public enum TravelCostType {
		travelTime, linkLength
	}

	/*
	Link Candidates
	 */

	/**
	 * max n closest links
	 */
	@StringGetter(N_LINK_THRESHOLD)
	public int getNLinkThreshold() {
		return nLinkThreshold;
	}

	@StringSetter(N_LINK_THRESHOLD)
	public void setNLinkThreshold(int n) {
		this.nLinkThreshold = n;
	}

	/**
	 * max distance
	 */
	@StringGetter(MAX_LINK_CANDIDATE_DISTANCE)
	public double getMaxLinkCandidateDistance() {
		return maxLinkCandidateDistance;
	}

	@StringSetter(MAX_LINK_CANDIDATE_DISTANCE)
	public void setMaxLinkCandidateDistance(double maxLinkCandidateDistance) {
		this.maxLinkCandidateDistance = maxLinkCandidateDistance;
	}

	/**
	 * Defines the radius [meter] from a stop facility within nodes are searched.
	 * Mainly a maximum value for performance.
	 */
	@StringGetter(CANDIDATE_DISTANCE_MULTIPLIER)
	public double getCandidateDistanceMultiplier() {
		return candiateDistanceMulitplier;
	}

	@StringSetter(CANDIDATE_DISTANCE_MULTIPLIER)
	public void setCandidateDistanceMultiplier(double multiplier) {
		this.candiateDistanceMulitplier = multiplier < 1 ? 1 : multiplier;
	}


	/**
	 * Parameterset that define which network transport modes the router
	 * can use for each schedule transport mode.<p/>
	 * <p>
	 * Network transport modes are the ones in {@link Link#getAllowedModes()}, schedule
	 * transport modes are from {@link TransitRoute#getTransportMode()}.
	 */
	public static class ModeRoutingAssignment extends ReflectiveConfigGroup implements MatsimParameters {

		public final static String SET_NAME = "modeRoutingAssignment";

		private static final String SCHEDULE_MODE = "scheduleMode";
		private static final String NETWORK_MODES = "networkModes";

		private String scheduleMode;
		private Set<String> networkModes;

		public ModeRoutingAssignment() {
			super(SET_NAME);
		}

		public ModeRoutingAssignment(String scheduleMode) {
			super(SET_NAME);
			this.scheduleMode = scheduleMode;
		}

		@Override
		public Map<String, String> getComments() {
			Map<String, String> map = super.getComments();
			map.put(NETWORK_MODES,
					"Transit Routes with the given scheduleMode can only use links with at least one of the network modes\n" +
					"\t\t\tdefined here. Separate multiple modes by comma. If no network modes are defined, the transit route will\n" +
					"\t\t\tuse artificial links.");
			return map;
		}

		@StringGetter(SCHEDULE_MODE)
		public String getScheduleMode() {
			return scheduleMode;
		}

		@StringSetter(SCHEDULE_MODE)
		public void setScheduleMode(String scheduleMode) {
			this.scheduleMode = scheduleMode;
		}

		@StringGetter(NETWORK_MODES)
		public String getNetworkModesStr() {
			return CollectionUtils.setToString(networkModes);
		}

		@StringSetter(NETWORK_MODES)
		public void setNetworkModesStr(String networkModesStr) {
			this.networkModes = CollectionUtils.stringToSet(networkModesStr);
		}

		public Set<String> getNetworkModes() {
			return this.networkModes;
		}

		public void setNetworkModes(Set<String> networkModes) {
			this.networkModes = networkModes;
		}
	}
}
