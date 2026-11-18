"""Compare car reachability in matched signed-only and arrow-enabled networks before cleaning."""
import argparse
from array import array
from collections import defaultdict, deque
import csv
import gzip
import json
from pathlib import Path
import xml.etree.ElementTree as ET

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import breadth_first_order, connected_components


def read_network(path):
    links, bans = {}, set()
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rb") as stream:
        # Clear completed elements from their parent, including the entire node section.
        stack = []
        for event, elem in ET.iterparse(stream, events=("start", "end")):
            if event == "start":
                stack.append(elem)
                continue
            stack.pop()
            if elem.tag not in ("link", "node"):
                continue
            if elem.tag == "link" and "car" in elem.get("modes", "").split(","):
                key = elem.attrib["id"]
                links[key] = (elem.attrib["from"], elem.attrib["to"])
                value = elem.findtext("attributes/attribute[@name='disallowedNextLinks']")
                if value:
                    bans.update((key, *seq) for seq in json.loads(value).get("car", []) if seq)
            stack[-1].remove(elem)
    return links, bans


def turn_graphs(links, signed, inferred):
    # One state per link plus prefix states for longer signed prohibitions. Keeping
    # the longest matching suffix handles overlapping via-way rules in both graphs.
    ids = list(links)
    indices = {key: i for i, key in enumerate(ids)}
    prefixes = {}
    for seq in signed:
        for n in range(2, len(seq)):
            prefixes.setdefault(seq[:n], len(ids) + len(prefixes))
    histories = list(prefixes)
    physical = np.array(list(range(len(ids))) + [indices[p[-1]] for p in histories])
    outgoing = defaultdict(list)
    for key, (source, _) in links.items():
        outgoing[source].append(key)
    starts = {p[0] for p in signed}
    rows, cols, cuts = array("q"), array("q"), array("q")
    for state, idx in enumerate(physical):
        key = ids[idx]
        history = (key,) if state < len(ids) else histories[state - len(ids)]
        for dest in outgoing.get(links[key][1], ()):
            target = indices[dest]
            if len(history) > 1 or key in starts:
                extended = (*history, dest)
                suffixes = [extended[i:] for i in range(len(extended) - 1)]
                if any(p in signed for p in suffixes):
                    continue
                target = next((prefixes[p] for p in suffixes if p in prefixes), target)
            if (key, dest) in inferred:
                cuts.append(len(rows))
            rows.append(state)
            cols.append(target)
    row, col = np.frombuffer(rows, dtype=np.int64), np.frombuffer(cols, dtype=np.int64)
    shape = (len(physical), len(physical))
    values = np.ones(len(row), dtype=np.int8)
    before = csr_matrix((values, (row, col)), shape=shape)
    values[np.frombuffer(cuts, dtype=np.int64)] = 0
    after = csr_matrix((values, (row, col)), shape=shape)
    after.eliminate_zeros()
    return ids, physical, before, after


def largest_component(graph, physical, mask):
    _, labels = connected_components(graph, directed=True, connection="strong")
    # A physical link can have several histories in a component. Count it once.
    width = int(physical.max()) + 1
    members = np.unique(labels[mask].astype(np.int64) * width + physical[mask])
    return mask & (labels == np.bincount(members // width).argmax())


def lost_paths(before, after, component, anchor):
    reachable = np.zeros(len(component), dtype=bool)
    reachable[breadth_first_order(after, anchor, directed=True, return_predecessors=False)] = True
    missing = component & ~reachable
    # Start at every boundary turn newly lost. Each reported chain is one witness.
    parent = {}
    queue = deque()
    if not missing.any():
        return parent
    for start in np.flatnonzero(component & reachable):
        for dest in before.indices[before.indptr[start]:before.indptr[start + 1]]:
            if missing[dest] and dest not in parent:
                parent[dest] = start
                queue.append(dest)
    while queue:
        state = queue.popleft()
        for dest in before.indices[before.indptr[state]:before.indptr[state + 1]]:
            if missing[dest] and dest not in parent:
                parent[dest] = state
                queue.append(dest)
    return parent


def audit(links, signed, with_arrows):
    if not signed <= with_arrows:
        raise ValueError("The inferred network must preserve all signed car restrictions.")
    inferred = with_arrows - signed
    if any(len(p) != 2 for p in inferred):
        raise ValueError("Expected only immediate inferred turns to differ.")
    if not links or not inferred:
        return
    ids, physical, before, after = turn_graphs(links, signed, inferred)
    component = largest_component(before, physical, np.ones(len(physical), dtype=bool))
    retained = largest_component(after, physical, component)
    lost = set(physical[component]) - set(physical[retained])
    anchor = int(np.flatnonzero(retained)[0])
    for direction in ("entry", "return"):
        if not lost:
            break
        g, h = (before, after) if direction == "entry" else (before.T.tocsr(), after.T.tocsr())
        parents = lost_paths(g, h, component, anchor)
        for state in parents:
            idx = physical[state]
            if idx not in lost:
                continue
            chain = [state]
            while chain[-1] in parents:
                chain.append(parents[chain[-1]])
            path = [ids[physical[s]] for s in reversed(chain)]
            turn = path[:2]
            if direction == "return":
                path.reverse()
                turn.reverse()
            yield ids[idx], direction, *turn, json.dumps(path)
            lost.remove(idx)
    if lost:
        raise RuntimeError("Could not trace every lost link to a changed movement")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("signed", type=Path)
    parser.add_argument("inferred", type=Path)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    links, signed = read_network(args.signed)
    other, with_arrows = read_network(args.inferred)
    if links != other:
        raise ValueError("Car link IDs, directions and topology must match before comparing restrictions.")
    del other
    with args.report.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(("lost_link", "lost_direction", "inferred_from", "inferred_to", "signed_legal_chain"))
        writer.writerows(audit(links, signed, with_arrows))


if __name__ == "__main__":
    main()
