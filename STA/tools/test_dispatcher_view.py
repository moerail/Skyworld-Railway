"""Presentation checks for the desktop dispatcher map."""
from __future__ import annotations

import time
import unittest
from types import SimpleNamespace

from dispatcher_view import FACES, assigned_line, current_shadow, edge_unit, face_arrow


class DispatcherViewTest(unittest.TestCase):
    def test_confirmed_line_only_gets_main_line_style(self):
        graph = SimpleNamespace(data={"lineAssignments": {
            "main": {"line": "Blue", "status": "CONFIRMED"},
            "siding": {"line": "Blue", "status": "UNASSIGNED"},
        }})
        self.assertEqual("Blue", assigned_line(graph, "main"))
        self.assertEqual("", assigned_line(graph, "siding"))

    def test_all_switch_faces_have_directions(self):
        self.assertEqual(16, len(FACES))
        self.assertEqual("↗", face_arrow("north_north_east"))
        self.assertEqual("↗", face_arrow("east_north_east"))
        self.assertEqual("←", face_arrow("west"))
        self.assertEqual("?", face_arrow(None))

    def test_train_arrow_uses_edge_geometry(self):
        edge = SimpleNamespace(length=10.0, point=lambda distance: (10 - distance, 70, 4))
        ux, uz = edge_unit(edge, 5)
        self.assertAlmostEqual(-1.0, ux)
        self.assertAlmostEqual(0.0, uz)

    def test_occupancy_rejects_stale_or_wrong_graph(self):
        now = time.monotonic()
        snapshot = {"status": "SHADOW", "graphRevision": 77,
                    "simulationOnly": True, "executable": False}
        self.assertTrue(current_shadow(snapshot, 77, now, now))
        self.assertFalse(current_shadow(snapshot, 78, now, now))
        self.assertFalse(current_shadow(snapshot, 77, now - 3, now))
        self.assertFalse(current_shadow({**snapshot, "executable": True}, 77, now, now))


if __name__ == "__main__":
    unittest.main()
