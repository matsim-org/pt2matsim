package org.matsim.pt2matsim.hafas.filter;

import java.util.Set;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public interface HafasFilter {

    Set<String> getIdsFilteredIn();

    Set<String> getIdsFilteredOut();

    boolean keepRoute(FPLANRoute route);

}
