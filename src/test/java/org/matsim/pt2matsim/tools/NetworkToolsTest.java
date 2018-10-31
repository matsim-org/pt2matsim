package org.matsim.pt2matsim.tools;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.matsim.pt2matsim.tools.CoordToolsTest.*;

/**
 * @author polettif
 */
public class NetworkToolsTest {

	private Network network;

	/*
			 ^
             |
		 W   |
		     |
	 E   ·   D   ·   C
	         |
	 ·   ·   |   ·   ·
	         |  X
	 F-------A---Y---B---->
	         |
	 ·   ·   H	 ·   ·
	    	 |
	 G   ·   |   ·   I
             |
	 ·   ·   |   Z   ·
	 */

	public static Network initNetwork() {
		Network net = NetworkTools.createNetwork();

		NetworkFactory fac = net.getFactory();

		net.addNode(fac.createNode(Id.createNodeId("A"), coordA));
		net.addNode(fac.createNode(Id.createNodeId("B"), coordB));
		net.addNode(fac.createNode(Id.createNodeId("C"), coordC));
		net.addNode(fac.createNode(Id.createNodeId("D"), coordD));
		net.addNode(fac.createNode(Id.createNodeId("E"), coordE));
		net.addNode(fac.createNode(Id.createNodeId("F"), coordF));
		net.addNode(fac.createNode(Id.createNodeId("G"), coordG));
		net.addNode(fac.createNode(Id.createNodeId("H"), coordH));
		net.addNode(fac.createNode(Id.createNodeId("I"), coordI));
		net.addNode(fac.createNode(Id.createNodeId("W"), coordW));
		net.addNode(fac.createNode(Id.createNodeId("X"), coordX));
		net.addNode(fac.createNode(Id.createNodeId("Z"), coordZ));


		Set<String> linksToCreate = new HashSet<>();
		// bidirectional
		/*
		  E-------D-------C
		  |       |     / |
		  |       |   X   |
		  |       | /   \ |
		  F       A-------B
		  |               |
		  |       	      |
		  |  	          |
		  G---------------I
		 */
		linksToCreate.add("AB");
		linksToCreate.add("BA");

		linksToCreate.add("BC");
		linksToCreate.add("CB");

		linksToCreate.add("CD");
		linksToCreate.add("DC");

		linksToCreate.add("DE");
		linksToCreate.add("ED");

		linksToCreate.add("EF");
		linksToCreate.add("FE");

		linksToCreate.add("FG");
		linksToCreate.add("GF");

		linksToCreate.add("GI");
		linksToCreate.add("IG");

		linksToCreate.add("IB");
		linksToCreate.add("BI");

		linksToCreate.add("AD");
		linksToCreate.add("DA");

		linksToCreate.add("AX");
		linksToCreate.add("XA");

		linksToCreate.add("XC");
		linksToCreate.add("CX");

		linksToCreate.add("XB");
		linksToCreate.add("BX");


		// one way AX, AH, EW
		/*
		      W
		    /   \
		  E   ·   D   ·   C

		  ·   ·   ·   X   ·

		  F   ·   A   ·   B
		          |
		  ·   ·   H	  ·   ·
		     	  \
		  G   ·    \  ·   I
    	            \   /
		  ·   ·       Z   ·
		 */
		linksToCreate.add("EW");
		linksToCreate.add("WD");

		linksToCreate.add("AH");
		linksToCreate.add("HZ");
		linksToCreate.add("ZI");


		for(Node fromNode : net.getNodes().values()) {
			for(Node toNode : net.getNodes().values()) {
				String strId = fromNode.getId().toString() + toNode.getId().toString();
				if(linksToCreate.contains(strId)) {
					double dist = CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord());
					NetworkUtils.createAndAddLink(net, Id.createLinkId(strId), fromNode, toNode, dist, 1, 1, 1);
				}
			}
		}

