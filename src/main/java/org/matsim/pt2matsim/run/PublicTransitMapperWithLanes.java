package org.matsim.pt2matsim.run;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.LanesReader;
import org.matsim.lanes.LanesWriter;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.lanes.StopMatchedLanesAdapter;
import org.matsim.pt2matsim.osm.LaneConversionReport;
import org.matsim.pt2matsim.tools.NetworkTools;

/** Maps transit, repairs restricted route legs and reconciles the matching lane file. */
public final class PublicTransitMapperWithLanes {
    private PublicTransitMapperWithLanes() { }

    /** Usage: mapper-config.xml input-lanes.xml.gz output-lanes.xml.gz report.csv */
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        if (args.length != 4) throw new IllegalArgumentException("Expected mapper-config input-lanes output-lanes report.csv");
        var config = PublicTransitMappingConfigGroup.loadConfig(args[0]);
        if (config.getInputNetworkFile() == null || config.getInputScheduleFile() == null
                || config.getOutputNetworkFile() == null || config.getOutputScheduleFile() == null)
            throw new IllegalArgumentException("Input and output network and schedule paths are required");
        String mapperConfig = args[0], originalNetwork = config.getInputNetworkFile(), originalLanes = args[1];
        var report = new LaneConversionReport();
        if (config.getSplitLinksAtStops()) {
            if (config.getOutputPreparedNetworkFile() == null)
                config.setOutputPreparedNetworkFile(config.getOutputNetworkFile() + ".prepared.xml.gz");
            mapperConfig = config.getOutputNetworkFile() + ".stop-matching-config.xml";
            config.writeToFile(mapperConfig);
        }
        PublicTransitMapper.run(mapperConfig);
        if (config.getSplitLinksAtStops()) {
            originalNetwork = config.getOutputPreparedNetworkFile();
            var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new LanesReader(scenario).readFile(args[1]);
            StopMatchedLanesAdapter.adapt(NetworkTools.readNetwork(originalNetwork), scenario.getLanes(), report);
            originalLanes = args[2] + ".prepared.xml.gz";
            new LanesWriter(scenario.getLanes()).write(originalLanes);
        }
        ReconcileMappedLanes.run(new String[] {originalNetwork, config.getOutputNetworkFile(),
                originalLanes, config.getOutputScheduleFile(), args[2], args[3], mapperConfig}, report);
    }
}
