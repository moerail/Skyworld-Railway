"""Small Tk rendering smoke test; requires a graphical Python installation."""
from __future__ import annotations

import tkinter as tk
import time
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from dispatcher_profile import ConnectionProfile
from sta_dispatcher import Desk, THEMES


class DispatcherGuiTest(unittest.TestCase):
    def test_map_layers_and_train_sidebar(self):
        root = tk.Tk()
        try:
            profile = ConnectionProfile("rail.example", 8767, "ab" * 32, "operator1")
            desk = Desk(root, profile)
            self.assertEqual(("rail.example", "8767", "ab" * 32, "operator1", ""),
                             (desk.host.get(), desk.port.get(), desk.fingerprint.get(),
                              desk.admin.get(), desk.token.get()))
            root.update()
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
                            "quality": "VALID", "lengthMeters": 6, "direction": "forward",
                            "integrity": {"state": "LOST", "reason": "GAP_EXCEEDED",
                                          "brakeHeld": True, "affectedMembers": ["cart-2"],
                                          "observedAtMillis": int(time.time() * 1000)}}]
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
            self.assertIn("GAP_EXCEEDED", desk.train_detail.get())
            self.assertIn("401", {desk.canvas.itemcget(item, "text") for item in items
                                  if desk.canvas.type(item) == "text"})
            self.assertEqual(["Blue"], list(desk.line_rows.values()))
            self.assertTrue(all(desk.canvas.itemcget(item, "fill") == THEMES["day"]["rail"]
                                for item in desk.canvas.find_withtag("rail")))
            desk.line_list.selection_set("line-0")
            with patch("sta_dispatcher.colorchooser.askcolor", return_value=((18, 52, 86), "#123456")):
                desk.choose_line_color()
            self.assertEqual("#123456", desk.canvas.itemcget(desk.canvas.find_withtag("rail")[0], "fill"))
            self.assertIn(THEMES["day"]["occupied"],
                          {desk.canvas.itemcget(item, "fill") for item in desk.canvas.find_all()})
            desk.reset_line_color()
            self.assertEqual({}, desk.line_colors)
            desk.font_size.set(18)
            desk.change_font_size()
            self.assertEqual(18, desk.map_font_size)
            self.assertTrue(desk.canvas.find_withtag("map-label"))
            for index, a in enumerate(desk.label_boxes):
                for b in desk.label_boxes[index + 1:]:
                    self.assertFalse(a[0] < b[2] and a[2] > b[0] and a[1] < b[3] and a[3] > b[1])
            desk.font_size.set("invalid")
            desk.change_font_size()
            self.assertEqual(18, desk.font_size.get())
            event = {"session": "s1", "sequence": 1, "emittedAtMillis": 1700000000000,
                     "type": "MA_UNAVAILABLE", "reason": "SERVICE_UNAVAILABLE", "trainName": "test-train"}
            desk.railway_history = {"events": [event, event]}
            desk.render_railway_events()
            text = desk.railway_log.get("1.0", "end")
            self.assertEqual(1, text.count("test-train"))
            self.assertIn("2023-", text)
            self.assertIn("MA 不可用", text)
            desk.warnings_only.set(True)
            desk.railway_history = {"events": [event, dict(event, sequence=2, type="DRIVER_ACQUIRED", trainName="normal-train")]}
            desk.render_railway_events()
            self.assertNotIn("normal-train", desk.railway_log.get("1.0", "end"))
            desk.railway_history = {"session": "s2", "events": []}
            desk.render_railway_events()
            self.assertNotIn("test-train", desk.railway_log.get("1.0", "end"))
            desk.write_log("connection-test")
            self.assertRegex(desk.log.get("1.0", "end"), r"\[\d{2}:\d{2}:\d{2}\] connection-test")
            with patch("sta_dispatcher.messagebox.askyesno", return_value=False):
                desk.revoke_ma()
            self.assertIsNone(desk.revoke_request)
            with patch("sta_dispatcher.messagebox.askyesno", return_value=True):
                desk.revoke_ma()
            operation, fields = desk.commands.get_nowait()
            self.assertEqual("ma.revoke", operation)
            self.assertEqual(train_id, fields["trainId"])
            self.assertIn("requestId", fields)
            desk.events.put(("reply", ("ma.revoke", {"status": "APPLIED", "reason": "REVOKED_TR"})))
            desk.drain_events()
            self.assertIsNone(desk.revoke_request)
        finally:
            root.destroy()


if __name__ == "__main__":
    unittest.main()
