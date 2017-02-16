/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
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
 * *********************************************************************** *
 */

package org.matsim.pt2matsim.run;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.PTMapperImpl;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Allows to run an implementation
 * of public transit mapping via config file path.
 *
 * Currently redirects to the only implementation
 * {@link PTMapperImpl}.
 *
 * @author polettif
 */
public final class PublicTransitMapper {

	/**
	 * Routes the unmapped MATSim Transit Schedule to the network using the file
	 * paths specified in the config. Writes the resulting schedule and network to xml files.<p/>
	 *
	 * @see CreateDefaultPTMapperConfig
	 *
	 * @param args <br/>[0] PublicTransitMapping config file<br/>
	 */
	public static void main(String[] args) {
		if(args.length == 1) {
			run(args[0]);
		} else {
			throw new IllegalArgumentException("Incorrect number of arguments: [0] Public Transit Mapping config file");
		}
	}

	/**
	 * Routes the unmapped MATSim Transit Schedule to the network using the file
	 * paths specified in the config. Writes the resulting schedule and network to xml files.<p/>
	 *
	 * @see CreateDefaultPTMapperConfig
	 *
	 * @param configFile the PublicTransitMapping config file
	 */
	public static void run(String configFile) {
		Config configAll = ConfigUtils.loadConfig(configFile, new PublicTransitMappingConfigGroup());
		PublicTransitMappingConfigGroup config = ConfigUtils.addOrGetModule(configAll, PublicTransitMappingConfigGroup.class);
		TransitSchedule schedule = config.getScheduleFile() == null ? null : ScheduleTools.readTransitSchedule(config.getScheduleFile());
		Network network = config.getNetworkFile() == null ? null : NetworkTools.readNetwork(config.getNetworkFile());

		new PTMapperImpl(config, schedule, network).run();
	}

	private PublicTransitMapper() {}
}
