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

package org.matsim.pt2matsim.tools;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.vehicles.*;

import java.util.*;

import static org.matsim.vehicles.VehicleUtils.createVehiclesContainer;

/**
 * Methods to load and modify transit schedules. Also provides
 * methods to get information from transit routes.
 *
 * @author polettif
 */
public final class ScheduleTools {

	protected static Logger log = Logger.getLogger(ScheduleTools.class);

	private ScheduleTools() {}

	/**
	 * @return the transitSchedule from scheduleFile.
	 */
	public static TransitSchedule readTransitSchedule(String fileName) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(fileName);
		return scenario.getTransitSchedule();
	}

	/**
	 * @return an empty transit schedule.
	 */
	public static TransitSchedule createSchedule() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		return scenario.getTransitSchedule();
	}

	/**
	 * Writes the transit schedule to filePath.
	 */
	public static void writeTransitSchedule(TransitSchedule schedule, String fileName) {
		log.info("Writing transit schedule to file " + fileName);
		new TransitScheduleWriter(schedule).writeFile(fileName);
		log.info("done.");
	}

	public static void mergeSchedules(TransitSchedule baseSchedule, TransitSchedule mergeSchedule) {
		mergeSchedules(baseSchedule, mergeSchedule, 0, 0);
	}

	/**
	 * Merges mergeSchedule with an offset into baseSchedule. baseSchedule is modified. Can be used to generate schedule
	 * that run longer than 24h for simulation purposes.
	 *
	 * @param mergeOffset offset in seconds added to the departures of mergeschedule
	 * @param timeLimit   departures are not added if they are after this timelimit (in seconds), starting from
	 *                    0.0 of the baseschedulel
	 */
	public static void mergeSchedules(TransitSchedule baseSchedule, TransitSchedule mergeSchedule, double mergeOffset, double timeLimit) {
		// merge stops
		for(TransitStopFacility tsf : mergeSchedule.getFacilities().values()) {
			if(!baseSchedule.getFacilities().containsKey(tsf.getId())) {
				baseSchedule.addStopFacility(tsf);
			}
		}

		// merge transit lines
		for(TransitLine mergeTransitLine : mergeSchedule.getTransitLines().values()) {
			TransitLine baseTransitLine = baseSchedule.getTransitLines().get(mergeTransitLine.getId());
			if(baseTransitLine == null) {
				baseSchedule.addTransitLine(mergeTransitLine);
			} else {
				for(TransitRoute mergeTR : mergeTransitLine.getRoutes().values()) {
					TransitRoute baseTR = baseTransitLine.getRoutes().get(mergeTR.getId());
					if(baseTR == null) {
						baseTransitLine.addRoute(mergeTR);
					} else {
						if(transitRouteStopSequenceIsEqual(baseTR, mergeTR)) {
							if(mergeOffset > 0) {
								for(Departure departure : mergeTR.getDepartures().values()) {
									if(departure.getDepartureTime() + mergeOffset < timeLimit) {
										Id<Departure> newDepartureId = Id.create(departure.getId() + "+" + mergeOffset / (3600) + "h", Departure.class);
										Departure newDeparture = baseSchedule.getFactory().createDeparture(newDepartureId, departure.getDepartureTime() + mergeOffset);
										baseTR.addDeparture(newDeparture);

									}
								}
							}
						} else {
							Id<TransitRoute> newTransitRouteId = Id.create(mergeTR.getId() + "_merged", TransitRoute.class);
							TransitRoute newTransitRoute = baseSchedule.getFactory().createTransitRoute(newTransitRouteId, mergeTR.getRoute(), mergeTR.getStops(), mergeTR.getTransportMode());
							baseTransitLine.addRoute(newTransitRoute);
						}
					}
				}
			}
		}
	}

	private static boolean transitRouteStopSequenceIsEqual(TransitRoute transitRoute1, TransitRoute transitRoute2) {
		List<TransitRouteStop> stops1 = transitRoute1.getStops();
		List<TransitRouteStop> stops2 = transitRoute2.getStops();
		if(stops1.size() != transitRoute2.getStops().size()) {
			return false;
		}

		for(int i = 0; i < stops1.size(); i++) {
			TransitRouteStop s1 = stops1.get(i);
			TransitRouteStop s2 = stops2.get(i);
			if(!s1.getStopFacility().getId().equals(s2.getStopFacility().getId()) ||
					!s1.getStopFacility().getCoord().equals(s2.getStopFacility().getCoord()) ||
					s1.getArrivalOffset() != s2.getArrivalOffset() ||
					s1.getDepartureOffset() != s2.getDepartureOffset()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Creates vehicles with default vehicle types depending on the schedule
	 * transport mode. Adds the vehicles to the given vehicle container.
	 */
	public static void createVehicles(TransitSchedule schedule, Vehicles vehicles) {
		log.info("Creating vehicles from schedule...");
		VehiclesFactory vf = vehicles.getFactory();
		Map<String, VehicleType> vehicleTypes = new HashMap<>();

		long vehId = 0;
		for(TransitLine line : schedule.getTransitLines().values()) {
			for(TransitRoute route : line.getRoutes().values()) {
				String transportMode = route.getTransportMode();
				// create vehicle type
				if(!vehicleTypes.containsKey(transportMode)) {
					VehicleType vehicleType = createDefaultVehicleType(transportMode, transportMode);
					vehicles.addVehicleType(vehicleType);
					vehicleTypes.put(transportMode, vehicleType);
				}

				VehicleType vehicleType = vehicleTypes.get(transportMode);
				// create a vehicle for each departure
				for(Departure departure : route.getDepartures().values()) {
					String vehicleId = "veh_" + Long.toString(vehId++) + "_" + route.getTransportMode();
					Vehicle veh = vf.createVehicle(Id.create(vehicleId, Vehicle.class), vehicleType);
					vehicles.addVehicle(veh);
					departure.setVehicleId(veh.getId());
				}
			}
		}
	}

	/**
	 * Creates a vehicle type with id and parameters of defaultVehicleType. Used to create different default
	 * vehicle types for schedules which can later changed manually in vehicles.xml
	 */
	public static VehicleType createDefaultVehicleType(String id, String defaultVehicleType) {
		String defVehType = defaultVehicleType.toUpperCase().replace(" ", "_");
		VehiclesFactory vf = VehicleUtils.createVehiclesContainer().getFactory();
		Id<VehicleType> vTypeId = Id.create(id, VehicleType.class);

		// using default values for vehicle type
		VehicleTypeDefaults.Type defaultValues = VehicleTypeDefaults.Type.OTHER;
		try {
			defaultValues = VehicleTypeDefaults.Type.valueOf(defVehType);
		} catch (IllegalArgumentException e) {
			log.warn("Vehicle category '" + defVehType + "' is unknown. Falling back to generic OTHER and adding to schedule.");
		}

		VehicleType vehicleType = vf.createVehicleType(vTypeId);
		vehicleType.setLength(defaultValues.length);
		vehicleType.setWidth(defaultValues.width);
		vehicleType.setAccessTime(defaultValues.accessTime);
		vehicleType.setEgressTime(defaultValues.egressTime);
		vehicleType.setDoorOperationMode(defaultValues.doorOperation);
		vehicleType.setPcuEquivalents(defaultValues.pcuEquivalents);

		VehicleCapacity capacity = vf.createVehicleCapacity();
		capacity.setSeats(defaultValues.capacitySeats);
		capacity.setStandingRoom(defaultValues.capacityStanding);
		vehicleType.setCapacity(capacity);

		return vehicleType;
	}

	/**
	 * @return the vehicles from a given vehicles file.
	 */
	public static Vehicles readVehicles(String vehiclesFile) {
		Vehicles vehicles = createVehiclesContainer();
		new VehicleReaderV1(vehicles).readFile(vehiclesFile);
		return vehicles;
	}


	/**
	 * Add mode "pt" to any link of the network that is
	 * passed by any transitRoute of the schedule.
	 */
	public static void addPTModeToNetwork(TransitSchedule schedule, Network network) {
		log.info("... Adding mode \"pt\" to all links with public transit");

		Map<Id<Link>, ? extends Link> networkLinks = network.getLinks();
		Set<Id<Link>> transitLinkIds = new HashSet<>();

		for(TransitLine line : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : line.getRoutes().values()) {
				if(transitRoute.getRoute() != null) {
					transitLinkIds.addAll(getTransitRouteLinkIds(transitRoute));
				}
			}
		}

		for(Id<Link> transitLinkId : transitLinkIds) {
			Link transitLink = networkLinks.get(transitLinkId);
			if(!transitLink.getAllowedModes().contains(TransportMode.pt)) {
				Set<String> modes = new HashSet<>(transitLink.getAllowedModes());
				modes.add(TransportMode.pt);
				transitLink.setAllowedModes(modes);
			}
		}
	}

	/**
	 * Generates link sequences (network route) for all transit routes in
	 * the schedule, modifies the schedule. All stopFacilities used by a
	 * route must have a link referenced.
	 *
	 * @param schedule where transitRoutes should be routed
	 * @param network  the network where the routes should be routed
	 * @param routers  schedule routers class defining the Router for each transit route.
	 */
	public static void routeSchedule(TransitSchedule schedule, Network network, ScheduleRouters routers) {
		Counter counterRoute = new Counter("route # ");

		log.info("Routing all routes with referenced links...");

		if(routers == null) {
			log.error("No routers given, routing cannot be completed!");
			return;
		}

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				if(transitRoute.getStops().size() > 0) {
					counterRoute.incCounter();

					List<TransitRouteStop> routeStops = transitRoute.getStops();
					List<Id<Link>> linkIdSequence = new LinkedList<>();
					linkIdSequence.add(routeStops.get(0).getStopFacility().getLinkId());

					// route
					for(int i = 0; i < routeStops.size() - 1; i++) {
						if(routeStops.get(i).getStopFacility().getLinkId() == null) {
							log.warn("stop facility " + routeStops.get(i).getStopFacility().getName() + " (" + routeStops.get(i).getStopFacility().getId() + ") not referenced!");
							linkIdSequence = null;
							break;
						}
						if(routeStops.get(i + 1).getStopFacility().getLinkId() == null) {
							log.warn("stop facility " + routeStops.get(i - 1).getStopFacility().getName() + " (" + routeStops.get(i + 1).getStopFacility().getId() + " not referenced!");
							linkIdSequence = null;
							break;
						}

						Id<Link> currentLinkId = Id.createLinkId(routeStops.get(i).getStopFacility().getLinkId().toString());
						Link currentLink = network.getLinks().get(currentLinkId);
						Link nextLink = network.getLinks().get(routeStops.get(i + 1).getStopFacility().getLinkId());

						LeastCostPathCalculator.Path leastCostPath = routers.calcLeastCostPath(currentLink.getToNode().getId(), nextLink.getFromNode().getId(), transitLine, transitRoute);


						List<Id<Link>> path = null;
						if(leastCostPath != null) {
							path = PTMapperTools.getLinkIdsFromPath(leastCostPath);
						}

						if(path != null)
							linkIdSequence.addAll(path);

						linkIdSequence.add(nextLink.getId());
					} // -for stops

					// add link sequence to schedule
					if(linkIdSequence != null) {
						transitRoute.setRoute(RouteUtils.createNetworkRoute(linkIdSequence, network));
					}
				} else {
					log.warn("Route " + transitRoute.getId() + " on line " + transitLine.getId() + " has no stop sequence");
				}
			} // -route
		} // -line
		log.info("Routing all routes with referenced links... done");
	}

	/**
	 * Adds mode the schedule transport mode to links. Removes all network
	 * modes elsewhere. Adds mode "artificial" to artificial
	 * links. Used for debugging and visualization.
	 */
	public static void assignScheduleModesToLinks(TransitSchedule schedule, Network network) {
		log.info("... Assigning schedule transport mode to network");

		Map<Id<Link>, Set<String>> transitLinkNetworkModes = new HashMap<>();

		for(TransitLine line : schedule.getTransitLines().values()) {
			for(TransitRoute route : line.getRoutes().values()) {
				if(route.getRoute() != null) {
					for(Id<Link> linkId : getTransitRouteLinkIds(route)) {
						MapUtils.getSet(linkId, transitLinkNetworkModes).add(route.getTransportMode());
					}
				}
			}
		}

		for(Link link : network.getLinks().values()) {
			if(transitLinkNetworkModes.containsKey(link.getId())) {
				Set<String> linkModes = transitLinkNetworkModes.get(link.getId());
				linkModes.addAll(link.getAllowedModes());

				Set<String> modes = new HashSet<>(linkModes);

				link.setAllowedModes(modes);
			}
		}
	}

	/**
	 * Transforms a MATSim Transit Schedule file. Overwrites the file.
	 */
	public static void transformScheduleFile(String scheduleFile, String fromCoordinateSystem, String toCoordinateSystem) {
		log.info("... Transformig schedule from " + fromCoordinateSystem + " to " + toCoordinateSystem);

		final CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation(fromCoordinateSystem, toCoordinateSystem);
		final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new TransitScheduleReader(coordinateTransformation, scenario).readFile(scheduleFile);
		TransitSchedule schedule = scenario.getTransitSchedule();
		new TransitScheduleWriter(schedule).writeFile(scheduleFile);
	}

	/**
	 * Adds a loop link at the routeStart
	 */
	public static void addLoopLinkAtRouteStart(TransitSchedule schedule, Network network) {
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				// get current start link
				Id<Link> cuckooLinkId = transitRoute.getRoute().getStartLinkId();
				Link cuckooLink = network.getLinks().get(cuckooLinkId);

				// create new link id list (which excludes the start link
				List<Id<Link>> newLinkIdList = new ArrayList<>();
				newLinkIdList.add(cuckooLinkId);
				newLinkIdList.addAll(transitRoute.getRoute().getLinkIds());
				Id<Link> endLinkId = transitRoute.getRoute().getEndLinkId();

				// generate new startlink id
				String str = PublicTransitMappingStrings.PREFIX_ARTIFICIAL +
						transitRoute.getStops().get(0).getStopFacility().getId() +
						"_" + cuckooLinkId.toString();
				Id<Link> newStartLinkId = Id.createLinkId(str);

				// create new start link if necessary
				if(!network.getLinks().keySet().contains(newStartLinkId)) {
					Link newStartLink = network.getFactory().createLink(newStartLinkId, cuckooLink.getFromNode(), cuckooLink.getFromNode());
					newStartLink.setAllowedModes(new HashSet<>());
					newStartLink.setCapacity(cuckooLink.getCapacity());
					newStartLink.setFreespeed(cuckooLink.getFreespeed());
					newStartLink.setLength(0);
					network.addLink(newStartLink);
				}

				// update allowed modes
				Link newStartLink = network.getLinks().get(newStartLinkId);
				Set<String> allowedModes = new HashSet<>(newStartLink.getAllowedModes());
				allowedModes.add(transitRoute.getTransportMode());
				newStartLink.setAllowedModes(allowedModes);

				// set new route
				transitRoute.getRoute().setLinkIds(newStartLinkId, newLinkIdList, endLinkId);
			}
		}
	}

	/**
	 * @return the list of link ids used by transit routes (first and last
	 * links are included). Returns an empty list if no links are assigned
	 * to the route.
	 */
	public static List<Id<Link>> getTransitRouteLinkIds(TransitRoute transitRoute) {
		List<Id<Link>> list = new ArrayList<>();
		if(transitRoute.getRoute() == null) {
			return list;
		}
		NetworkRoute networkRoute = transitRoute.getRoute();
		list.add(networkRoute.getStartLinkId());
		list.addAll(networkRoute.getLinkIds());
		list.add(networkRoute.getEndLinkId());
		return list;
	}

	public static List<Id<Link>> getSubRouteLinkIds(TransitRoute transitRoute, Id<Link> fromLinkId, Id<Link> toLinkId) {
		NetworkRoute route = transitRoute.getRoute();
		if(fromLinkId == null) {
			fromLinkId = route.getStartLinkId();
		}
		if(toLinkId == null) {
			toLinkId = route.getEndLinkId();
		}
		List<Id<Link>> list = new ArrayList<>();
		NetworkRoute networkRoute = route.getSubRoute(fromLinkId, toLinkId);
		list.add(networkRoute.getStartLinkId());
		list.addAll(networkRoute.getLinkIds());
		list.add(networkRoute.getEndLinkId());
		return list;
	}


	/**
	 * Based on {@link org.matsim.core.population.routes.LinkNetworkRouteImpl#getSubRoute}
	 *
	 * @param transitRoute the transitRoute
	 * @param fromLinkId   first link of the subroute. If <tt>null</tt> the first link of the route is used.
	 * @param toLinkId     last link of the subroute. If <tt>null</tt> the first link of the route is used.
	 * @return the list of link ids used by transit routes (fromLink and toLink
	 * links are included)
	 */
	public static List<Id<Link>> getLoopSubRouteLinkIds(TransitRoute transitRoute, Id<Link> fromLinkId, Id<Link> toLinkId) {
		NetworkRoute route = transitRoute.getRoute();
		if(fromLinkId == null) {
			fromLinkId = route.getStartLinkId();
		}
		if(toLinkId == null) {
			toLinkId = route.getEndLinkId();
		}

		List<Id<Link>> linkIdList = getTransitRouteLinkIds(transitRoute);

		/*
		  the index where the link after fromLinkId can be found in the route:
		  fromIndex==0 --> fromLinkId == startLinkId,
		  fromIndex==1 --> fromLinkId == first link in the route, etc.
		 */
		int fromIndex = -1;
		/*
		  the index where toLinkId can be found in the route
		 */
		int toIndex = -1;

		if(fromLinkId.equals(route.getStartLinkId())) {
			fromIndex = 0;
		} else {
			for(int i = 0, n = linkIdList.size(); (i < n) && (fromIndex < 0); i++) {
				if(fromLinkId.equals(linkIdList.get(i))) {
					fromIndex = i;
				}
			}
			if(fromIndex < 0 && fromLinkId.equals(route.getEndLinkId())) {
				fromIndex = linkIdList.size();
			}
			if(fromIndex < 0) {
				throw new IllegalArgumentException("Cannot create subroute because fromLinkId is not part of the route.");
			}
		}

		if(fromLinkId.equals(toLinkId)) {
			toIndex = fromIndex - 1;
		} else {
			for(int i = fromIndex, n = linkIdList.size(); (i < n) && (toIndex < 0); i++) {
				if(toLinkId.equals(linkIdList.get(i))) {
					toIndex = i;
				}
			}
			if(toIndex < 0 && toLinkId.equals(route.getEndLinkId())) {
				toIndex = linkIdList.size();
			}
			if(toIndex < 0) {
				throw new IllegalArgumentException("Cannot create subroute because toLinkId is not part of the route.");
			}
		}

		return linkIdList.subList(fromIndex, toIndex);
	}

	/**
	 * Writes the vehicles to the output file.
	 */
	public static void writeVehicles(Vehicles vehicles, String filePath) {
		log.info("Writing vehicles to file " + filePath);
		new VehicleWriterV1(vehicles).writeFile(filePath);
	}

	/**
	 * checks if a stop is accessed twice in a stop sequence
	 */
	public static boolean routeHasStopSequenceLoop(TransitRoute transitRoute) {
		Set<String> parentFacilities = new HashSet<>();
		for(TransitRouteStop stop : transitRoute.getStops()) {
			if(!parentFacilities.add(getParentStopFacilityId(stop.getStopFacility().getId().toString()))) {
				return true;
			}
		}
		return false;
	}

	public static Id<TransitStopFacility> createParentStopFacilityId(TransitStopFacility stopFacility) {
		String str = getParentStopFacilityId(stopFacility.getId().toString());
		return Id.create(str, TransitStopFacility.class);
	}

	public static Id<TransitStopFacility> createParentStopFacilityId(String stopFacilityId) {
		String str = getParentStopFacilityId(stopFacilityId);
		return Id.create(str, TransitStopFacility.class);
	}

	public static Id<TransitStopFacility> createChildStopFacilityId(Id<TransitStopFacility> stopFacilityId, Id<Link> linkId) {
		String str = getChildStopFacilityId(stopFacilityId.toString(), linkId.toString());
		return Id.create(str, TransitStopFacility.class);
	}

	public static Id<TransitStopFacility> createChildStopFacilityId(String stopFacilityId, String linkId) {
		String str = getChildStopFacilityId(stopFacilityId, linkId);
		return Id.create(str, TransitStopFacility.class);
	}

	/**
	 * @return the parent id of a stop facility id. This is the part left to the
	 * child stop facility suffix ".link:"
	 */
	public static String getParentStopFacilityId(String stopFacilityId) {
		String[] childStopSplit = stopFacilityId.split(PublicTransitMappingStrings.SUFFIX_CHILD_STOP_FACILITIES_REGEX);
		return childStopSplit[0];
	}

	public static String getChildStopFacilityId(String stopFacilityId, String linkId) {
		return stopFacilityId + PublicTransitMappingStrings.SUFFIX_CHILD_STOP_FACILITIES + linkId;
	}

	/**
	 * Changes the free speed of links based on the necessary travel times
	 * given by the schedule. Rather experimental and only recommended for
	 * artificial and possibly rail links.
	 */
	public static void setFreeSpeedBasedOnSchedule(Network network, TransitSchedule schedule, Set<String> networkModes) {
		Map<Id<Link>, Double> necessaryMinSpeeds = new HashMap<>();

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				List<Id<Link>> linkIds = getTransitRouteLinkIds(transitRoute);
				
				if (linkIds.size() > 0) {
					Iterator<TransitRouteStop> stopsIterator = transitRoute.getStops().iterator();
					List<Link> links = NetworkTools.getLinksFromIds(network, linkIds);
	
					List<Id<Link>> linkIdsUpToCurrentStop = new ArrayList<>();
	
					/*
					Since transit stops are handled at the end of a link, changing the route's first link's
					freespeed (i.e. the first stop link) has no effect. The freespeed of the first link
					is adjusted according to the necessary travel time to the second stop.
					 */
					double lengthUpToCurrentStop = -links.get(0).getLength();
					stopsIterator.next();
					TransitRouteStop stop = stopsIterator.next();
					double departTime = 0;
	
					for(Link link : links) {
						linkIdsUpToCurrentStop.add(link.getId());
						lengthUpToCurrentStop += link.getLength();
	
						if(stop.getStopFacility().getLinkId().equals(link.getId())) {
							double ttSchedule = stop.getArrivalOffset() - departTime;
							double theoreticalMinSpeed = (lengthUpToCurrentStop / ttSchedule) * 1.02;
	
							for(Id<Link> linkId : linkIdsUpToCurrentStop) {
								double setMinSpeed = MapUtils.getDouble(linkId, necessaryMinSpeeds, 0);
								if(theoreticalMinSpeed > setMinSpeed) {
									necessaryMinSpeeds.put(linkId, theoreticalMinSpeed);
								}
							}
	
							// reset
							lengthUpToCurrentStop = 0;
							linkIdsUpToCurrentStop = new ArrayList<>();
							departTime = stop.getDepartureOffset();
							if(stopsIterator.hasNext()) {
								stop = stopsIterator.next();
							}
						}
					}
				}
			}
		}

		for(Link link : network.getLinks().values()) {
			if(MiscUtils.collectionsShareMinOneStringEntry(link.getAllowedModes(), networkModes)) {
				if(necessaryMinSpeeds.containsKey(link.getId())) {
					double necessaryMinSpeed = necessaryMinSpeeds.get(link.getId());
					if(necessaryMinSpeed > link.getFreespeed()) {
						link.setFreespeed(Math.ceil(necessaryMinSpeed));
					}
				}
			}
		}
	}

	/**
	 * @return true if the stop facility ids contain the child stop string ".link:"
	 * which might lead to problems during mapping
	 */
	public static boolean idsContainChildStopString(TransitSchedule schedule) {
		for(TransitStopFacility stopFacility : schedule.getFacilities().values()) {
			if(createParentStopFacilityId(stopFacility.getId().toString()).toString().length() != stopFacility.getId().toString().length()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the shape id for the transit route (current workaround with description)
	 */
	public static void setShapeId(TransitRoute transitRoute, Id<RouteShape> id) {
		transitRoute.setDescription(getDescriptionStrFromShapeId(id));
	}

	public static Id<RouteShape> getShapeId(TransitRoute transitRoute) {
		return getShapeIdFromDescription(transitRoute.getDescription());
	}

	private static String getDescriptionStrFromShapeId(Id<RouteShape> id) {
		return PublicTransitMappingStrings.DESCR_SHAPE_ID_PREFIX + id.toString();
	}

	private static Id<RouteShape> getShapeIdFromDescription(String transitRouteDescription) {
		if(transitRouteDescription == null || !transitRouteDescription.contains(PublicTransitMappingStrings.DESCR_SHAPE_ID_PREFIX)) {
			return null;
		} else {
			String[] shapeIdSplit = transitRouteDescription.split(PublicTransitMappingStrings.DESCR_SHAPE_ID_PREFIX);
			return Id.create(shapeIdSplit[1], RouteShape.class);
		}
	}
}
