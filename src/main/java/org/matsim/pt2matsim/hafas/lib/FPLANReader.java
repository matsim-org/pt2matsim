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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt2matsim.hafas.filter.HafasFilter;
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
	public static List<FPLANRoute> parseFPLAN(Map<String, String> operators, String FPLANfile, List<HafasFilter> filters, Charset encodingCharset) throws IOException {
		List<FPLANRoute> hafasRoutes = new ArrayList<>();

			FPLANRoute currentFPLANRoute = null;

			Counter counter = new Counter("FPLAN line # ");
			BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(FPLANfile), encodingCharset));

			String newLine = readsLines.readLine();
			while(newLine != null) {
				if(newLine.charAt(0) == '*') {
					// Skip initial comment line
					if(newLine.charAt(1) == 'F' && newLine.charAt(2) == ' ') {
						newLine = readsLines.readLine();
						continue;
					}

					if(newLine.charAt(1) == 'Z' || (newLine.charAt(1) == 'K' && newLine.charAt(2) == 'W')) {
						// new trip is beginning. Store previous route
						if (keepRoute(currentFPLANRoute, filters)) {
							hafasRoutes.add(currentFPLANRoute);
						}
						currentFPLANRoute = null;
					}

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
					}

					// *KW (Kurswagen) share attributes with actual trips (*Z). The following skips KW until finding the next Z.
					if (currentFPLANRoute == null) {
						newLine = readsLines.readLine();
						continue;
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
						// Vehicle Id:
						String vehicleType = newLine.substring(3, 6).trim();
						Id<VehicleType> typeId = Id.create(vehicleType, VehicleType.class);
						currentFPLANRoute.setVehicleTypeId(typeId);
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
						String startStopId = null;
						String endStopId = null;
						int localBitfeldnr = 0;
						if(!newLine.substring(6, 13).trim().isEmpty()) {
							startStopId = newLine.substring(6, 13);
						}
						if(!newLine.substring(14, 21).trim().isEmpty()) {
							endStopId = newLine.substring(14, 21);
						}
						if(!newLine.substring(22, 28).trim().isEmpty()) {
							localBitfeldnr = Integer.parseInt(newLine.substring(22, 28));
						}
						currentFPLANRoute.addLocalBitfeldNr(localBitfeldnr, startStopId, endStopId);
					}

					// Bahnersatz: *A BE
					else if(newLine.charAt(1) == 'A' && newLine.charAt(3) == 'B' && newLine.charAt(4) == 'E') {
						currentFPLANRoute.setIsRailReplacementBus();
					}

					/*
					 1-2 CHAR *L
					 4-11 CHAR Liniennummer
					 */
					else if(newLine.charAt(1) == 'L') {
						currentFPLANRoute.setRouteDescription(newLine.substring(3, 11).trim());
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
					boolean isAlightingForbidden = newLine.charAt(29) == '-';
					boolean isBoardingForbidden = newLine.charAt(36) == '-';

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

					currentFPLANRoute.addRouteStop(newLine.substring(0, 7), arrivalTime, departureTime, !isBoardingForbidden, !isAlightingForbidden);
				}

				newLine = readsLines.readLine();
				counter.incCounter();
			}
			readsLines.close();
			counter.printCounter();

			log.info("Finished parsing FPLAN.");

			writeFilterStats(filters);

		return hafasRoutes;
	}

	private static void writeFilterStats(List<HafasFilter> filters) {
		for(HafasFilter filter : filters) {
			String filterName = filter.getClass().getSimpleName();
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filterName + "_filteredIn.txt"), "utf-8"))) {
				for (String id : filter.getIdsFilteredIn()) {
					bw.write(id + "\n");
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filterName + "_filteredOut.txt"), "utf-8"))) {
				for (String id : filter.getIdsFilteredOut()) {
					bw.write(id + "\n");
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
    }

	private static boolean keepRoute(FPLANRoute route, List<HafasFilter> filters) {
		if (route == null) return false;
		if (filters.isEmpty()) return true;
		return filters.stream().allMatch(f -> f.keepRoute(route));
	}

}
