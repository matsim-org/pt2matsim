package org.matsim.pt2matsim.plausibility;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.mapping.PTMapperTest;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;
import org.matsim.pt2matsim.tools.ShapeToolsTest;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author polettif
 */
public class MappingAnalysisTest {

	private MappingAnalysis analysis;
	private Id<TransitLine> lineA = Id.create("lineA", TransitLine.class);
	private Id<TransitLine> lineB = Id.create("lineB", TransitLine.class);
	private Id<TransitRoute> routeA1 = Id.create("routeA1", TransitRoute.class);
	private Id<TransitRoute> routeA2 = Id.create("routeA2", TransitRoute.class);
	private Id<TransitRoute> routeB = Id.create("routeB", TransitRoute.class);

	@Before
	public void prepare() {
		PublicTransitMappingConfigGroup ptmConfig = PTMapperTest.initPTMConfig();
		Network network = NetworkToolsTest.initNetwork();
		TransitSchedule schedule = ScheduleToolsTest.initUnmappedSchedule();

		new PTMapper(schedule, network).run(ptmConfig);

		Map<Id<RouteShape>, RouteShape> shapes = ShapeToolsTest.initShapes();
		analysis = new MappingAnalysis(schedule, network, shapes);
		analysis.run();
	}

	@Test
	public void quantiles() {
		Assert.assertEquals(7, analysis.getQ8585(), 0.001);

		TreeMap<Integer, Double> quantilesA1 = analysis.getQuantiles(lineA, routeA1);
		Assert.assertEquals(0.0, quantilesA1.get(0), 0.001);

		TreeMap<Integer, Double> quantilesA2 = analysis.getQuantiles(lineA, routeA2);
		Assert.assertEquals(ShapeToolsTest.offset, quantilesA2.get(50), 0.001);

		TreeMap<Integer, Double> quantilesB = analysis.getQuantiles(lineB, routeB);
		Assert.assertEquals(0.0, quantilesB.get(0), 0.001);
	}

	@Test
	public void lengthRatios() {
		analysis.getLengthRatio(lineA, routeA1);
	}
}