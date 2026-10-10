package org.matsim.pt2matsim.tools;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PTMapperStopOrderTest {
    @Test void closerReferencesCannotReverseTwoConsecutiveStops() {
        Fixture f = fixture(0, 4, new int[]{0,1,2,4}, new double[][]{{-15,0},{10,8},{8,0},{28,10}});
        assertTrue(ordered(f.route));
        assertTrue(PTMapperTools.pullChildStopFacilitiesTogether(f.schedule, f.network) > 0);
        assertTrue(ordered(f.route), "Old pulling logic moves the middle stops to L2 then L1");
        assertNotEquals(List.of("L2", "L1"), f.route.getStops().subList(1,3).stream().map(s -> s.getStopFacility().getLinkId().toString()).toList());
        // Also stays valid through the mapper's fixed-point passes.
        for (int i=0;i<10;i++) {
            int changed=PTMapperTools.pullChildStopFacilitiesTogether(f.schedule,f.network);
            assertTrue(ordered(f.route));
            if(changed==0) return;
        }
        fail("Pulling should converge");
    }

    @Test void legitimateInteriorImprovementStillWorks() {
        Fixture f = fixture(0,4,new int[]{0,1,4},new double[][]{{-15,0},{10,5},{28,10}});
        assertTrue(PTMapperTools.pullChildStopFacilitiesTogether(f.schedule,f.network)>0);
        assertEquals("L2",f.route.getStops().get(1).getStopFacility().getLinkId().toString());
        assertTrue(ordered(f.route));
    }

    @Test void endpointImprovementsExtendThePathAndKeepOrder() {
        Fixture f = fixture(1,3,new int[]{1,2,3},new double[][]{{-15,0},{10,5},{28,10}});
        PTMapperTools.pullChildStopFacilitiesTogether(f.schedule,f.network);
        assertEquals("L0",f.route.getRoute().getStartLinkId().toString());
        assertEquals("L4",f.route.getRoute().getEndLinkId().toString());
        assertTrue(ordered(f.route));
    }

    private record Fixture(Network network,TransitSchedule schedule,TransitRoute route) {}
    private static Fixture fixture(int first,int last,int[] stopLinks,double[][] stopCoords) {
        Network n=NetworkUtils.createNetwork();
        double[][] xy={{-20,0},{0,0},{10,0},{10,10},{20,10},{30,10}};
        List<Node> nodes=new ArrayList<>();List<Id<Link>> ids=new ArrayList<>();
        for(int i=0;i<xy.length;i++) {
            Node node=n.getFactory().createNode(Id.createNodeId("N"+i),new Coord(xy[i][0],xy[i][1]));n.addNode(node);nodes.add(node);
        }
        for(int i=0;i+1<xy.length;i++) {
            Link l=n.getFactory().createLink(Id.createLinkId("L"+i),nodes.get(i),nodes.get(i+1));
            l.setLength(10);l.setFreespeed(10);l.setCapacity(1000);l.setNumberOfLanes(1);l.setAllowedModes(Set.of("bus"));n.addLink(l);ids.add(l.getId());
        }
        TransitSchedule s=ScheduleTools.createSchedule();var factory=s.getFactory();List<TransitRouteStop> stops=new ArrayList<>();
        for(int i=0;i<stopLinks.length;i++) {
            String parent="stop"+i;Id<TransitStopFacility> id=ScheduleTools.createChildStopFacilityId(Id.create(parent,TransitStopFacility.class),ids.get(stopLinks[i]));
            TransitStopFacility stop=factory.createTransitStopFacility(id,new Coord(stopCoords[i][0],stopCoords[i][1]),false);
            stop.setLinkId(ids.get(stopLinks[i]));stop.setName(parent);s.addStopFacility(stop);stops.add(factory.createTransitRouteStop(stop,i*60,i*60));
        }
        TransitRoute r=factory.createTransitRoute(Id.create("route",TransitRoute.class),RouteUtils.createNetworkRoute(ids.subList(first,last+1)),stops,"bus");
        TransitLine line=factory.createTransitLine(Id.create("line",TransitLine.class));line.addRoute(r);s.addTransitLine(line);return new Fixture(n,s,r);
    }
    private static boolean ordered(TransitRoute route) {
        var path=ScheduleTools.getTransitRouteLinkIds(route);int cursor=0;
        for(var stop:route.getStops()) {
            while(cursor<path.size()&&!path.get(cursor).equals(stop.getStopFacility().getLinkId()))cursor++;
            if(cursor==path.size())return false;
        }
        return true;
    }
}
