/* *********************************************************************** *
 * project: org.matsim.*
 * Frequency.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

import org.matsim.core.utils.misc.Time;

public class FrequencyImpl implements Frequency {

	private final int startTime;
	private final int endTime;
	private final int headwaySecs;
	/** optional **/
	private final boolean exactTimes;

	public FrequencyImpl(int startTime, int endTime, int headwaySecs) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.headwaySecs = headwaySecs;
		this.exactTimes = false;
	}

	public FrequencyImpl(int startTime, int endTime, int headwaySecs, boolean exactTimes) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.headwaySecs = headwaySecs;
		this.exactTimes = exactTimes;
	}

	/** required */
	@Override
	public int getStartTime() {
		return startTime;
	}

	/** required */

	@Override
	public int getEndTime() {
		return endTime;
	}

	/** required */

	@Override
	public int getHeadWaySecs() {
		return headwaySecs;
	}

	@Override
	public boolean isExactlyScheduled() {
		return exactTimes;
	}


	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		FrequencyImpl other = (FrequencyImpl) o;

		return startTime == other.startTime && endTime == other.endTime && headwaySecs == other.headwaySecs;
	}

	@Override
	public int hashCode() {
		int result = startTime;
		result = 31 * result + endTime;
		result = 31 * result + headwaySecs;
		return result;
	}

	@Override
	public String toString() {
		return "[startTime:" + Time.writeTime(startTime) + ", endTime:" + Time.writeTime(endTime) + ", headwaySecs:" + headwaySecs + "]";
	}

}
