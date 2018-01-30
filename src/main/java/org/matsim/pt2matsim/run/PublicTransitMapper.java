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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.Collections;

/**
 * Allows to run an implementation
 * of public transit mapping via config file path.
 *
 * Currently redirects to the only implementation
 * {@link PTMapper}.
 *
 * @author polettif
 */
public final class PublicTransitMapper {

	protected static Logger log = Logger.getLogger(PublicTransitMapper.class);

	private PublicTransitMapper() {
	}

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
			throw new IllegalArgumentException("Public Transit Mapping config file as argument needed");
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
		// Load config, input schedule and input network
		Config configAll = ConfigUtils.loadConfig(configFile, new PublicTransitMappingConfigGroup());
		PublicTransitMappingConfigGroup config = ConfigUtils.addOrGetModule(configAll, PublicTransitMappingConfigGroup.class);
		TransitSchedule schedule = config.getInputScheduleFile() == null ? null : ScheduleTools.readTransitSchedule(config.getInputScheduleFile());
		Network network = config.getInputNetworkFile() == null ? null : NetworkTools.readNetwork(config.getInputNetworkFile());

		// Run PTMapper
		PTMapper.mapScheduleToNetwork(schedule, network, config);
		// or: new PTMapper(schedule, network).run(config);

		// Write the schedule and network to output files (if defined in config)
		if(config.getOutputNetworkFile() != null && config.getOutputScheduleFile() != null) {
			log.info("Writing schedule and network to file...");
			try {
				ScheduleTools.writeTransitSchedule(schedule, config.getOutputScheduleFile());
				NetworkTools.writeNetwork(network, config.getOutputNetworkFile());
			} catch (Exception e) {
				log.error("Cannot write to output directory!");
			}
			if(config.getOutputStreetNetworkFile() != null) {
				NetworkTools.writeNetwork(NetworkTools.createFilteredNetworkByLinkMode(network, Collections.singleton(TransportMode.car)), config.getOutputStreetNetworkFile());
			}
		} else {
			log.info("No output paths defined, schedule and network are not written to files.");
		}
	}
}
