"""Regression checks for schedule-derived usage and complete city coverage."""
import json
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from types import SimpleNamespace

from pyproj import Transformer
from shapely.geometry import box

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'main' / 'python'))
from create_lane_webmap import build, transit_data


def route(id, mode, path, departures, stops=('s1', 's2')):
    return (f'<transitRoute id="{id}"><transportMode>{mode}</transportMode><routeProfile>'
            + ''.join(f'<stop refId="{stop}"/>' for stop in stops)
            + '</routeProfile><route>' + ''.join(f'<link refId="{link}"/>' for link in path)
            + '</route><departures>' + ''.join(f'<departure id="{id}_{i}"/>' for i in range(departures))
            + '</departures></transitRoute>')


class TransitDataTest(unittest.TestCase):
    def test_all_bus_routes_count_and_preview_never_bridges_crop_gaps(self):
        with tempfile.TemporaryDirectory() as temporary:
            schedule = Path(temporary) / 'schedule.xml'
            schedule.write_text('<transitSchedule><transitStops>'
                                '<stopFacility id="s1" name="Origin" x="1" y="1"/>'
                                '<stopFacility id="s2" name="Destination" x="2" y="2"/>'
                                '</transitStops><transitLine id="L" name="7">'
                                + route('outside', 'bus', ['far'], 100)
                                + route('busy', 'bus', ['a', 'outside', 'b', 'b'], 8)
                                + route('variant', 'bus', ['a'], 2)
                                + route('not-running', 'bus', ['a'], 0)
                                + '</transitLine><transitLine id="R">'
                                + route('train', 'rail', ['b'], 12) + '</transitLine></transitSchedule>')
            candidates = {'a': {'artificial': False}, 'b': {'artificial': True}}
            result = transit_data(schedule, candidates, {'a', 'b'},
                                  Transformer.from_crs(4326, 4326, always_xy=True), box(0, 0, 10, 10))
            line = next(line for line in result['lines'] if line['id'] == 'L')
            self.assertEqual(line['representative']['routeId'], 'busy')
            self.assertEqual(line['representative']['segments'], [['a'], ['b', 'b']])
            self.assertEqual(line['cityRouteCount'], 2)
            self.assertEqual(candidates['a']['busDepartures'], 10)
            self.assertEqual(candidates['a']['busRoutes'], 2)
            self.assertEqual(candidates['b']['busDepartures'], 8)
            self.assertEqual(candidates['b']['busRoutes'], 1)
            self.assertEqual(line['representative']['origin'], 'Origin')
            self.assertEqual(line['representative']['destination'], 'Destination')

    def test_ties_prefer_longer_stop_pattern_then_stable_id(self):
        with tempfile.TemporaryDirectory() as temporary:
            schedule = Path(temporary) / 'schedule.xml'
            schedule.write_text('<transitSchedule><transitLine id="L">'
                                + route('z', 'bus', ['a'], 5, ('s1', 's2', 's3'))
                                + route('short', 'bus', ['a'], 5)
                                + route('a', 'bus', ['a'], 5, ('s1', 's2', 's3'))
                                + '</transitLine></transitSchedule>')
            result = transit_data(schedule, {'a': {'artificial': False}}, {'a'},
                                  Transformer.from_crs(4326, 4326, always_xy=True), box(0, 0, 10, 10))
            self.assertEqual(result['lines'][0]['representative']['routeId'], 'a')


class CompleteCoverageTest(unittest.TestCase):
    def test_unassigned_and_artificial_links_are_included_with_city_bounds(self):
        with tempfile.TemporaryDirectory() as temporary:
            p = Path(temporary)
            (p / 'network.xml').write_text('<network><nodes>'
                                           '<node id="1" x="2" y="5"/><node id="2" x="8" y="5"/>'
                                           '<node id="3" x="-100" y="6"/><node id="4" x="100" y="6"/>'
                                           '</nodes><links>'
                                           '<link id="road" from="1" to="2" length="6" capacity="1000" freespeed="10" permlanes="1" modes="car,bus">'
                                           '<attributes><attribute name="osm:way:id">1</attribute>'
                                           '<attribute name="osm:way:highway">residential</attribute></attributes></link>'
                                           '<link id="connector" from="3" to="4" length="200" capacity="9999" freespeed="10" permlanes="1" modes="artificial,rail"/>'
                                           '</links></network>')
            (p / 'lanes.xml').write_text('<laneDefinitions/>')
            (p / 'geometry.csv').write_text('LinkId,Geometry\nroad,"LINESTRING (2 5, 8 5)"\n')
            (p / 'report.csv').write_text('category,osmId,linkId,detail\n')
            (p / 'boundary.json').write_text(json.dumps({'type': 'FeatureCollection', 'features': [
                {'type': 'Feature', 'properties': {'bfs': 1}, 'geometry': box(0, 0, 10, 10).__geo_interface__}]}))
            (p / 'template.html').write_text('/*__LEAFLET_JS__*/ /*__LEAFLET_CSS__*/ /*__LANE_DATA__*/')
            (p / 'leaflet.js').write_text('')
            (p / 'leaflet.css').write_text('')
            args = SimpleNamespace(output=p / 'out', boundary=p / 'boundary.json', bfs=1, city='Test', crs='EPSG:4326',
                                  network=p / 'network.xml', lanes=p / 'lanes.xml', geometry=p / 'geometry.csv',
                                  report=p / 'report.csv', template=p / 'template.html', leaflet_js=p / 'leaflet.js',
                                  leaflet_css=p / 'leaflet.css', boundary_source='Test', example_link='road', schedule=None)
            build(args)
            data = json.loads((p / 'out' / 'data.json').read_text())
            links = {link['id']: link for link in data['links']}
            self.assertEqual(set(links), {'road', 'connector'})
            self.assertEqual(data['stats']['linksWithoutLanes'], 2)
            self.assertEqual(data['stats']['artificialLinks'], 1)
            self.assertEqual(links['connector']['bounds'], [6, 0, 6, 10])
            self.assertEqual(links['connector']['geometry'], [[6, -100], [6, 100]])
            self.assertLessEqual(data['bounds'][1][1], 10)
            # Mapper cleanup can remove a road; the reference layer restores
            # its display without claiming it is part of the simulation.
            tree = ET.parse(p / 'network.xml')
            tree.getroot().find('links').remove(tree.getroot().find("links/link[@id='road']"))
            tree.write(p / 'mapped.xml')
            args.reference_network = p / 'network.xml'
            args.network = p / 'mapped.xml'
            build(args)
            data = json.loads((p / 'out' / 'data.json').read_text())
            links = {link['id']: link for link in data['links']}
            self.assertFalse(links['road']['mapped'])
            self.assertTrue(links['connector']['mapped'])
            self.assertEqual(data['stats']['referenceOnlyLinks'], 1)
            self.assertEqual(data['stats']['mappedLinks'], 1)


if __name__ == '__main__':
    unittest.main()
