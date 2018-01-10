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
	/** optional **/
	private final Integer minTransferTime;

	public TransferImpl(String fromStopId, String toStopId, GtfsDefinitions.TransferType transferType) {
		this.fromStopId = fromStopId;
		this.toStopId = toStopId;
		this.transferType = transferType;
		this.minTransferTime = null;
	}

	public TransferImpl(String fromStopId, String toStopId, GtfsDefinitions.TransferType transferType, Integer minTransferTime) {
		this.fromStopId = fromStopId;
		this.toStopId = toStopId;
		this.transferType = transferType;
		this.minTransferTime = minTransferTime;
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
	public Integer getMinTransferTime() {
		return minTransferTime;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		TransferImpl transfer = (TransferImpl) o;

		if(!fromStopId.equals(transfer.fromStopId)) return false;
		if(!toStopId.equals(transfer.toStopId)) return false;
		if(transferType != transfer.transferType) return false;
		return minTransferTime != null ? minTransferTime.equals(transfer.minTransferTime) : transfer.minTransferTime == null;
	}

	@Override
	public int hashCode() {
		int result = fromStopId.hashCode();
		result = 31 * result + toStopId.hashCode();
		result = 31 * result + transferType.hashCode();
		result = 31 * result + (minTransferTime != null ? minTransferTime.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "[transfer from:" + fromStopId + " to:" + toStopId + " type:" + transferType + "]";
	}
}
