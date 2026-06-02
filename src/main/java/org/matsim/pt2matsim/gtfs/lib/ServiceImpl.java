/* *********************************************************************** *
 * project: org.matsim.*
 * Service.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.gtfs.lib;

import java.time.LocalDate;
import java.util.*;

public class ServiceImpl implements Service {

	private final String id;
	private final boolean[] days;
	private final LocalDate startDate;
	private final LocalDate endDate;

	private final SortedSet<LocalDate> additions = new TreeSet<>();
	private final SortedSet<LocalDate> exceptions = new TreeSet<>();
	private final SortedSet<LocalDate> coveredDays = new TreeSet<>();

	private final Map<String, Trip> trips = new HashMap<>();

	public ServiceImpl(String serviceId, boolean[] days, String startDateStr, String endDateStr) {
		this.id = serviceId;
		this.days = days;
		this.startDate = parseDateFormat(startDateStr);
		this.endDate = parseDateFormat(endDateStr);

		LocalDate currentDate = startDate;
		while(currentDate.isBefore(endDate.plusDays(1))) {
			int currentWeekday = currentDate.getDayOfWeek().getValue() - 1;
			if(days[currentWeekday]) {
				coveredDays.add(currentDate);
			}
			currentDate = currentDate.plusDays(1);
		}
	}

	public ServiceImpl(String serviceId) {
		this.id = serviceId;
		this.days = new boolean[]{false, false, false, false, false, false, false};
		this.startDate = null;
		this.endDate = null;
	}

	/**
	 * Adds a new addition date
	 */
	public void addAddition(String addition) {
		LocalDate additionDate = parseDateFormat(addition);
		additions.add(additionDate);
		coveredDays.add(additionDate);
	}

	/**
	 * Adds a new exception date
	 */
	public void addException(String exception) {
		LocalDate exceptionDate = parseDateFormat(exception);
		exceptions.add(exceptionDate);
		coveredDays.remove(exceptionDate);
	}

	public void addTrip(Trip newTrip) {
		trips.put(newTrip.getId(), newTrip);
	}

	/**
	 * parses the date format YYYYMMDD to LocalDate
	 */
	private LocalDate parseDateFormat(String yyyymmdd) {
		if(yyyymmdd.length() != 8) {
			throw new IllegalArgumentException("Invalid date format YYYYMMDD: \"" + yyyymmdd + "\"");
		}
		return LocalDate.of(Integer.parseInt(yyyymmdd.substring(0, 4)), Integer.parseInt(yyyymmdd.substring(4, 6)), Integer.parseInt(yyyymmdd.substring(6, 8)));
	}

	/** required */
	@Override
	public String getId() {
		return id;
	}

	/** required */
	@Override
	public boolean[] getDays() {
		return days;
	}

	/** required */
	@Override
	public LocalDate getStartDate() {
		return startDate;
	}

	/** required */
	@Override
	public LocalDate getEndDate() {
		return endDate;
	}

	/** required */
	@Override
	public SortedSet<LocalDate> getAdditions() {
		return Collections.unmodifiableSortedSet(additions);
	}

	/** required */
	@Override
	public SortedSet<LocalDate> getExceptions() {
		return Collections.unmodifiableSortedSet(exceptions);
	}

	@Override
	public Map<String, Trip> getTrips() {
		return Collections.unmodifiableMap(trips);
	}

	/**
	 * @return a set of dates on which this service runs
	 */
	@Override
	public SortedSet<LocalDate> getCoveredDays() {
		return Collections.unmodifiableSortedSet(coveredDays);
	}

	@Override
	public boolean runsOnDate(LocalDate checkDate) {
		if(checkDate == null) {
			return true;
		}
		// check if checkDate is an addition
		if(this.getAdditions().contains(checkDate)) {
			return true;
		}
		if(this.getEndDate() == null || this.getStartDate() == null || checkDate.isAfter(this.getEndDate()) || checkDate.isBefore(this.getStartDate())) {
			return false;
		}
		// check if the checkDate is not an exception of the service
		if(this.getExceptions().contains(checkDate)) {
			return false;
		}
		// test if checkdate's weekday is covered (0 = monday)
		int weekday = checkDate.getDayOfWeek().getValue() - 1;
		return this.getDays()[weekday];
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		ServiceImpl service = (ServiceImpl) o;

		if(!id.equals(service.id)) return false;
		if(!Arrays.equals(days, service.days)) return false;
		if(!startDate.equals(service.startDate)) return false;
		if(!endDate.equals(service.endDate)) return false;
		if(!additions.equals(service.additions)) return false;
		if(!exceptions.equals(service.exceptions)) return false;
		if(!coveredDays.equals(service.coveredDays)) return false;
		return trips.equals(service.trips);
	}

	@Override
	public int hashCode() {
		int result = id.hashCode();
		result = 31 * result + Arrays.hashCode(days);
		result = 31 * result + startDate.hashCode();
		result = 31 * result + endDate.hashCode();
		result = 31 * result + additions.hashCode();
		result = 31 * result + exceptions.hashCode();
		result = 31 * result + coveredDays.hashCode();
		result = 31 * result + trips.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "[service:" + id + ", startDate:" + startDate + ", endDate:" + endDate + "]";
	}
}
