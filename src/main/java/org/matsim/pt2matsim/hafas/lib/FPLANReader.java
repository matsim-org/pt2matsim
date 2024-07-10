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

import java.nio.charset.Charset;
import java.util.HashSet;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.VehicleType;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the transit lines from a given FPLAN file.
 *
 * @author polettif
 */
public final class FPLANReader {
	private static final Logger log = LogManager.getLogger(FPLANReader.class);

    private FPLANReader() {
    }

    /**
	 * Only reads the PtRoutes and leaves line/route
	 * separation to a later process
	 *
	 * @return the list of FPLANRoutes
	 */
	public static List<FPLANRoute> parseFPLAN(Set<Integer> bitfeldNummern, Map<String, String> operators, String FPLANfile, Set<String> vehicleTypes, boolean includeRailReplacementBus, Charset encodingCharset) throws IOException {
		List<FPLANRoute> hafasRoutes = new ArrayList<>();

			FPLANRoute currentFPLANRoute = null;

			Counter counter = new Counter("FPLAN line # ");
			BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(FPLANfile), encodingCharset));

			if (vehicleTypes == null) {
				vehicleTypes = new HashSet<>();
				includeRailReplacementBus = false;
			} else {
				log.info("Parsing HAFAS using following Vehicle types: " + vehicleTypes);
			}
			Set<String> skippedVehicleTypes = new HashSet<>();
			boolean busInVehicleType = vehicleTypes.contains("B");
			if (includeRailReplacementBus && !busInVehicleType) {
				vehicleTypes.add("B");
			}
			boolean busToBePotentiallyRemoved = false;

			String newLine = readsLines.readLine();
			while(newLine != null) {
				if(newLine.charAt(0) == '*') {

					/*
					 Initialzeile neue Fahrt
					 1−2 	CHAR 	*Z
					 4−9 	INT32 	Fahrtnummer (6-stellig)
					 11−16 	CHAR 	Verwaltung (6-stellig); Die Verwaltungsangabe darf keine Leerzeichen enthalten.
					 17−19 	INT16 	leer
					 20-22 	INT16 	Nummer der Variante des Verkehrsmittels (Kein Standard Feld von HRDF). Hat aber keine fachliche Bedeutung.
					 24−26 	INT16 	Taktanzahl; gibt die Anzahl der noch folgenden Takte an.
					 28−30 	INT16 	Taktzeit in Minuten (Abstand zwischen zwei Fahrten).
					 */
					if(newLine.charAt(1) == 'Z') {
						// reset rail replacement bus-removal flag
						busToBePotentiallyRemoved = false;

						// get operator
						String operatorCode = newLine.substring(10, 16).trim();
						String operator = operators.get(operatorCode);

						// get the fahrtnummer
						String fahrtnummer = newLine.substring(3, 9).trim();

						int numberOfDepartures = 0;
						int cycleTime = 0;
						try {
							numberOfDepartures = Integer.parseInt(newLine.substring(23, 26));
							cycleTime = Integer.parseInt(newLine.substring(27, 30));
						} catch (Exception ignored) {
						}
						currentFPLANRoute = new FPLANRoute(operator, operatorCode, fahrtnummer, numberOfDepartures, cycleTime);
						hafasRoutes.add(currentFPLANRoute);
					}

					/*
					 Verkehrsmittelzeile
					 1−2 	CHAR 		*G
					 4−6 	CHAR 		Verkehrsmittel bzw. Gattung
					 8−14 	[#]INT32 	(optional) Laufwegsindex oder Haltestellennummer,
					 					ab der die Gattung gilt.
					 16−22 	[#]INT32 	(optional) Laufwegsindex oder Haltestellennummer,
					 					bis zu der die Gattung gilt.
					 24−29 [#]INT32 	(optional) Index für das x. Auftreten oder Abfahrtszeitpunkt // 26-27 hour, 28-29 minute
					 31−36 [#]INT32 	(optional) Index für das x. Auftreten oder Ankunftszeitpunkt
					 */
					else if(newLine.charAt(1) == 'G') {
						if(currentFPLANRoute != null) {
							// Vehicle Id:
							String vehicleType = newLine.substring(3, 6).trim();
							if (vehicleTypes.isEmpty() || vehicleTypes.contains(vehicleType)) {
								Id<VehicleType> typeId = Id.create(vehicleType, VehicleType.class);
								currentFPLANRoute.setVehicleTypeId(typeId);
								busToBePotentiallyRemoved = vehicleType.equals("B") && includeRailReplacementBus && !busInVehicleType;
							} else {
								skippedVehicleTypes.add(vehicleType);
								hafasRoutes.remove(currentFPLANRoute);
								currentFPLANRoute = null;
							}

						}
					}

					/*
					 1-5 	CHAR 		*A VE
					 7-13 	[#]INT32 	(optional) Laufwegsindex oder Haltestellennummer, ab der die Verkehrstage im Laufweg gelten.
					 15-21 	[#]INT32 	(optional) Laufwegsindex oder Haltestellennummer, bis zu der die Verkehrstage im Laufweg gelten.
					 23-28 	INT16 		(optional) Verkehrstagenummer für die Tage, an denen die Fahrt stattfindet. Fehlt diese Angabe, so verkehrt diese Fahrt täglich (entspricht dann 000000).
					 30-35 	[#]INT32 	(optional) Index für das x. Auftreten oder Abfahrtszeitpunkt.
					 37-42 	[#]INT32 	(optional) Index für das x. Auftreten oder Ankunftszeitpunkt.
					 */
					else if(newLine.charAt(1) == 'A' && newLine.charAt(3) == 'V' && newLine.charAt(4) == 'E') {
						if(currentFPLANRoute != null) {
							int localBitfeldnr = 0;
							if(newLine.substring(22, 28).trim().length() > 0) {
								localBitfeldnr = Integer.parseInt(newLine.substring(22, 28));
								// TODO there may be more than one *A VE line per *Z block (when the bitfield changes during the route). This is an important issue in HAFAS!
							}
							if(!bitfeldNummern.contains(localBitfeldnr)) {
								// Linie gefunden, die nicht werktäglich verkehrt... => Ignorieren wir...
								hafasRoutes.remove(currentFPLANRoute);
								currentFPLANRoute = null;
							}
						}
					}

					// Bahnersatz: *A BE
					else if(busToBePotentiallyRemoved && newLine.charAt(1) == 'A' && newLine.charAt(3) == 'B' && newLine.charAt(4) == 'E') {
						busToBePotentiallyRemoved = false;
					}

					/*
					 1-2 CHAR *L
					 4-11 CHAR Liniennummer
					 */
					else if(newLine.charAt(1) == 'L') {
						if(currentFPLANRoute != null) {
							currentFPLANRoute.setRouteDescription(newLine.substring(3, 11).trim());
						}
					}

					/*
					 Initialzeile neue freie Fahrt (Linien welche nicht nach Taktfahrplan fahren)
					 */
					else if(newLine.charAt(1) == 'T') {
						log.error("*T-Line in HAFAS discovered. Please implement appropriate read out.");
					}
				}

				/*
				 Regionszeile (Bedarfsfahrten)
				 We don't have this transport mode in  MATSim (yet). => Delete Route and if Line now empty, delete Line.
				 */
				else if(newLine.charAt(0) == '+') {
					log.error("+-Line in HRDF discovered. Please implement appropriate read out.");
				}

				else if(busToBePotentiallyRemoved) {
					hafasRoutes.remove(currentFPLANRoute);
					currentFPLANRoute = null;
				}

				/*
				 Laufwegzeile
				 1−7 	INT32 Haltestellennummer
				 9−29 	CHAR (optional zur Lesbarkeit) Haltestellenname
				 30−35 	INT32 Ankunftszeit an der Haltestelle (lt. Ortszeit der Haltestelle) // 32-33 hour, 34-35 minute
				 37−42 	INT32 Abfahrtszeit an Haltestelle (lt. Ortszeit der Haltestelle) // 39-40 hour, 41-42 minute
				 44−48 	INT32 Ab dem Halt gültige Fahrtnummer (optional)
				 50−55 	CHAR Ab dem Halt gültige Verwaltung (optional)
				 57−57 	CHAR (optional) "X", falls diese Haltestelle auf dem Laufschild der Fahrt aufgeführt wird.
				 */
				else {
					boolean arrivalTimeNegative = newLine.charAt(29) == '-';
					boolean departureTimeNegative = newLine.charAt(36) == '-';

					if(currentFPLANRoute != null) {
						int arrivalTime;
						int departureTime;

						try {
							arrivalTime = Integer.parseInt(newLine.substring(31, 33)) * 3600 +
									Integer.parseInt(newLine.substring(33, 35)) * 60;
						} catch (Exception e) {
							arrivalTime = -1;
						}
						try {
							departureTime = Integer.parseInt(newLine.substring(38, 40)) * 3600 +
									Integer.parseInt(newLine.substring(40, 42)) * 60;
						} catch (Exception e) {
							departureTime = -1;
						}

						if(arrivalTime < 0) {
							arrivalTime = departureTime;
						}
						else if(departureTime < 0) {
							departureTime = arrivalTime;
						}


						// if no departure has been set yet
						if(currentFPLANRoute.getFirstDepartureTime() < 0) {
							currentFPLANRoute.setFirstDepartureTime(departureTime);
						}

						// only add if stop is not "Durchfahrt" or "Diensthalt"
						if(!(arrivalTimeNegative && departureTimeNegative)) {
							currentFPLANRoute.addRouteStop(newLine.substring(0, 7), arrivalTime, departureTime);
						}
					}
				}

				newLine = readsLines.readLine();
				counter.incCounter();
			}
			readsLines.close();
			counter.printCounter();

			log.info("Finished parsing FPLAN.");
			if (!vehicleTypes.isEmpty()) {
				log.info("Skipped vehicle types: " + skippedVehicleTypes);
			}

		return hafasRoutes;
	}

}
