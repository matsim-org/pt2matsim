/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt2matsim.run;

import org.matsim.pt2matsim.config.OsmConverterConfigGroup;

/**
 * Creates a default osmConverter config file.
 *
 * @author polettif
 */
public final class CreateDefaultOsmConfig {

	/**
	 * Creates a default publicTransitMapping config file.
	 * @param args [0] default config filename
	 */
	public static void main(final String[] args) {
		if(args.length < 1) {
			throw new IllegalArgumentException("Config file name as argument needed");
		}

		OsmConverterConfigGroup defaultConfig = OsmConverterConfigGroup.createDefaultConfig();
		defaultConfig.writeToFile(args[0]);
	}
}