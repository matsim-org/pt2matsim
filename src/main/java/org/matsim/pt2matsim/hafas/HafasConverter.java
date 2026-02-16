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

package org.matsim.pt2matsim.hafas;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.ChainedDeparture;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.transitSchedule.ChainedDepartureImpl;
import org.matsim.pt2matsim.hafas.lib.Durchbindung;
import org.matsim.pt2matsim.hafas.filter.BoundingBoxStopFilter;
import org.matsim.pt2matsim.hafas.filter.HafasFilter;
import org.matsim.pt2matsim.hafas.filter.OperationDayFilter;
import org.matsim.pt2matsim.hafas.filter.StopsFilter;
import org.matsim.pt2matsim.hafas.lib.DurchbiReader;
import org.matsim.pt2matsim.hafas.lib.FPLANReader;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;
import org.matsim.pt2matsim.hafas.lib.MinimalTransferTimesReader;
import org.matsim.pt2matsim.hafas.lib.OperatorReader;
import org.matsim.pt2matsim.hafas.lib.StopReader;
import org.matsim.pt2matsim.tools.VehicleTypeDefaults;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
 * Converts hafas files to a matsim transit schedule.
 *
 * @author polettif
 */
public final class HafasConverter {

	static Logger log = LogManager.getLogger(HafasConverter.class);

    private HafasConverter() {
    }

    public static void run(String hafasFolder, TransitSchedule schedule, CoordinateTransformation transformation, Vehicles vehicles) throws IOException {
		run(hafasFolder, schedule, transformation, vehicles, new ArrayList<>(), StandardCharsets.UTF_8, false);
	}

	public static void run(String hafasFolder, TransitSchedule schedule, CoordinateTransformation transformation, Vehicles vehicles, List<HafasFilter> filters, Charset encodingCharset,
		boolean keepStopsInFilter) throws IOException {
		run(hafasFolder, schedule, transformation, vehicles, filters, encodingCharset, keepStopsInFilter, 0.0);
	}

	public static void run(String hafasFolder, TransitSchedule schedule, CoordinateTransformation transformation, Vehicles vehicles, List<HafasFilter> filters, Charset encodingCharset,
		boolean keepStopsInFilter, double defaultMinTransferTime) throws IOException {
		if(!hafasFolder.endsWith("/")) hafasFolder += "/";

		log.info("Creating the schedule based on HAFAS...");

		// 1. Read and create stop facilities
		log.info("  Read transit stops...");
		StopReader.run(schedule, transformation, hafasFolder + "BFKOORD_WGS", encodingCharset);
		log.info("  Read transit stops... done.");

		// 1.a Read minimal transfer times
		log.info("  Read minimal transfer times...");
		MinimalTransferTimesReader.run(schedule, hafasFolder, "UMSTEIGB","METABHF", encodingCharset, defaultMinTransferTime);
		log.info("  Read minimal transfer times... done.");

		// 2. Read all operators from BETRIEB_DE
		log.info("  Read operators...");
		Map<String, String> operators = OperatorReader.readOperators(hafasFolder + "BETRIEB_DE", encodingCharset);
		log.info("  Read operators... done.");

		// 4. Create all lines from HAFAS-Schedule
		log.info("  Read transit lines...");
		// set schedule so fplanRoutes have stopfacilities available
		FPLANRoute.setSchedule(schedule);
		List<FPLANRoute> routes = FPLANReader.parseFPLAN(operators, hafasFolder + "FPLAN", filters, encodingCharset);
		log.info("  Read transit lines... done.");

		// 5. Read durchbindungen (through-services) from DURCHBI file
		log.info("  Read durchbindungen...");
		List<Durchbindung> durchbindungen = DurchbiReader.readDurchbindungen(hafasFolder + "DURCHBI", encodingCharset);
		applyDurchbindungen(routes, durchbindungen);
		log.info("  Read durchbindungen... done.");

		log.info("  Creating transit routes...");
		OperationDayFilter operationDayFilter = (OperationDayFilter) filters.stream().filter(f -> f instanceof OperationDayFilter).findAny().orElse(null);
		Set<Integer> bitfeldNummern = operationDayFilter == null ? new HashSet<>() : operationDayFilter.getBitfeldNummern();
		createTransitRoutesFromFPLAN(routes, schedule, vehicles, bitfeldNummern);
		log.info("  Creating transit routes... done.");

		// 6. Clean schedule
		Set<Id<TransitStopFacility>> stopsToKeep = new HashSet<>();
		if (keepStopsInFilter) {
			stopsToKeep = getStopsFromFilters(schedule, filters);
		}
		ScheduleCleaner.removeNotUsedStopFacilities(schedule, stopsToKeep);
		ScheduleCleaner.removeNotUsedMinimalTransferTimes(schedule);
		ScheduleCleaner.combineIdenticalTransitRoutes(schedule);
		ScheduleCleaner.cleanDepartures(schedule);
		ScheduleCleaner.cleanVehicles(schedule, vehicles);

		log.info("Creating the schedule based on HAFAS... done.");
	}

