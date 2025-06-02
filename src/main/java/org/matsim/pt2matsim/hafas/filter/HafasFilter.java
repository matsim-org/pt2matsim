package org.matsim.pt2matsim.hafas.filter;

import java.util.Set;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public interface HafasFilter {

    boolean keepRoute(FPLANRoute route);

    void writeFilterStats(String outputDir);

}
