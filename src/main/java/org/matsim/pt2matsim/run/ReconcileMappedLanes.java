package org.matsim.pt2matsim.run;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinksUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.LanesReader;
import org.matsim.lanes.LanesWriter;
import org.matsim.pt2matsim.lanes.MappedLanesReconciler;
import org.matsim.pt2matsim.lanes.MappedTransitRouteRepair;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.osm.LaneConversionReport;
import org.matsim.pt2matsim.mapping.stopMatching.LinkGeometryIndex;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

/** Usage: original-network mapped-network original-lanes mapped-schedule output-lanes report.csv */
public final class ReconcileMappedLanes {
    private ReconcileMappedLanes() { }

    public static void main(String[] args) throws IOException {
        run(args, new LaneConversionReport());
    }

    public static void run(String[] args, LaneConversionReport report) throws IOException {
        if (args.length != 6 && args.length != 7) throw new IllegalArgumentException(
                "Expected original-network mapped-network original-lanes mapped-schedule output-lanes report.csv [mapper-config for route repair]");
        var original = NetworkTools.readNetwork(args[0]);
        Set<Id<Link>> originalLinks = new HashSet<>(original.getLinks().keySet());
        var network = NetworkTools.readNetwork(args[1]);
        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new LanesReader(scenario).readFile(args[2]);
        var schedule = ScheduleTools.readTransitSchedule(args[3]);
        if (args.length == 7) {
            NetworkTools.integrateNetwork(original, network, false);
            MappedTransitRouteRepair.repair(original, network, schedule,
                    PublicTransitMappingConfigGroup.loadConfig(args[6]), report);
        }
        MappedLanesReconciler.reconcile(network, originalLinks, scenario.getLanes(), schedule, report);
        for (var link : network.getLinks().values()) {
            var restrictions = NetworkUtils.getDisallowedNextLinks(link);
            if (restrictions != null) for (var mode : restrictions.getAsMap().entrySet()) for (var sequence : mode.getValue()) {
                if (!link.getAllowedModes().contains(mode.getKey()) || sequence.stream().anyMatch(id ->
                        !network.getLinks().containsKey(id) || !network.getLinks().get(id).getAllowedModes().contains(mode.getKey())))
                    report.add("removedUnrepresentedRestrictionSequences", "", link.getId(), "mode=" + mode.getKey() + "; sequence=" + sequence);
            }
        }
        // Cleaning removes only sequences that cannot be traversed in the final network.
        DisallowedNextLinksUtils.clean(network);
        if (!DisallowedNextLinksUtils.isValid(network)) throw new IllegalStateException("Invalid final network restrictions");
        var validation = TransitScheduleValidator.validateAll(schedule, network);
        for (String error : validation.getErrors()) report.add("mappedScheduleValidationErrors", "", "", error);
        report.write(Path.of(args[5]));
        if (!validation.isValid() || report.getCounts().getOrDefault("invalidMappedTransitRoutes", 0L) > 0
                || report.getCounts().getOrDefault("unmappedTransitRoutes", 0L) > 0)
            throw new IllegalStateException("Mapped routes conflict with network or lanes; see " + args[5]);
        new LanesWriter(scenario.getLanes()).write(args[4]);
        if (args.length == 7) {
            NetworkTools.writeNetwork(network, args[1]);
            ScheduleTools.writeTransitSchedule(schedule, args[3]);
            var config = PublicTransitMappingConfigGroup.loadConfig(args[6]);
            if (config.getSplitLinksAtStops() && config.getOutputNetworkGeometryFile() != null)
                new LinkGeometryIndex(network, config.getOutputPreparedNetworkFile() + ".geometry.csv")
                        .write(config.getOutputNetworkGeometryFile());
            if (config.getOutputStreetNetworkFile() != null)
                NetworkTools.writeNetwork(NetworkTools.createFilteredNetworkByLinkMode(network, Set.of("car")), config.getOutputStreetNetworkFile());
        }
    }

}
