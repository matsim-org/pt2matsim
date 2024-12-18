package org.matsim.pt2matsim.examples;

import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility;
import org.matsim.pt2matsim.run.Gtfs2TransitSchedule;
import org.matsim.pt2matsim.run.Osm2MultimodalNetwork;
import org.matsim.pt2matsim.run.gis.Network2Geojson;
import org.matsim.pt2matsim.run.gis.Schedule2Geojson;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * This is an example workflow using config files. The network and schedule files are placeholders and
 * not part of the GitHub repository
 *
 * @author polettif
 */
public class Workflow {

	public static void main(String[] args) {
		// Convert Network
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.loadConfig("osm2matsimConfig.xml");
		Osm2MultimodalNetwork.run(osmConfig); // or just: Osm2MultimodalNetwork.run("osm2matsimConfig.xml");

		// convert schedule
		String unmappedSchedule = "intermediate/schedule_unmapped.xml.gz";
		Gtfs2TransitSchedule.run("gtfs", "dayWithMostTrips", osmConfig.getOutputCoordinateSystem(), unmappedSchedule, "output/vehicles.xml.gz", "schedule");

		// setup public transit mapper
		PublicTransitMappingConfigGroup mapperConfig = PublicTransitMappingConfigGroup.createDefaultConfig();
		Network network = NetworkTools.readNetwork(osmConfig.getOutputNetworkFile());
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(unmappedSchedule);

		// map schedule to network
		PTMapper.mapScheduleToNetwork(schedule,  network, mapperConfig);

		// write mapping results
		NetworkTools.writeNetwork(network, "output/network.xml.gz");
		ScheduleTools.writeTransitSchedule(schedule, "output/schedule.xml.gz");

		// Write geojson result
		Network2Geojson.run(osmConfig.getOutputCoordinateSystem(), network, "output/network.geojson");
		Schedule2Geojson.run(osmConfig.getOutputCoordinateSystem(), schedule, "output/schedule.geojson");

		// check schedule
		CheckMappedSchedulePlausibility.run("output/schedule.xml.gz", "output/network.xml.gz", osmConfig.getOutputCoordinateSystem(), "output/check/");
	}
}
