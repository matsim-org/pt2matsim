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
 *
 * @author polettif
 */
public class TransferImpl implements Transfer {

	private final String fromStopId;
	private final String toStopId;
	private final GtfsDefinitions.TransferType transferType;
	private final Integer minTransferTime;
	private final int hash;

	public TransferImpl(String fromStopId, String toStopId, GtfsDefinitions.TransferType transferType) {
		this.fromStopId = fromStopId;
		this.toStopId = toStopId;
		this.transferType = transferType;
		this.minTransferTime = null;
		this.hash = (fromStopId + toStopId).hashCode() + transferType.hashCode();
	}

	public TransferImpl(String fromStopId, String toStopId, GtfsDefinitions.TransferType transferType, Integer minTransferTime) {
		this.fromStopId = fromStopId;
		this.toStopId = toStopId;
		this.transferType = transferType;
		this.minTransferTime = minTransferTime;
		this.hash = (fromStopId + toStopId).hashCode() + transferType.hashCode() + minTransferTime;
	}

	@Override
	public String getFromStopId() {
		return fromStopId;
	}

	@Override
	public String getToStopId() {
		return toStopId;
	}

	@Override
	public GtfsDefinitions.TransferType getTransferType() {
		return transferType;
	}

	@Override
	public Integer minTransferTime() {
		return minTransferTime;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		Transfer other = (Transfer) obj;
		return (other.getFromStopId().equals(this.getFromStopId()) &&
				other.getToStopId().equals(this.getToStopId()) &&
				other.getTransferType().equals(this.getTransferType()) &&
				equalsMinTransferTime(other));
	}

	private boolean equalsMinTransferTime(Transfer other) {
		if (other.minTransferTime() == null) {
			return this.minTransferTime == null;
		} else {
			return other.minTransferTime().equals(this.minTransferTime());
		}
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return "[transfer from:" + fromStopId + " to:" + toStopId + " type:" + transferType + "]";
	}
}
