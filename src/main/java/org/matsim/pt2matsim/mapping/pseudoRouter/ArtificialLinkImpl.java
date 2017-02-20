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

package org.matsim.pt2matsim.mapping.pseudoRouter;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.Set;

/**
 * Container class for artificial links
 */
public class ArtificialLinkImpl implements ArtificialLink {

	private final Id<Link> id;
	private final Id<Node> fromNodeId;
	private final Id<Node> toNodeId;
	private final Coord fromNodeCoord;
	private final Coord toNodeCoord;
	private final double freespeed;
	private final double linkLength;
	private final double capacity = 9999;
	private final Set<String> transportModes = PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE_AS_SET;

	private final Node fromNode;
	private final Node toNode;
	private final double numberOfLanes;

	public ArtificialLinkImpl(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, double freespeed, double linklength) {
		this.id = ScheduleTools.createArtificialLinkId(fromLinkCandidate, toLinkCandidate);
		this.fromNodeId = fromLinkCandidate.getToNodeId();
		this.toNodeId = toLinkCandidate.getFromNodeId();
		this.fromNodeCoord = fromLinkCandidate.getToNodeCoord();
		this.toNodeCoord = toLinkCandidate.getFromNodeCoord();
		this.freespeed = freespeed;
		this.linkLength = linklength;

		this.fromNode = fromLinkCandidate.getLink().getToNode();
		this.toNode = toLinkCandidate.getLink().getFromNode();

		this.numberOfLanes = 1.0;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		ArtificialLinkImpl other = (ArtificialLinkImpl) obj;
		return (fromNodeId.equals(other.getFromNodeId()) &&
				toNodeId.equals(other.getToNodeId()));
	}

	@Override
	public double getCapacity() {
		return capacity;
	}

	@Override
	public void setCapacity(double capacity) {

	}

	@Override
	public double getCapacity(double time) {
		return 0;
	}

	@Override
	public Set<String> getAllowedModes() {
		return transportModes;
	}

	@Override
	public void setAllowedModes(Set<String> modes) {

	}

	@Override
	public double getFlowCapacityPerSec() {
		return 0;
	}

	@Override
	public double getFlowCapacityPerSec(double time) {
		return 0;
	}

	@Override
	public Id<Node> getToNodeId() {
		return toNodeId;
	}

	@Override
	public Id<Node> getFromNodeId() {
		return fromNodeId;
	}

	@Override
	public Coord getFromNodeCoord() {
		return fromNodeCoord;
	}

	@Override
	public Coord getToNodeCoord() {
		return toNodeCoord;
	}

	@Override
	public double getFreespeed() {
		return freespeed;
	}

	@Override
	public void setFreespeed(double freespeed) {

	}

	@Override
	public double getFreespeed(double time) {
		return 0;
	}

	//
	//
	// Link interface Methods
	//
	//
	@Override
	public Id<Link> getId() {
		return id;
	}

	@Override
	public boolean setFromNode(Node node) {
		throw new IllegalAccessError();
	}

	@Override
	public boolean setToNode(Node node) {
		throw new IllegalAccessError();
	}

	@Override
	public Node getToNode() {
		return toNode;
	}

	@Override
	public Node getFromNode() {
		return fromNode;
	}

	@Override
	public double getLength() {
		return linkLength;
	}

	@Override
	public void setLength(double length) {

	}

	@Override
	public double getNumberOfLanes() {
		return numberOfLanes;
	}

	@Override
	public void setNumberOfLanes(double lanes) {

	}

	@Override
	public double getNumberOfLanes(double time) {
		return numberOfLanes;
	}

	@Override
	public int hashCode() {
		return (fromNodeId.toString() + toNodeId.toString() + freespeed + linkLength).hashCode();
	}

	@Override
	public Attributes getAttributes() {
		return null;
	}

	@Override
	public Coord getCoord() {
		throw new IllegalAccessError();
	}
}
