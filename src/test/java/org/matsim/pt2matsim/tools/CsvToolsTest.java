package org.matsim.pt2matsim.tools;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.core.utils.collections.MapUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author polettif
 */
public class CsvToolsTest {

	@Test
	public void mapCsvTest() throws IOException {
		String file = "test/testfile.csv";
		Map<String, Map<String, String>> testMap = new HashMap<>();

		MapUtils.getMap("A", testMap).put("a", "0");
		MapUtils.getMap("A", testMap).put("b", "1");
		MapUtils.getMap("A", testMap).put("c", "2");
		MapUtils.getMap("B", testMap).put("a", "3");
		MapUtils.getMap("B", testMap).put("b", "4");
		MapUtils.getMap("B", testMap).put("c", "5");
		MapUtils.getMap("C", testMap).put("a", "6");

		CsvTools.writeNestedMapToFile(testMap, file);
		Map<String, Map<String, String>> readMap = CsvTools.readNestedMapFromFile(file, false);

		Assert.assertEquals(testMap, readMap);

		File f = new File(file);
		boolean del = f.delete();
	}

}