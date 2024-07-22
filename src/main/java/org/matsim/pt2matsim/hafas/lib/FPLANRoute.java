/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.hafas.lib;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import java.util.ArrayList;
import java.util.List;

/**
 * A public transport route as read out from HAFAS FPLAN.
 *
 * @author boescpa
 */
public class FPLANRoute {

	private static final Logger log = LogManager.getLogger(FPLANRoute.class);

	private static TransitSchedule schedule;
	private static TransitScheduleFactory scheduleFactory;
	private static int depId = 1;

	private final int initialDelay = 60; // [s] In MATSim a pt route starts with the arrival at the first station. In HAFAS with the departure at the first station. Ergo we have to set a delay which gives some waiting time at the first station while still keeping the schedule.

	public static final String PT = "pt";

	private final String operator;
	private final String operatorCode;
	private final String fahrtNummer;
	private String routeDescription;

	private final int numberOfDepartures;
	private final int cycleTime; // [sec]

	private final List<HafasRouteStop> hafasRouteStops = new ArrayList<>();
	private List<TransitRouteStop> transitRouteStops;

	private Id<VehicleType> vehicleTypeId;
	private boolean isRailReplacementBus;
	private final Map<Integer, Tuple<String, String>> localBitfeldNummern = new TreeMap<>();

	public record HafasRouteStop(String stopFacilityId, int arrivalTime, int departureTime, boolean isBoardingAllowed, boolean isAlightingAllowed) {}

	public static void setSchedule(TransitSchedule schedule) {
		FPLANRoute.schedule = schedule;
		FPLANRoute.scheduleFactory = schedule.getFactory();
	}

	public FPLANRoute(String operator, String operatorCode, String fahrtNummer, int numberOfDepartures, int cycleTime) {
		this.operator = operator;
		this.operatorCode = operatorCode;
		this.fahrtNummer = fahrtNummer;
		this.numberOfDepartures = numberOfDepartures + 1; // Number gives all occurrences of route additionally to first... => +1
		this.cycleTime = cycleTime * 60; // Cycle time is given in minutes in HAFAS -> Have to change it here...
		this.routeDescription = null;
	}

	public void setRouteDescription(String nr) {
		this.routeDescription = nr;
	}


	// First departure time:
	private int firstDepartureTime = -1; //[sec]
	public void setFirstDepartureTime(int time) {
		if(firstDepartureTime < 0) {
			this.firstDepartureTime = time;
		} else {
			log.error("First departure time already set");
		}
	}

	public int getFirstDepartureTime() {
		return firstDepartureTime;
	}

	// Used vehicle type, Id and mode:
	private String mode = PT;

