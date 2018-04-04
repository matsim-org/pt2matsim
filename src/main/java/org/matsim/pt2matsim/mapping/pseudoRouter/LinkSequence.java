/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.mapping.pseudoRouter;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.vehicles.Vehicle;

import java.util.List;

/**
 * Custom implementation mirroring {@link LinkNetworkRouteImpl}
 *
 * @author polettif
 */
public class LinkSequence implements NetworkRoute {

	private Id<Link> startLinkId;
	private List<Id<Link>> route;
	private Id<Link> endLinkId;

	public LinkSequence(Id<Link> startLinkId, List<Id<Link>> ids, Id<Link> endLinkId) {
		this.startLinkId = startLinkId;
		this.route = ids;
		this.endLinkId = endLinkId;
	}

	public LinkSequence(List<Id<Link>> ids) {
		this.startLinkId = ids.get(0);
		this.route = ids.subList(1, ids.size() - 1);
		this.endLinkId = ids.get(ids.size() - 1);
	}

	@Override
	public double getDistance() {
		return 0;
	}

	@Override
	public void setDistance(double v) {
		throw new IllegalAccessError();
	}

	@Override
	public double getTravelTime() {
		return 0;
	}

	@Override
	public void setTravelTime(double v) {
		throw new IllegalAccessError();
	}

	@Override
	public Id<Link> getStartLinkId() {
		return startLinkId;
	}

	@Override
	public Id<Link> getEndLinkId() {
		return endLinkId;
	}

	@Override
	public void setStartLinkId(Id<Link> id) {
		this.startLinkId = id;
	}

	@Override
	public void setEndLinkId(Id<Link> id) {
		this.endLinkId = id;
	}

	@Override
	public String getRouteDescription() {
		return "";
	}

	@Override
	public void setRouteDescription(String s) {
		throw new IllegalAccessError();
	}

	@Override
	public String getRouteType() {
		return "";
	}

	@Override
	public void setLinkIds(Id<Link> id, List<Id<Link>> list, Id<Link> id1) {
		throw new IllegalAccessError();
	}

	@Override
	public void setTravelCost(double v) {
		throw new IllegalAccessError();
	}

	@Override
	public double getTravelCost() {
		return 0;
	}

	@Override
	public List<Id<Link>> getLinkIds() {
		return route;
	}

	@Override
	public NetworkRoute getSubRoute(Id<Link> fromLinkId, Id<Link> toLinkId) {
		int fromIndex = -1;
		int toIndex = -1;
		int i;
		int n;
		if(fromLinkId.equals(this.getStartLinkId())) {
			fromIndex = 0;
		} else {
			i = 0;

			for(n = this.route.size(); i < n && fromIndex < 0; ++i) {
				if(fromLinkId.equals(this.route.get(i))) {
					fromIndex = i + 1;
				}
			}

			if(fromIndex < 0 && fromLinkId.equals(this.getEndLinkId())) {
				fromIndex = this.route.size();
			}

			if(fromIndex < 0) {
				throw new IllegalArgumentException("Cannot create subroute because fromLinkId is not part of the route.");
			}
		}

		if(fromLinkId.equals(toLinkId)) {
			toIndex = fromIndex - 1;
		} else {
			i = fromIndex;

			for(n = this.route.size(); i < n && toIndex < 0; ++i) {
				if(fromLinkId.equals(this.route.get(i))) {
					fromIndex = i + 1;
				}

				if(toLinkId.equals(this.route.get(i))) {
					toIndex = i;
				}
			}

			if(toIndex < 0 && toLinkId.equals(this.getEndLinkId())) {
				toIndex = this.route.size();
			}

			if(toIndex < 0) {
				throw new IllegalArgumentException("Cannot create subroute because toLinkId is not part of the route.");
			}
		}

		 NetworkRoute ret = RouteUtils.createLinkNetworkRouteImpl(fromLinkId, toLinkId);
		if(toIndex > fromIndex) {
			ret.setLinkIds(fromLinkId, this.route.subList(fromIndex, toIndex), toLinkId);
		} else {
			ret.setLinkIds(fromLinkId, null, toLinkId);
		}

		return ret;
	}

	@Override
	public void setVehicleId(Id<Vehicle> id) {

	}

	@Override
	public Id<Vehicle> getVehicleId() {
		return null;
	}

	@Override
	public NetworkRoute clone() {
		throw new IllegalAccessError();
	}
}
