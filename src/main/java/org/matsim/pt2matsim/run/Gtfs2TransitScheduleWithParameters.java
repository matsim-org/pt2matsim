package org.matsim.pt2matsim.run;

import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;

/**
 * @author sebhoerl
 */
public final class Gtfs2TransitScheduleWithParameters {
	private Gtfs2TransitScheduleWithParameters() {
	}

	public static void main(String[] args) throws ConfigurationException {
		CommandLine cmd = new CommandLine.Builder(args)
				.requireOptions("input-path", "day", "crs", "output-schedule-path")
				.allowOptions("output-vehicles-path", "output-additional-line-info-path", "write-crs")
				.build();

		Gtfs2TransitSchedule.run(
				cmd.getOptionStrict("input-path"),
				cmd.getOptionStrict("day"),
				cmd.getOptionStrict("crs"),
				cmd.getOptionStrict("output-schedule-path"),
				cmd.getOption("output-vehicles-path").orElse(null),
				cmd.getOption("output-additional-line-info-path").orElse(null),
				cmd.getOption("write-crs").map(Boolean::parseBoolean).orElse(false));
	}
}