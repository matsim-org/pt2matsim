package org.matsim.pt2matsim.hafas;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.hafas.filter.HafasFilter;
import org.matsim.pt2matsim.hafas.filter.OperationDayFilter;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author polettif
 */
class HafasConverterTest {

	private TransitSchedule schedule;
	private Vehicles vehicles;

	@BeforeEach
	public void convert() throws IOException {
		this.schedule = ScheduleTools.createSchedule();
		this.vehicles = VehicleUtils.createVehiclesContainer();
		String hafasFolder = "test/BrienzRothornBahn-HAFAS/";
		String cs = "EPSG:2056";
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", cs);

		HafasConverter.run(hafasFolder, schedule, ct, vehicles);
	}

	@Test
	void transitRoutes() {
		Assertions.assertEquals(1, schedule.getTransitLines().size());

		int nRoutes = 0;
		for(TransitLine tl : schedule.getTransitLines().values()) {
			Assertions.assertEquals("BRB", tl.getId().toString());
			for(TransitRoute tr : tl.getRoutes().values()) {
				nRoutes++;
			}
		}
		Assertions.assertEquals(2, nRoutes);
	}

	@Test
	void minimalTransferTimes() {
		int nbMinimalTransferTimes = 0;
		MinimalTransferTimes transferTimes = schedule.getMinimalTransferTimes();
		MinimalTransferTimes.MinimalTransferTimesIterator iterator = transferTimes.iterator();
		while (iterator.hasNext()) {
			iterator.next();
			nbMinimalTransferTimes += 1;
		}
		Assertions.assertEquals(3, nbMinimalTransferTimes);

		Assertions.assertEquals(5 * 60.0, transferTimes.get(
				Id.create("8508350", TransitStopFacility.class),
				Id.create("8508350", TransitStopFacility.class)), 0.00001);

		Assertions.assertEquals(6 * 60.0, transferTimes.get(
				Id.create("8508351", TransitStopFacility.class),
				Id.create("8508351", TransitStopFacility.class)), 0.00001);

		Assertions.assertEquals(60 * 60.0, transferTimes.get(
				Id.create("8508350", TransitStopFacility.class),
				Id.create("8508351", TransitStopFacility.class)), 0.00001);
	}

	@Test
	void nStops() {
		Assertions.assertEquals(3, schedule.getFacilities().size());
	}

