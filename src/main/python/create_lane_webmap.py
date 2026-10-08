"""Build a self-contained city lane inspector from MATSim files (pyproj + shapely)."""
import argparse
import base64
import csv
import gzip
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.parsers import expat

from pyproj import Transformer
from shapely import wkt
from shapely.errors import GEOSException
from shapely.geometry import LineString, Point, shape, mapping
from shapely.ops import transform
from shapely.prepared import prep


def parse_xml(file, start, end=None, text=None):
    parser = expat.ParserCreate()
    parser.StartElementHandler = start
    if end:
        parser.EndElementHandler = end
    if text:
        parser.CharacterDataHandler = text
    opener = gzip.open if str(file).endswith('.gz') else open
    with opener(file, 'rb') as stream:
        parser.ParseFile(stream)


def transit_data(schedule, candidates, selected, projection, city):
    """Collect actual bus usage and one busiest city-serving route per line.

    Preserve contiguous city path segments: never join across links omitted by
    the city crop. Usage counts include every bus route, not just the previews.
    """
    if not schedule:
        return {'lines': [], 'stops': {}, 'selectionRule': 'most departures among routes serving this city'}
    stops, lines, used_stops = {}, [], set()
    opener = gzip.open if str(schedule).endswith('.gz') else open
    with opener(schedule, 'rb') as stream:
        stack = []
        for event, element in ET.iterparse(stream, events=('start', 'end')):
            if event == 'start':
                stack.append(element)
                continue
            if element.tag == 'stopFacility':
                x, y = float(element.get('x')), float(element.get('y'))
                lon, lat = projection.transform(x, y)
                stops[element.get('id')] = {'id': element.get('id'), 'name': element.get('name') or element.get('id'),
                                           'position': [round(lat, 7), round(lon, 7)], 'xy': (x, y)}
            elif element.tag == 'transitLine':
                routes = element.findall('transitRoute')
                serving, best, best_score, total_departures = [], None, None, 0
                for route in routes:
                    departures = len(route.findall('departures/departure'))
                    total_departures += departures
                    path = [link.get('refId') for link in route.findall('route/link')]
                    local_ids = set(path) & selected
                    if not local_ids or not departures:
                        continue
                    mode = route.findtext('transportMode') or 'other'
                    serving.append((mode, departures))
                    if mode == 'bus':
                        for link_id in local_ids:
                            link = candidates[link_id]
                            link['busRoutes'] = link.get('busRoutes', 0) + 1
                            link['busDepartures'] = link.get('busDepartures', 0) + departures
                    stop_ids = [stop.get('refId') for stop in route.findall('routeProfile/stop')]
                    artificial_count = sum(candidates[link_id]['artificial'] for link_id in local_ids)
                    score = (departures, len(stop_ids), -artificial_count)
                    # Stable route-ID tie break avoids depending on writer order.
                    if best is not None and (score < best_score or (score == best_score and route.get('id') >= best['routeId'])):
                        continue
                    segments, segment = [], []
                    for link_id in path:
                        if link_id in selected:
                            segment.append(link_id)
                        elif segment:
                            segments.append(segment); segment = []
                    if segment:
                        segments.append(segment)
                    local_stops = [stop_id for stop_id in stop_ids if stop_id in stops
                                   and city.covers(Point(stops[stop_id]['xy']))]
                    best = {'routeId': route.get('id'), 'mode': mode, 'departures': departures,
                            'segments': segments, 'stops': local_stops, 'stopCount': len(stop_ids),
                            'origin': stops.get(stop_ids[0], {}).get('name', '') if stop_ids else '',
                            'destination': stops.get(stop_ids[-1], {}).get('name', '') if stop_ids else '',
                            'totalLinkCount': len(path), 'cityLinkCount': sum(map(len, segments)),
                            'artificialCityLinks': artificial_count}
                    best_score = score
                if best is not None:
                    lines.append({'id': element.get('id'), 'name': element.get('name') or element.get('id'),
                                  'modes': sorted({mode for mode, _ in serving}), 'routeCount': len(routes),
                                  'cityRouteCount': len(serving), 'totalDepartures': total_departures,
                                  'cityDepartures': sum(count for _, count in serving), 'representative': best})
                    used_stops.update(best['stops'])
            if element.tag in ('stopFacility', 'transitLine'):
                if len(stack) > 1:
                    stack[-2].remove(element)
                element.clear()
            stack.pop()
    lines.sort(key=lambda line: (line['name'].casefold(), line['id']))
    return {'lines': lines, 'stops': {id: {key: value for key, value in stops[id].items() if key != 'xy'}
                                    for id in sorted(used_stops)},
            'selectionRule': 'most departures among city-serving routes; ties: most stops, fewer artificial city links, route ID',
            'schedule': str(schedule)}


