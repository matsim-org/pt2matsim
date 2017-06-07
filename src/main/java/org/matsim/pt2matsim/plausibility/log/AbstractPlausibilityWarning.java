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

package org.matsim.pt2matsim.plausibility.log;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.List;

/**
 * Abstract class for plausibility warnings
 *
 * @author polettif
 */
public abstract class AbstractPlausibilityWarning implements PlausibilityWarning {

	protected static Network net;
	protected static long idLong = 0;

	protected final Type type;

	protected final Id<PlausibilityWarning> id;
	protected final TransitLine transitLine;
	protected final TransitRoute transitRoute;

	protected List<Id<Link>> linkIdList;
	protected String fromId;
	protected String toId;
	protected double expected;
	protected double actual;
	protected double difference;

	public AbstractPlausibilityWarning(Type type, TransitLine transitLine, TransitRoute transitRoute) {
		this.id = Id.create(idLong++, PlausibilityWarning.class);
		this.type = type;
		this.transitLine = transitLine;
		this.transitRoute = transitRoute;
	}

	public static void setNetwork(Network network) {
		net = network;
	}

	@Override
	public Id<PlausibilityWarning> getId() {
		return id;
	}

	@Override
	public List<Id<Link>> getLinkIds() {
		return linkIdList;
	}

	@Override
	public TransitRoute getTransitRoute() {
		return transitRoute;
	}

	@Override
	public TransitLine getTransitLine() {
		return transitLine;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public String getFromId() {
		return fromId;
	}

	@Override
	public String getToId() {
		return toId;
	}

	@Override
	public double getExpected() {
		return expected;
	}

	@Override
	public double getActual() {
		return actual;
	}

	@Override
	public double getDifference() {
		return difference;
	}


	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		AbstractPlausibilityWarning that = (AbstractPlausibilityWarning) o;

		if(type != null ? !type.equals(that.type) : that.type != null) return false;
		return linkIdList != null ? linkIdList.equals(that.linkIdList) : that.linkIdList == null;
	}

	@Override
	public int hashCode() {
		int result = type != null ? type.hashCode() : 0;
		result = 31 * result + (linkIdList != null ? linkIdList.hashCode() : 0);
		return result;
	}
}