	@Test
	void durchbindungen() {
		TransitLine transitLine = schedule.getTransitLines().get(Id.create("BRB", TransitLine.class));
		Assertions.assertNotNull(transitLine, "Transit line BRB should exist");

		TransitRoute sourceRoute = transitLine.getRoutes().values().stream()
			.filter(route -> route.getId().toString().startsWith("000001_"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Source route for trip 000001 not found"));

		TransitRoute targetRoute = transitLine.getRoutes().values().stream()
			.filter(route -> route.getId().toString().startsWith("000002_"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Target route for trip 000002 not found"));

		List<Departure> sourceDepartures = new ArrayList<>(sourceRoute.getDepartures().values());
		sourceDepartures.sort(Comparator.comparing(dep -> dep.getId().toString()));

		List<Departure> targetDepartures = new ArrayList<>(targetRoute.getDepartures().values());
		targetDepartures.sort(Comparator.comparing(dep -> dep.getId().toString()));

		Assertions.assertFalse(sourceDepartures.isEmpty(), "Source route should have departures");
		Assertions.assertEquals(targetDepartures.size(), sourceDepartures.size(),
			"Source and target routes should have same number of departures for one-to-one chaining");

		for (int i = 0; i < sourceDepartures.size(); i++) {
			Departure sourceDeparture = sourceDepartures.get(i);
			Departure targetDeparture = targetDepartures.get(i);

			Assertions.assertNotNull(sourceDeparture.getChainedDepartures(), "Source departure should define chained departures");
			Assertions.assertEquals(1, sourceDeparture.getChainedDepartures().size(),
				"Each source departure should chain to exactly one target departure in this fixture");

			ChainedDeparture chainedDeparture = sourceDeparture.getChainedDepartures().getFirst();
			Assertions.assertEquals(transitLine.getId(), chainedDeparture.getChainedTransitLineId(), "Chained line id should match BRB line");
			Assertions.assertEquals(targetRoute.getId(), chainedDeparture.getChainedRouteId(), "Chained route id should point to trip 000002 route");
			Assertions.assertEquals(targetDeparture.getId(), chainedDeparture.getChainedDepartureId(),
				"Chained departure id should point to corresponding target departure");
		}
	}

	@Test
	void durchbindungenPointToRoutesRemainingAfterCleanup(@TempDir Path tempDir) throws IOException {
		Path hafasFixture = tempDir.resolve("hafas");
		copyDirectory(Path.of("test/BrienzRothornBahn-HAFAS"), hafasFixture);

		Files.writeString(hafasFixture.resolve("DURCHBI"), "*DURCHBI\n000001 000104 8508352 000003 000104 003499\n", StandardCharsets.ISO_8859_1);
		Files.writeString(hafasFixture.resolve("FPLAN"), Files.readString(hafasFixture.resolve("FPLAN"), StandardCharsets.ISO_8859_1) + """
			*Z 000003 000104   001                                    % 00003 000104   001 (001)
			*G R   8508352 8508350  00840  00940                      % 00003 000104   001 (002)
			*A VE 8508352 8508350 003499  00840  00940                % 00003 000104   001 (003)
			*A 2  8508352 8508350         00840  00940                % 00003 000104   001 (004)
			*A DZ 8508352 8508350         00840  00940                % 00003 000104   001 (005)
			*R                                                        % 00003 000104   001 (006)
			8508352 Brienzer Rothorn             00840                % 00003 000104   001 (007)
			8508351 Planalp               00912  00912                % 00003 000104   001 (008)
			8508350 Brienz BRB            00940                       % 00003 000104   001 (009)
			""", StandardCharsets.ISO_8859_1);

		TransitLine transitLine = convertFixture(hafasFixture, List.of()).getTransitLines().get(Id.create("BRB", TransitLine.class));
		TransitRoute sourceRoute = routeStartingWith(transitLine, "000001_");
		Departure sourceDeparture = sourceRoute.getDepartures().get(Id.create("000001", Departure.class));
		ChainedDeparture chainedDeparture = sourceDeparture.getChainedDepartures().getFirst();

		TransitRoute chainedRoute = transitLine.getRoutes().get(chainedDeparture.getChainedRouteId());
		Assertions.assertNotNull(chainedRoute, "Chained route should still exist after route cleanup");
		Assertions.assertTrue(chainedRoute.getDepartures().containsKey(chainedDeparture.getChainedDepartureId()),
			"Chained departure should still exist on the chained route after cleanup");
	}

	@Test
	void durchbindungenRespectOperationDayBitfeld(@TempDir Path tempDir) throws IOException {
		Path hafasFixture = tempDir.resolve("hafas");
		copyDirectory(Path.of("test/BrienzRothornBahn-HAFAS"), hafasFixture);
		Files.writeString(hafasFixture.resolve("DURCHBI"), "*DURCHBI\n000001 000104 8508352 000002 000104 000001\n", StandardCharsets.ISO_8859_1);

		OperationDayFilter operationDayFilter = new OperationDayFilter(hafasFixture.toString(), StandardCharsets.ISO_8859_1);
		TransitLine transitLine = convertFixture(hafasFixture, List.of(operationDayFilter)).getTransitLines().get(Id.create("BRB", TransitLine.class));
		TransitRoute sourceRoute = routeStartingWith(transitLine, "000001_");
		Departure sourceDeparture = sourceRoute.getDepartures().get(Id.create("000001", Departure.class));

		Assertions.assertTrue(sourceDeparture.getChainedDepartures() == null || sourceDeparture.getChainedDepartures().isEmpty(),
			"DURCHBI relation with an unselected bitfeld should not create chained departures");
	}

	@Test
	void durchbindungenSelectTargetRouteStartingAtConnectionStop(@TempDir Path tempDir) throws IOException {
		Path hafasFixture = tempDir.resolve("hafas");
		copyDirectory(Path.of("test/BrienzRothornBahn-HAFAS"), hafasFixture);

		String fplan = Files.readString(hafasFixture.resolve("FPLAN"), StandardCharsets.ISO_8859_1);
		String wrongTarget = """
			*Z 000002 000104   001                                    % wrong target
			*G R   8508351 8508350  00831  00931                      % wrong target
			*A VE 8508351 8508350 003499  00831  00931                % wrong target
			*A 2  8508351 8508350         00831  00931                % wrong target
			*A DZ 8508351 8508350         00831  00931                % wrong target
			*R                                                        % wrong target
			8508351 Planalp                      00831                % wrong target
			8508350 Brienz BRB            00931                       % wrong target
			""";
		Files.writeString(hafasFixture.resolve("FPLAN"), fplan.replace("*Z 000002", wrongTarget + "*Z 000002"), StandardCharsets.ISO_8859_1);

		TransitLine transitLine = convertFixture(hafasFixture, List.of()).getTransitLines().get(Id.create("BRB", TransitLine.class));
		TransitRoute sourceRoute = routeStartingWith(transitLine, "000001_");
		Departure sourceDeparture = sourceRoute.getDepartures().get(Id.create("000001", Departure.class));
		ChainedDeparture chainedDeparture = sourceDeparture.getChainedDepartures().getFirst();
		TransitRoute chainedRoute = transitLine.getRoutes().get(chainedDeparture.getChainedRouteId());

		Assertions.assertEquals("8508352", chainedRoute.getStops().getFirst().getStopFacility().getId().toString(),
			"Target route should be the 000002 route starting at the DURCHBI connection stop");
	}

	@Test
	void networkGeneration(@TempDir Path tempDir) throws IOException {
		Path hafasFixture = tempDir.resolve("hafas");
		copyDirectory(Path.of("test/BrienzRothornBahn-HAFAS"), hafasFixture);

		Files.writeString(hafasFixture.resolve("STRECKENPT"), """
			*Z 
			8508350 8.032644 46.756209
			V123456 8.032645 46.756210
			8508351 8.032646 46.756211
			""", StandardCharsets.ISO_8859_1);
		
		Files.writeString(hafasFixture.resolve("KANTEN"), """
			*F 43 1
			8508350 8508351 B
			*G V123456
			""", StandardCharsets.ISO_8859_1);

		TransitSchedule convertedSchedule = ScheduleTools.createSchedule();
		Vehicles convertedVehicles = VehicleUtils.createVehiclesContainer();
		Network convertedNetwork = NetworkUtils.createNetwork();
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:2056");
		
		HafasConverter.run(hafasFixture.toString(), convertedSchedule, convertedNetwork, ct, convertedVehicles, List.of(), StandardCharsets.ISO_8859_1, false, 0.0);
		
		Assertions.assertEquals(4, convertedNetwork.getNodes().size(), "Network should contain 4 nodes (3 stops + 1 geometry node)");
		Assertions.assertEquals(6, convertedNetwork.getLinks().size(), "Network should contain 6 links (4 bidirectional for 8508350-V123456-8508351 + 2 pseudo links for isolated stop 8508352)");
		Assertions.assertTrue(convertedNetwork.getNodes().containsKey(Id.createNodeId("8508350")), "Station node should be present");
		Assertions.assertTrue(convertedNetwork.getNodes().containsKey(Id.createNodeId("V123456")), "Geometry node should be present");
	}

	private static TransitSchedule convertFixture(Path hafasFixture, List<HafasFilter> filters) throws IOException {
		TransitSchedule convertedSchedule = ScheduleTools.createSchedule();
		Vehicles convertedVehicles = VehicleUtils.createVehiclesContainer();
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:2056");
		HafasConverter.run(hafasFixture.toString(), convertedSchedule, ct, convertedVehicles, filters, StandardCharsets.ISO_8859_1, false);
		return convertedSchedule;
	}

	private static TransitRoute routeStartingWith(TransitLine transitLine, String routeIdPrefix) {
		return transitLine.getRoutes().values().stream()
			.filter(route -> route.getId().toString().startsWith(routeIdPrefix))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Route starting with " + routeIdPrefix + " not found"));
	}

	private static void copyDirectory(Path source, Path target) throws IOException {
		try (var paths = Files.walk(source)) {
			for (Path path : paths.toList()) {
				Path destination = target.resolve(source.relativize(path));
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

}
