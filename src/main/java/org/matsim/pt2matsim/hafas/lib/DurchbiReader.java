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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the HAFAS DURCHBI file and provides through-service relations.
 *
 * Fixed-width format:
 * 1-6   INT32 Fahrtnummer 1
 * 8-13  ASCII Verwaltung für Fahrt 1
 * 15-21 INT32 letzter Halt der Fahrt 1
 * 23-28 INT32 Fahrtnummer 2
 * 30-35 ASCII Verwaltung für Fahrt 2
 * 37-42 INT16 Verkehrstagebitfeldnummer
 */
public class DurchbiReader {

	private static final Logger log = LogManager.getLogger(DurchbiReader.class);

	/**
	 * A through-service relation from HAFAS DURCHBI.
	 *
	 * @param firstTripNumber Fahrtnummer 1
	 * @param firstOperator Verwaltung für Fahrt 1
	 * @param lastStopOfFirstTrip letzter Halt der Fahrt 1
	 * @param secondTripNumber Fahrtnummer 2
	 * @param secondOperator Verwaltung für Fahrt 2
	 * @param operationDayBitfeldNumber Verkehrstagebitfeldnummer
	 */
	public record Durchbindung(
		String firstTripNumber,
		String firstOperator,
		String lastStopOfFirstTrip,
		String secondTripNumber,
		String secondOperator,
		int operationDayBitfeldNumber
	) {
	}

	/**
	 * Reads the DURCHBI file and returns through-service relations.
	 *
	 * @param durchbiFile Path to the DURCHBI file
	 * @param encodingCharset Character encoding of the file
	 * @return List of durchbindungen
	 * @throws IOException if file cannot be read
	 */
	public static List<Durchbindung> readDurchbindungen(String durchbiFile, Charset encodingCharset) throws IOException {
		List<Durchbindung> durchbindungen = new ArrayList<>();
		
		// Check if file exists
		if (!Files.exists(Paths.get(durchbiFile))) {
			log.warn("DURCHBI file not found at: " + durchbiFile + ". Continuing without durchbindungen.");
			return durchbindungen;
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(durchbiFile), encodingCharset))) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				// Skip comments and empty lines
				if (line.startsWith("*") || line.trim().isEmpty()) {
					continue;
				}

				try {
					if (line.length() < 42) {
						log.warn("Line " + lineNumber + " in DURCHBI is too short, skipping: " + line);
						continue;
					}

					String firstTrip = line.substring(0, 6).trim();
					String firstOperator = line.substring(7, 13).trim();
					String lastStopOfFirstTrip = line.substring(14, 21).trim();
					String secondTrip = line.substring(22, 28).trim();
					String secondOperator = line.substring(29, 35).trim();
					String bitfeldText = line.substring(36, 42).trim();

					if (firstTrip.isEmpty() || secondTrip.isEmpty()) {
						log.warn("Line " + lineNumber + " in DURCHBI has missing trip number(s), skipping: " + line);
						continue;
					}

					int bitfeldNumber;
					try {
						bitfeldNumber = bitfeldText.isEmpty() ? 0 : Integer.parseInt(bitfeldText);
					} catch (NumberFormatException nfe) {
						log.warn("Line " + lineNumber + " in DURCHBI has invalid bitfeld number, skipping: " + line);
						continue;
					}

					durchbindungen.add(new Durchbindung(
						firstTrip,
						firstOperator,
						lastStopOfFirstTrip,
						secondTrip,
						secondOperator,
						bitfeldNumber
					));
				} catch (Exception e) {
					log.warn("Error parsing line " + lineNumber + " in DURCHBI: " + line, e);
				}
			}
		}

		log.info("Read " + durchbindungen.size() + " durchbindungen from DURCHBI file");
		return durchbindungen;
	}
}
