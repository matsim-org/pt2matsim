package org.matsim.pt2matsim.osm;

import java.util.*;
import java.util.stream.Collectors;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;

/** Existing lanes-converter interpretation of restriction mode aliases and exemptions. */
final class OsmRestrictionModeResolver {
    private final OsmConverterConfigGroup config;
    private static final Set<String> RESTRICTION_VALUES = Set.of("no_left_turn", "no_right_turn", "no_straight_on", "no_u_turn",
            "only_left_turn", "only_right_turn", "only_straight_on", "only_u_turn", "no_entry", "no_exit");
    OsmRestrictionModeResolver(OsmConverterConfigGroup config) { this.config = config; }
    String restrictionForMode(Map<String, String> tags, String mode) {
        return restrictionForMode(tags, mode, new HashSet<>());
    }

    private String restrictionForMode(Map<String, String> tags, String mode, Set<String> visited) {
        if (!visited.add(mode)) throw new IllegalArgumentException("Cyclic derived mode definition involving " + mode);
        List<String> aliases = switch (mode) {
            case "car" -> List.of("motorcar", "motor_vehicle", "vehicle");
            case "taxi" -> List.of("taxi", "psv", "motorcar", "motor_vehicle", "vehicle");
            case "bus", "pt" -> List.of("bus", "psv", "motor_vehicle", "vehicle");
            case "bike" -> List.of("bicycle", "vehicle");
            case "truck" -> List.of("hgv", "motor_vehicle", "vehicle");
            case "motorcycle" -> List.of("motorcycle", "motor_vehicle", "vehicle");
            case "walk" -> List.of("foot");
            default -> List.of(mode);
        };
        Set<String> except = Arrays.stream(tags.getOrDefault("except", "").split(";")).map(String::trim).collect(Collectors.toSet());
        if (except.contains(mode) || aliases.stream().anyMatch(except::contains)) return null;
        checkConditionalMode(tags, mode, mode);
        if (tags.containsKey("restriction:" + mode)) return turnRestrictionValue(tags.get("restriction:" + mode));
        if (aliases.equals(List.of(mode))) {
            Set<String> sources = config.getParameterSets(OsmConverterConfigGroup.RoutableSubnetworkParams.SET_NAME).stream()
                    .map(p -> (OsmConverterConfigGroup.RoutableSubnetworkParams) p).filter(p -> p.subnetworkMode.equals(mode))
                    .flatMap(p -> p.allowedTransportModes.stream()).filter(source -> !source.equals(mode)).collect(Collectors.toSet());
            if (!sources.isEmpty()) {
                Set<String> sourceRules = new HashSet<>();
                for (String source : sources) sourceRules.add(restrictionForMode(tags, source, new HashSet<>(visited)));
                if (sourceRules.size() > 1) throw new IllegalArgumentException("Derived mode " + mode
                        + " combines incompatible source-mode restrictions; specify restriction:" + mode + " explicitly");
                return sourceRules.iterator().next();
            }
        }
        for (String alias : aliases) {
            checkConditionalMode(tags, alias, mode);
            if (tags.containsKey("restriction:" + alias)) return turnRestrictionValue(tags.get("restriction:" + alias));
        }
        if (tags.containsKey("restriction:conditional") && !mode.equals("walk")) {
            throw new IllegalArgumentException("Conditional generic restriction for mode " + mode + " requires a time-aware model");
        }
        String generic = tags.get("restriction");
        return mode.equals("walk") || generic == null ? null : turnRestrictionValue(generic);
    }

    private static void checkConditionalMode(Map<String, String> tags, String alias, String mode) {
        if (tags.containsKey("restriction:" + alias + ":conditional") || tags.containsKey(alias + ":conditional")) {
            throw new IllegalArgumentException("Conditional rule for " + alias + " affects mode " + mode + "; requires a time-aware model");
        }
    }

    private static String turnRestrictionValue(String value) {
        if (Set.of("give_way", "stop", "none").contains(value)) return null;
        if (!RESTRICTION_VALUES.contains(value)) throw new IllegalArgumentException("Unsupported mode-specific restriction value " + value);
        return value;
    }

}
