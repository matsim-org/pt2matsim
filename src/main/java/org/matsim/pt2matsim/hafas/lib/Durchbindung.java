/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2026 by the members listed in the COPYING,        *
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

/**
 * A through-service relation from HAFAS DURCHBI.
 *
 * @param firstTripNumber Fahrtnummer 1
 * @param firstAdministration Verwaltung für Fahrt 1
 * @param lastStopOfFirstTrip letzter Halt der Fahrt 1
 * @param secondTripNumber Fahrtnummer 2
 * @param secondAdministration Verwaltung für Fahrt 2
 * @param operationDayBitfeldNumber Verkehrstagebitfeldnummer
 */
public record Durchbindung(
	String firstTripNumber,
	String firstAdministration,
	String lastStopOfFirstTrip,
	String secondTripNumber,
	String secondAdministration,
	int operationDayBitfeldNumber
) {
}
