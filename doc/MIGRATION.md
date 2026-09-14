# Source Migration: 2026-09-14

This directory is a copy-based reorganisation of the current SkyTrain Suite source baseline. The original development directories and all servers remain untouched.

| Original relative path | New path |
| --- | --- |
| Releases/Skyworld-2.0.0/src | SkyTrainFolia/src |
| Releases/Skyworld-2.0.0/tests/net | SkyTrainFolia/src/test/java/net |
| Releases/Skyworld-2.0.0/companions/STA-0.3.0/src | STA/src |
| Releases/Skyworld-2.0.0/companions/STCS-0.8.0/src | STCS/src |
| Releases/Skyworld-2.0.0/companions/SkyPCC-0.4.0/src | SkyPCC/src |
| Releases/Skyworld-2.0.0/shared/src | shared/src |
| PCC tests and historical web QA scripts | SkyPCC/tests |
| STCS graph viewer | STCS/tools/railgraph_viewer.py |
| tools/stcs-m2-reference core Python and synthetic tests | STCS/tools/testbench |

No application Java/resources were intentionally changed. Test paths now use the new layout; browser dependencies no longer point at an author's home directory. Browser outputs stay under SkyPCC/target. Build dependencies are supplied as parameters instead of hard-coded local defaults.

The two standalone manuals were retained, with repository-name/layout notes updated. Old release-note collections and historical installation ZIPs were not copied; the original workspace still contains them. The formerly top-level MaSoundSettingsTest.java now sits under its Java package directory. Optional Python real-graph regressions use STCS_TEST_GRAPH rather than the old server path.

Not copied: production graphs/ledgers, local server installations, build outputs, third-party source repositories, Python bytecode, old packaged releases or personal environment data. PCC's two bundled logo PNGs remain runtime resources and require an asset-rights review before public distribution.

The existing META-INF/NOTICE-TrainCarts.txt is retained byte-for-byte, including its reference commit and MIT notice. It remains part of STF resources and is included in the source archive. The suite now separately uses the root MIT LICENSE, with artwork exclusions recorded in ASSET-LICENSE.md. Both root legal files are packaged into all four plugin JARs without replacing the TrainCarts notice.

Runtime versions remain STF 2.1.0-alpha.7, STCS 2.2.0-alpha.1, STA 0.8.0 and PCC 0.8.1. This reorganisation is not a new feature release or a declaration of ATP readiness.
