package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.*;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.MapperModule;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Link candidates are weighted according to their priority, travel costs are reduced for
 * high priority candidates.
 *
 * @author polettif
 */
public class ScheduleRoutersWeightedCandidates extends ScheduleRoutersTransportMode {

	protected static Logger log = Logger.getLogger(ScheduleRoutersWeightedCandidates.class);
	private final PublicTransitMappingConfigGroup config;

	public ScheduleRoutersWeightedCandidates(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network) {
		super(config, schedule, network);
		this.config = config;
	}

	@Override
	public double getLinkCandidateTravelCost(TransitLine transitLine, TransitRoute transitRoute, LinkCandidate linkCandidateCurrent) {
		return (1 - linkCandidateCurrent.getPriority()) * PTMapperTools.calcTravelCost(linkCandidateCurrent.getLink(), config.getTravelCostType());
	}

}
