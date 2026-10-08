# Geometry-aware transit stop matching

This optional mapper mode prevents consecutive stops on one road link from
losing their road candidates. Existing configurations retain endpoint matching.

Add these parameters to `PublicTransitMapping`:

```xml
<param name="inputNetworkGeometryFile" value="detailed_network.csv" />
<param name="splitLinksAtStops" value="true" />
<param name="stopLinkSplittingModes" value="bus" />
<param name="outputPreparedNetworkFile" value="prepared_network.xml.gz" />
<param name="outputNetworkGeometryFile" value="mapped_geometry.csv" />
<param name="stopMatchingReportFile" value="stop_matching_report.csv" />
```

The geometry CSV needs `LinkId` and `Geometry` columns with WKT LineStrings in
the network's coordinate system. Endpoint/direction mismatches fail explicitly.
Missing or degenerate shapes use endpoint geometry and are counted. A geometry
spatial index finds roads near the stop even when their endpoints are far away.
Network mode access and configured candidate distance/count limits still apply.

With splitting enabled, candidate roads are divided at stop projections. Each
stop prefers the incoming fragment ending at its projection, so consecutive
stops receive distinct candidate links. The original link ID stays on the final
fragment. New IDs are `stopMatch:<original-id>:<number>`. Internal nodes are
private to the directed original link: they add no junction turns, U-turns or
crossovers between parallel general/reserved roads. Lengths sum to the original
length; speeds, capacity and lane count are copied without multiplying capacity.
Co-located general/reserved and opposite-direction OSM copies receive matching
physical cuts, while retaining separate internal nodes and their original access.
Single-turn and via-way restrictions are expanded to the fragment sequence.

Artificial fallback remains for genuine unavailable roads or legal routing gaps.
The CSV audit records projection assignments, new-link counts, missing eligible
candidates, geometry fallback counts and sub-metre fragments. Configurations
with `splitLinksAtStops=true` and no geometry CSV use straight endpoint geometry.

Use the final `mapped_geometry.csv` with the final mapped network, not the
original geometry CSV. `prepared_network.xml.gz` records the full correspondence
before mapper cleanup and is needed by lane-aware reconciliation. Splitting does
not itself convert a separate lane file; use the lane-aware mapper integration
when running with lane definitions. Timetable offsets and departures are unchanged.

Regression coverage includes two stops on one curved road, mode access, missing
roads, directed split topology, capacity/length conservation, and single/via-way
turn restrictions. Real Lausanne validation of lines 13 and 16 is also performed
on the lane feature branch; the core implementation has no lane feature dependency.
