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

import static org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup.TravelCostType.travelTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.config.groups.ControllerConfigGroup.RoutingAlgorithmType;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt2matsim.run.PublicTransitMapper;


/**
 * Config Group used by {@link PublicTransitMapper}. Defines parameters for
 * mapping public transit to a network.
 *
 * @author polettif
 */
public class PublicTransitMappingConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUP_NAME = "PublicTransitMapping";

	public enum TravelCostType { linkLength, travelTime }

	private static final String INPUT_NETWORK_FILE = "inputNetworkFile";
	private static final String INPUT_SCHEDULE_FILE = "inputScheduleFile";
	private static final String OUTPUT_NETWORK_FILE = "outputNetworkFile";
	private static final String OUTPUT_SCHEDULE_FILE = "outputScheduleFile";
	private static final String OUTPUT_STREET_NETWORK_FILE = "outputStreetNetworkFile";

	private static final String TRAVEL_COST_TYPE = "travelCostType";
	private static final String MAX_TRAVEL_COST_FACTOR = "maxTravelCostFactor";
	private static final String SCHEDULE_FREESPEED_MODES = "scheduleFreespeedModes";
	private static final String NUM_OF_THREADS = "numOfThreads";

	private static final String MODES_TO_KEEP_ON_CLEAN_UP = "modesToKeepOnCleanUp";
	private static final String REMOVE_NOT_USED_STOP_FACILITIES = "removeNotUsedStopFacilities";

	private static final String N_LINK_THRESHOLD = "nLinkThreshold";
	private static final String CANDIDATE_DISTANCE_MULTIPLIER = "candidateDistanceMultiplier";
	private static final String MAX_LINK_CANDIDATE_DISTANCE = "maxLinkCandidateDistance";

	private static final String ROUTING_WITH_CANDIDATE_DISTANCE = "routingWithCandidateDistance";
	
	private static final String NETWORK_ROUTER = "networkRouter";
	
	private static final String USE_MODE_SPECIFIC_RULES = "modeSpecificRules";
	
	private static final String THREAD_CHUNK_SIZE = "threadChunkSize";

	// default values
	private Map<String, Set<String>> transportModeAssignment = new HashMap<>();
	private Map<String, TransportModeParameterSet> parameterSetsForMode = new HashMap<>();
	private Set<String> scheduleFreespeedModes = PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE_AS_SET;
	private Set<String> modesToKeepOnCleanUp = new HashSet<>();
	private double maxTravelCostFactor = 5.0;
	private int numOfThreads = 2;
	private int chunkSize = 100;
	private boolean removeNotUsedStopFacilities = true;

	private String inputNetworkFile = null;
	private String inputScheduleFile = null;
	private String outputNetworkFile = null;
	private String outputStreetNetworkFile = null;
	private String outputScheduleFile = null;
	private TravelCostType travelCostType = TravelCostType.linkLength;

	private boolean routingWithCandidateDistance = true;
	private int nLinkThreshold = 6;
	private double maxLinkCandidateDistance = 90;
	private double candiateDistanceMulitplier = 1.6;
	
	private boolean modeSpecificRules = false;
	
	private RoutingAlgorithmType networkRouter = ControllerConfigGroup.RoutingAlgorithmType.SpeedyALT;

	public PublicTransitMappingConfigGroup() {
		super(GROUP_NAME);
	}

	/**
	 * @return a new default public transit mapping config
	 */
	public static PublicTransitMappingConfigGroup createDefaultConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");

		TransportModeParameterSet tmaBus = new TransportModeParameterSet("bus");
		tmaBus.setNetworkModesStr("car,bus");
		TransportModeParameterSet tmaRail = new TransportModeParameterSet("rail");
		tmaRail.setNetworkModesStr("rail,light_rail");
		config.addParameterSet(tmaBus);
		config.addParameterSet(tmaRail);

		return config;
	}

	/**
	 * Loads a Public Transit Mapper Config File<p/>
	 *
	 * @param configFile the PublicTransitMapping config file (xml)
	 */
	public static PublicTransitMappingConfigGroup loadConfig(String configFile) {
		Config configAll = ConfigUtils.loadConfig(configFile, new PublicTransitMappingConfigGroup());
		return ConfigUtils.addOrGetModule(configAll, PublicTransitMappingConfigGroup.GROUP_NAME, PublicTransitMappingConfigGroup.class);
	}

	public void writeToFile(String filename) {
		Config matsimConfig = ConfigUtils.createConfig();
		matsimConfig.addModule(this);
		Set<String> toRemove = matsimConfig.getModules().keySet().stream().filter(module -> !module.equals(PublicTransitMappingConfigGroup.GROUP_NAME)).collect(Collectors.toSet());
		toRemove.forEach(matsimConfig::removeModule);
		new ConfigWriter(matsimConfig).write(filename);
	}


	@Override
	public final Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(MODES_TO_KEEP_ON_CLEAN_UP,
				"All links that do not have a transit route on them are removed, except the ones \n" +
				"\t\tlisted in this set (typically only car). Separated by comma.");
		map.put(TRAVEL_COST_TYPE,
				"Defines which link attribute should be used for routing. Possible values \"" + TravelCostType.linkLength + "\" (default) \n" +
				"\t\tand \"" + travelTime + "\".");
		map.put(SCHEDULE_FREESPEED_MODES,
				"After the schedule has been mapped, the free speed of links can be set according to the necessary travel \n" +
				"\t\ttimes given by the transit schedule. The freespeed of a link is set to the minimal value needed by all \n" +
				"\t\ttransit routes passing using it. This is recommended for \"" + PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE + "\", additional \n" +
				"\t\tmodes (especially \"rail\", if used) can be added, separated by commas.");
		map.put(MAX_TRAVEL_COST_FACTOR,
				"If all paths between two stops have a [travelCost] > [" + MAX_TRAVEL_COST_FACTOR + "] * [minTravelCost], \n" +
				"\t\tan artificial link is created. If " + TRAVEL_COST_TYPE + " is " + travelTime + ", minTravelCost is the travel time\n" +
				"\t\tbetween stops from the schedule. If " + TRAVEL_COST_TYPE + " is \n" +
				"\t\t" + TravelCostType.linkLength + " minTravel cost is the beeline distance.");
		map.put(NUM_OF_THREADS,
				"Defines the number of numOfThreads that should be used for pseudoRouting. Default: 2.");
		map.put(INPUT_NETWORK_FILE, "Path to the input network file. Not needed if PTMapper is called within another class.");
		map.put(INPUT_SCHEDULE_FILE, "Path to the input schedule file. Not needed if PTMapper is called within another class.");
		map.put(OUTPUT_NETWORK_FILE, "Path to the output network file. Not needed if PTMapper is used within another class.");
		map.put(OUTPUT_STREET_NETWORK_FILE, "Path to the output car only network file. The input multimodal map is filtered. \n" +
				"\t\tNot needed if PTMapper is used within another class.");
		map.put(OUTPUT_SCHEDULE_FILE, "Path to the output schedule file. Not needed if PTMapper is used within another class.");
		map.put(REMOVE_NOT_USED_STOP_FACILITIES,
				"If true, stop facilities that are not used by any transit route are removed from the schedule. Default: true");
		map.put(ROUTING_WITH_CANDIDATE_DISTANCE,
				"The travel cost of a link candidate can be increased according to its distance to the\n" +
				"\t\tstop facility x2. This tends to give more accurate results. If "+ TRAVEL_COST_TYPE +" is "+ travelTime +", freespeed on \n" +
				"\t\tthe link is applied to the beeline distance.");

		// link candidates
		map.put(CANDIDATE_DISTANCE_MULTIPLIER,
				"After " + N_LINK_THRESHOLD + " link candidates have been found, additional link \n" +
				"\t\tcandidates within [" + CANDIDATE_DISTANCE_MULTIPLIER + "] * [distance to the Nth link] are added to the set.\n" +
				"\t\tMust be >= 1.");
		map.put(N_LINK_THRESHOLD,
				"Number of link candidates considered for all stops, depends on accuracy of stops and desired \n" +
				"\t\tperformance. Somewhere between 4 and 10 seems reasonable for bus stops, depending on the\n" +
				"\t\taccuracy of the stop facility coordinates and performance desires. Default: " + nLinkThreshold);
		map.put(MAX_LINK_CANDIDATE_DISTANCE,
				"The maximal distance [meter] a link candidate is allowed to have from the stop facility.\n" +
				"\t\tNo link candidates beyond this distance are added.");
		map.put(NETWORK_ROUTER,
				"The router that should be used. Possible options are: [SpeedyALT, AStarLandmarks]");
		map.put(USE_MODE_SPECIFIC_RULES, "Instead of using general number of links and maximum search distance rule, use the schedule mode specific rules "
				+ "to be defined within the parameter sets. For those that no information is provided the general values will be used. Options: [false, true]. Default: false.");
		map.put(THREAD_CHUNK_SIZE, "The size of the chunk that is sent to the pt mapper thread at the time to build"
				+ "facilities and links. Default: 100.");
		return map;
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch(type) {
			case TransportModeParameterSet.GROUP_NAME:
				return new TransportModeParameterSet();
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
	 * Loads the parameter set for TransportModeAssignment for easier access
	 */
	private void loadParameterSets() {
		for(ConfigGroup e : this.getParameterSets(TransportModeParameterSet.GROUP_NAME)) {
			TransportModeParameterSet tmps = (TransportModeParameterSet) e;
			transportModeAssignment.put(tmps.getScheduleMode(), tmps.getNetworkModes());
			parameterSetsForMode.put(tmps.getScheduleMode(), tmps);
		}
	}

	/**
	 * References transportModes from the schedule (key) and the
	 * allowed network mode of a link from the network (value). <p/>
	 * <p/>
	 * Schedule transport mode should be in gtfs categories:
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
	public Map<String, Set<String>> getTransportModeAssignment() {
		return transportModeAssignment;
	}

	public void setTransportModeAssignment(Map<String, Set<String>> transportModeAssignment) {
		this.transportModeAssignment = transportModeAssignment;
	}
	
	public TransportModeParameterSet getParameterSetForMode(String scheduleMode) {
		return this.parameterSetsForMode.get(scheduleMode);
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
	private void setModesToKeepOnCleanUpStr(String modesToKeepOnCleanUp) {
		if(modesToKeepOnCleanUp == null) {
			this.modesToKeepOnCleanUp = null;
			return;
		}
		this.modesToKeepOnCleanUp = CollectionUtils.stringToSet(modesToKeepOnCleanUp);
	}

	@StringGetter(MODES_TO_KEEP_ON_CLEAN_UP)
	private String getModesToKeepOnCleanUpString() {
		if(modesToKeepOnCleanUp == null) {
			return "";
		} else {
			return CollectionUtils.setToString(modesToKeepOnCleanUp);
		}
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
		if(this.scheduleFreespeedModes == null) {
			return "";
		} else {
			return CollectionUtils.setToString(this.scheduleFreespeedModes);
		}
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
	@StringGetter(INPUT_NETWORK_FILE)
	public String getNetworkFileStr() {
		return this.inputNetworkFile == null ? "" : this.inputNetworkFile;
	}

	public String getInputNetworkFile() {
		return this.inputNetworkFile;
	}

	@StringSetter(INPUT_NETWORK_FILE)
	public void setInputNetworkFile(String inputNetworkFile) {
		this.inputNetworkFile = inputNetworkFile.equals("") ? null : inputNetworkFile;
	}

	@StringGetter(INPUT_SCHEDULE_FILE)
	public String getScheduleFileStr() {
		return this.inputScheduleFile == null ? "" : this.inputScheduleFile;
	}

	public String getInputScheduleFile() {
		return this.inputScheduleFile;
	}

	@StringSetter(INPUT_SCHEDULE_FILE)
	public void setInputScheduleFile(String inputScheduleFile) {
		this.inputScheduleFile = inputScheduleFile.equals("") ? null : inputScheduleFile;
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

	@StringGetter(ROUTING_WITH_CANDIDATE_DISTANCE)
	public boolean getRoutingWithCandidateDistance() {
		return routingWithCandidateDistance;
	}


	@StringSetter(ROUTING_WITH_CANDIDATE_DISTANCE)
	public void setRoutingWithCandidateDistance(boolean v) {
		this.routingWithCandidateDistance = v;
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
	
	@StringGetter(NETWORK_ROUTER)
	public RoutingAlgorithmType getNetworkRouter() {
		return networkRouter;
	}

	@StringSetter(NETWORK_ROUTER)
	public void setNetworkRouter(RoutingAlgorithmType networkRouter) {
		this.networkRouter = networkRouter;
	}
	
	@StringGetter(USE_MODE_SPECIFIC_RULES)
	public boolean getMdeSpecificRules() {
		return this.modeSpecificRules;
	}

	@StringSetter(USE_MODE_SPECIFIC_RULES)
	public void setModeSpecificRules(String modeSpecificRules) {
		this.modeSpecificRules = Boolean.parseBoolean(modeSpecificRules);
	}

	@StringGetter(THREAD_CHUNK_SIZE)
	public int getChunkSize() {
		return this.chunkSize;
	}

	@StringSetter(THREAD_CHUNK_SIZE)
	public void setModeSpecificRules(int chunkSize) {
		this.chunkSize = chunkSize;
	}
	
}
