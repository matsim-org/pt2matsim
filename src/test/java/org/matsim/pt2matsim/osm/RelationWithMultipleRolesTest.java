package org.matsim.pt2matsim.osm;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.Osm.Relation;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;

class RelationWithMultipleRolesTest {

	@Test
	void testMultipleRoles() {
		OsmData osmData = new OsmDataImpl();
		new OsmFileReader(osmData).readFile("test/osm/relation_multiple_roles.osm");

		Id<Osm.Relation> relationId = Id.create("-79", Osm.Relation.class);
		Id<Osm.Way> wayId = Id.create("-1279", Osm.Way.class);

		Osm.Way way = osmData.getWays().get(wayId);
		Relation relation = osmData.getRelations().get(relationId);

		Assertions.assertEquals(2, relation.getMemberRoles(way).size());
		Assertions.assertEquals(List.of("from", "to"), relation.getMemberRoles(way));
		// members with multiple roles will be added multiple times
		Assertions.assertEquals(3, relation.getMembers().size());
	}

}
