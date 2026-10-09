package org.matsim.pt2matsim.osm;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/** Conservative motor-lane counts from OSM lane arrays, without lane-file dependencies. */
final class OsmLaneCountInference {
    record Result(int count, String evidence) { }
    private static final List<String> KEYS = List.of("turn:lanes", "vehicle:lanes", "motor_vehicle:lanes",
            "motorcar:lanes", "hgv:lanes", "bus:lanes", "psv:lanes", "taxi:lanes", "bicycle:lanes", "cycleway:lanes");

    static Result infer(Map<String,String> tags, boolean forward, boolean oneway) {
        String direction = forward ? ":forward" : ":backward";
        boolean travelDirection = forward != "-1".equals(tags.get("oneway"));
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (String key : KEYS) {
            String actual = key + direction;
            String value = tags.get(actual);
            if (value == null && oneway && travelDirection) { actual = key; value = tags.get(key); }
            // Single numerical bus:lanes values count reserved lanes, not total lanes.
            if (value == null || !value.contains("|")) continue;
            int slots = value.split("\\|", -1).length, motor = slots;
            for (int i = 0; i < slots; i++) {
                String broad = slot(tags,"motor_vehicle:lanes",direction,oneway && travelDirection,slots,i);
                if (broad.isEmpty()) broad = slot(tags,"vehicle:lanes",direction,oneway && travelDirection,slots,i);
                boolean exception = false;
                for (String mode : List.of("motorcar", "hgv", "bus", "psv", "taxi"))
                    if (Set.of("yes","designated","only","exclusive").contains(
                            slot(tags,mode+":lanes",direction,oneway && travelDirection,slots,i))) exception = true;
                if ("no".equals(broad) && !exception) motor--;
            }
            counts.put(actual,motor);
        }
        if (counts.isEmpty()) return null;
        Set<Integer> unique = Set.copyOf(counts.values());
        return new Result(unique.size()==1 && unique.iterator().next()>0 ? unique.iterator().next() : 0,counts.toString());
    }

    private static String slot(Map<String,String> tags,String key,String direction,boolean generic,int count,int index) {
        String value = tags.get(key+direction);
        if (value==null && generic) value=tags.get(key);
        if (value==null) return "";
        String[] columns=value.split("\\|",-1);
        return columns.length==count ? columns[index].trim() : "";
    }
}