def build(args):
    destination = Path(args.output)
    destination.mkdir(parents=True, exist_ok=True)
    boundary_source = json.loads(Path(args.boundary).read_text())
    features = boundary_source['features']
    assert len(features) == 1 and int(features[0]['properties']['bfs']) == args.bfs
    city = shape(features[0]['geometry']).buffer(0)
    prepared_city = prep(city)
    x0, y0, x1, y1 = city.bounds
    projection = Transformer.from_crs(args.crs, 'EPSG:4326', always_xy=True)
    nodes, candidates = {}, {}
    current = None
    source_is_mapped = True
    attribute = None
    chunks = []

    def network_start(name, attrs):
        nonlocal current, attribute, chunks
        if name == 'node':
            nodes[attrs['id']] = (float(attrs['x']), float(attrs['y']))
        elif name == 'link':
            a, b = nodes[attrs['from']], nodes[attrs['to']]
            if max(a[0], b[0]) < x0-1200 or min(a[0], b[0]) > x1+1200 or max(a[1], b[1]) < y0-1200 or min(a[1], b[1]) > y1+1200:
                current = None
            else:
                current = {'id': attrs['id'], 'from': attrs['from'], 'to': attrs['to'], 'modes': attrs['modes'].split(','),
                           'length': float(attrs['length']), 'capacity': float(attrs['capacity']), 'freespeed': float(attrs['freespeed']),
                           'lanesCount': float(attrs['permlanes']), 'attrs': {}, 'mapped': source_is_mapped}
        elif name == 'attribute' and current is not None:
            attribute = attrs['name']
            chunks = []

    def network_end(name):
        nonlocal current, attribute
        if name == 'attribute' and current is not None and attribute is not None:
            current['attrs'][attribute] = ''.join(chunks)
            attribute = None
        elif name == 'link' and current is not None:
            attrs = current.pop('attrs')
            current.update(way=attrs.get('osm:way:id', ''), name=attrs.get('osm:way:name', ''), highway=attrs.get('osm:way:highway', ''),
                           rules=json.loads(attrs.get('disallowedNextLinks', '{}')), lanes=[],
                           artificial='artificial' in current['modes'])
            candidates[current['id']] = current
            current = None

    def network_text(text):
        if attribute is not None:
            chunks.append(text)

    reference_network = getattr(args, 'reference_network', None)
    if reference_network:
        source_is_mapped = False
        parse_xml(reference_network, network_start, network_end, network_text)
    source_is_mapped = True
    parse_xml(args.network, network_start, network_end, network_text)
    print('Nearby network links:', len(candidates), flush=True)
    geometries = {}
    with open(args.geometry) as stream:
        for row in csv.DictReader(stream):
            if row['LinkId'] in candidates:
                try:
                    geometries[row['LinkId']] = wkt.loads(row['Geometry'])
                except GEOSException:
                    # Loop links can have a single-point WKT from the exporter.
                    candidates[row['LinkId']]['geometryFallback'] = True
    selected = set()
    for id, link in candidates.items():
        geometry = geometries.get(id, LineString([nodes[link['from']], nodes[link['to']]]))
        link['geometrySource'] = 'detailed' if id in geometries else 'endpoints'
        spatial_geometry = geometry if geometry.length else Point(geometry.coords[0])
        link['visible'] = prepared_city.intersects(spatial_geometry)
        if link['visible']:
            selected.add(id)
            # A long artificial rail connector can cross the city while both
            # endpoints lie far outside it. Index/fit its city part only.
            link['cityXYBounds'] = spatial_geometry.intersection(city).bounds
        link['xy'] = list(geometry.coords)
    print('Network links intersecting ' + args.city + ':', len(selected), flush=True)
    transit = transit_data(getattr(args, 'schedule', None), candidates, selected, projection, city)
    print('City-serving transit lines:', len(transit['lines']), flush=True)
    assignment = None
    lane = None
    lane_attr = None
    lane_chunks = []
    reference_lanes_pass = False

    def lanes_start(name, attrs):
        nonlocal assignment, lane, lane_attr, lane_chunks
        if name == 'lanesToLinkAssignment':
            assignment = candidates.get(attrs['linkIdRef']) if attrs['linkIdRef'] in selected else None
            if assignment is not None and reference_lanes_pass and assignment['mapped']:
                assignment = None
        elif name == 'lane' and assignment is not None:
            lane = {'id': attrs['id'], 'to': [], 'inferred': False, 'notes': []}
        elif lane is not None:
            if name == 'toLink': lane['to'].append(attrs['refId'])
            elif name == 'representedLanes': lane['represented'] = float(attrs['number'])
            elif name == 'capacity': lane['capacity'] = float(attrs['vehiclesPerHour'])
            elif name == 'startsAt': lane['start'] = float(attrs['meterFromLinkEnd'])
            elif name == 'alignment': lane_attr = 'alignment'; lane_chunks = []
            elif name == 'attribute' and attrs.get('name') in ('osmLaneIndex', 'osmMotorLaneIndex'):
                lane_attr = 'osmIndex' if attrs['name'] == 'osmLaneIndex' else 'motorIndex'
                lane_chunks = []

    def lanes_end(name):
        nonlocal assignment, lane, lane_attr
        if name in ('attribute', 'alignment') and lane is not None and lane_attr is not None:
            lane[lane_attr] = int(''.join(lane_chunks)); lane_attr = None
        elif name == 'lane' and lane is not None:
            if lane['to']:
                lane['index'] = lane.get('motorIndex', lane.get('osmIndex', 1))
                assignment['lanes'].append(lane)
            lane = None
        elif name == 'lanesToLinkAssignment': assignment = None

    def lanes_text(text):
        if lane_attr is not None: lane_chunks.append(text)

    parse_xml(args.lanes, lanes_start, lanes_end, lanes_text)
    reference_lanes = getattr(args, 'reference_lanes', None)
    if reference_lanes:
        reference_lanes_pass = True
        parse_xml(reference_lanes, lanes_start, lanes_end, lanes_text)
    mapping_report = getattr(args, 'mapping_report', None)
    for report_path in filter(None, (args.report, mapping_report)):
        with open(report_path) as stream:
            for row in csv.DictReader(stream):
                id, category = row['linkId'], row['category']
                if id not in selected or category == 'COUNT': continue
                link = candidates[id]
                match = re.search(r'Lane (\d+)', row['detail'])
                exact_lane = re.search(r'lane=([^;]+)', row['detail'])
                for item in link['lanes']:
                    if match and int(match[1]) != item.get('osmIndex'): continue
                    if exact_lane and exact_lane[1] != item['id']: continue
                    if category in ('inferredLaneMovements', 'inferredReservedLanePositions', 'laneTagCountMismatches', 'ambiguousTurnMatches', 'inferredUTurnMovementsExcluded', 'uTurnOnlyExitLaneConnectionsRestored', 'inferredConnectorLaneConnections'):
                        item['inferred'] = True
                    if category in ('inferredLaneMovements', 'inferredReservedLanePositions', 'laneTagCountMismatches', 'ambiguousTurnMatches', 'conflictingTurnIndications', 'motorLaneLayoutsResolved', 'nonMotorLaneSlotsExcluded', 'reservedLanePositionsFromAccess', 'reservedLanePositionsFromCounts', 'laneAccessTagCountMismatches', 'inferredUTurnMovementsExcluded', 'uTurnExplicitArrowExceptions', 'uTurnModeExemptionExceptions', 'uTurnTurningFacilityExceptions', 'uTurnOnlyLegalExitExceptions', 'uTurnOnlyExitLaneConnectionsRestored', 'inferredConnectorLaneConnections'):
                        note = category + ': ' + row['detail']
                        if note not in item['notes']: item['notes'].append(note)
    needed = selected | {target for id in selected for lane in candidates[id]['lanes'] for target in lane['to']}
    missing = needed - set(candidates)
    assert not missing, f'Missing road context for lane destinations: {sorted(missing)[:20]}'
    links = [candidates[id] for id in sorted(needed)]
    groups = {}
    for link in links:
        key = (link['way'] or link['id'], link['from'], link['to'])
        groups.setdefault(key, []).append(link)
    for link in links:
        group = groups[link['way'] or link['id'], link['from'], link['to']]
        link['groupTotal'] = max([lane.get('index', 1) for road in group for lane in road['lanes']] + [round(sum(road['lanesCount'] for road in group))])
        link['opposite'] = (link['way'] or link['id'], link['to'], link['from']) in groups
        xs, ys = zip(*link.pop('xy'))
        lon, lat = projection.transform(xs, ys)
        link['geometry'] = [[round(a, 7), round(b, 7)] for a, b in zip(lat, lon)]
        if link['visible']:
            bx0, by0, bx1, by1 = link.pop('cityXYBounds')
            blon, blat = projection.transform([bx0, bx0, bx1, bx1], [by0, by1, by0, by1])
            link['bounds'] = [min(blat), min(blon), max(blat), max(blon)]
        else:
            link['bounds'] = [min(lat), min(lon), max(lat), max(lon)]
    boundary = mapping(transform(projection.transform, city.simplify(2, preserve_topology=True)))
    lat0, lon0, lat1, lon1 = min(l['bounds'][0] for l in links if l['visible']), min(l['bounds'][1] for l in links if l['visible']), max(l['bounds'][2] for l in links if l['visible']), max(l['bounds'][3] for l in links if l['visible'])
    visible_links = [link for link in links if link['visible']]
    center = [sum(link['geometry'][len(link['geometry']) // 2][i] for link in visible_links) / len(visible_links) for i in (0, 1)]
    examples = [link for link in visible_links if link['length'] >= 40 and 'car' in link['modes']
                and len(link['lanes']) > 1 and all(not lane['inferred'] for lane in link['lanes'])
                and len({target for lane in link['lanes'] for target in lane['to']}) > 1] or visible_links
    example = min(examples, key=lambda link: sum((link['geometry'][len(link['geometry']) // 2][i] - center[i]) ** 2 for i in (0, 1)))
    example_id = args.example_link or example['id']
    assert example_id in selected, 'Example link must belong to the selected city'
    data = {'city': args.city, 'bfs': args.bfs, 'exampleLink': example_id,
            'links': links, 'transit': transit, 'boundary': boundary, 'bounds': [[lat0, lon0], [lat1, lon1]],
            'stats': {'roads': len(selected), 'assignedLinks': sum(bool(candidates[id]['lanes']) for id in selected),
                      'detailedGeometryLinks': sum(candidates[id]['geometrySource'] == 'detailed' for id in selected),
                      'geometryFallbackLinks': sum(candidates[id]['geometrySource'] != 'detailed' for id in selected),
                      'lanes': sum(len(candidates[id]['lanes']) for id in selected), 'contextLinks': len(links)-len(selected),
                      'roadLinks': sum(bool(candidates[id]['highway']) for id in selected),
                      'mappedLinks': sum(candidates[id]['mapped'] for id in selected),
                      'referenceOnlyLinks': sum(not candidates[id]['mapped'] for id in selected),
                      'artificialLinks': sum(candidates[id]['artificial'] for id in selected),
                      'linksWithoutLanes': sum(not candidates[id]['lanes'] for id in selected),
                      'busUsedLinks': sum(bool(candidates[id].get('busRoutes')) for id in selected),
                      'busUsedArtificialLinks': sum(bool(candidates[id].get('busRoutes')) and candidates[id]['artificial'] for id in selected),
                      'transitLines': len(transit['lines'])},
            'source': {'network': str(args.network), 'lanes': str(args.lanes), 'boundary': args.boundary_source,
                       'referenceNetwork': str(reference_network) if reference_network else None,
                       'referenceLanes': str(reference_lanes) if reference_lanes else None,
                       'mappingReport': str(mapping_report) if mapping_report else None,
                       'schedule': str(args.schedule) if getattr(args, 'schedule', None) else None}}
    template = Path(args.template).read_text()
    template = template.replace('/*__LEAFLET_JS__*/', Path(args.leaflet_js).read_text())
    template = template.replace('/*__LEAFLET_CSS__*/', Path(args.leaflet_css).read_text())
    packed = base64.b64encode(gzip.compress(json.dumps(data, separators=(',', ':'), ensure_ascii=False).encode(), mtime=0)).decode()
    template = template.replace('/*__LANE_DATA__*/', json.dumps(packed))
    (destination / 'index.html').write_text(template)
    (destination / 'data.json').write_text(json.dumps(data, separators=(',', ':'), ensure_ascii=False))
    (destination / 'build-summary.json').write_text(json.dumps({'stats': data['stats'], 'bytes': (destination/'index.html').stat().st_size, 'sources': data['source']}, indent=2))
    print(json.dumps(data['stats']), flush=True)
    print(destination / 'index.html', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for argument in ('network', 'lanes', 'geometry', 'report', 'boundary', 'template', 'leaflet-js', 'leaflet-css', 'output'):
        parser.add_argument('--'+argument, required=True)
    parser.add_argument('--crs', default='EPSG:2056')
    parser.add_argument('--city', default='Zürich')
    parser.add_argument('--bfs', type=int, default=261)
    parser.add_argument('--example-link')
    parser.add_argument('--schedule', help='Mapped transit schedule for bus usage and representative line previews')
    parser.add_argument('--reference-network', help='Original network; retain cleaned-away links as labelled display references')
    parser.add_argument('--reference-lanes', help='Original lanes for reference-only links absent from the mapped network')
    parser.add_argument('--mapping-report', help='Lane reconciliation audit, including inferred artificial-connector exits')
    parser.add_argument('--boundary-source', default='Kanton Zürich, Gemeindegrenzen WFS, BFS 261')
    build(parser.parse_args())
