# Optional additional turn information in the normal OSM converter

In the `osmConverter` config module:

```xml
<param name="parseTurnRestrictions" value="true" />
<param name="useOsmTurnArrows" value="true" />
<param name="outputTurnRestrictionsReportFile" value="turn-decisions.csv" />
```

`useOsmTurnArrows` defaults to `false`, preserving existing conversion behavior.
The report path is optional. No detailed lane-assignment file is generated.

When enabled, the converter uses the movement calculation extracted from
`OsmNetworkWithLanesConverter`: directional `turn:lanes` arrays, physical motor
lane positions (including reserved bus slots), geometry-based turn matching,
and audited fallbacks when arrows cannot be resolved. Outgoing movements not
served by any inferred motor lane are added to `DisallowedNextLinks`.

The option also enables the existing missing-arrow U-turn policy: prohibit an
immediate return on the same OSM segment for motor modes with alternative legal
exits. Retain explicit reverse arrows, turning facilities, signed mode exemptions,
nonmotor movements, and U-turns that are a mode's only legal exit. Existing signed
restrictions are never removed.

This shares the arrow and inference policy, not the lanes converter's enhanced
OSM restriction-relation parser. Scalar `turn=*`, mode-specific arrow tags,
conditional arrows and exact receiving-lane connections are outside this feature.
Inferred movements depend on incomplete OSM information; inspect the CSV counts
and individual decisions when validating a network. Unusable links are audited
and skipped by this optional stage, leaving their existing restrictions unchanged.
