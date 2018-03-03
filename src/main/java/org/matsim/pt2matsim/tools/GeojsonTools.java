/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.geojson.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author polettif
 */
public class GeojsonTools {

	public static void writeFeatureCollectionToFile(FeatureCollection featureCollection, String outFile) {
		try (FileWriter file = new FileWriter(outFile)) {
			String json = new ObjectMapper().writeValueAsString(featureCollection);
			file.write(json);
			file.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static Feature createPointFeature(Coord coord) {
		Feature pointFeature = new Feature();
		Point geometry = new Point();
		geometry.setCoordinates(new LngLatAlt(coord.getX(), coord.getY()));
		pointFeature.setGeometry(geometry);
		return pointFeature;
	}

	public static Feature createLineFeature(List<Coord> coords) {
		Feature lineFeature = new Feature();
		LineString geometry = new LineString();

		for(Coord coord : coords) {
			geometry.add(new LngLatAlt(coord.getX(), coord.getY()));
		}

		lineFeature.setGeometry(geometry);
		return lineFeature;
	}

	public static List<Coord> links2Coords(List<Link> links) {
		List<Coord> coords = new ArrayList<>();
		coords.add(links.get(0).getFromNode().getCoord());
		for(Link link : links) {
			coords.add(link.getToNode().getCoord());
		}
		return coords;
	}
}
