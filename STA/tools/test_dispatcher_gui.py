"""Small Tk rendering smoke test; requires a graphical Python installation."""
from __future__ import annotations

import tkinter as tk
import time
import unittest
from types import SimpleNamespace

from sta_dispatcher import Desk, THEMES


class DispatcherGuiTest(unittest.TestCase):
    def test_map_layers_and_train_sidebar(self):
        root = tk.Tk()
        root.withdraw()
        try:
            desk = Desk(root)
            root.update_idletasks()
            train_id = "train-1"
            edge_id = "edge-1"
            edge = SimpleNamespace(id=edge_id, source="switch-1", target="end-1", resource="rail-1",
                                   length=10.0, points=[(0, 0, 64, 0), (10, 10, 64, 0)],
                                   point=lambda offset: (offset, 64, 0))
            desk.graph = SimpleNamespace(
                data={"revision": 77, "lineAssignments": {edge_id: {"line": "Blue", "status": "CONFIRMED"}}},
                nodes={"switch-1": {"rail": {"world": "world", "x": 0, "y": 64, "z": 0},
                                    "type": "switch", "name": "401", "state": "diverging",
                                    "ports": {"straight": "east", "diverging": "south"}},
                       "end-1": {"rail": {"world": "world", "x": 10, "y": 64, "z": 0},
                                 "type": "end", "name": "E"}},
                edges={edge_id: edge}, unresolved=set())
            desk.world.set("world")
            desk.connected = True
            desk.trains = [{"trainId": train_id, "name": "test", "world": "world", "edgeId": edge_id,
                            "edgeOffsetMeters": 4, "speedMetersPerSecond": 0, "graphRevision": 77,
                            "quality": "VALID", "lengthMeters": 6, "direction": "forward"}]
            desk.shadow = {"status": "SHADOW", "graphRevision": 77, "simulationOnly": True,
                           "executable": False, "sections": [{"edgeId": edge_id, "state": "OCCUPIED",
                                                            "fromMeters": 2, "toMeters": 6,
                                                            "occupants": [train_id], "reservations": []}]}
            desk.shadow_at = time.monotonic()
            desk.show_trains()
            desk.choose_train(train_id)
            for theme in ("night", "day"):
                desk.theme.set(theme)
                desk.draw()
                items = desk.canvas.find_all()
                self.assertGreater(len(items), 15)
                colors = {desk.canvas.itemcget(item, "fill") for item in items}
                self.assertIn(THEMES[theme]["occupied"], colors)
                self.assertIn(THEMES[theme]["switch_diverging"], colors)
            self.assertEqual(1, len(desk.train_hits))
            self.assertIn("test", desk.train_detail.get())
            self.assertIn("401", {desk.canvas.itemcget(item, "text") for item in items
                                  if desk.canvas.type(item) == "text"})
        finally:
            root.destroy()


if __name__ == "__main__":
    unittest.main()