	public void setVehicleTypeId(Id<VehicleType> typeId) {
		vehicleTypeId = typeId;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getVehicleId() {
		return vehicleTypeId + "_" + operator;
	}

	/**
	 * @param arrivalTime   Expected as seconds from midnight or zero if not available.
	 * @param departureTime Expected as seconds from midnight or zero if not available.
	 */
	public void addRouteStop(String stopFacilityId, int arrivalTime, int departureTime, boolean isBoardingAllowed, boolean isAlightingAllowed) {
		hafasRouteStops.add(new HafasRouteStop(stopFacilityId, arrivalTime, departureTime, isBoardingAllowed, isAlightingAllowed));
	}

	/**
	 * @param arrivalTime   Expected as seconds from midnight or zero if not available.
	 * @param departureTime Expected as seconds from midnight or zero if not available.
	 */
	public void addRouteStop(String stopFacilityId, int arrivalTime, int departureTime) {
		hafasRouteStops.add(new HafasRouteStop(stopFacilityId, arrivalTime, departureTime, true, true));
	}

	/**
	 * @return A list of all departures of this route.
	 * If firstDepartureTime or usedVehicle are not set before this is called, null is returned.
	 * If vehicleType is not set, the vehicle is not in the list and entry will not be created.
	 */
	public List<Departure> getDepartures() {
		if(firstDepartureTime < 0 || getVehicleId() == null) {
			log.error("getDepartures before first departureTime and usedVehicleId set.");
			return null;
		}
		if(vehicleTypeId == null) {
			//log.warn("VehicleType not defined in vehicles list.");
			return null;
		}

		List<Departure> departures = new ArrayList<>();
		for(int i = 0; i < numberOfDepartures; i++) {
			// Departure ID
			Id<Departure> departureId = Id.create(String.format("%05d", depId++), Departure.class);
			// Departure time
			double departureTime = firstDepartureTime + (i * cycleTime) - initialDelay;
			// Departure vehicle
			Id<Vehicle> vehicleId = Id.create(getVehicleId() + "_" + departureId, Vehicle.class);
			// create and add departure
			departures.add(createDeparture(departureId, departureTime, vehicleId));
		}
		return departures;
	}

	/**
	 * @return the id of the first stop
	 */
	public String getFirstStopId() {
		return (String) hafasRouteStops.get(0).stopFacilityId;
	}

	/**
	 * @return the id of the last stop
	 */
	public String getLastStopId() {
		return (String) hafasRouteStops.get(hafasRouteStops.size()-1).stopFacilityId;
	}

	public List<TransitRouteStop> getTransitRouteStops() {
		return getTransitRouteStops(new HashSet<>());
	}

	/**
	 * @return the transit route stops of this route. Static schedule needs to be set.
	 */
	public List<TransitRouteStop> getTransitRouteStops(Set<Integer> bitfeldNummern) {
		if(schedule == null) {
			throw new RuntimeException("Schedule and stopFacilities not yet defined for FPLANRoute!");
		}

		List<Tuple<String, String>> validBitfeldNummern = getValidBitfeldNummern(bitfeldNummern);
		List<String> validStartStopIds = validBitfeldNummern.stream().map(Tuple::getFirst).collect(Collectors.toCollection(ArrayList::new));
		List<String> validEndStopIds = validBitfeldNummern.stream().map(Tuple::getSecond).collect(Collectors.toCollection(ArrayList::new));

		if (this.transitRouteStops == null || !bitfeldNummern.isEmpty()) {
			transitRouteStops = new ArrayList<>();
			firstDepartureTime = -1;
			processStops(validStartStopIds, validEndStopIds);
			assert this.transitRouteStops.size() <= this.hafasRouteStops.size();
			assert getLocalBitfeldNumbers().size() > 1 || this.transitRouteStops.size() == this.hafasRouteStops.size();
		}

		return this.transitRouteStops;
	}

	private List<Tuple<String, String>> getValidBitfeldNummern(Set<Integer> bitfeldNummern) {
		if (bitfeldNummern.isEmpty()) {
			return new ArrayList<>(this.localBitfeldNummern.values());
		}
		return this.localBitfeldNummern.entrySet().stream()
			.filter(entry -> bitfeldNummern.contains(entry.getKey())).map(Map.Entry::getValue).toList();
	}

	private void processStops(List<String> validStartStopIds, List<String> validEndStopIds) {
		boolean stopWithinValidSection = false;
		String lastSectionStopId = null;

		for(HafasRouteStop hafasRouteStop : this.hafasRouteStops) {
			String stopId = hafasRouteStop.stopFacilityId;
			// if section is open, process last stop and close section
			if (stopWithinValidSection && validEndStopIds.contains(stopId)) {
				processStop(hafasRouteStop, stopId);
				stopWithinValidSection = false;
				validEndStopIds.remove(stopId);
				lastSectionStopId = stopId;
			}
			// opens the section
			if (validStartStopIds.contains(stopId)) {
				stopWithinValidSection = true;
				validStartStopIds.remove(stopId);
			}
			// process stops between section's boundaries (avoids re-processing previous section's end stop)
			if (stopWithinValidSection && !stopId.equals(lastSectionStopId)) {
				processStop(hafasRouteStop, stopId);
			}

		}
	}

	private void processStop(HafasRouteStop hafasRouteStop, String stopId) {
		Id<TransitStopFacility> stopFacilityId = Id.create(stopId, TransitStopFacility.class);

		int arrivalTime = hafasRouteStop.arrivalTime;
		int departureTime = hafasRouteStop.departureTime;

		// if no departure has been set yet
		if(this.firstDepartureTime < 0) {
			this.firstDepartureTime = departureTime;
			arrivalTime = -1;  // default for the first arrivalTime
		}

		double arrivalDelay = calculateArrivalDelay(arrivalTime);
		double departureDelay = calculateDepartureDelay(departureTime, arrivalDelay);

		TransitStopFacility stopFacility = schedule.getFacilities().get(stopFacilityId);
		if (stopFacility == null) {
            log.warn("StopFacility {} not defined, not adding stop {}", stopFacilityId, this.fahrtNummer);
		} else {
			TransitRouteStop routeStop = scheduleFactory.createTransitRouteStop(stopFacility, arrivalDelay, departureDelay);
			routeStop.setAllowBoarding(hafasRouteStop.isBoardingAllowed);
			routeStop.setAllowAlighting(hafasRouteStop.isAlightingAllowed);
			routeStop.setAwaitDepartureTime(true); // Only *T-Lines (currently not implemented) would have this as false...
			this.transitRouteStops.add(routeStop);
		}
	}

	private double calculateArrivalDelay(int arrivalTime) {
		if (arrivalTime > 0 && firstDepartureTime > 0) {
			return arrivalTime + initialDelay - firstDepartureTime;
		}
		return 0.0;
	}

	private double calculateDepartureDelay(int departureTime, double arrivalDelay) {
		if (departureTime > 0 && firstDepartureTime > 0) {
			return departureTime + initialDelay - firstDepartureTime;
		} else if (arrivalDelay > 0) {
			return arrivalDelay + initialDelay;
		}
		return 0.0;
	}

	private Departure createDeparture(Id<Departure> departureId, double departureTime, Id<Vehicle> vehicleId) {
		Departure departure = scheduleFactory.createDeparture(Id.create(this.fahrtNummer, Departure.class), departureTime);
		departure.setVehicleId(vehicleId);
		return departure;
	}

	private static boolean isTypeOf(Class<? extends Enum> vehicleGroup, String vehicle) {
		for(Object val : vehicleGroup.getEnumConstants()) {
			if(((Enum) val).name().equals(vehicle)) {
				return true;
			}
		}
		return false;
	}

	public String getRouteDescription() {
		return routeDescription;
	}

	public String getFahrtNummer() {
		return fahrtNummer;
	}

	public String getMode() {
		return mode;
	}

	public String getOperator() {
		return operator;
	}

	public String getOperatorCode() {
		return operatorCode;
	}

	public Id<VehicleType> getVehicleTypeId() {
		return vehicleTypeId;
	}

	public void setIsRailReplacementBus() {
		this.isRailReplacementBus = true;
	}

	public boolean isRailReplacementBus() {
		return isRailReplacementBus;
	}

	public void addLocalBitfeldNr(int localBitfeldnr, String startStopId, String endStopId) {
		this.localBitfeldNummern.put(localBitfeldnr, new Tuple<>(startStopId, endStopId));
	}

	public List<Integer> getLocalBitfeldNumbers() {
		return localBitfeldNummern.keySet().stream().toList();
	}
}
