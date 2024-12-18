/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2024 by the members listed in the COPYING,        *
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
 * @author tkohl (Royal2Flush)
 */
public class AgencyImpl implements Agency {
	
	private final String id;
	private final String name;
	private final String url;
	private final String timezone;

	public AgencyImpl(String id, String name, String url, String timezone) {
		this.id = id;
		this.name = name;
		this.url = url;
		this.timezone = timezone;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getAgencyName() {
		return this.name;
	}

	@Override
	public String getAgencyUrl() {
		return this.url;
	}

	@Override
	public String getAgencyTimeZone() {
		return this.timezone;
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		AgencyImpl other = (AgencyImpl) o;

		if(!this.id.equals(other.id)) return false;
		if(!this.name.equals(other.name)) return false;
		if(!this.url.equals(other.url)) return false;
		return this.timezone.equals(other.timezone);
	}

	@Override
	public int hashCode() {
		int result = id.hashCode();
		result = 31 * result + name.hashCode();
		result = 31 * result + url.hashCode();
		result = 31 * result + timezone.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "[agency:" + id + ", \"" + name + "\"]";
	}

}
