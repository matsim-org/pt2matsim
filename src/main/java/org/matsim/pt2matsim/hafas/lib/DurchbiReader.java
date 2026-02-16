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
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the HAFAS DURCHBI file and provides durchbindungen (through-service) information.
 * DURCHBI describes cases where two separately listed trips are in reality one through-journey
 * for passengers (vehicles continue as another service without passengers having to change).
 *
 * Format: Each line typically contains:
 * - First trip/route number
 * - Second trip/route number (continuation)
 * - Additional metadata (bitfeld nummer, etc.)
 *
 * @author copilot
 */
public class DurchbiReader {

	private static final Logger log = LogManager.getLogger(DurchbiReader.class);

	/**
	 * Reads the DURCHBI file and returns a map of trip numbers to their continuation trip numbers.
	 * Key: Initial trip number
	 * Value: Continuation trip number (the trip that this vehicle continues as)
	 *
	 * @param durchbiFile Path to the DURCHBI file
	 * @param encodingCharset Character encoding of the file
	 * @return Map of durchbindungen relationships
	 * @throws IOException if file cannot be read
	 */
	public static Map<String, String> readDurchbindungen(String durchbiFile, Charset encodingCharset) throws IOException {
		Map<String, String> durchbindungen = new HashMap<>();
		
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
					// Parse the DURCHBI format
					// According to HRDF spec, DURCHBI contains:
					// Column 1-6: First trip number (fahrt nummer)
					// Column 8-13: Second trip number (continuation)
					// Additional columns may contain bitfeld nummer and other metadata
					
					if (line.length() < 13) {
						log.warn("Line " + lineNumber + " in DURCHBI is too short, skipping: " + line);
						continue;
					}

					String firstTrip = line.substring(0, 6).trim();
					String secondTrip = line.substring(7, 13).trim();

					if (!firstTrip.isEmpty() && !secondTrip.isEmpty()) {
						durchbindungen.put(firstTrip, secondTrip);
						log.debug("Durchbindung: trip " + firstTrip + " continues as trip " + secondTrip);
					}
				} catch (Exception e) {
					log.warn("Error parsing line " + lineNumber + " in DURCHBI: " + line, e);
				}
			}
		}

		log.info("Read " + durchbindungen.size() + " durchbindungen from DURCHBI file");
		return durchbindungen;
	}
}