	private static Set<Id<TransitStopFacility>> getStopsFromFilters(TransitSchedule schedule, List<HafasFilter> filters) {
		Set<Id<TransitStopFacility>> stopsToKeep = new HashSet<>();
		for (HafasFilter filter : filters) {
			for (TransitStopFacility stopFacility : schedule.getFacilities().values()) {
				if (filter instanceof BoundingBoxStopFilter) {
					if(((BoundingBoxStopFilter) filter).stopInBoundingBox(stopFacility)) {
						stopsToKeep.add(stopFacility.getId());
					}
				} else if (filter instanceof StopsFilter) {
					if(((StopsFilter) filter).getStopIds().contains(stopFacility.getId().toString())) {
						stopsToKeep.add(stopFacility.getId());
					}
				}
			}
		}
		return stopsToKeep;
	}

	/**
	 * Applies durchbindungen (through-services) to FPLANRoutes.
	 * Stores complete DURCHBI records on matching source routes.
	 *
	 * @param routes List of FPLANRoute objects
	 * @param durchbindungen List of DURCHBI records
	 */
	private static void applyDurchbindungen(List<FPLANRoute> routes, List<Durchbindung> durchbindungen) {
		if (durchbindungen.isEmpty()) {
			return;
		}

		Map<String, List<FPLANRoute>> tripAdminToRoutes = new HashMap<>();
		for (FPLANRoute route : routes) {
			tripAdminToRoutes.computeIfAbsent(createTripAdminKey(route.getFahrtNummer(), route.getOperatorCode()), key -> new ArrayList<>()).add(route);
		}

		int appliedCount = 0;
		for (Durchbindung durchbindung : durchbindungen) {
			List<FPLANRoute> candidates = tripAdminToRoutes.get(createTripAdminKey(durchbindung.firstTripNumber(), durchbindung.firstAdministration()));
			if (candidates == null || candidates.isEmpty()) {
				log.debug("Source trip {} / {} not found for durchbindung to {} / {}", durchbindung.firstTripNumber(), durchbindung.firstAdministration(),
					durchbindung.secondTripNumber(), durchbindung.secondAdministration());
				continue;
			}

			FPLANRoute sourceRoute = selectBestMatchingSourceRoute(candidates, durchbindung);
			if (sourceRoute == null) {
				log.debug("No suitable source route found for durchbindung {} -> {} with bitfeld {}", durchbindung.firstTripNumber(),
					durchbindung.secondTripNumber(), durchbindung.operationDayBitfeldNumber());
				continue;
			}

			sourceRoute.addDurchbindung(durchbindung);
			appliedCount++;
		}

		log.info("Applied " + appliedCount + " durchbindungen to routes");
	}

	private record CreatedTransitRoute(FPLANRoute fplanRoute, Id<TransitLine> lineId, Id<TransitRoute> routeId, List<Departure> departures) {
	}

