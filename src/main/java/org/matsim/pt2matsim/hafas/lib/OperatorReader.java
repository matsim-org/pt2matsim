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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the HAFAS-file BETRIEB_DE and provides the operators in a String-String-Map.
 *
 * @author boescpa
 */
public class OperatorReader {

	public static Map<String, String> readOperators(String BETRIEB_DE, Charset encodingCharset) throws IOException {
		Map<String, String> operators = new HashMap<>();
		try(BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(BETRIEB_DE), encodingCharset))) {
			String newLine;
			while ((newLine = readsLines.readLine()) != null) {
				if (newLine.startsWith("*")) {
					continue;
				}
				String abbrevationOperator = newLine.split("\"")[1].replace(" ","");

				String[] operatorIds;
				if (newLine.split(":").length == 1) { // handle format variants
					newLine = readsLines.readLine();
					if (newLine == null) break;
					operatorIds = newLine.substring(8).trim().split("\\s+");
				} else {
					operatorIds = newLine.split(":")[1].trim().split("\\s+");
				}
				for (String operatorId : operatorIds) {
					operators.put(operatorId.trim(), abbrevationOperator);
				}
			}
		}
		return operators;
	}

}
