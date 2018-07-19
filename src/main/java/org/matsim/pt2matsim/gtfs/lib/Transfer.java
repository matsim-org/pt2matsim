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

package org.matsim.pt2matsim.gtfs.lib;

/**
 * Not implemented in GtfsFeed
 * @author polettif
 */
public interface Transfer {

	/** required **/
	String getFromStopId();

	/** required **/
	String getToStopId();

	/** required **/
	GtfsDefinitions.TransferType getTransferType();

	/**
	 * optional, necessary if transferType is REQUIRES_MIN_TRANSFER_TIME
	 *
	 * @return <tt>null</tt> when not defined
	 */
	Integer getMinTransferTime();
}