	private static void createTransitRoutesFromFPLAN(List<FPLANRoute> routes, TransitSchedule schedule, Vehicles vehicles, Set<Integer> bitfeldNummern) {
		TransitScheduleFactory scheduleFactory = schedule.getFactory();
		VehiclesFactory vehicleFactory = vehicles.getFactory();
		Map<Id<TransitLine>, Integer> routeNrs = new HashMap<>();
		Map<FPLANRoute, CreatedTransitRoute> createdRoutesByFplanRoute = new HashMap<>();
		Map<String, List<CreatedTransitRoute>> createdRoutesByTripAdmin = new HashMap<>();

		Counter lineCounter = new Counter(" TransitLine # ");

		for(FPLANRoute fplanRoute : routes) {
			Id<VehicleType> vehicleTypeId = fplanRoute.getVehicleTypeId();

			VehicleTypeDefaults.Type defaultVehicleType = VehicleTypeDefaults.Type.OTHER;

			try {
				defaultVehicleType = VehicleTypeDefaults.Type.valueOf(vehicleTypeId.toString());
			} catch (IllegalArgumentException e) {
				log.warn("Vehicle category '" + vehicleTypeId.toString() + "' is unknown. Falling back to generic OTHER and adding to schedule.");
			}

			// get wheter the route using this vehicle type should be added & set transport mode
			if(defaultVehicleType.addToSchedule) {
				String transportMode = defaultVehicleType.transportMode.name;

				Id<TransitLine> lineId = createLineId(fplanRoute);

				// create or get TransitLine
				TransitLine transitLine;
				if(!schedule.getTransitLines().containsKey(lineId)) {
					transitLine = scheduleFactory.createTransitLine(lineId);
					transitLine.getAttributes().putAttribute("operator", String.valueOf(fplanRoute.getOperator()));
					transitLine.getAttributes().putAttribute("operatorCode", String.valueOf(fplanRoute.getOperatorCode()));
					schedule.addTransitLine(transitLine);
					lineCounter.incCounter();
				} else {
					transitLine = schedule.getTransitLines().get(lineId);
				}

				// create vehicle type if needed
				VehicleType vehicleType = vehicles.getVehicleTypes().get(vehicleTypeId);
				if(vehicleType == null) {
					vehicleType = vehicleFactory.createVehicleType(Id.create(vehicleTypeId.toString(), VehicleType.class));

					// using default values for vehicle type
					vehicleType.setLength(defaultVehicleType.length);
					vehicleType.setWidth(defaultVehicleType.width);
					VehicleUtils.setAccessTime(vehicleType, defaultVehicleType.accessTime);
					VehicleUtils.setEgressTime(vehicleType, defaultVehicleType.egressTime);
					VehicleUtils.setDoorOperationMode(vehicleType, defaultVehicleType.doorOperation);
					vehicleType.setPcuEquivalents(defaultVehicleType.pcuEquivalents);

					VehicleCapacity vehicleCapacity = vehicleType.getCapacity();
					vehicleCapacity.setSeats(defaultVehicleType.capacitySeats);
					vehicleCapacity.setStandingRoom(defaultVehicleType.capacityStanding);

					vehicles.addVehicleType(vehicleType);
				}

				// get route id
				int routeNr = MapUtils.getInteger(lineId, routeNrs, 0);
				Id<TransitRoute> routeId = createRouteId(fplanRoute, ++routeNr);
				routeNrs.put(lineId, routeNr);

				// create actual TransitRoute
				TransitRoute transitRoute = scheduleFactory.createTransitRoute(routeId, null, fplanRoute.getTransitRouteStops(bitfeldNummern), fplanRoute.getMode());
				List<Departure> departures = fplanRoute.getDepartures();
				for(Departure departure : departures) {
					transitRoute.addDeparture(departure);
					try {
						vehicles.addVehicle(vehicleFactory.createVehicle(departure.getVehicleId(), vehicleType));
					} catch (Exception e) {
						e.printStackTrace();
						fplanRoute.getDepartures();
					}
				}

				transitRoute.setTransportMode(transportMode);

				transitLine.addRoute(transitRoute);

				CreatedTransitRoute createdTransitRoute = new CreatedTransitRoute(fplanRoute, lineId, routeId, departures);
				createdRoutesByFplanRoute.put(fplanRoute, createdTransitRoute);
				createdRoutesByTripAdmin.computeIfAbsent(createTripAdminKey(fplanRoute.getFahrtNummer(), fplanRoute.getOperatorCode()), key -> new ArrayList<>()).add(createdTransitRoute);
			}
		}

		applyChainedDepartures(routes, createdRoutesByFplanRoute, createdRoutesByTripAdmin);
	}

