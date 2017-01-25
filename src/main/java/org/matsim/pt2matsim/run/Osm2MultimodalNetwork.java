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

package org.matsim.pt2matsim.run;

import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter;

/**
 * Run this class to create a multimodal MATSim network from OSM.
 *
 * @author polettif
 */
public class Osm2MultimodalNetwork {

	/**
	 * Converts an osm file to a MATSim network. The input and output file as well
	 * as conversion parameters are defined in this file. Run {@link CreateDefaultOsmConfig}
	 * to create a default config.
	 *
	 * @param args [0] the config.xml file<br/>
	 */
	public static void main(String[] args) {
		if(args.length == 1) {
			run(args[0]);
		} else {
			throw new IllegalArgumentException("Wrong number of arguments");
		}
	}

	/**
	 * Converts an osm file to a MATSim network. The input and output file as well
	 * as conversion parameters are defined in this file. Run {@link CreateDefaultOsmConfig}
	 * to create a default config.
	 *
	 * @param configFile the config.xml file
	 */
	public static void run(String configFile) {
		new OsmMultimodalNetworkConverter(configFile).run();
	}

	/**
	 * Converts an osm file with default conversion parameters.
	 * @param osmFile the osm file
	 * @param outputNetworkFile the path to the output network file
	 * @param outputCoordinateSystem output coordinate system (no transformation is applied if <tt>null</tt>)
	 */
	public static void run(String osmFile, String outputNetworkFile, String outputCoordinateSystem) {
		OsmConverterConfigGroup configGroup = OsmConverterConfigGroup.createDefaultConfig();
		configGroup.setOsmFile(osmFile);
		configGroup.setOutputNetworkFile(outputNetworkFile);
		configGroup.setOutputCoordinateSystem(outputCoordinateSystem);
		new OsmMultimodalNetworkConverter(configGroup).run();
	}
}
