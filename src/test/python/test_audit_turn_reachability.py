import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "main/python"))
from audit_turn_reachability import audit


class TestTurnReachability(unittest.TestCase):
    links = dict(a=("0", "1"), b=("1", "2"), c=("2", "3"), d=("3", "4"), h=("4", "0"),
                 x=("0", "0"), e=("1", "X"), f=("X", "Y"), g=("Y", "2"))

    def test_upstream_and_return_cuts_across_a_whole_chain(self):
        signed = {("x", "a", "e")}
        for cut, direction in [(("a", "e"), "entry"), (("g", "c"), "return")]:
            with self.subTest(cut=cut):
                rows = list(audit(self.links, signed, signed | {cut}))
                self.assertEqual({r[0] for r in rows}, {"e", "f", "g"})
                for key, loss, a, b, chain in rows:
                    self.assertEqual((a, b), cut)
                    self.assertEqual(loss, direction)
                    path = json.loads(chain)
                    self.assertIn(key, path)
                    self.assertIn(cut, list(zip(path, path[1:])))
                    self.assertFalse(any(tuple(path[i:i+3]) in signed for i in range(len(path))))

    def test_preexisting_signed_disconnect_is_not_attributed_to_arrows(self):
        baseline = {("h", "a", "e"), ("x", "a", "e")}
        self.assertEqual(list(audit(self.links, baseline, baseline | {("a", "e")})), [])

    def test_rejects_network_that_reopens_signed_turn(self):
        with self.assertRaises(ValueError):
            list(audit(self.links, {("a", "e")}, set()))