	private static void applyChainedDepartures(List<FPLANRoute> routes, Map<FPLANRoute, CreatedTransitRoute> createdRoutesByFplanRoute,
			Map<String, List<CreatedTransitRoute>> createdRoutesByTripAdmin) {
		int chainCount = 0;
		for (FPLANRoute sourceRoute : routes) {
			if (sourceRoute.getDurchbindungen().isEmpty()) {
				continue;
			}

			CreatedTransitRoute createdSource = createdRoutesByFplanRoute.get(sourceRoute);
			if (createdSource == null) {
				continue;
			}

			Map<Id<Departure>, List<ChainedDeparture>> chainedByDepartureId = new HashMap<>();
			for (Durchbindung durchbindung : sourceRoute.getDurchbindungen()) {
				List<CreatedTransitRoute> targetCandidates = createdRoutesByTripAdmin.get(createTripAdminKey(durchbindung.secondTripNumber(), durchbindung.secondAdministration()));
				if (targetCandidates == null || targetCandidates.isEmpty()) {
					log.debug("No target route found for durchbindung {} / {} -> {} / {}", durchbindung.firstTripNumber(), durchbindung.firstAdministration(),
						durchbindung.secondTripNumber(), durchbindung.secondAdministration());
					continue;
				}

				CreatedTransitRoute targetRoute = selectBestMatchingTargetRoute(targetCandidates, durchbindung.operationDayBitfeldNumber());
				if (targetRoute == null) {
					continue;
				}

				int pairCount = Math.min(createdSource.departures().size(), targetRoute.departures().size());
				for (int i = 0; i < pairCount; i++) {
					Departure sourceDeparture = createdSource.departures().get(i);
					Departure targetDeparture = targetRoute.departures().get(i);
					chainedByDepartureId.computeIfAbsent(sourceDeparture.getId(), key -> new ArrayList<>())
						.add(new ChainedDepartureImpl(targetRoute.lineId(), targetRoute.routeId(), targetDeparture.getId()));
					chainCount++;
				}
			}

			for (Departure departure : createdSource.departures()) {
				List<ChainedDeparture> chainedDepartures = chainedByDepartureId.get(departure.getId());
				if (chainedDepartures != null && !chainedDepartures.isEmpty()) {
					departure.setChainedDepartures(chainedDepartures);
				}
			}
		}
		log.info("Created " + chainCount + " chained departures from DURCHBI relations");
	}

	private static String createTripAdminKey(String tripNumber, String administration) {
		return (tripNumber == null ? "" : tripNumber.trim()) + "|" + (administration == null ? "" : administration.trim());
	}

	private static FPLANRoute selectBestMatchingSourceRoute(List<FPLANRoute> candidates, Durchbindung durchbindung) {
		for (FPLANRoute candidate : candidates) {
			boolean lastStopMatches = durchbindung.lastStopOfFirstTrip() == null || durchbindung.lastStopOfFirstTrip().isEmpty()
				|| durchbindung.lastStopOfFirstTrip().equals(candidate.getLastStopId().trim());
			if (lastStopMatches && routeContainsBitfeld(candidate, durchbindung.operationDayBitfeldNumber())) {
				return candidate;
			}
		}

		for (FPLANRoute candidate : candidates) {
			if (routeContainsBitfeld(candidate, durchbindung.operationDayBitfeldNumber())) {
				return candidate;
			}
		}

		return candidates.get(0);
	}

	private static CreatedTransitRoute selectBestMatchingTargetRoute(List<CreatedTransitRoute> candidates, int bitfeldNumber) {
		for (CreatedTransitRoute candidate : candidates) {
			if (routeContainsBitfeld(candidate.fplanRoute(), bitfeldNumber)) {
				return candidate;
			}
		}
		return candidates.get(0);
	}

	private static boolean routeContainsBitfeld(FPLANRoute route, int bitfeldNumber) {
		if (bitfeldNumber <= 0) {
			return true;
		}
		return route.getLocalBitfeldNumbers().contains(bitfeldNumber);
	}

	private static Id<TransitLine> createLineId(FPLANRoute route) {
		String operator = route.getOperator();
		if(operator == null) {
			operator = route.getOperatorCode();
		}
		if(operator.equals("SBB")) {
			long firstStopId;
			long lastStopId;

			// possible S-Bahn number
			String sBahnNr = (route.getVehicleTypeId().toString().equals("S") && route.getRouteDescription() != null) ? route.getRouteDescription() : "";

			try {
				firstStopId = Long.parseLong(route.getFirstStopId());
				lastStopId = Long.parseLong(route.getLastStopId());
			} catch (NumberFormatException e) {
				firstStopId = 0;
				lastStopId = 1;
			}

			if(firstStopId < lastStopId) {
				return Id.create("SBB_" + route.getVehicleTypeId() + sBahnNr + "_" + route.getFirstStopId() + "-" + route.getLastStopId(), TransitLine.class);
			} else {
				return Id.create("SBB_" + route.getVehicleTypeId() + sBahnNr + "_" + route.getLastStopId() + "-" + route.getFirstStopId(), TransitLine.class);
			}
		}
		else if(route.getRouteDescription() == null) {
			return Id.create(operator, TransitLine.class);
		} else {
			return Id.create(operator + "_line" + route.getRouteDescription(), TransitLine.class);
		}
	}

	private static Id<TransitRoute> createRouteId(FPLANRoute route, int routeNr) {
		return Id.create(route.getFahrtNummer() + "_" + String.format("%03d", routeNr), TransitRoute.class);
	}
}