		return net;
	}

	@Before
	public void prepare() {
		network = initNetwork();
	}

	private Link getLink(String id) {
		return network.getLinks().get(Id.createLinkId(id));
	}

	@Test
	public void getNearestLink() {
		Coord testR = new Coord(2600041.0, 1200050.0);
		Coord testL = new Coord(2600039.0, 1200050.0);

		Node nearestNode = NetworkUtils.getNearestNode(network, testR);
		Assert.assertEquals("A", nearestNode.getId().toString());

		Assert.assertEquals("AD", NetworkTools.getNearestLink(network, testR, 4).getId().toString());
		Assert.assertEquals("DA", NetworkTools.getNearestLink(network, testL, 4).getId().toString());
	}

	@Test
	public void getOppositeLink() {
		Network network = initNetwork();

		Assert.assertEquals("AD", NetworkTools.getOppositeLink(network.getLinks().get(Id.createLinkId("DA"))).getId().toString());
	}

	@Test
	public void coordIsOnRightSideOfLink() {
		Coord c = new Coord(2600039.0, 1200041.0);

		Assert.assertTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("IG"))));
		Assert.assertTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("FE"))));
		Assert.assertTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("ED"))));
		Assert.assertTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("DA"))));

		Assert.assertFalse(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("AD"))));
		Assert.assertFalse(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("AX"))));
	}

	@Test
	public void linkSequenceHasDuplicateLink() {
		List<Link> seq = new ArrayList<>();
		seq.add(getLink("XA"));
		seq.add(getLink("AB"));
		seq.add(getLink("BC"));
		seq.add(getLink("CD"));
		seq.add(getLink("DA"));
		seq.add(getLink("AB"));
		seq.add(getLink("BI"));
		seq.add(getLink("IH"));

		assertTrue(NetworkTools.linkSequenceHasDuplicateLink(seq));
	}

	@Test
	public void linkSequenceHasUTurns() {
		List<Link> seq = new ArrayList<>();
		seq.add(getLink("AB"));
		seq.add(getLink("BC"));
		seq.add(getLink("CB"));
		seq.add(getLink("BI"));

		assertTrue(NetworkTools.linkSequenceHasUTurns(seq));
	}

	@Test
	public void getSingleFilePrecedingLink() {
		Assert.assertEquals("AH", NetworkTools.getSingleFilePrecedingLink(getLink("HZ")).getId().toString());
		Assert.assertEquals("ZI", NetworkTools.getSingleFileSucceedingLink(getLink("HZ")).getId().toString());
	}

	@Test
	public void reduceSequencedLinks() {
		List<Link> seq = new ArrayList<>();
		seq.add(getLink("AH"));
		seq.add(getLink("HZ"));
		seq.add(getLink("ZI"));

		NetworkTools.reduceSequencedLinks(seq, new Coord(2600050.0, 1200035.0));
		Assert.assertEquals(1, seq.size());
		Assert.assertEquals("AH", seq.get(0).getId().toString());

		Collection<? extends Link> seqAll = new HashSet<>(network.getLinks().values());
		NetworkTools.reduceSequencedLinks(seqAll, new Coord(2600041.0, 1200042.0));
		Assert.assertEquals(10, network.getLinks().size()-seqAll.size());
	}

	@Test
	public void calcRouteLength() {
		List<Link> seq = new ArrayList<>();
		seq.add(getLink("AB"));
		seq.add(getLink("BC"));
		seq.add(getLink("CD"));
		seq.add(getLink("DA"));
		Assert.assertEquals(80.0, NetworkTools.calcRouteLength(seq, true), 0.0001);
	}

	@Test
	public void findClosestLinks() {
		Node node = network.getNodes().get(Id.createNodeId("G"));
		Coord coordToLookFrom = new Coord(node.getCoord().getX() + 2, node.getCoord().getY() + 2);

		Map<Double, Set<Link>> cars = NetworkTools.findClosestLinks(network, coordToLookFrom, 3, Collections.singleton("car"));
		Assert.assertEquals(1, cars.keySet().size());
		Assert.assertEquals(4, cars.get(2.0).size());

		Map<Double, Set<Link>> nullTransportModes = NetworkTools.findClosestLinks(network, coordToLookFrom, 3, null);
		Assert.assertEquals(1, nullTransportModes.keySet().size());
		Assert.assertEquals(4, nullTransportModes.get(2.0).size());

	}



}