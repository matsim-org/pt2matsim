/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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


package org.matsim.pt2matsim.tools;

import com.opencsv.CSVReader;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.lib.GTFSRoute;

import java.io.*;
import java.util.*;

/**
 * Methods to create and write csv data.
 *
 * @author polettif
 */
public class CsvTools {

	private static final char STANDARD_SEPARATOR = ',';

	/**
	 * Converts a table with Tuple&lt;line, column&gt; as key to a list of csv lines.
	 */
	public static List<String> convertToCsvLines(Map<Tuple<Integer, Integer>, String> keyTable, char separator) {
		int maxCol = 0;

		// From <<line, column>, value> to <line, <column, value>>
		Map<Integer, Map<Integer, String>> lin_colVal = new TreeMap<>();
		for(Map.Entry<Tuple<Integer, Integer>, String> entry : keyTable.entrySet()) {
			Map<Integer, String> line = MapUtils.getMap(entry.getKey().getFirst(), lin_colVal);
			line.put(entry.getKey().getSecond(), entry.getValue());
			if(entry.getKey().getSecond() > maxCol) {
				maxCol = entry.getKey().getSecond();
			}
		}

		// From <line, <column, value>> value> to <line, String>
		Map<Integer, String> csvLines = new TreeMap<>();
		for(Map.Entry<Integer, Map<Integer, String>> entry : lin_colVal.entrySet()) {
			String line = "";
			Map<Integer, String> cols = entry.getValue();
			for(int i = 1; i <= maxCol; i++) {
				String value = (cols.get(i) == null ? "" : cols.get(i));
				line += value + separator;
			}
			csvLines.put(entry.getKey(), line.substring(0, line.length() - 1));
		}

		return new LinkedList<>(csvLines.values());
	}

	/**
	 * Writes a list of csvLines to a file
	 *
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public static void writeToFile(List<String> csvLines, String filename) throws FileNotFoundException, UnsupportedEncodingException {
		PrintWriter writer = new PrintWriter(filename, "UTF-8");

		csvLines.forEach(writer::println);

		writer.close();
	}

	/**
	 * In case optional columns in a csv file are missing or are out of order, addressing array
	 * values directly via integer (i.e. where the column should be) does not work.
	 *
	 * @param header      the header (first line) of the csv file
	 * @param columnNames array of attributes you need the indices of
	 * @return the index for each attribute given in columnNames
	 */
	public static Map<String, Integer> getIndices(String[] header, String[] columnNames) {
		Map<String, Integer> indices = new HashMap<>();

		for(String columnName : columnNames) {
			for(int i = 0; i < header.length; i++) {
				if(header[i].equals(columnName)) {
					indices.put(columnName, i);
					break;
				}
			}
		}

		if(columnNames.length != indices.size())
			throw new IllegalArgumentException("Column name not found in csv. Might be some additional characters in the header or the encoding not being UTF-8.");

		return indices;
	}

	public static <K, E, V> void writeNestedMapToFile(Map<K, Map<E, V>> map, String filename, char separator) throws IOException {
		List<String> csvLines = new LinkedList<>();
		for(Map.Entry<K, Map<E, V>> entry : map.entrySet()) {
			for(Map.Entry<E, V> f : entry.getValue().entrySet()) {
				String newLine = "";
				newLine += entry.getKey().toString() + separator;
				newLine += f.getKey().toString() + separator;
				newLine += f.getValue().toString();
				csvLines.add(newLine);
			}
		}
		writeToFile(csvLines, filename);
	}

	public static <K, E, V> void writeNestedMapToFile(Map<K, Map<E, V>> map, String filename) throws IOException {
		writeNestedMapToFile(map, filename, STANDARD_SEPARATOR);
	}


	public static Map<String, Map<String, String>> readMapFromFile(String fileName) throws IOException {
		Map<String, Map<String, String>> map = new HashMap<>();

		CSVReader reader = new CSVReader(new FileReader(fileName));
		String[] line = reader.readNext();
		while(line != null) {
			MapUtils.getMap(line[0], map).put(line[1], line[2]);
			line = reader.readNext();
		}
		reader.close();
		return map;
	}

}