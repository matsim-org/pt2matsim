package org.matsim.pt2matsim.run;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;

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
        PublicTransitMapper.run(args[0]);
        ReconcileMappedLanes.main(new String[] {config.getInputNetworkFile(), config.getOutputNetworkFile(),
                args[1], config.getOutputScheduleFile(), args[2], args[3], args[0]});
    }
}
