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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes.MinimalTransferTimesIterator;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * @author polettif
 */
public class PseudoScheduleImpl implements PseudoSchedule {

	private final Set<PseudoTransitRoute> pseudoSchedule = new HashSet<>();

	@Override
	public void addPseudoRoute(TransitLine transitLine, TransitRoute transitRoute,
			List<PseudoRouteStop> pseudoStopSequence, List<Id<Link>> networkLinkList) {
		pseudoSchedule.add(new PseudoTransitRouteImpl(transitLine, transitRoute, pseudoStopSequence, networkLinkList));
	}

	@Override
	public Set<PseudoTransitRoute> getPseudoRoutes() {
		return pseudoSchedule;
	}

	@Override
	public void mergePseudoSchedule(PseudoSchedule otherPseudoSchedule) {
		pseudoSchedule.addAll(otherPseudoSchedule.getPseudoRoutes());
	}
	
	public void createFacilitiesAndLinkSequences(final TransitSchedule schedule) 
	        throws InterruptedException, ExecutionException {
		Logger logger = LogManager.getLogger(PseudoScheduleImpl.class);
	    // 1) Prepare your full list of pseudo‐routes:
	    List<PseudoTransitRoute> allRoutes = new ArrayList<>(pseudoSchedule);
	    int numThreads = 48;
	    int chunkSize  = 100;
	    List<List<PseudoTransitRoute>> chunks = new ArrayList<>();
	    for (int i = 0; i < allRoutes.size(); i += chunkSize) {
	        chunks.add(allRoutes.subList(i, Math.min(i + chunkSize, allRoutes.size())));
	    }

	    // 2) Define a holder for per‐thread results:
	    class ChunkResult {
	        Map<Id<TransitStopFacility>, TransitStopFacility> newFacilities = new HashMap<>();
	        Map<Id<TransitLine>, List<TransitRoute>>    newRoutes     = new HashMap<>();
	        Map<Id<TransitStopFacility>, Set<Id<TransitStopFacility>>> parentsToChildren
	            = new HashMap<>();
	        // Transfer‐time records: (parentFrom, parentTo, seconds)
	        List<Triple<Id<TransitStopFacility>,Id<TransitStopFacility>,Double>> transfers
	            = new ArrayList<>();
	    }

	    // 3) Submit Callable tasks
	    ExecutorService exec = Executors.newFixedThreadPool(numThreads);
	    List<Future<ChunkResult>> futures = new ArrayList<>();

	    for (List<PseudoTransitRoute> chunk : chunks) {
	        futures.add(exec.submit(() -> {
	            ChunkResult result = new ChunkResult();
	            TransitScheduleFactory factory = schedule.getFactory();

	            for (PseudoTransitRoute ptr : chunk) {
	                // build child facilities & new TransitRoute stops BUT only in result.newFacilities
	                List<PseudoRouteStop> stops = ptr.getPseudoStops();
	                List<TransitRouteStop> newStops = new ArrayList<>(stops.size());
	                for (PseudoRouteStop ps : stops) {
	                    Id<TransitStopFacility> childId = 
	                        ScheduleTools.createChildStopFacilityId(ps.getParentStopFacilityId(), ps.getLinkId());

	                    // only create one facility per childId
	                    result.newFacilities.computeIfAbsent(childId, id -> {
	                        TransitStopFacility f = factory.createTransitStopFacility(
	                            id, ps.getCoord(), ps.isBlockingLane());
	                        f.setLinkId(ps.getLinkId());
	                        f.setName(ps.getFacilityName());
	                        f.setStopAreaId(ps.getStopAreaId());
	                        return f;
	                    });

	                    TransitRouteStop trs = factory.createTransitRouteStop(
	                        result.newFacilities.get(childId), 
	                        ps.getArrivalOffset().seconds(),
	                        ps.getDepartureOffset().seconds());
	                    trs.setAwaitDepartureTime(ps.awaitsDepartureTime());
	                    newStops.add(trs);

	                    result.parentsToChildren
	                          .computeIfAbsent(ps.getParentStopFacilityId(), k -> new HashSet<>())
	                          .add(childId);
	                }

	                // assemble new TransitRoute
	                TransitRoute oldRoute = ptr.getTransitRoute();
	                TransitRoute newRoute = factory.createTransitRoute(
	                    oldRoute.getId(), null, newStops, oldRoute.getTransportMode());
	                oldRoute.getDepartures().values().forEach(newRoute::addDeparture);

	                List<Id<Link>> links = ptr.getNetworkLinkIdList();
	                newRoute.setRoute(new LinkSequence(
	                    links.get(0),
	                    links.subList(1, links.size() - 1),
	                    links.get(links.size() - 1)
	                ));
	                newRoute.setDescription(oldRoute.getDescription());

	                // queue it for addition/removal later
	                result.newRoutes
	                      .computeIfAbsent(ptr.getTransitLineId(), id -> new ArrayList<>())
	                      .add(newRoute);

	                // capture transfer‐time updates for this route’s parent stops
	                MinimalTransferTimes mtt = schedule.getMinimalTransferTimes();
	                for (MinimalTransferTimesIterator it = mtt.iterator(); it.hasNext(); ) {
	                    it.next();
	                    Id<TransitStopFacility> fromP = it.getFromStopId();
	                    Id<TransitStopFacility> toP   = it.getToStopId();
	                    if (result.parentsToChildren.containsKey(fromP) &&
	                        result.parentsToChildren.containsKey(toP)) {
	                        double secs = it.getSeconds();
	                        result.transfers.add(new ImmutableTriple<>(fromP, toP, secs));
	                    }
	                }
	            }
	            return result;
	        }));
	    }

	    exec.shutdown();
	    exec.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

	    Map<Id<TransitStopFacility>, Set<Id<TransitStopFacility>>> globalParentsToChildren = new HashMap<>();
	    List<Triple<Id<TransitStopFacility>,Id<TransitStopFacility>,Double>> allTransfers = new ArrayList<>();

	    // 4) Merge everything on the main thread
	    for (Future<ChunkResult> f : futures) {
	        ChunkResult r = f.get();

	        // 4a) Add new facilities
	        for (TransitStopFacility fac : r.newFacilities.values()) {
	            if (!schedule.getFacilities().containsKey(fac.getId())) {
	                schedule.addStopFacility(fac);
	            }
	        }

	        // 4b) Remove old routes and add new ones
	        for (Map.Entry<Id<TransitLine>,List<TransitRoute>> e : r.newRoutes.entrySet()) {
	            TransitLine line = schedule.getTransitLines().get(e.getKey());
	            

	            // remove all old Pseudo‑routes and add new ones
	            for (TransitRoute nr : e.getValue()) {
	            	line.removeRoute(line.getRoutes().get(nr.getId()));
	                line.addRoute(nr);
	            }
	        }

	     // 4c) Accumulate parents→children and transfer records
	        // Merge this chunk's parentsToChildren into the global map:
	        r.parentsToChildren.forEach((parent, childSet) ->
	            globalParentsToChildren
	                .computeIfAbsent(parent, k -> new HashSet<>())
	                .addAll(childSet)
	        );
	        
	        // Collect all transfer triples:
	        allTransfers.addAll(r.transfers);

	    }
	    
	    MinimalTransferTimesIterator iterator = schedule.getMinimalTransferTimes().iterator();

		while (iterator.hasNext()) {
			iterator.next();

			Id<TransitStopFacility> fromId = iterator.getFromStopId();
			Id<TransitStopFacility> toId = iterator.getToStopId();

			if (globalParentsToChildren.containsKey(fromId) && globalParentsToChildren.containsKey(toId)) {
				for (Id<TransitStopFacility> childFromId : globalParentsToChildren.get(fromId)) {
					for (Id<TransitStopFacility> childToId : globalParentsToChildren.get(toId)) {
						schedule.getMinimalTransferTimes().set(childFromId, childToId, iterator.getSeconds());
					}
				}
			}
		}
	    

	    // 5) Finally, log progress or do any single‑threaded cleanup/validation
	    logger.info("createFacilitiesAndLinkSequences done.");
	}

}
