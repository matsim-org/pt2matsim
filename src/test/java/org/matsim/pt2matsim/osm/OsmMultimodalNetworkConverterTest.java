package org.matsim.pt2matsim.osm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmElement;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.run.CreateDefaultOsmConfig;

/**
 * @author polettif
 * @author mstraub - Austrian Institute of Technology
 */
public class OsmMultimodalNetworkConverterTest {
	/** MATSim links created from the Gerasdorf test file */
	private static Map<Long, Set<Link>> osmid2link;
	private static final double DELTA = 0.001;
	
	@BeforeAll
	public static void convertGerasdorfArtificialLanesAndMaxspeed() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:31256");
		osmConfig.setOsmFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
		osmConfig.setOutputNetworkFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.xml.gz");
		osmConfig.setMaxLinkLength(1000);

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		Network network = converter.getNetwork();
		
		// write file
		//NetworkTools.writeNetwork(network, osmConfig.getOutputNetworkFile());
		
		osmid2link = collectLinkMap(network);
	}
	
	private static Map<Long, Set<Link>> collectLinkMap(Network network) {
		HashMap<Long, Set<Link>> osmid2link = new HashMap<>();
		for (Link l : network.getLinks().values()) {
			long key = (long) l.getAttributes().getAttribute("osm:way:id");
			if (!osmid2link.containsKey(key))
				osmid2link.put(key, new HashSet<>());
			osmid2link.get(key).add(l);
		}
		return osmid2link;
	}
	
	private static Set<Link> getLinksTowardsNode(Set<Link> links, long osmNodeId) {
		return links.stream()
				.filter(l -> l.getToNode().getId().toString().equals("" + osmNodeId))
				.collect(Collectors.toSet());
	}
	
	@Test
	void testRemovePrivateRoads() {
		Assertions.assertFalse(OsmConverterConfigGroup.createDefaultConfig().getRemovePrivateRoads());
		for (boolean removePrivateRoads : new boolean[] {false, true}) {
			for (String access : new String[] {Osm.Value.PRIVATE, "yes", "no"}) {
				OsmData osm = new OsmDataImpl();
				new OsmFileReader(osm).readFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
				Map<String, String> tags = osm.getWays().get(Id.create(7994889L, Osm.Way.class)).getTags();
				tags.put(Osm.Key.ACCESS, access);
				OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
				config.addParam("removePrivateRoads", Boolean.toString(removePrivateRoads));
				Assertions.assertEquals(removePrivateRoads, config.getRemovePrivateRoads());
				Assertions.assertEquals(Boolean.toString(removePrivateRoads), config.getParams().get("removePrivateRoads"));
				config.setOutputCoordinateSystem("EPSG:31256");
				config.setMaxLinkLength(1000);
				OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
				converter.convert(config);
				Map<Long, Set<Link>> links = collectLinkMap(converter.getNetwork());
				Assertions.assertEquals(!(removePrivateRoads && access.equals(Osm.Value.PRIVATE)), links.containsKey(7994889L));
				Assertions.assertTrue(links.containsKey(7994890L), "Roads without an access tag remain");
			}
		}
	}

	@Test
	void testPrivatePublicTransportRoads() {
		for (boolean enabled : new boolean[] {false, true}) {
			for (String evidence : new String[] {"none", "bus", "psv", "route", "route_master", "taxi"}) {
				OsmData osm = new OsmDataImpl();
				new OsmFileReader(osm).readFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
				Osm.Way way = osm.getWays().get(Id.create(7994889L, Osm.Way.class));
				way.getTags().put(Osm.Key.ACCESS, Osm.Value.PRIVATE);
				way.getTags().put(Osm.Key.LANES, "2");
				if (evidence.equals("route") || evidence.equals("route_master")) {
					Osm.Relation relation = new OsmElement.Relation(123456789L, Map.of(evidence, Osm.Value.TROLLEYBUS));
					way.getRelations().put(relation.getId(), relation);
				} else if (!evidence.equals("none")) {
					if (evidence.equals("taxi")) {
						way.getTags().put(Osm.Key.BUS, Osm.Value.YES);
					}
					way.getTags().put(evidence, evidence.equals("psv") ? Osm.Value.DESIGNATED : Osm.Value.YES);
				}
				OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
				config.setRemovePrivateRoads(enabled);
				if (enabled) {
					way.getTags().put("lanes:psv:forward", "1");
					way.getTags().put("lanes:psv:backward", "1");
				}
				config.setOutputCoordinateSystem("EPSG:31256");
				config.setMaxLinkLength(1000);
				// Default bus permission alone is not evidence of PT use on a private road.
				for (var params : config.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
					((OsmConverterConfigGroup.OsmWayParams) params).setAllowedTransportModes(Set.of("car", "bus", "taxi"));
				}
				config.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("truck", Set.of("car")));
				config.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("car_passenger", Set.of("car")));
				OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
				converter.convert(config);
				Set<Link> links = collectLinkMap(converter.getNetwork()).get(7994889L);
				if (enabled && evidence.equals("none")) {
					Assertions.assertNull(links);
					continue;
				}
				Assertions.assertEquals(2, links.size(), evidence);
				for (Link link : links) {
					Assertions.assertEquals(1, link.getNumberOfLanes(), DELTA);
					Assertions.assertEquals(1500, link.getCapacity(), DELTA);
					if (enabled) {
						Assertions.assertEquals(evidence.equals("taxi") ? Set.of("pt", "bus", "taxi") : Set.of("pt", "bus"), link.getAllowedModes(), evidence);
					} else {
						Assertions.assertTrue(link.getAllowedModes().containsAll(Set.of("car", "taxi", "truck", "car_passenger")));
					}
				}
			}
		}
	}

	@Test
	void testVehicleAccessRestrictions() {
		Assertions.assertFalse(OsmConverterConfigGroup.createDefaultConfig().getRespectVehicleAccess());
		Set<String> defaults = Set.of("car", "car_passenger", "truck", "bus", "taxi");
		assertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, Osm.Key.PSV, Osm.Value.YES), false, defaults);
		assertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, Osm.Key.PSV, Osm.Value.YES), true, Set.of("bus", "pt", "taxi"));
		for (String key : new String[] {Osm.Key.ACCESS, Osm.Key.VEHICLE, Osm.Key.MOTOR_VEHICLE}) {
			for (String value : new String[] {Osm.Value.NO, Osm.Value.PRIVATE, Osm.Value.PERMIT,
					Osm.Value.AGRICULTURAL, Osm.Value.FORESTRY, Osm.Value.DELIVERY}) {
				assertVehicleAccess(Map.of(key, value), true, Set.of());
			}
		}
		assertVehicleAccess(Map.of(Osm.Key.MOTORCAR, Osm.Value.NO), true, Set.of("bus"));
		assertVehicleAccess(Map.of(Osm.Key.ACCESS, Osm.Value.NO, Osm.Key.VEHICLE, Osm.Value.YES), true, defaults);
		assertVehicleAccess(Map.of(Osm.Key.VEHICLE, Osm.Value.NO, Osm.Key.MOTOR_VEHICLE, Osm.Value.YES), true, defaults);
		assertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, Osm.Key.MOTORCAR, Osm.Value.YES), true, Set.of("car", "car_passenger", "taxi"));
		assertVehicleAccess(Map.of(Osm.Key.ACCESS, Osm.Value.NO, Osm.Key.BUS, Osm.Value.YES), true, Set.of("bus", "pt"));
		assertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, Osm.Key.PSV, Osm.Value.YES, Osm.Key.BUS, Osm.Value.NO), true, Set.of("taxi"));
		assertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, Osm.Key.PSV, Osm.Value.YES, Osm.Key.TAXI, Osm.Value.NO), true, Set.of("bus", "pt"));
		assertVehicleAccess(Map.of(Osm.Key.HGV, Osm.Value.NO), true, Set.of("car", "car_passenger", "bus", "taxi"));
		for (String value : new String[] {"destination", "customers", "permissive"}) {
			assertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, value), true, defaults);
		}
		assertVehicleAccess(Map.of("motor_vehicle:conditional", "no @ (08:00-18:00)"), true, defaults);
	}

	@Test
	void testVehicleAccessWithPrivateRoadOptionAndPtRelation() {
		Set<Link> links = convertVehicleAccess(Map.of(Osm.Key.ACCESS, Osm.Value.PRIVATE,
				Osm.Key.MOTORCAR, Osm.Value.YES), true, true, true, false);
		Assertions.assertNotNull(links);
		for (Link link : links) {
			Assertions.assertEquals(Set.of("car", "car_passenger", "taxi"), link.getAllowedModes());
		}
		Assertions.assertNull(convertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO), true, false, true, false),
				"PT route membership must not override an explicit vehicle prohibition");
	}

	@Test
	void testVehicleAccessAddsExplicitPsvModesAndFiltersDedicatedLanes() {
		Set<Link> links = convertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, Osm.Key.PSV, Osm.Value.YES,
				"lanes:psv:forward", "1", "lanes:psv:backward", "1"), true, false, false, true);
		Assertions.assertEquals(2, links.size());
		for (Link link : links) {
			Assertions.assertEquals(Set.of("bus", "pt", "taxi"), link.getAllowedModes());
			Assertions.assertEquals(1, link.getNumberOfLanes(), DELTA);
			Assertions.assertEquals(1500, link.getCapacity(), DELTA);
		}
		links = convertVehicleAccess(Map.of(Osm.Key.BUS, Osm.Value.NO, Osm.Key.LANES, "4",
				"lanes:psv:forward", "1", "lanes:psv:backward", "1"), true, false, false, false);
		Assertions.assertEquals(4, links.size());
		for (Link link : links) {
			Assertions.assertFalse(link.getAllowedModes().contains("bus"));
			Assertions.assertFalse(link.getAllowedModes().contains("pt"));
			if (link.getId().toString().endsWith("_spec")) {
				Assertions.assertEquals(Set.of("taxi"), link.getAllowedModes());
			}
		}
	}

	@Test
	void testDirectionalVehicleAccess() {
		Set<String> defaults = Set.of("car", "car_passenger", "truck", "bus", "taxi");
		for (String direction : new String[] {Osm.Key.FORWARD, Osm.Key.BACKWARD}) {
			boolean forward = direction.equals(Osm.Key.FORWARD);
			Map<String, String> tags = Map.of("motor_vehicle:" + direction, Osm.Value.DELIVERY,
					"bus:" + direction, Osm.Value.YES, "taxi:" + direction, "destination");
			Set<Link> links = convertVehicleAccess(tags, true, false, false, false);
			assertDirectionalModes(links, forward, Set.of("bus", "pt", "taxi"));
			assertDirectionalModes(links, !forward, defaults);
			links = convertVehicleAccess(tags, false, false, false, false);
			assertDirectionalModes(links, true, defaults);
			assertDirectionalModes(links, false, defaults);

			// Directional bus/PSV exceptions also work when defaults only allow cars.
			for (String key : new String[] {Osm.Key.BUS, Osm.Key.PSV}) {
				links = convertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO,
						key + ":" + direction, Osm.Value.YES), true, false, false, true);
				assertDirectionalModes(links, forward, key.equals(Osm.Key.PSV) ? Set.of("bus", "pt", "taxi") : Set.of("bus", "pt"));
				assertDirectionalModes(links, !forward, Set.of());
			}
			links = convertVehicleAccess(Map.of(Osm.Key.ACCESS, Osm.Value.NO,
					"motor_vehicle:" + direction, Osm.Value.YES), true, false, false, false);
			assertDirectionalModes(links, forward, defaults);
			assertDirectionalModes(links, !forward, Set.of());
			links = convertVehicleAccess(Map.of("motorcar:" + direction, Osm.Value.NO), true, false, false, false);
			assertDirectionalModes(links, forward, Set.of("bus"));
			assertDirectionalModes(links, !forward, defaults);
			links = convertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO,
					Osm.Key.PSV, Osm.Value.YES, "taxi:" + direction, Osm.Value.NO), true, false, false, false);
			assertDirectionalModes(links, forward, Set.of("bus", "pt"));
			assertDirectionalModes(links, !forward, Set.of("bus", "pt", "taxi"));
			links = convertVehicleAccess(Map.of(Osm.Key.HIGHWAY, "service", Osm.Key.MOTOR_VEHICLE, Osm.Value.NO,
					"bus:" + direction, Osm.Value.YES), true, false, false, true);
			assertDirectionalModes(links, forward, Set.of("bus", "pt"));
			assertDirectionalModes(links, !forward, Set.of());
			// More specific mode tags win over broader directional restrictions.
			links = convertVehicleAccess(Map.of("motor_vehicle:" + direction, Osm.Value.NO,
					Osm.Key.BUS, Osm.Value.YES), true, false, false, false);
			assertDirectionalModes(links, forward, Set.of("bus", "pt"));
			links = convertVehicleAccess(Map.of(Osm.Key.MOTOR_VEHICLE, Osm.Value.NO,
					Osm.Key.BUS, Osm.Value.YES, "bus:" + direction, Osm.Value.NO), true, false, false, false);
			assertDirectionalModes(links, forward, Set.of());
			assertDirectionalModes(links, !forward, Set.of("bus", "pt"));
			links = convertVehicleAccess(Map.of("hgv:" + direction, Osm.Value.NO), true, false, false, false);
			assertDirectionalModes(links, forward, Set.of("car", "car_passenger", "bus", "taxi"));
			assertDirectionalModes(links, !forward, defaults);
		}
	}

	@Test
	void testDirectionalAccessOnOnewaysAndDedicatedLanes() {
		for (String oneway : new String[] {"yes", "-1"}) {
			boolean forward = oneway.equals("yes");
			String direction = forward ? Osm.Key.FORWARD : Osm.Key.BACKWARD;
			Set<Link> links = convertVehicleAccess(Map.of(Osm.Key.ONEWAY, oneway,
					Osm.Key.MOTOR_VEHICLE, Osm.Value.NO, "bus:" + direction, Osm.Value.YES), true, false, false, true);
			assertDirectionalModes(links, forward, Set.of("bus", "pt"));
			assertDirectionalModes(links, !forward, Set.of());
		}
		Set<Link> links = convertVehicleAccess(Map.of(Osm.Key.LANES, "4", "lanes:psv:forward", "1",
				"lanes:psv:backward", "1", "bus:backward", Osm.Value.NO), true, false, false, false);
		Assertions.assertEquals(4, links.size());
		for (Link link : links) {
			Assertions.assertEquals(1, link.getNumberOfLanes(), DELTA);
			Assertions.assertEquals(1500, link.getCapacity(), DELTA);
			boolean forward = link.getToNode().getId().toString().equals("59836737");
			if (link.getId().toString().endsWith("_spec")) {
				Assertions.assertEquals(forward ? Set.of("bus", "pt", "taxi") : Set.of("taxi"), link.getAllowedModes());
			} else if (!forward) {
				Assertions.assertFalse(link.getAllowedModes().contains("bus"));
			}
		}
	}

	private void assertDirectionalModes(Set<Link> links, boolean forward, Set<String> expectedModes) {
		Assertions.assertNotNull(links);
		Set<Link> directed = getLinksTowardsNode(links, forward ? 59836737L : 59836736L);
		Assertions.assertEquals(expectedModes.isEmpty() ? 0 : 1, directed.size());
		for (Link link : directed) {
			Assertions.assertEquals(expectedModes, link.getAllowedModes());
			Assertions.assertTrue(link.getNumberOfLanes() > 0);
			Assertions.assertTrue(link.getCapacity() > 0);
		}
	}

	private void assertVehicleAccess(Map<String, String> tags, boolean enabled, Set<String> expectedModes) {
		Set<Link> links = convertVehicleAccess(tags, enabled, false, false, false);
		if (expectedModes.isEmpty()) {
			Assertions.assertNull(links, tags.toString());
			return;
		}
		Assertions.assertEquals(2, links.size(), tags.toString());
		for (Link link : links) {
			Assertions.assertEquals(expectedModes, link.getAllowedModes(), tags.toString());
			Assertions.assertEquals(1, link.getNumberOfLanes(), DELTA);
			Assertions.assertEquals(1500, link.getCapacity(), DELTA);
		}
	}

	private Set<Link> convertVehicleAccess(Map<String, String> tags, boolean enabled, boolean removePrivate,
			boolean ptRelation, boolean carOnlyDefaults) {
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
		Osm.Way way = osm.getWays().get(Id.create(7994889L, Osm.Way.class));
		way.getTags().put(Osm.Key.LANES, "2");
		way.getTags().putAll(tags);
		if (ptRelation) {
			Osm.Relation relation = new OsmElement.Relation(123456789L, Map.of(Osm.Key.ROUTE, Osm.Value.BUS));
			way.getRelations().put(relation.getId(), relation);
		}
		OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
		config.addParam("respectVehicleAccess", Boolean.toString(enabled));
		Assertions.assertEquals(enabled, config.getRespectVehicleAccess());
		Assertions.assertEquals(Boolean.toString(enabled), config.getParams().get("respectVehicleAccess"));
		config.setRemovePrivateRoads(removePrivate);
		config.setOutputCoordinateSystem("EPSG:31256");
		config.setMaxLinkLength(1000);
		if (!carOnlyDefaults) {
			for (var params : config.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
				((OsmConverterConfigGroup.OsmWayParams) params).setAllowedTransportModes(Set.of("car", "bus", "taxi"));
			}
		}
		config.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("truck", Set.of("car")));
		config.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("car_passenger", Set.of("car")));
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(config);
		return collectLinkMap(converter.getNetwork()).get(7994889L);
	}

	@Test
	void testDefaultResidential() {
		Set<Link> links = osmid2link.get(7994891L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes(links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	
	@Test
	void testDefaultPrimary() {
		Set<Link> links = osmid2link.get(7994890L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 80);
	}
	
	@Test
	void testPrimaryWithLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994889L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testDirectionalReservedLanesWithTotalLaneCount() {
		for (String direction : Set.of("forward", "backward")) {
			for (Map<String, String> reservedTags : Set.of(
					Map.of("lanes:psv:" + direction, "1"),
					Map.of("psv:lanes:" + direction, "yes|designated"),
					Map.of("lanes:psv:" + direction, "1", "psv:lanes:" + direction, "yes|designated"))) {
				OsmData osm = new OsmDataImpl();
				new OsmFileReader(osm).readFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
				Map<String, String> tags = osm.getWays().get(Id.create(7994889L, Osm.Way.class)).getTags();
				tags.put("lanes", "4");
				tags.putAll(reservedTags);
				OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
				config.setOutputCoordinateSystem("EPSG:31256");
				config.setMaxLinkLength(1000);
				OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
				converter.convert(config);
				Set<Link> links = collectLinkMap(converter.getNetwork()).get(7994889L);
				Assertions.assertEquals(3, links.size(), reservedTags.toString());
				boolean forward = direction.equals("forward");
				Set<Link> reservedDirection = getLinksTowardsNode(links, forward ? 59836737L : 59836736L);
				Set<Link> otherDirection = getLinksTowardsNode(links, forward ? 59836736L : 59836737L);
				Assertions.assertEquals(2, reservedDirection.size());
				Assertions.assertEquals(1, otherDirection.size());
				assertLanes(reservedDirection, 1);
				assertLanes(otherDirection, 2);
				Link reserved = reservedDirection.stream().filter(l -> !l.getAllowedModes().contains("car")).findFirst().orElseThrow();
				Assertions.assertEquals(Set.of("bus", "pt", "taxi"), reserved.getAllowedModes());
				Link general = reservedDirection.stream().filter(l -> l.getAllowedModes().contains("car")).findFirst().orElseThrow();
				Assertions.assertEquals(general.getCapacity(), reserved.getCapacity(), DELTA);
				Assertions.assertEquals(2 * reserved.getCapacity(), otherDirection.iterator().next().getCapacity(), DELTA);
			}
		}
	}

	@Test
	void testPtOnlyReservedLanesRetainLanesAndCapacity() {
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
		Map<String, String> tags = osm.getWays().get(Id.create(7994889L, Osm.Way.class)).getTags();
		tags.put("highway", "service");
		tags.put("psv", "designated");
		tags.put("lanes", "2");
		tags.put("lanes:psv:forward", "1");
		tags.put("psv:lanes:backward", "designated");
		OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
		config.setOutputCoordinateSystem("EPSG:31256");
		config.setMaxLinkLength(1000);
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(config);
		Set<Link> links = collectLinkMap(converter.getNetwork()).get(7994889L);
		Assertions.assertEquals(2, links.size());
		assertLanes(links, 1);
		for (Link link : links) {
			Assertions.assertEquals(Set.of("pt"), link.getAllowedModes());
			Assertions.assertEquals(9999, link.getCapacity(), DELTA);
			Assertions.assertFalse(link.getId().toString().endsWith("_spec"));
		}
	}

	@Test
	void testPrimaryWithOddLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994888L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes(links, 3.5);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryWithForwardAndBackwardLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994887L);
		Assertions.assertEquals(2, links.size(), "bidirectional");

		Set<Link> linksToNorth = getLinksTowardsNode(links, 59836731L);
		assertLanes(linksToNorth, 3);
		assertMaxspeed(linksToNorth, 70);

		Set<Link> linksToSouth = getLinksTowardsNode(links, 59836730L);
		assertLanes(linksToSouth, 4);
		assertMaxspeed(linksToSouth, 100);
	}
	
	@Test
	void testPrimaryWithForwardAndBackwardSpecialLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994886L);
		// we add tow links that can be used only by bus
		Assertions.assertEquals(4, links.size(), "bidirectional");

		Set<Link> linksToNorth = getLinksTowardsNode(links, 57443579L);
		// assertLanes("4 minus one bus lane", linksToNorth, 3);
		assertMaxspeed(linksToNorth, 70);

		Set<Link> linksToSouth = getLinksTowardsNode(links, 59836729L);
		//assertLanes("5 minus one psv lane", linksToSouth, 4);
		assertLanesSpecial(linksToSouth, 5);
		assertMaxspeed(linksToSouth, 100);
	}

	@Test
	void testPrimaryWithSpecialLanes() {
		Set<Link> links = osmid2link.get(7994912L);
		// we create two additional links where taxis can go
		Assertions.assertEquals(4 , links.size(), "bidirectional");
		// two links with three lanes and two links with one lane
		// assertLanes("4 per direction minus one taxi lane", links, 3);
		assertLanesSpecial(links, 8);
		assertMaxspeed(links, 70);
	}

	@Test
	void testDefaultResidentialOneway() {
		Set<Link> links = osmid2link.get(7994914L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836794L).size(), "oneway up north");
		assertLanes(links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	
	@Test
	void testResidentialInvalidLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994891L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	

	@Test
	void testDefaultPrimaryOneway() {
		Set<Link> links = osmid2link.get(7994919L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836804L).size(), "oneway up north");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 80);
	}

	@Test
	void testPrimaryOnewayWithLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(240536138L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 2482638327L).size(), "oneway up north");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryOnewayWithForwardLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994920L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836807L).size(), "oneway up north");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryOnewayWithForwardSpecialLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994925L);
		// we add one bus link
		Assertions.assertEquals(2, links.size(), "oneway");
		Assertions.assertEquals(2, getLinksTowardsNode(links, 59836816L).size(), "oneway up north");
		assertLanesSpecial(links, 4);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryOnewayWithSpecialLane() {
		Set<Link> links = osmid2link.get(7994927L);
		// we create an additional link accessible to bus
		Assertions.assertEquals(2, links.size(), "oneway");
		Assertions.assertEquals(2, getLinksTowardsNode(links, 59836820L).size(), "oneway up north");
		// we expect 4 lanes in total
		assertLanesSpecial(links, 4);
		assertMaxspeed(links, 70);
	}
	
	@Test
	void testPrimaryDefaultReversedOneway() {
		Set<Link> links = osmid2link.get(7994930L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836834L).size(), "oneway down south");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}
	
	@Test
	void testMotorwayWithoutMaxspeedAndOneway() {
		Set<Link> links = osmid2link.get(7994932L);
		Assertions.assertEquals(1, links.size(),
				"oneway by default - taken from OsmConverterConfigGroup.createDefaultConfig");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836844L).size(), "oneway up north");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 2);
		assertMaxspeed(links, OsmMultimodalNetworkConverter.SPEED_LIMIT_NONE_KPH);
	}
	
	@Test
	void testResidentialWithMaxspeedWalk() {
		Set<Link> links = osmid2link.get(7994934L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed(links, OsmMultimodalNetworkConverter.SPEED_LIMIT_WALK_KPH);
	}
	
	@Test
	void testResidentialWithMaxspeedMiles() {
		Set<Link> links = osmid2link.get(7994935L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed(links, 20 * 1.609344);
	}
	
	@Test
	void testResidentialWithMaxspeedKnots() {
		Set<Link> links = osmid2link.get(7994935L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed(links, 20 * 1.609344);
	}
	
	@Test
	void testResidentialMultipleSpeedLimits() {
		Set<Link> links = osmid2link.get(7999581L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed("second speed limit is ignored", links, 40);
	}
	
	@Test
	void testDeadEndStreetsAreContainedInNetwork() {
		Assertions.assertEquals(2, osmid2link.get(22971704L).size());
		Assertions.assertEquals(2, osmid2link.get(153227314L).size());
		Assertions.assertEquals(2, osmid2link.get(95142433L).size());
		Assertions.assertEquals(2, osmid2link.get(95142441L).size());
	}
	
	private static void assertLanes(Set<Link> links, double expectedLanes) {
		assertLanes("", links, expectedLanes);
	}

	private static void assertLanes(String message, Set<Link> links, double expectedLanes) {
		Assertions.assertFalse(links.isEmpty(), "at least one link expected");
		for (Link link : links) {
			Assertions.assertEquals(expectedLanes, link.getNumberOfLanes(), DELTA,
					"lanes (in one direction): " + message);
		}
	}
	
	private static void assertLanesSpecial(Set<Link> links, double totalExpectedLanes) {
		Assertions.assertFalse(links.isEmpty(), "at least one link expected");
		double sumLanes = 0;
		for (Link link : links) {
			sumLanes += link.getNumberOfLanes();
		}
		Assertions.assertEquals(totalExpectedLanes, sumLanes, DELTA);
	}
	
	private static void assertMaxspeed(Set<Link> links, double expectedFreespeedKph) {
		assertMaxspeed("", links, expectedFreespeedKph);
	}

	private static void assertMaxspeed(String message, Set<Link> links, double expectedFreespeedKph) {
		Assertions.assertFalse(links.isEmpty(), "at least one link expected");
		for (Link link : links) {
			Assertions.assertEquals(expectedFreespeedKph / 3.6, link.getFreespeed(), DELTA,
					"freespeed m/s: " + message);
		}
	}

	@Test
	void convertWaterlooCityCentre() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("WGS84");
		osmConfig.setOsmFile("test/osm/WaterlooCityCentre.osm");
		osmConfig.setOutputNetworkFile("test/output/WaterlooCityCentre.xml.gz");
		osmConfig.setMaxLinkLength(20);

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		// write file
		// NetworkTools.writeNetwork(converter.getNetwork(), osmConfig.getOutputNetworkFile());
	}

	@Test
	void convertEPSG() {
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:8682");
		osmConfig.setOsmFile("test/osm/Belgrade.osm");

		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);
	}

	@Test
	void defaultConfig() {
		CreateDefaultOsmConfig.main(new String[]{"doc/defaultOsmConfig.xml"});
	}

}