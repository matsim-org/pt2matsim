# Local OSM way corrections

The normal `Osm2MultimodalNetwork` converter accepts explicit `wayTagOverride`
parameter sets inside its `OsmConverter` module. Corrections apply to parsed OSM
tags before lane counts, link modes, turn restrictions and network cleaning are
calculated. They do not edit the source OSM file. Without these parameter sets,
conversion behavior is unchanged. The feature does not require a lanes XML file.

For the Geneva modelling correction discussed for Rue du Rhône:

```xml
<parameterset type="wayTagOverride">
  <param name="wayId" value="1354965562" />
  <param name="key" value="lanes" />
  <param name="expectedValue" value="1" />
  <param name="value" value="2" />
  <param name="reason" value="Model one general lane plus the existing reserved lane to preserve car access to Place du Rhone; physical lane count assumed." />
</parameterset>
```

This models one general lane plus the existing `lanes:psv=1` reserved lane. It is
an explicit modelling assumption, not a survey of the physical lane count.
If the road instead has one shared lane, remove the incorrect reservation tag:
use `key=lanes:psv`, `expectedValue=1`, `remove=true`, and omit `value`.
Do not apply both alternatives.

Every correction logs the way ID, tag, old value, new value and reason. A summary
reports configured, applied, unchanged and missing corrections. Missing ways are
logged and skipped, so the same configuration can be used on a smaller extract.
Duplicate corrections for the same way/tag are rejected. An `expectedValue`
mismatch aborts before applying any corrections to prevent silently reusing a
stale correction after OSM changes.

Overrides correct way tags, not relation members or relation tags. Normal routing
continues to use network connectivity and parsed OSM turn-restriction relations;
`turn:lanes` arrows alone do not create restrictions in the normal converter.
