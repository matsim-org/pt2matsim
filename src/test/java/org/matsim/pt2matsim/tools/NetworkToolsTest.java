package org.matsim.pt2matsim.tools;

import com.vividsolutions.jts.util.Assert;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

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
	         | X
	 F-------A---Y---B---->
	         |
	 ·   ·   |	 ·   ·
	    	 |
	 G   ·   H   ·   I
             |
	 ·   ·   |   Z   ·
	 */

	@Before
	public void prepare() {
		network = initNetwork();
	}

	private Network initNetwork() {
		Network net = NetworkTools.createNetwork();

		NetworkFactory fac = net.getFactory();

		net.addNode(fac.createNode(Id.createNodeId("A"), CoordToolsTest.coordA));
		net.addNode(fac.createNode(Id.createNodeId("B"), CoordToolsTest.coordB));
		net.addNode(fac.createNode(Id.createNodeId("C"), CoordToolsTest.coordC));
		net.addNode(fac.createNode(Id.createNodeId("D"), CoordToolsTest.coordD));
		net.addNode(fac.createNode(Id.createNodeId("E"), CoordToolsTest.coordE));
		net.addNode(fac.createNode(Id.createNodeId("F"), CoordToolsTest.coordF));
		net.addNode(fac.createNode(Id.createNodeId("G"), CoordToolsTest.coordG));
		net.addNode(fac.createNode(Id.createNodeId("H"), CoordToolsTest.coordH));
		net.addNode(fac.createNode(Id.createNodeId("I"), CoordToolsTest.coordI));
		net.addNode(fac.createNode(Id.createNodeId("W"), CoordToolsTest.coordW));
		net.addNode(fac.createNode(Id.createNodeId("X"), CoordToolsTest.coordX));
		net.addNode(fac.createNode(Id.createNodeId("Y"), CoordToolsTest.coordY));
		net.addNode(fac.createNode(Id.createNodeId("Z"), CoordToolsTest.coordZ));


		Set<String> linksToCreate = new HashSet<>();
		// bidirectional
		/*
		  E-------D-------C
		  |       |       |
		  |       |       |
		  |       |       |
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


		// one way AX, AH, EW
		/*
		      W
		    /   \
		  E   ·   D   ·   C
		                /
		  ·   ·   ·   X   ·
		            /
		  F   ·   A   ·   B
		          |
		  ·   ·   |	  ·   ·
		     	  |
		  G   ·   H   ·   I
    	            \   /
		  ·   ·       Z   ·
		 */
		linksToCreate.add("AX");
		linksToCreate.add("XC");

		linksToCreate.add("EW");
		linksToCreate.add("WD");

		linksToCreate.add("AH");
		linksToCreate.add("HZ");
		linksToCreate.add("ZI");


		for(Node fromNode : net.getNodes().values()) {
			for(Node toNode : net.getNodes().values()) {
				String strId = fromNode.getId().toString() + toNode.getId().toString();
				if(linksToCreate.contains(strId)) {
					NetworkUtils.createAndAddLink(net, Id.createLinkId(strId), fromNode, toNode, CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord()), 1, 1, 1);
				}
			}
		}

		return net;
	}

	private Link getLink(String id) {
		return network.getLinks().get(Id.createLinkId(id));
	}

	@Test
	public void getNearestLink() throws Exception {
		Coord testR = new Coord(0.01, 1.0);
		Coord testL = new Coord(-0.01, 1.0);

		Node nearestNode = NetworkUtils.getNearestNode(network, testR);
		Assert.equals(nearestNode.getId().toString(), "X");

		Assert.equals("AD", NetworkTools.getNearestLink(network, testR, 4).getId().toString());
		Assert.equals("DA", NetworkTools.getNearestLink(network, testL, 4).getId().toString());
	}

	@Test
	public void findClosestLinks() throws Exception {
		Coord coord = new Coord(0.2, 0.2);

		Assert.equals(1, NetworkTools.findClosestLinks(network, coord, 9, 1, 0, null, 5).size());
		Assert.equals(5, NetworkTools.findClosestLinks(network, coord, 9, 2, 1, null, 10).size());
		Assert.equals(7, NetworkTools.findClosestLinks(network, coord, 9, 6, 5, null, 10).size());
		Assert.equals(6, NetworkTools.findClosestLinks(network, coord, 9, 4, 5, null, Math.sqrt(0.16)).size());
	}

	@Test
	public void getOppositeLink() throws Exception {
		Network network = initNetwork();

		Assert.equals("AD", NetworkTools.getOppositeLink(network.getLinks().get(Id.createLinkId("DA"))).getId().toString());
	}

	@Test
	public void coordIsOnRightSideOfLink() throws Exception {
		Coord c = new Coord(-1.0, 1.0);

		Assert.isTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("IG"))));
		Assert.isTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("FE"))));
		Assert.isTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("ED"))));
		Assert.isTrue(NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("DA"))));

		Assert.isTrue(!NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("AD"))));
		Assert.isTrue(!NetworkTools.coordIsOnRightSideOfLink(c, network.getLinks().get(Id.createLinkId("AX"))));
	}

	@Test
	public void linkSequenceHasDuplicateLink() throws Exception {
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
	public void linkSequenceHasUTurns() throws Exception {
		List<Link> seq = new ArrayList<>();
		seq.add(getLink("AB"));
		seq.add(getLink("BC"));
		seq.add(getLink("CB"));
		seq.add(getLink("BI"));

		assertTrue(NetworkTools.linkSequenceHasUTurns(seq));
	}

	@Test
	public void getSingleFilePrecedingLink() {
		NetworkTools.getSingleFilePrecedingLink(getLink("HZ"));
		NetworkTools.getSingleFileSucceedingLink(getLink("HZ"));
	}

	@Test
	public void calcRouteLength() throws Exception {
		List<Link> seq = new ArrayList<>();
		seq.add(getLink("AB"));
		seq.add(getLink("BC"));
		seq.add(getLink("CD"));
		seq.add(getLink("DA"));
		Assert.equals(8.0, NetworkTools.calcRouteLength(seq, true));
	}

}