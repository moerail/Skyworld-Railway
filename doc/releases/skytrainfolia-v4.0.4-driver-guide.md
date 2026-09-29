# SkyTrainFolia 4.0.4 — Kuri-chan driver guide

Stationary riders of manual trains receive short, clickable hints from **库莉酱 / クリちゃん / Kuri-chan** in the language selected with `/st lang zh|en|fr|jp`:

1. Claim an unoccupied cab with `/st drive`.
2. After claiming, set a neutral reverser with `/st forward` or `/st backward`.
3. In SB, request an MA with `/stcs ma demand` only when STCS reports a fresh idle or released authority state. Shadow mode describes the request as observational and explicitly says ATP does not protect it.
4. After a Trip and a complete stop, acknowledge with `/stcs ma ack`. This enters PT; the next hint asks the driver to release the old MA with `/stcs ma release` before requesting another.

Hints never claim control, set the reverser, request an MA, acknowledge a Trip or release an MA automatically. They do not appear for automatic trains, moving trains, passengers when another driver controls the cab, or stale/pending MA service. A repeated hint waits 60 seconds by default. Configure `settings.driver-guide-enabled` and `settings.driver-guide-repeat-seconds` in SkyTrainFolia's `config.yml`.

Replace only the SkyTrainFolia JAR while the test server is stopped. Existing train data, language preferences, STA, STCS, and SkyPCC are unaffected. Acceptance: board a stopped manual train, claim, set the reverser, check fresh SB/MA guidance, then verify stopped TR and PT prompts. Check each language and confirm that clicking a hint executes only its displayed command.
