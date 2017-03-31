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

	private final Collection<LocalDate> additions = new ArrayList<>();
	private final Collection<LocalDate> exceptions = new ArrayList<>();
	private final Set<LocalDate> coveredDays = new HashSet<>();

	private final Map<String, Trip> trips = new HashMap<>();

	public ServiceImpl(String serviceId, boolean[] days, String startDateStr, String endDateStr) {
		this.id = serviceId;
		this.days = days;
		this.startDate = parseDateFormat(startDateStr);
		this.endDate = parseDateFormat(endDateStr);

		LocalDate currentDate = startDate;
		while(currentDate.isBefore(endDate)) {
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

	/**
	 * @return a set of dates on which this service runs
	 */
	@Override
	public Collection<LocalDate> getCoveredDays() {
		return Collections.unmodifiableCollection(coveredDays);
	}

	/**
	 * parses the date format YYYYMMDD to LocalDate
	 */
	private LocalDate parseDateFormat(String yyyymmdd) {
		return LocalDate.of(Integer.parseInt(yyyymmdd.substring(0, 4)), Integer.parseInt(yyyymmdd.substring(4, 6)), Integer.parseInt(yyyymmdd.substring(6, 8)));
	}

	/**
	 * required attribute
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * required attribute
	 */
	@Override
	public boolean[] getDays() {
		return days;
	}

	/**
	 * required attribute (null if service ist not defined in calendar.txt)
	 */
	@Override
	public LocalDate getStartDate() {
		return startDate;
	}

	/**
	 * required attribute (null if service ist not defined in calendar.txt)
	 */
	@Override
	public LocalDate getEndDate() {
		return endDate;
	}

	/**
	 * required attribute
	 */
	@Override
	public Collection<LocalDate> getAdditions() {
		return Collections.unmodifiableCollection(additions);
	}

	/**
	 * required attribute
	 */
	@Override
	public Collection<LocalDate> getExceptions() {
		return Collections.unmodifiableCollection(exceptions);
	}

	@Override
	public Map<String, Trip> getTrips() {
		return Collections.unmodifiableMap(trips);
	}

	public void addTrip(Trip newTrip) {
		trips.put(newTrip.getId(), newTrip);
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

		Service that = (Service) o;
		return (that.getId().equals(id));
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return "[service:" + id + ", startDate:" + startDate + ", endDate:" + endDate + "]";
	}
}
