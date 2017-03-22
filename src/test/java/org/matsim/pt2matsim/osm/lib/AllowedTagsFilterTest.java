package org.matsim.pt2matsim.osm.lib;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author polettif
 */
public class AllowedTagsFilterTest {

	private AllowedTagsFilter filter1;
	private AllowedTagsFilter filter2;
	private AllowedTagsFilter filter3;
	private Osm.Element way11;
	private Osm.Element way12;
	private Osm.Element way21;
	private Osm.Element way22;
	private Osm.Element node;

	@Before
	public void prepare() {
		filter1 = new AllowedTagsFilter();
		filter1.add(Osm.ElementType.WAY, "key", "value1");
		filter1.add(Osm.ElementType.WAY, "otherKey", "value1");
		filter1.add(Osm.ElementType.NODE, null, null);
		filter1.add(Osm.ElementType.RELATION, null, null);

		filter2 = new AllowedTagsFilter();
		filter2.add(Osm.ElementType.WAY, "key", "value2");
		filter2.add(Osm.ElementType.WAY, "otherKey", "value2");

		filter3 = new AllowedTagsFilter();
		filter3.add(Osm.ElementType.WAY, "key", null);
		filter3.addException(Osm.ElementType.WAY, "key", "value2");

		// elements
		Map<String, String> tags1 = new HashMap<>();
		tags1.put("key", "value1");
		tags1.put("otherKey", "value1");
		way11 = new OsmElement.Way(11, null, tags1);

		Map<String, String> tags2 = new HashMap<>();
		tags2.put("key", "value1");
		tags2.put("otherKey", "value2");
		way12 = new OsmElement.Way(12, null, tags2);

		Map<String, String> tags3 = new HashMap<>();
		tags3.put("key", "value2");
		tags3.put("otherKey", "value1");
		way21 = new OsmElement.Way(21, null, tags3);

		Map<String, String> tags4 = new HashMap<>();
		tags4.put("key", "value2");
		tags4.put("otherKey", "value2");
		way22 = new OsmElement.Way(22, null, tags4);

		Map<String, String> tagsN = new HashMap<>();
		node = new OsmElement.Node(9, null, tagsN);
	}

	@Test
	public void matches() {
		Assert.assertTrue(filter1.matches(way11));
		Assert.assertTrue(filter1.matches(way12));
		Assert.assertTrue(filter1.matches(way21));
		Assert.assertFalse(filter1.matches(way22));
		Assert.assertFalse(filter1.matches(node));

		Assert.assertFalse(filter2.matches(way11));
		Assert.assertTrue(filter2.matches(way12));
		Assert.assertTrue(filter2.matches(way21));
		Assert.assertTrue(filter2.matches(way22));
		Assert.assertTrue(filter2.matches(node));

		Assert.assertTrue(filter3.matches(way11));
		Assert.assertTrue(filter3.matches(way12));
		Assert.assertFalse(filter3.matches(way21));
		Assert.assertFalse(filter3.matches(way22));
	}

	@Test
	public void mergeFilter() {
		AllowedTagsFilter merged12 = new AllowedTagsFilter();
		merged12.mergeFilter(filter1);
		merged12.mergeFilter(filter2);
		Assert.assertTrue(merged12.matches(way11));
		Assert.assertTrue(merged12.matches(way12));
		Assert.assertTrue(merged12.matches(way21));
		Assert.assertTrue(merged12.matches(way22));

		AllowedTagsFilter merged123 = new AllowedTagsFilter();
		merged123.mergeFilter(filter1);
		merged123.mergeFilter(filter2);
		merged123.mergeFilter(filter3);
		Assert.assertTrue(merged123.matches(way11));
		Assert.assertTrue(merged123.matches(way12));
		Assert.assertFalse(merged123.matches(way21));
		Assert.assertFalse(merged123.matches(way22));
	}

}