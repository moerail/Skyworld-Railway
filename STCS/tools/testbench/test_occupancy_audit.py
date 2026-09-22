import copy
import unittest

from occupancy_audit import audit, records, resource_edges
from railgraph_simulation import Graph
from railgraph_testbench import demo_graph


class AuditTest(unittest.TestCase):
    def test_owner_and_read_only(self):
        graph = Graph(demo_graph())
        train = "00000000-0000-0000-0000-000000000001"
        document = {"version": 2, "trains": {train: {"name": "archived", "graphRevision": 1,
                    "resources": ["cell@demo:80:64:0"], "positions": []}}}
        before = copy.deepcopy(document)
        result = audit(graph, document, ["A", "B"])
        self.assertIn(train, result)
        self.assertIn("matched resources: 1", result)
        self.assertIn("clears ALL", result)
        self.assertEqual(document, before)
        self.assertNotIn(train, audit(graph, document, ["P", "Q"]))
        self.assertIn("A:east|B:west", resource_edges(graph))

    def test_unknown_and_legacy(self):
        train = "00000000-0000-0000-0000-000000000001"
        self.assertIn("UNMAPPED", audit(Graph(demo_graph()), {train: ["old-edge"]}))
        with self.assertRaises(ValueError):
            records({"version": 3, "trains": {}})
        with self.assertRaises(ValueError):
            records({"records": []})
        with self.assertRaises(ValueError):
            audit(Graph(demo_graph()), {}, ["missing", "B"])


if __name__ == "__main__":
    unittest.main()
