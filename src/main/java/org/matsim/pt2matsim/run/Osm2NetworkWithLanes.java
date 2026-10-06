package org.matsim.pt2matsim.run;

import java.io.IOException;
import java.nio.file.Path;
import org.matsim.lanes.LanesWriter;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.OsmNetworkWithLanesConverter;
import org.matsim.pt2matsim.osm.lib.*;
import org.matsim.pt2matsim.tools.NetworkTools;

/** Usage: Osm2NetworkWithLanes osm-config.xml lanes.xml report.csv */
public final class Osm2NetworkWithLanes {
    private Osm2NetworkWithLanes() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException("Expected osm-config.xml lanes.xml report.csv");
        run(OsmConverterConfigGroup.loadConfig(args[0]), args[1], Path.of(args[2]));
    }

    public static void run(OsmConverterConfigGroup config, String lanesFile, Path reportFile) throws IOException {
        AllowedTagsFilter filter = new AllowedTagsFilter();
        filter.add(Osm.ElementType.WAY, Osm.Key.HIGHWAY, null);
        filter.add(Osm.ElementType.WAY, Osm.Key.RAILWAY, null);
        OsmData data = new OsmLaneData(filter);
        OsmNetworkWithLanesConverter converter = new OsmNetworkWithLanesConverter(data);
        try {
            new OsmFileReader(data).readFile(config.getOsmFile());
            converter.convert(config);
        } catch (RuntimeException failure) {
            converter.getReport().recordError(failure);
            converter.getReport().write(reportFile);
            throw failure;
        }
        // Both representations validated before output is published.
        converter.getReport().write(reportFile);
        NetworkTools.writeNetwork(converter.getNetwork(), config.getOutputNetworkFile());
        new LanesWriter(converter.getLanes()).write(lanesFile);
    }
}
