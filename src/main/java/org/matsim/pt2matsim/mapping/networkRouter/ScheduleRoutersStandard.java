package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.ControllerConfigGroup.RoutingAlgorithmType;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.router.AStarLandmarksFactory;
import org.matsim.core.router.speedy.StaticCHRouterFactory;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates a Router for each transportMode of a schedule.
 * <p>
 * Default ScheduleRouters
 *
 * @author polettif
 */
public class ScheduleRoutersStandard implements ScheduleRouters {

	private static final Logger log = LogManager.getLogger(ScheduleRoutersStandard.class);

	private final PublicTransitMappingConfigGroup.TravelCostType travelCostType;
	private final boolean considerCandidateDist;
	private final Map<String, PathCalculator> pathCalculatorsByMode = new HashMap<>();
	private final Map<String, Network> networksByMode = new HashMap<>();

	/**
	 * Constructor used by {@link Factory} with pre-computed filtered networks and a
	 * shared {@link LeastCostPathCalculatorFactory}. This avoids redundant network
	 * filtering and router preprocessing (ALT landmarks / CH contraction) across
	 * parallel instances.
	 */
	private ScheduleRoutersStandard(TransitSchedule schedule,
			Map<String, Set<String>> transportModeAssignment,
			PublicTransitMappingConfigGroup.TravelCostType costType,
			boolean routingWithCandidateDistance,
			Map<String, Network> sharedFilteredNetworks,
			LeastCostPathCalculatorFactory sharedLcpFactory) {
		this.travelCostType = costType;
		this.considerCandidateDist = routingWithCandidateDistance;
		this.initRouters(schedule, transportModeAssignment, sharedFilteredNetworks, sharedLcpFactory);
	}

	/**
	 * Initialize path calculators for all schedule modes that are both declared
	 * in the config and actually used by at least one transit route.
	 */
	private void initRouters(TransitSchedule schedule,
			Map<String, Set<String>> transportModeAssignment,
			Map<String, Network> sharedFilteredNetworks,
			LeastCostPathCalculatorFactory factory) {
		log.info("Creating network routers for transit routes...");

		Set<String> actualScheduleModes = schedule.getTransitLines().values().stream()
				.flatMap(line -> line.getRoutes().values().stream())
				.map(TransitRoute::getTransportMode)
				.collect(Collectors.toSet());

		for (String scheduleMode : transportModeAssignment.keySet()) {
			if (!actualScheduleModes.contains(scheduleMode)) {
				log.warn("Schedule mode '{}' declared in config but not used by any transit route -- creating empty router.", scheduleMode);
			}

			log.info("New router for schedule mode {}", scheduleMode);

			Network filteredNetwork = sharedFilteredNetworks.get(scheduleMode);
			LocalRouter r = new LocalRouter();
			PathCalculator pathCalculator = new PathCalculator(
					factory.createPathCalculator(filteredNetwork, r, r));

			this.pathCalculatorsByMode.put(scheduleMode, pathCalculator);
			this.networksByMode.put(scheduleMode, filteredNetwork);
		}
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute) {
		return this.calcLeastCostPath(fromLinkCandidate.getLink().getId(), toLinkCandidate.getLink().getId(), transitLine, transitRoute);
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute, double maxCost) {
		Network n = this.networksByMode.get(transitRoute.getTransportMode());
		if (n == null) {
			return null;
		}
		Link fromLink = n.getLinks().get(fromLinkCandidate.getLink().getId());
		Link toLink = n.getLinks().get(toLinkCandidate.getLink().getId());
		if (fromLink == null || toLink == null) {
			return null;
		}
		return this.pathCalculatorsByMode.get(transitRoute.getTransportMode()).calcPath(fromLink, toLink, maxCost);
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(Id<Link> fromLinkId, Id<Link> toLinkId, TransitLine transitLine, TransitRoute transitRoute) {
		Network n = this.networksByMode.get(transitRoute.getTransportMode());
		if (n == null) {
			return null;
		}
		Link fromLink = n.getLinks().get(fromLinkId);
		Link toLink = n.getLinks().get(toLinkId);
		if (fromLink == null || toLink == null) {
			return null;
		}
		return this.pathCalculatorsByMode.get(transitRoute.getTransportMode()).calcPath(fromLink, toLink);
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		double minTC = PTMapperTools.calcMinTravelCost(fromTransitRouteStop, toTransitRouteStop, this.travelCostType);
		if (minTC == 0) {
			minTC = CoordUtils.calcEuclideanDistance(fromTransitRouteStop.getStopFacility().getCoord(), toTransitRouteStop.getStopFacility().getCoord()) / 10;
		}
		return minTC;
	}

	@Override
	public double getLinkCandidateTravelCost(LinkCandidate linkCandidateCurrent) {
		double dist = 0;
		if (this.considerCandidateDist) {
			dist += (this.travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime)
					? linkCandidateCurrent.getStopFacilityDistance() / linkCandidateCurrent.getLink().getFreespeed()
					: linkCandidateCurrent.getStopFacilityDistance());
			dist *= 2;
		}
		return dist + PTMapperTools.calcTravelCost(linkCandidateCurrent.getLink(), this.travelCostType);
	}

