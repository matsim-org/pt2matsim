package org.matsim.pt2matsim.osm.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.pt2matsim.osm.LaneConversionReport;

/** Reports unavailable approaches and detects unresolved restrictions on retained approaches. */
public final class OsmLaneData extends OsmDataImpl {
    private final LaneConversionReport report = new LaneConversionReport();
    public OsmLaneData(AllowedTagsFilter... filters) { super(filters); }
    public LaneConversionReport getReport() { return report; }

    @Override public void buildMap() {
        List<OsmFileReader.ParsedRelation> checked = new ArrayList<>();
        if (parsedRelations != null) for (var relation : parsedRelations.values()) {
            String type = relation.tags.get("type");
            if ("restriction".equals(type) || "connectivity".equals(type) || type != null && type.startsWith("restriction:")) checked.add(relation);
        }
        // Country extracts contain tens of millions of nodes belonging to ways
        // excluded by the highway/railway filter. Release those before building
        // way/node associations, instead of retaining them until network conversion.
        LongOpenHashSet referenced = new LongOpenHashSet();
        if (parsedWays != null) for (var way : parsedWays.values()) {
            for (long node : way.nodes) referenced.add(node);
        }
        if (parsedRelations != null) for (var relation : parsedRelations.values()) {
            for (var member : relation.members) if (member.type == Osm.ElementType.NODE) referenced.add(member.refId);
        }
        int initialNodes = nodes.size();
        nodes.entrySet().removeIf(e -> !referenced.contains(Long.parseLong(e.getKey().toString())));
        LogManager.getLogger(OsmLaneData.class).info("Discarded {} nodes not referenced by retained ways or relations before building the OSM map", initialNodes - nodes.size());
        referenced.clear();
        super.buildMap();
        for (var relation : checked) {
            var fromWays = relation.members.stream().filter(m -> m.type == Osm.ElementType.WAY && "from".equals(m.role)).toList();
            for (var from : fromWays) if (!ways.containsKey(Id.create(from.refId, Osm.Way.class))) {
                report.add("missingFromWays", relation.id, "", "Incoming way " + from.refId + " is absent, filtered out, or incomplete");
            }
            if (fromWays.stream().noneMatch(m -> ways.containsKey(Id.create(m.refId, Osm.Way.class)))) {
                report.add("relationsWithoutRepresentedApproach", relation.id, "", "Skipped " + relation.tags.get("type")
                        + ": " + (fromWays.isEmpty() ? "no incoming way member" : "no represented incoming way")
                        + "; do not invent an approach or a prohibited movement");
                discardRelation(relation.id);
                continue;
            }
            if (relation.members.stream().noneMatch(m -> m.type == Osm.ElementType.WAY && "to".equals(m.role))) {
                report.add("relationsSkippedMissingDestination", relation.id, "", "Skipped " + relation.tags
                        + ": no destination way member; other represented exits are not constrained by this relation");
                discardRelation(relation.id);
                continue;
            }
            var missing = relation.members.stream().filter(m -> List.of("via", "to").contains(m.role))
                    .filter(m -> !isPresent(m)).toList();
            var values = relation.tags.entrySet().stream()
                    .filter(e -> e.getKey().equals("restriction") || e.getKey().startsWith("restriction:"))
                    .map(java.util.Map.Entry::getValue).toList();
            // Preserve surviving no_exit targets, but ignore other relations with
            // unavailable destinations under the extract-boundary fallback policy.
            boolean prohibitory = "restriction".equals(relation.tags.get("type")) && !values.isEmpty()
                    && values.stream().allMatch(Set.of("no_left_turn", "no_right_turn", "no_straight_on", "no_u_turn", "no_entry", "no_exit")::contains)
                    && relation.tags.keySet().stream().noneMatch(k -> k.contains("conditional")
                            || Set.of("day_on", "day_off", "hour_on", "hour_off").contains(k));
            var missingDestinations = missing.stream().filter(m -> "to".equals(m.role)).toList();
            for (var member : missingDestinations) report.add("missingDestinationMembers", relation.id, "",
                    "Destination " + member.type + " " + member.refId + " is absent, filtered out, or incomplete");
            if (!missingDestinations.isEmpty() && !prohibitory) {
                report.add("relationsSkippedMissingDestination", relation.id, "", "Skipped " + relation.tags
                        + ": unavailable destination; other represented exits are not constrained by this relation");
                discardRelation(relation.id);
                continue;
            }
            if (!missing.isEmpty() && prohibitory) {
                for (var member : missing) report.add("unavailableProhibitedPathMembers", relation.id, "",
                        "Missing " + member.role + " " + member.type + " " + member.refId + "; absent, filtered out, or incomplete; border location not verified");
                boolean survivingTarget = relation.members.stream().anyMatch(m -> "to".equals(m.role) && isPresent(m));
                if (missing.stream().anyMatch(m -> "via".equals(m.role)) || !survivingTarget) {
                    if (!missingDestinations.isEmpty()) report.add("relationsSkippedMissingDestination", relation.id, "",
                            "Skipped " + values + ": unavailable destination");
                    report.add("restrictionsWithoutRepresentedProhibitedPath", relation.id, "",
                            "Skipped " + values + ": prohibited path is unavailable in the represented network");
                    discardRelation(relation.id);
                    continue;
                }
                report.add("partiallyRepresentedRestrictionRelations", relation.id, "",
                        "Retained restriction on represented targets; missing targets cannot be driven");
            }
            if (missing.stream().anyMatch(m -> "via".equals(m.role))) {
                report.add("relationsSkippedUnresolvedJunction", relation.id, "", "Unavailable via members: "
                        + missing.stream().filter(m -> "via".equals(m.role)).map(m -> m.type + " " + m.refId).toList()
                        + "; ignored restriction " + relation.tags);
                discardRelation(relation.id);
                continue;
            }
            for (var member : relation.members) {
                if (!List.of("from", "via", "to").contains(member.role)) continue;
                // Multi-from no_entry relations still constrain their surviving approaches.
                if ("from".equals(member.role) && member.type == Osm.ElementType.WAY) continue;
                if (prohibitory && "to".equals(member.role) && !isPresent(member)) continue;
                if (!isPresent(member)) {
                    report.add("relationsSkippedUnsupported", relation.id, "", "stage=reading relation; missing " + member.role
                            + " " + member.type + " " + member.refId + "; ignored relation " + relation.tags);
                    discardRelation(relation.id);
                    break;
                }
            }
        }
    }

    private boolean isPresent(OsmFileReader.ParsedRelationMember member) {
        return switch (member.type) {
            case NODE -> nodes.containsKey(Id.create(member.refId, Osm.Node.class));
            case WAY -> ways.containsKey(Id.create(member.refId, Osm.Way.class));
            case RELATION -> false;
        };
    }

    private void discardRelation(long relationId) {
        Id<Osm.Relation> id = Id.create(relationId, Osm.Relation.class);
        removeRelation(id);
        relations.remove(id);
    }
}
