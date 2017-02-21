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
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.PositiveTagFilter;

import java.util.Collections;
import java.util.Map;
import java.util.Set;


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

	private static final String OSM_FILE ="osmFile";
	private static final String OUTPUT_NETWORK_FILE ="outputNetworkFile";
	private static final String OUTPUT_COORDINATE_SYSTEM ="outputCoordinateSystem";
	private static final String KEEP_PATHS ="keepPaths";
	private static final String MAX_LINK_LENGTH = "maxLinkLength";
	private static final String SCALE_MAX_SPEED = "scaleMaxSpeed";
	private static final String GUESS_FREE_SPEED = "guessFreeSpeed";
	private static final String KEEP_TAGS_AS_ATTRIBUTES = "keepTagsAsAttributes";

	private String osmFile;
	private String outputNetworkFile;
	private String outputCoordinateSystem;
	private CoordinateTransformation coordinateTransformation = new IdentityTransformation();

	private double maxLinkLength = 500.0;
	private boolean keepPaths = false;
	private boolean scaleMaxSpeed = false;
	private boolean guessFreeSpeed = false;
	private boolean keepTagsAsAttributes = true;

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
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY, 2, 120.0 / 3.6, 1.0, 2000, true, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY_LINK, 1, 80.0 / 3.6, 1.0, 1500, true, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK, 1, 80.0 / 3.6, 1.0, 2000, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK_LINK, 1, 50.0 / 3.6, 1.0, 1500, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY, 1, 80.0 / 3.6, 1.0, 1500, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY_LINK, 1, 60.0 / 3.6, 1.0, 1500, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SECONDARY, 1, 60.0 / 3.6, 1.0, 1000, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TERTIARY, 1, 50.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MINOR, 1, 40.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.UNCLASSIFIED, 1, 50.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.RESIDENTIAL, 1, 30.0 / 3.6, 1.0, 600, false, carSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.LIVING_STREET, 1, 15.0 / 3.6, 1.0, 300, false, carSingleton));
//		defaultConfig.addParameterSet(new OsmWayParams(OsmValue.SERVICE,		Osm.Key.HIGHWAY, 1, 15.0 / 3.6, 1.0, 200, 	false, carSingleton));

		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.RAILWAY, Osm.Value.RAIL, 1, 160.0 / 3.6, 1.0, 9999, false, railSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.RAILWAY, Osm.Value.TRAM, 1, 40.0 / 3.6, 1.0, 9999, true, railSingleton));
		defaultConfig.addParameterSet(new OsmWayParams(Osm.Key.RAILWAY, Osm.Value.LIGHT_RAIL, 1, 80.0 / 3.6, 1.0, 9999, false, railSingleton));

		return defaultConfig;
	}

	@StringGetter(OSM_FILE)
	public String getOsmFile() {
		return osmFile;
	}

	@StringSetter(OSM_FILE)
	public void setOsmFile(String osmFile) {
		this.osmFile = osmFile;
	}

	@StringGetter(KEEP_PATHS)
	public boolean getKeepPaths() {
		return keepPaths;
	}

	@StringSetter(KEEP_PATHS)
	public void setKeepPaths(boolean keepPaths) {
		this.keepPaths = keepPaths;
	}

	@StringGetter(GUESS_FREE_SPEED)
	public boolean getGuessFreeSpeed() {
		return guessFreeSpeed;
	}

	@StringSetter(GUESS_FREE_SPEED)
	public void setGuessFreeSpeed(boolean guessFreeSpeed) {
		this.guessFreeSpeed = guessFreeSpeed;
	}

	@StringGetter(MAX_LINK_LENGTH)
	public double getMaxLinkLength() {
		return maxLinkLength;
	}

	@StringSetter(MAX_LINK_LENGTH)
	public void setMaxLinkLength(double maxLinkLength) {
		this.maxLinkLength = maxLinkLength;
	}

	@StringGetter(SCALE_MAX_SPEED)
	public boolean getScaleMaxSpeed() {
		return scaleMaxSpeed;
	}

	@StringSetter(SCALE_MAX_SPEED)
	public void setScaleMaxSpeed(boolean scaleMaxSpeed) {
		this.scaleMaxSpeed = scaleMaxSpeed;
	}

	@StringGetter(KEEP_TAGS_AS_ATTRIBUTES)
	public boolean getKeepTagsAsAttributes() {
		return keepTagsAsAttributes;
	}

	@StringSetter(KEEP_TAGS_AS_ATTRIBUTES)
	public void setKeepTagsAsAttributes(boolean v) {
		this.keepTagsAsAttributes = v;
	}

	@StringGetter(OUTPUT_NETWORK_FILE)
	public String getOutputNetworkFile() {
		return outputNetworkFile;
	}

	@StringSetter(OUTPUT_NETWORK_FILE)
	public void setOutputNetworkFile(String outputNetworkFile) {
		this.outputNetworkFile = outputNetworkFile;
	}

	@StringGetter(OUTPUT_COORDINATE_SYSTEM)
	public String getOutputCoordinateSystem() {
		return outputCoordinateSystem;
	}

	@StringSetter(OUTPUT_COORDINATE_SYSTEM)
	public void setOutputCoordinateSystem(String outputCoordinateSystem) {
		if(outputCoordinateSystem != null) {
			this.outputCoordinateSystem = outputCoordinateSystem;
			this.coordinateTransformation = TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem);
		}
	}

	@Override
	public final Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(OSM_FILE,
				"The path to the osm file.");
		map.put(KEEP_PATHS,
				"Sets whether the detailed geometry of the roads should be retained in the conversion or not.\n" +
				"\t\tKeeping the detailed paths results in a much higher number of nodes and links in the resulting MATSim network.\n" +
				"\t\tNot keeping the detailed paths removes all nodes where only one road passes through, thus only real intersections\n" +
				"\t\tor branchings are kept as nodes. This reduces the number of nodes and links in the network, but can in some rare\n" +
				"\t\tcases generate extremely long links (e.g. for motorways with only a few ramps every few kilometers).\n" +
				"\t\tDefaults to <code>false</code>.");
		map.put(GUESS_FREE_SPEED,
				"If true: The first two digits of the maxspeed tag are used if it cannot be parsed (e.g. \"50; 80\" or similar).");
		map.put(KEEP_TAGS_AS_ATTRIBUTES,
				"If true: The osm tags for ways and containing relations are saved as link attributes in the network. Increases filesize. Default: true.");
		map.put(SCALE_MAX_SPEED,
				"In case the speed limit allowed does not represent the speed a vehicle can actually realize, e.g. by constrains of\n" +
				"\t\ttraffic lights not explicitly modeled, a kind of \"average simulated speed\" can be used.\n" +
				"\t\tDefaults to false. Set true to scale the speed limit down by the value specified by the wayValues)");
		return map;
	}

	public CoordinateTransformation getCoordinateTransformation() {
		return this.coordinateTransformation;
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		switch(type) {
			case OsmWayParams.SET_NAME :
				return new OsmWayParams();
			default:
				throw new IllegalArgumentException("Unknown parameterset name!");
		}
	}

	public PositiveTagFilter getBasicWayFilter() {
		PositiveTagFilter filter = new PositiveTagFilter();
		for(ConfigGroup e : this.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
			OsmConverterConfigGroup.OsmWayParams w = (OsmConverterConfigGroup.OsmWayParams) e;
			filter.add(Osm.ElementType.WAY, w.getOsmKey(), w.getOsmValue());
		}
		return filter;
	}

	/**
	 * Defines link attributes for converting OSM highway paths
	 * into MATSim links.
	 *
	 */
	public static class OsmWayParams extends ReflectiveConfigGroup implements MatsimParameters {

		public final static String SET_NAME = "wayParams";

		private String osmKey;
		private String osmValue;

		/** number of lanes on that road type **/
		private double lanes;
		/** free speed vehicles can drive on that road type [meters/second] **/
		private double freespeed;
		/** factor the freespeed is scaled **/
		private double freespeedFactor;
		/** capacity per lane [veh/h] **/
		private double laneCapacity;
		/** true to say that this road is a oneway road  **/
		private boolean oneway;
		/** defines the allowed transport modes for the link  **/
		private Set<String> allowedTransportModes;

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
			this.freespeedFactor = freespeedFactor;
			this.laneCapacity = laneCapacity;
			this.oneway = oneway;
			this.allowedTransportModes = allowedTransportModes;
		}

		@StringGetter("osmValue")
		public String getOsmValue() {
			return osmValue;
		}

		@StringSetter("osmValue")
		public void setOsmValue(String osmValue) {
			this.osmValue = osmValue;
		}

		@StringGetter("osmKey")
		public String getOsmKey() {
			return osmKey;
		}

		@StringSetter("osmKey")
		public void setOsmKey(String osmKey) {
			this.osmKey = osmKey;
		}


		@StringGetter("lanes")
		public double getLanes() {
			return lanes;
		}

		@StringSetter("lanes")
		public void setLanes(double lanes) {
			this.lanes = lanes;
		}

		@StringGetter("freespeed")
		public double getFreespeed() {
			return freespeed;
		}

		@StringSetter("freespeed")
		public void setFreespeed(double freespeed) {
			this.freespeed = freespeed;
		}

		@StringGetter("freespeedFactor")
		public double getFreespeedFactor() {
			return freespeedFactor;
		}

		@StringSetter("freespeedFactor")
		public void setFreespeedFactor(double freespeedFactor) {
			this.freespeedFactor = freespeedFactor;
		}

		@StringGetter("laneCapacity")
		public double getLaneCapacity() {
			return laneCapacity;
		}

		@StringSetter("laneCapacity")
		public void setLaneCapacity(double laneCapacity) {
			this.laneCapacity = laneCapacity;
		}

		@StringGetter("oneway")
		public boolean getOneway() {
			return oneway;
		}

		@StringSetter("oneway")
		public void setOneway(boolean oneway) {
			this.oneway = oneway;
		}


		public Set<String> getAllowedTransportModes() {
			return this.allowedTransportModes;
		}

		public void setAllowedTransportModes(Set<String> allowedTransportModes) {
			this.allowedTransportModes = allowedTransportModes;
		}

		@StringGetter("allowedTransportModes")
		private String getAllowedTransportModesString() {
			return CollectionUtils.setToString(allowedTransportModes);
		}

		@StringSetter("allowedTransportModes")
		private void setAllowedTransportModesString(String allowedTransportModesString) {
			this.allowedTransportModes = CollectionUtils.stringToSet(allowedTransportModesString);
		}
	}
}
