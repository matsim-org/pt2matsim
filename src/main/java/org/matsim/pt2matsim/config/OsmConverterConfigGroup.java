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

import org.matsim.core.api.internal.MatsimParameters;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.pt2matsim.osm.lib.Osm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config group for osm conversion {@link org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter}
 *
 * @author polettif
 */
public class OsmConverterConfigGroup extends ReflectiveConfigGroup {

	// constant values used in converter
	public static final String LINK_ATTRIBUTE_WAY_ID = "osm:way:id";
	public static final String LINK_ATTRIBUTE_WAY_PREFIX = "osm:way:";
	public static final String LINK_ATTRIBUTE_RELATION_ROUTE = "osm:relation:route";
	public static final String LINK_ATTRIBUTE_RELATION_ROUTE_MASTER = "osm:relation:route_master";

	// actual config values
	public static final String GROUP_NAME = "OsmConverter";

	@Parameter
	@Comment("The path to the osm file.")
	public String osmFile;

	@Parameter
	public String outputNetworkFile;

	@Parameter
	@Comment("CSV file containing the full geometry (including start end end node) for each link.\n" + //
			"\t\tThis file can be used for visualization purposes in Simunto Via or GIS software.")
	public String outputDetailedLinkGeometryFile;

	@Parameter
	@Comment("Output coordinate system. EPSG:* codes are supported and recommended.\n" + //
			"\t\tUse 'WGS84' for no transformation (though this may lead to errors with PT mapping).")
	public String outputCoordinateSystem;

	@Parameter
	public double maxLinkLength = 500.0;

	@Parameter
	@Comment("Sets whether the detailed geometry of the roads should be retained in the conversion or not.\n" + //
			"\t\tKeeping the detailed paths results in a much higher number of nodes and links in the resulting MATSim network.\n" + //
			"\t\tNot keeping the detailed paths removes all nodes where only one road passes through, thus only real intersections\n" + //
			"\t\tor branchings are kept as nodes. This reduces the number of nodes and links in the network, but can in some rare\n" + //
			"\t\tcases generate extremely long links (e.g. for motorways with only a few ramps every few kilometers).\n" + //
			"\t\tDefaults to <code>false</code>.")
	public boolean keepPaths = false;

	@Parameter
	@Comment("In case the speed limit allowed does not represent the speed a vehicle can actually realize, \n" + //
			"\t\te.g. by constrains of traffic lights not explicitly modeled, a kind of \"average simulated speed\" can be used.\n" + //
			"\t\tDefaults to false. Set true to scale the speed limit down by the value specified by the wayDefaultParams)")
	public boolean scaleMaxSpeed = false;

	@Parameter
	@Comment("If true: The osm tags for ways and containing relations are saved as link attributes in the network.\n" + //
			"\t\tIncreases filesize. Default: true.")
	public boolean keepTagsAsAttributes = true;

	@Parameter
	@Comment("Keep all ways (highway=* and railway=*) with public transit even if they don't have wayDefaultParams defined")
	public boolean keepWaysWithPublicTransit = true;


	public OsmConverterConfigGroup() {
		super(GROUP_NAME);
	}