	/**
	 * Combined {@link TravelDisutility} and {@link TravelTime} implementation
	 * passed to the path calculator factory.
	 */
	private class LocalRouter implements TravelDisutility, TravelTime {

		@Override
		public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
			return this.getLinkMinimumTravelDisutility(link);
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return PTMapperTools.calcTravelCost(link, travelCostType);
		}

		@Override
		public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
			return link.getLength() / link.getFreespeed();
		}
	}

	/**
	 * Factory for {@link ScheduleRoutersStandard} instances.
	 * <p>
	 * Pre-computes filtered networks and a shared
	 * {@link LeastCostPathCalculatorFactory} on first use, so that subsequent calls
	 * to {@link #createInstance()} only create cheap per-thread router instances
	 * without redundant network filtering, graph building, or ALT landmark / CH
	 * contraction computation.
	 */
	public static class Factory implements ScheduleRoutersFactory {
		private final TransitSchedule schedule;
		private final Network network;
		private final Map<String, Set<String>> transportModeAssignment;
		private final PublicTransitMappingConfigGroup.TravelCostType costType;
		private final boolean routingWithCandidateDistance;
		private final int networkRoutingLandmarks;
		private final RoutingAlgorithmType networkRouter;
		private final int nThreads;

		// Cached infrastructure, lazily initialized by ensureInitialized()
		private Map<String, Network> filteredNetworkCache;
		private LeastCostPathCalculatorFactory lcpFactoryCache;

		public Factory(TransitSchedule schedule, Network network, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType costType, boolean routingWithCandidateDistance,
				RoutingAlgorithmType networkRouter, int nThreads) {
			this.schedule = schedule;
			this.network = network;
			this.transportModeAssignment = transportModeAssignment;
			this.costType = costType;
			this.routingWithCandidateDistance = routingWithCandidateDistance;
			this.networkRoutingLandmarks = new RoutingConfigGroup().getNetworkRoutingLandmarks();
			this.networkRouter = networkRouter;
			this.nThreads = nThreads;
		}

		public Factory(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
			this(schedule, network, config.getTransportModeAssignment(), config.getTravelCostType(),
					config.getRoutingWithCandidateDistance(), config.getNetworkRouter(), config.getNumOfThreads());
		}

		/**
		 * Pre-computes filtered networks (one per unique network mode set) and creates
		 * a single shared {@link LeastCostPathCalculatorFactory}. The factory's
		 * internal caches (keyed by Network identity) ensure that graph building and
		 * routing preprocessing (ALT landmarks / CH contraction) are performed only
		 * once per mode set, regardless of how many instances are created.
		 */
		private synchronized void ensureInitialized() {
			if (this.filteredNetworkCache != null) {
				return;
			}
			log.info("Pre-computing filtered networks and router factory for schedule mode assignment...");

			this.filteredNetworkCache = new HashMap<>();
			Map<Set<String>, Network> byModeSet = new HashMap<>();
			for (Map.Entry<String, Set<String>> entry : this.transportModeAssignment.entrySet()) {
				Network filtered = byModeSet.computeIfAbsent(entry.getValue(),
						modes -> NetworkTools.createFilteredNetworkByLinkMode(this.network, modes));
				this.filteredNetworkCache.put(entry.getKey(), filtered);
			}

			this.lcpFactoryCache = createLcpFactory(this.networkRouter, this.nThreads, this.networkRoutingLandmarks);

			log.info("Pre-computed {} filtered network(s) and shared router factory.", byModeSet.size());
		}

		@Override
		public ScheduleRouters createInstance() {
			this.ensureInitialized();
			return new ScheduleRoutersStandard(this.schedule, this.transportModeAssignment,
					this.costType, this.routingWithCandidateDistance,
					this.filteredNetworkCache, this.lcpFactoryCache);
		}

		/**
		 * Creates a {@link LeastCostPathCalculatorFactory} for the given routing
		 * algorithm type.
		 */
		private static LeastCostPathCalculatorFactory createLcpFactory(RoutingAlgorithmType routerType,
				int nThreads, int networkRoutingLandmarks) {
			if (routerType.equals(RoutingAlgorithmType.SpeedyALT)) {
				return new SpeedyALTFactory();
			} else if (routerType.equals(RoutingAlgorithmType.AStarLandmarks)) {
				return new AStarLandmarksFactory(nThreads, networkRoutingLandmarks);
			} else if (routerType.equals(RoutingAlgorithmType.CHRouter)) {
				GlobalConfigGroup global = new GlobalConfigGroup();
				global.setNumberOfThreads(nThreads);
				return new StaticCHRouterFactory(global);
			}
			throw new IllegalArgumentException("Unsupported routing algorithm: " + routerType);
		}
	}
}