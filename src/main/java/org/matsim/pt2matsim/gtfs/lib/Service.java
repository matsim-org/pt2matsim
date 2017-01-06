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

import org.matsim.core.utils.collections.MapUtils;

import java.time.LocalDate;
import java.util.*;

public class Service {

	//Attributes
	private final String id;
	private final boolean[] days;
	private final LocalDate startDate;
	private final LocalDate endDate;

	private final Collection<LocalDate> additions = new ArrayList<>();
	private final Collection<LocalDate> exceptions = new ArrayList<>();
	private final Set<LocalDate> coveredDays = new HashSet<>();

	public Service(String serviceId, boolean[] days, String startDateStr, String endDateStr) {
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

	public Service(String serviceId) {
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
	public Set<LocalDate> getCoveredDays() {
		return coveredDays;
	}

	/**
	 * parses the date format YYYYMMDD to LocalDate
	 */
	private LocalDate parseDateFormat(String yyyymmdd) {
		return LocalDate.of(Integer.parseInt(yyyymmdd.substring(0, 4)), Integer.parseInt(yyyymmdd.substring(4, 6)), Integer.parseInt(yyyymmdd.substring(6, 8)));
	}

	// required fields
	public String getId() {
		return id;
	}

	public boolean[] getDays() {
		return days;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public Collection<LocalDate> getAdditions() {
		return additions;
	}

	public Collection<LocalDate> getExceptions() {
		return exceptions;
	}

}