	/**
	 * @return A new default OsmConverter config
	 */
	public static OsmConverterConfigGroup createDefaultConfig() {
		Set<String> carSingleton = Collections.singleton("car");
		Set<String> railSingleton = Collections.singleton("rail");

		OsmConverterConfigGroup defaultConfig = new OsmConverterConfigGroup();
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY, 2, 120.0 / 3.6, 1.0, 2000, true, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY_LINK, 1, 80.0 / 3.6, 1.0, 1500, true, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK, 2, 80.0 / 3.6, 1.0, 2000, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK_LINK, 1, 50.0 / 3.6, 1.0, 1500, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY, 1, 80.0 / 3.6, 1.0, 1500, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY_LINK, 1, 60.0 / 3.6, 1.0, 1500, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SECONDARY, 1, 30.0 / 3.6, 1.0, 1000, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SECONDARY_LINK, 1, 30.0 / 3.6, 1.0, 1000, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TERTIARY, 1, 25.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TERTIARY_LINK, 1, 25.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.UNCLASSIFIED, 1, 25.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.RESIDENTIAL, 1, 15.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.LIVING_STREET, 1, 10.0 / 3.6, 1.0, 300, false, carSingleton));

		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.RAILWAY, Osm.Value.RAIL, 1, 160.0 / 3.6, 1.0, 9999, false, railSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.RAILWAY, Osm.Value.TRAM, 1, 40.0 / 3.6, 1.0, 9999, true, railSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.RAILWAY, Osm.Value.LIGHT_RAIL, 1, 80.0 / 3.6, 1.0, 9999, false, railSingleton));

		defaultConfig.addParameterSet(new RoutableSubnetworkParams("car", carSingleton));
		defaultConfig.addParameterSet(new RoutableSubnetworkParams("bus", new HashSet<>(Arrays.asList("car", "bus"))));
		
		return defaultConfig;
	}

	public static OsmConverterConfigGroup loadConfig(String configFile) {
		Config configAll = ConfigUtils.loadConfig(configFile, new OsmConverterConfigGroup());
		return ConfigUtils.addOrGetModule(configAll, OsmConverterConfigGroup.GROUP_NAME, OsmConverterConfigGroup.class);
	}

	public void writeToFile(String filename) {
		Config matsimConfig = ConfigUtils.createConfig();
		matsimConfig.addModule(this);
		Set<String> toRemove = matsimConfig.getModules().keySet().stream().filter(module -> !module.equals(OsmConverterConfigGroup.GROUP_NAME)).collect(Collectors.toSet());
		toRemove.forEach(matsimConfig::removeModule);
		new ConfigWriter(matsimConfig).write(filename);
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch(type) {
			case OsmWayParams.SET_NAME :
				return new OsmWayParams();
			case RoutableSubnetworkParams.SET_NAME:
			    return new RoutableSubnetworkParams();
			default:
				throw new IllegalArgumentException("Unknown parameterset name!");
		}
	}

	/**
	 * Defines link attributes for converting OSM highway paths
	 * into MATSim links.
	 *
	 */
	public static class OsmWayParams extends ReflectiveConfigGroup implements MatsimParameters {

		public static final String SET_NAME = "wayDefaultParams";

		@Parameter
		public String osmKey;

		@Parameter
		public String osmValue;

		@Parameter
		@Comment("number of lanes on that road type")
		public double lanes;

		@Parameter
		@Comment("free speed vehicles can drive on that road type [meters/second]")
		public double freespeed;

		@Parameter
		@Comment("factor the freespeed is scaled")
		public double freespeedFactor;

		@Parameter
		@Comment("capacity per lane [veh/h]")
		public double laneCapacity;

		@Parameter
		@Comment("true to say that this road is a oneway road")
		public boolean oneway;

		@Parameter
		@Comment("defines the allowed transport modes for the link")
		public Set<String> allowedTransportModes;

		/**
		 * Constructors
		 */
		public OsmWayParams() {
			super(SET_NAME);
		}

		public OsmWayParams(String osmKey, String osmValue, double lanes, double freespeed, double freespeedFactor, double laneCapacity, boolean oneway, Set<String> allowedTransportModes) {
			super(SET_NAME);
			this.osmKey = osmKey;
			this.osmValue = osmValue;
			this.lanes = lanes;
			this.freespeed = freespeed;
			this.laneCapacity = laneCapacity;
			this.oneway = oneway;
			this.allowedTransportModes = allowedTransportModes;
			this.freespeedFactor = freespeedFactor;
		}

	}
	
	/**
	 * Defines for which modes the converter should make sure that a consistent network for routing exists.
	 */
	public static class RoutableSubnetworkParams extends ReflectiveConfigGroup implements MatsimParameters {
	    
		public static final String SET_NAME = "routableSubnetwork";
	    
		@Parameter
		@Comment("Network mode, for which a consistent routable network is created")
		public String subnetworkMode;
	    
		@Parameter
		@Comment("The allowed transport modes that are considered for this sub-network")
		public Set<String> allowedTransportModes;
	    
	    public RoutableSubnetworkParams() {
	        super(SET_NAME);
	    }
	    
	    public RoutableSubnetworkParams(String subnetworkMode, Set<String> allowedTransportModes) {
	        super(SET_NAME);
	        
	        this.subnetworkMode = subnetworkMode;
	        this.allowedTransportModes = allowedTransportModes;
		}

	}
}
