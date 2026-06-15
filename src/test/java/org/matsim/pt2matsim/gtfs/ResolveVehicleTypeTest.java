package org.matsim.pt2matsim.gtfs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions.RouteType;
import org.matsim.pt2matsim.tools.VehicleTypeDefaults;

/**
 * Tests for {@link GtfsConverter#resolveVehicleType(Route)}.
 */
class ResolveVehicleTypeTest {

	private TestableGtfsConverter converter;

	@BeforeEach
	void setUp() {
		GtfsFeed feed = new GtfsFeedImpl("test/gtfs-feed/");
		converter = new TestableGtfsConverter(feed);
	}

	// --- Prefix matching: rail short names ---

	@Test
	void iceRoute() {
		Route route = createRoute("ICE 1234", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.ICE, converter.resolveVehicleType(route));
	}

	@Test
	void sbahnRoute() {
		Route route = createRoute("S1", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.S, converter.resolveVehicleType(route));
	}

	@Test
	void reRoute() {
		Route route = createRoute("RE5", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.RE, converter.resolveVehicleType(route));
	}

	@Test
	void rbRoute() {
		Route route = createRoute("RB51", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.RB, converter.resolveVehicleType(route));
	}

	@Test
	void icRoute() {
		Route route = createRoute("IC 2042", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.IC, converter.resolveVehicleType(route));
	}

	@Test
	void ecRoute() {
		Route route = createRoute("EC 8", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.EC, converter.resolveVehicleType(route));
	}

	@Test
	void fexRoute() {
		Route route = createRoute("FEX", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.FEX, converter.resolveVehicleType(route));
	}

	@Test
	void mexRoute() {
		Route route = createRoute("MEX18", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.MEX, converter.resolveVehicleType(route));
	}

	// --- Cross-mode guard: bus with rail-like short name ---

	@Test
	void busWithSbahnName() {
		// SEV bus named "S1" should NOT get S-Bahn type
		Route route = createRoute("S1", RouteType.BUS);
		Assertions.assertEquals(VehicleTypeDefaults.Type.BUS, converter.resolveVehicleType(route));
	}

	@Test
	void busWithReName() {
		// SEV bus named "RE1" should NOT get RegioExpress type
		Route route = createRoute("RE1", RouteType.BUS);
		Assertions.assertEquals(VehicleTypeDefaults.Type.BUS, converter.resolveVehicleType(route));
	}

	@Test
	void busWithRbName() {
		Route route = createRoute("RB49", RouteType.BUS);
		Assertions.assertEquals(VehicleTypeDefaults.Type.BUS, converter.resolveVehicleType(route));
	}

	// --- U-Bahn coded as RAIL should still match SUBWAY type ---

	@Test
	void ubahnCodedAsRail() {
		Route route = createRoute("U1", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.U, converter.resolveVehicleType(route));
	}

	@Test
	void ubahnCodedAsSubway() {
		Route route = createRoute("U7", RouteType.SUBWAY);
		Assertions.assertEquals(VehicleTypeDefaults.Type.U, converter.resolveVehicleType(route));
	}

	// --- Tram matching ---

	@Test
	void tramRoute() {
		Route route = createRoute("T3", RouteType.TRAM);
		Assertions.assertEquals(VehicleTypeDefaults.Type.T, converter.resolveVehicleType(route));
	}

	@Test
	void tramWithMPrefix() {
		// Berlin MetroTram "M1" — M is SUBWAY type, TRAM route_type -> not compatible -> fallback
		Route route = createRoute("M1", RouteType.TRAM);
		Assertions.assertEquals(VehicleTypeDefaults.Type.TRAM, converter.resolveVehicleType(route));
	}

	// --- Fallback: numeric-only short name ---

	@Test
	void numericShortName() {
		Route route = createRoute("5007", RouteType.BUS);
		Assertions.assertEquals(VehicleTypeDefaults.Type.BUS, converter.resolveVehicleType(route));
	}

	@Test
	void numericRailShortName() {
		Route route = createRoute("12345", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.RAIL, converter.resolveVehicleType(route));
	}

	// --- Fallback: null or empty short name ---

	@Test
	void nullShortName() {
		Route route = createRoute(null, RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.RAIL, converter.resolveVehicleType(route));
	}

	@Test
	void emptyShortName() {
		Route route = createRoute("", RouteType.BUS);
		Assertions.assertEquals(VehicleTypeDefaults.Type.BUS, converter.resolveVehicleType(route));
	}

	// --- Fallback: unknown prefix ---

	@Test
	void unknownPrefix() {
		Route route = createRoute("XYZ99", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.RAIL, converter.resolveVehicleType(route));
	}

	// --- Bus prefix matching ---

	@Test
	void sevBusRoute() {
		Route route = createRoute("SEV", RouteType.BUS);
		Assertions.assertEquals(VehicleTypeDefaults.Type.SEV, converter.resolveVehicleType(route));
	}

	// --- Case insensitivity of prefix ---

	@Test
	void lowercasePrefix() {
		Route route = createRoute("ice 801", RouteType.RAIL);
		Assertions.assertEquals(VehicleTypeDefaults.Type.ICE, converter.resolveVehicleType(route));
	}

	// --- Helper methods ---

	private static final Agency DUMMY_AGENCY = new AgencyImpl("test", "Test Agency", "http://test.com", "Europe/Berlin");

	private static Route createRoute(String shortName, RouteType routeType) {
		return new RouteImpl("test_route", shortName, "Test Route", DUMMY_AGENCY, routeType);
	}

	/**
	 * Exposes the protected resolveVehicleType method for testing.
	 */
	private static class TestableGtfsConverter extends GtfsConverter {
		TestableGtfsConverter(GtfsFeed feed) {
			super(feed);
		}

		@Override
		public VehicleTypeDefaults.Type resolveVehicleType(Route gtfsRoute) {
			return super.resolveVehicleType(gtfsRoute);
		}
	}
}
