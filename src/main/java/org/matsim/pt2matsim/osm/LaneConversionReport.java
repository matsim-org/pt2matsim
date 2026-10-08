package org.matsim.pt2matsim.osm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Machine-readable counts plus individually traceable lane conversion decisions. */
public final class LaneConversionReport {
    public record Entry(String category, String osmId, String linkId, String detail) { }
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Long> counts = new TreeMap<>();

    public LaneConversionReport() {
        for (String key : List.of("inferredLaneMovements", "linksWithInferredConnections", "ambiguousTurnMatches",
                "inferredReservedLanePositions", "laneTagCountMismatches", "deadEndLinks", "unservedLegalMovements",
                "linksWithLanes", "assumedLaneLengths", "restrictionRelationsApplied", "linksRemovedByCleaning",
                "missingFromWays", "relationsWithoutRepresentedApproach", "unavailableProhibitedPathMembers",
                "restrictionsWithoutRepresentedProhibitedPath", "partiallyRepresentedRestrictionRelations",
                "missingDestinationMembers", "relationsSkippedMissingDestination", "inferredRestrictionJunctions",
                "relationsSkippedUnresolvedJunction", "priorityRulesNotSimulated", "relationsSkippedUnsupported",
                "ignoredNonNetworkRelations", "conflictingTurnIndications", "unusableNetworkLinksRemoved",
                "restrictionModeApplications", "restrictionModesSkippedUnsupported", "restrictionRelationsPartiallyApplied",
                "conditionalTagsNotSimulated", "unsupportedRestrictionTags", "normalizedRestrictionTags",
                "inferredRestrictionCardinalities", "nonMotorLaneSlotsExcluded", "motorLaneLayoutsResolved",
                "laneAccessTagCountMismatches", "inferredUTurnMovementsExcluded", "inferredUTurnModeBans",
                "linksWithInferredUTurnExclusions", "uTurnExplicitArrowExceptions", "uTurnTurningFacilityExceptions",
                "uTurnOnlyLegalExitExceptions", "uTurnNonMotorExceptions", "uTurnOnlyExitLaneConnectionsRestored",
                "uTurnModeExemptionExceptions", "directionalLaneCountsDerived",
                "directionalLaneCountInferenceRejected", "reservedLanePositionsFromAccess",
                "reservedLanePositionsFromCounts")) counts.put(key, 0L);
    }

    void count(String category) { counts.merge(category, 1L, Long::sum); }

    public void add(String category, Object osmId, Object linkId, String detail) {
        count(category);
        entries.add(new Entry(category, String.valueOf(osmId), String.valueOf(linkId), detail));
    }

    public Map<String, Long> getCounts() { return Collections.unmodifiableMap(counts); }
    public List<Entry> getEntries() { return List.copyOf(entries); }

    public void recordError(Throwable failure) {
        if (!counts.containsKey("conversionErrors")) add("conversionErrors", "", "",
                failure.getMessage() == null ? failure.toString() : failure.getMessage());
    }

    public void write(Path file) throws IOException {
        try (var writer = Files.newBufferedWriter(file)) {
            writer.write("category,osmId,linkId,detail\n");
            for (var count : counts.entrySet()) {
                writer.write(csv("COUNT") + ",," + csv(count.getKey()) + "," + count.getValue() + "\n");
            }
            for (Entry e : entries) {
                writer.write(csv(e.category()) + "," + csv(e.osmId()) + "," + csv(e.linkId()) + "," + csv(e.detail()) + "\n");
            }
        }
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}
