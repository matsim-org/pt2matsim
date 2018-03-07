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

package org.matsim.pt2matsim.osm.lib;

import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.MapUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public class AllowedTagsFilter {

	private static final String MATCH_ALL = "*";
	private static final Set<String> MATCH_ALL_SET = CollectionUtils.stringToSet(MATCH_ALL);
	private final Map<Osm.ElementType, Map<String, Set<String>>> keyValuePairs = new HashMap<>();
	private final Map<Osm.ElementType, Map<String, Set<String>>> keyValueExceptions = new HashMap<>();

	/**
	 * @return <code>true</code> if at least one of the given tags matches any one of the specified filter-tags.
	 * Also returns true if no tags have been defined for the given element type.
	 */
	public boolean matches(Osm.Element element) {
		Map<String, Set<String>> checkPairs = keyValuePairs.get(element.getType());
		Map<String, Set<String>> checkExceptions = keyValueExceptions.get(element.getType());
		Map<String, String> tags = element.getTags();

		if(checkExceptions != null) {
			// check for exceptions
			for(Map.Entry<String, Set<String>> e : checkExceptions.entrySet()) {
				if(tags.containsKey(e.getKey()) && (e.getValue().contains(tags.get(e.getKey())) || e.getValue().contains(MATCH_ALL))) {
					return false;
				}
			}
		}
		if(checkPairs != null) {
			// check for positive list
			for(Map.Entry<String, Set<String>> e : checkPairs.entrySet()) {
				if(tags.containsKey(e.getKey()) && (e.getValue().contains(tags.get(e.getKey())) || e.getValue().contains(MATCH_ALL))) {
					return true;
				}
			}
		}

		// return true if no tags have been defined for the element type
		return (checkPairs == null && checkExceptions == null);
	}

	/**
	 * @param elementType osm element type (node/way/relation)
	 * @param key         tag name
	 * @param value       <code>null</code> if all values should be taken
	 */
	public void add(Osm.ElementType elementType, final String key, final String value) {
		Map<String, Set<String>> map = MapUtils.getMap(elementType, keyValuePairs);
		if(value == null) {
			map.put(key, MATCH_ALL_SET);
		} else {
			MapUtils.getSet(key, map).add(value);
		}
	}

	/**
	 * Adds an exception to the filter, that is if this key and value appears,
	 * the filter will return false. Use <tt>null</tt> for all values.
	 */
	public void addException(Osm.ElementType elementType, final String key, final String value) {
		Map<String, Set<String>> map = MapUtils.getMap(elementType, keyValueExceptions);
		if(value == null) {
			map.put(key, MATCH_ALL_SET);
		} else {
			MapUtils.getSet(key, map).add(value);
		}
	}

	/**
	 * Adds the tags and exceptions from otherFilter into this filter
	 */
	public void mergeFilter(AllowedTagsFilter otherFilter) {
		for(Osm.ElementType t : otherFilter.keyValuePairs.keySet()) {
			Map<String, Set<String>> tmpMap = MapUtils.getMap(t, keyValuePairs);
			for(Map.Entry<String, Set<String>> pair : otherFilter.keyValuePairs.get(t).entrySet()) {
				if(tmpMap.containsKey(pair.getKey())) {
					tmpMap.get(pair.getKey()).addAll(pair.getValue());
				} else {
					tmpMap.put(pair.getKey(), pair.getValue());
				}
			}
		}
		for(Osm.ElementType t : otherFilter.keyValueExceptions.keySet()) {
			Map<String, Set<String>> tmpMap = MapUtils.getMap(t, keyValueExceptions);
			for(Map.Entry<String, Set<String>> pair : otherFilter.keyValueExceptions.get(t).entrySet()) {
				if(tmpMap.containsKey(pair.getKey())) {
					tmpMap.get(pair.getKey()).addAll(pair.getValue());
				} else {
					tmpMap.put(pair.getKey(), pair.getValue());
				}
			}
		}
	}
}
