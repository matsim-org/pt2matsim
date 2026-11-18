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

At a two-branch fork the converters resolve the selected physical lanes together.
They first try complete, node-via OSM connectivity mappings with plain numeric
lane indices, then unambiguous lane-specific destination names or route references.
Both legal branches and all represented incoming lanes must be accounted for.
Shared lane arrows retain their alternatives. The output identifies outgoing links;
it does not encode which lane the driver enters on the receiving road.

If independent arrow matching collapses an ordered left/through or through/right
layout onto one branch, the first segment and the next stretch of outgoing geometry
(up to 30 network length units, metres in a projected metric network) establish
the left-to-right order. This also applies to slight turns and left/right splits.
If the bearings tie or the side order changes, a unique same-class continuation
with a matching road name or route reference can identify the through branch.
Otherwise the matcher retains both plausible branches.
Multiple through-only lanes at a fork with two plausible through branches also
retain both when destination signage cannot distinguish them. Existing access
and signed turn sequences still apply. See `forkLaneAssignments` for the evidence
and outgoing IDs in physical lane order.

At a branching **interior node** of a longer OSM way, way-level turn arrows are
deferred to the downstream end in driving direction. The interior movements use
a complete node-specific connectivity mapping when the approach direction is
unambiguous, or the existing missing-arrow policy including its U-turn handling. These junctions
are listed under `interiorWayArrowsDeferred`.

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

## Checking which links a turn ban disconnects

Before downstream connectivity cleaning, compare two outputs from the normal
converter for the same OSM input and config. Enable signed restriction parsing
in both, and set `useOsmTurnArrows` to false in the first and true in the second.
Use distinct output paths. Run the following from the repository root with
Python, NumPy and SciPy installed:

```sh
python3 src/main/python/audit_turn_reachability.py signed-network.xml.gz arrow-network.xml.gz lost-links.csv
```

The audit compares the largest strongly connected car component of the signed
network with its largest surviving component. It uses directed link states that
remember partial via-way prohibitions. Each newly disconnected physical link is
listed once with an entry or return failure, one boundary turn added by inference,
and a signed-legal chain in driving order. Several different cuts can contribute;
the listed chain is a witness. The two networks must have identical car link IDs
and endpoints. The audit reads the networks without altering them.
