package org.matsim.pt2matsim.osm;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt2matsim.osm.lib.Osm;

/**
 * Test finding a sequence of link ids from OSM way ids.
 */
class OsmTurnRestrictionTest {

	private static final Logger LOG = LogManager.getLogger(OsmTurnRestrictionTest.class);

	protected static final Map<Id<Link>, Id<Osm.Way>> osmIds = new HashMap<>();
	protected static final Map<Id<Osm.Way>, List<Id<Link>>> wayLinkMap = new HashMap<>(); // reverse of osmIds
	protected static final Network network = NetworkUtils.createNetwork();

	static {

		Node n0 = NetworkUtils.createNode(Id.createNodeId("0"));
		Node n1 = NetworkUtils.createNode(Id.createNodeId("1"));
		Node n2 = NetworkUtils.createNode(Id.createNodeId("2"));
		Node n3 = NetworkUtils.createNode(Id.createNodeId("3"));
		Node n4 = NetworkUtils.createNode(Id.createNodeId("4"));
		Node n5 = NetworkUtils.createNode(Id.createNodeId("5"));

		network.addNode(n0);
		network.addNode(n1);
		network.addNode(n2);
		network.addNode(n3);
		network.addNode(n4);
		network.addNode(n5);

		// * n0
		// l01 l10 (0110)
		// * n1 - l15 l51 (1551) - n5
		// l12 l21 (1221) \
		// * n2
		// l23 l32 (2332) | l14 l41 (1441)
		// * n3
		// l34 l43 (3443) /
		// * n4

		Id<Osm.Way> w0110 = Id.create("0110", Osm.Way.class);
		Link l01 = NetworkUtils.createLink(Id.createLinkId("01"), n0, n1, network, 1, 1, 300, 1);
		Link l10 = NetworkUtils.createLink(Id.createLinkId("10"), n1, n0, network, 1, 1, 300, 1);
		osmIds.put(l01.getId(), w0110);
		osmIds.put(l10.getId(), w0110);

		Id<Osm.Way> w1221 = Id.create("1221", Osm.Way.class);
		Link l12 = NetworkUtils.createLink(Id.createLinkId("12"), n1, n2, network, 1, 1, 300, 1);
		Link l21 = NetworkUtils.createLink(Id.createLinkId("21"), n2, n1, network, 1, 1, 300, 1);
		osmIds.put(l12.getId(), w1221);
		osmIds.put(l21.getId(), w1221);

		Id<Osm.Way> w1441 = Id.create("1441", Osm.Way.class);
		Link l14 = NetworkUtils.createLink(Id.createLinkId("14"), n1, n4, network, 1, 1, 300, 1);
		Link l41 = NetworkUtils.createLink(Id.createLinkId("41"), n4, n1, network, 1, 1, 300, 1);
		osmIds.put(l14.getId(), w1441);
		osmIds.put(l41.getId(), w1441);

		Id<Osm.Way> w2332 = Id.create("2332", Osm.Way.class);
		Link l23 = NetworkUtils.createLink(Id.createLinkId("23"), n2, n3, network, 1, 1, 300, 1);
		Link l32 = NetworkUtils.createLink(Id.createLinkId("32"), n3, n2, network, 1, 1, 300, 1);
		osmIds.put(l23.getId(), w2332);
		osmIds.put(l32.getId(), w2332);

		Id<Osm.Way> w3443 = Id.create("3443", Osm.Way.class);
		Link l34 = NetworkUtils.createLink(Id.createLinkId("34"), n3, n4, network, 1, 1, 300, 1);
		Link l43 = NetworkUtils.createLink(Id.createLinkId("43"), n4, n3, network, 1, 1, 300, 1);
		osmIds.put(l34.getId(), w3443);
		osmIds.put(l43.getId(), w3443);

		Id<Osm.Way> w1551 = Id.create("1551", Osm.Way.class);
		Link l15 = NetworkUtils.createLink(Id.createLinkId("15"), n1, n5, network, 1, 1, 300, 1);
		Link l51 = NetworkUtils.createLink(Id.createLinkId("51"), n5, n1, network, 1, 1, 300, 1);
		osmIds.put(l15.getId(), w1551);
		osmIds.put(l51.getId(), w1551);

		network.addLink(l01);
		network.addLink(l10);
		network.addLink(l12);
		network.addLink(l21);
		network.addLink(l14);
		network.addLink(l41);
		network.addLink(l23);
		network.addLink(l32);
		network.addLink(l34);
		network.addLink(l43);
		network.addLink(l15);
		network.addLink(l51);

		wayLinkMap.putAll(osmIds.entrySet().stream().collect(
				Collectors.groupingBy(Entry::getValue, Collectors.mapping(Entry::getKey, Collectors.toList()))));
	}

