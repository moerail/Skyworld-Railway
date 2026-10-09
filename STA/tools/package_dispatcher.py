"""Build a single Python zipapp for administrators; requires Python 3.11+ to run."""
from __future__ import annotations

import shutil
import tempfile
import zipapp
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "dist" / "SkyRail-Dispatcher-2.1.1.pyz"
SOURCES = (
    ROOT / "STA" / "tools" / "sta_dispatcher.py",
    ROOT / "STA" / "tools" / "sta_remote.py",
    ROOT / "STA" / "tools" / "dispatcher_view.py",
    ROOT / "STCS" / "tools" / "testbench" / "railgraph_simulation.py",
    ROOT / "STCS" / "tools" / "testbench" / "railgraph_geometry.py",
    ROOT / "STCS" / "tools" / "testbench" / "solver.py",
    ROOT / "LICENSE",
)


def main():
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT / "target") as temporary:
        stage = Path(temporary)
        for source in SOURCES:
            shutil.copy2(source, stage / source.name)
        (stage / "__main__.py").write_text(
            "from sta_dispatcher import main\nmain()\n", encoding="utf-8"
        )
        zipapp.create_archive(stage, DESTINATION, compressed=True)
    print(DESTINATION)


if __name__ == "__main__":
    main()
