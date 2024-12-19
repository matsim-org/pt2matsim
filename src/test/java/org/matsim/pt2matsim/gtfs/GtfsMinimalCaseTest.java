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

package org.matsim.pt2matsim.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions;

/**
 * This test should check that ftfs datasets, that only do the bare minimum (i.e. do our best
 * do not meet the conditions in "conditionally required" and omit the corresponding fields)
 * are still converted correctly
 * 
 * @author Tobias Kohl / Senozon
 */
class GtfsMinimalCaseTest {
	
	@Test
	void noAgencyId() {
		GtfsFeed feed = new GtfsFeedImpl("test/gtfs-feed-min/noAgencyId");
		feed.getRoutes().values().forEach(route -> Assertions.assertNotNull(route.getAgency(), "no agency in route " + route.getId()));
		Assertions.assertEquals("pt2matsim", feed.getRoutes().get("lineA").getAgency().getAgencyName());
		Assertions.assertEquals("https://github.com/matsim-org/pt2matsim", feed.getRoutes().get("lineB").getAgency().getAgencyUrl());
		Assertions.assertEquals("Europe/Zurich", feed.getRoutes().get("lineC").getAgency().getAgencyTimeZone());
	}
	
	@Test
	void noShortName() {
		GtfsFeed feed = new GtfsFeedImpl("test/gtfs-feed-min/noShortName");
		Assertions.assertEquals("Bus Line A", feed.getRoutes().get("lineA").getShortName());
		Assertions.assertEquals("Bus Line A", feed.getRoutes().get("lineA").getLongName());
		Assertions.assertEquals(GtfsDefinitions.RouteType.BUS, feed.getRoutes().get("lineA").getRouteType());
		Assertions.assertEquals("P2M", feed.getRoutes().get("lineB").getAgency().getId());
	}
	
	@Test
	void noLongName() {
		GtfsFeed feed = new GtfsFeedImpl("test/gtfs-feed-min/noLongName");
		Assertions.assertEquals("Line A", feed.getRoutes().get("lineA").getShortName());
		Assertions.assertEquals("Line A", feed.getRoutes().get("lineA").getLongName());
		Assertions.assertEquals(GtfsDefinitions.RouteType.BUS, feed.getRoutes().get("lineA").getRouteType());
		Assertions.assertEquals("P2M", feed.getRoutes().get("lineB").getAgency().getId());
	}
	
	@Test
	void noNameAtAll() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new GtfsFeedImpl("test/gtfs-feed-min/noNameAtAll"));
	}

	@Test
	void multipleAgencies() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new GtfsFeedImpl("test/gtfs-feed-min/multipleAgencies"));
	}
}