	@Test
	void testFindLinks0() {

		OsmMultimodalNetworkConverter.OsmTurnRestriction tr = new OsmMultimodalNetworkConverter.OsmTurnRestriction(null,
				List.of(Id.create("1221", Osm.Way.class),
						Id.create("2332", Osm.Way.class)),
				OsmMultimodalNetworkConverter.OsmTurnRestriction.RestrictionType.PROHIBITIVE);
		LOG.info(tr.nextWayIds());

		Id<Node> nodeId = Id.createNodeId("0"); // wrong!
		LOG.info(nodeId);
		Node node = network.getNodes().get(nodeId);
		List<Id<Link>> linkIds = OsmMultimodalNetworkConverter.findLinkIds(wayLinkMap, network, node,
				tr.nextWayIds());
		LOG.info(linkIds);

		// NodeId does not fit to nextWayIds -> list of links should be empty
		Assertions.assertEquals(Collections.emptyList(), linkIds);
	}

	@Test
	void testFindLinks1() {

		OsmMultimodalNetworkConverter.OsmTurnRestriction tr = new OsmMultimodalNetworkConverter.OsmTurnRestriction(null,
				List.of(Id.create("1221", Osm.Way.class),
						Id.create("2332", Osm.Way.class)),
				OsmMultimodalNetworkConverter.OsmTurnRestriction.RestrictionType.PROHIBITIVE);
		LOG.info(tr.nextWayIds());

		Id<Node> nodeId = Id.createNodeId("1");
		LOG.info(nodeId);
		Node node = network.getNodes().get(nodeId);
		List<Id<Link>> linkIds = OsmMultimodalNetworkConverter.findLinkIds(wayLinkMap, network, node,
				tr.nextWayIds());
		LOG.info(linkIds);

		Assertions.assertEquals(List.of(Id.createLinkId("12"), Id.createLinkId("23")), linkIds);
	}

	@Test
	void testFindLinks2() {

		OsmMultimodalNetworkConverter.OsmTurnRestriction tr = new OsmMultimodalNetworkConverter.OsmTurnRestriction(null,
				List.of(Id.create("1221", Osm.Way.class),
						Id.create("2332", Osm.Way.class),
						Id.create("3443", Osm.Way.class)),
				OsmMultimodalNetworkConverter.OsmTurnRestriction.RestrictionType.PROHIBITIVE);
		LOG.info(tr.nextWayIds());

		Id<Node> nodeId = Id.createNodeId("1");
		LOG.info(nodeId);
		Node node = network.getNodes().get(nodeId);
		List<Id<Link>> linkIds = OsmMultimodalNetworkConverter.findLinkIds(wayLinkMap, network, node,
				tr.nextWayIds());
		LOG.info(linkIds);

		Assertions.assertEquals(List.of(Id.createLinkId("12"), Id.createLinkId("23"), Id.createLinkId("34")), linkIds);
	}

	@Test
	void testFindLinks3() {

		OsmMultimodalNetworkConverter.OsmTurnRestriction tr = new OsmMultimodalNetworkConverter.OsmTurnRestriction(null,
				List.of(Id.create("1221", Osm.Way.class),
						Id.create("2332", Osm.Way.class),
						Id.create("3443", Osm.Way.class),
						Id.create("3443", Osm.Way.class),
						Id.create("2332", Osm.Way.class)),
				OsmMultimodalNetworkConverter.OsmTurnRestriction.RestrictionType.PROHIBITIVE);
		LOG.info(tr.nextWayIds());

		Id<Node> nodeId = Id.createNodeId("1");
		LOG.info(nodeId);
		Node node = network.getNodes().get(nodeId);
		List<Id<Link>> linkIds = OsmMultimodalNetworkConverter.findLinkIds(wayLinkMap, network, node,
				tr.nextWayIds());
		LOG.info(linkIds);

		Assertions.assertEquals(List.of(
				Id.createLinkId("12"),
				Id.createLinkId("23"),
				Id.createLinkId("34"),
				Id.createLinkId("43"),
				Id.createLinkId("32")), linkIds);
	}

	@Test
	void testFindLinks4() {

		OsmMultimodalNetworkConverter.OsmTurnRestriction tr = new OsmMultimodalNetworkConverter.OsmTurnRestriction(null,
				List.of(Id.create("1221", Osm.Way.class),
						Id.create("2332", Osm.Way.class),
						Id.create("3443", Osm.Way.class),
						Id.create("3443", Osm.Way.class),
						Id.create("2332", Osm.Way.class),
						Id.create("1221", Osm.Way.class),
						Id.create("1551", Osm.Way.class)),
				OsmMultimodalNetworkConverter.OsmTurnRestriction.RestrictionType.PROHIBITIVE);
		LOG.info(tr.nextWayIds());

		Id<Node> nodeId = Id.createNodeId("1");
		LOG.info(nodeId);
		Node node = network.getNodes().get(nodeId);
		List<Id<Link>> linkIds = OsmMultimodalNetworkConverter.findLinkIds(wayLinkMap, network, node,
				tr.nextWayIds());
		LOG.info(linkIds);

		Assertions.assertEquals(List.of(
				Id.createLinkId("12"),
				Id.createLinkId("23"),
				Id.createLinkId("34"),
				Id.createLinkId("43"),
				Id.createLinkId("32"),
				Id.createLinkId("21"),
				Id.createLinkId("15")), linkIds);
	}
}
