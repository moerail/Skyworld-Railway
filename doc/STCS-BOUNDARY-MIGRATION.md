# STCS 2.2.0-alpha.2: line boundaries

ORIGIN sends mileage from zero towards its fourth-line left/right side.
END receives mileage from its fourth-line left/right side and explicitly
terminates line attribution. Both directions are relative to the sign face.
Neither marker terminates physical connectivity or, by itself, movement authority.
The final ordinary balise is not automatically promoted to END.

## Existing servers

Back up the STCS data folder before upgrading. Replace only the STCS JAR while
the server is stopped. No production graph is modified by the source build.

Re-edit and submit affected ORIGIN/END signs to register their direction again.
In particular, old records named `left` or `right` with a zero direction were
created by the shared physical-scan/registration bug. Rebuild alone cannot recover
their direction. Existing END signs must also be reviewed: their fourth line now
points towards the incoming line, not away from it. Do not blindly negate saved
vectors: on curves the two track endpoints need not be opposite.

Registration retains the marker UUID when the same sign is edited. Avoid breaking
and replacing signs unnecessarily. Finish with a graph rebuild once the relevant
track chunks are available. Retained graphs are not evidence of successful repair.

## Acceptance checks

- ORIGIN/END have nonzero directions and generated names, not `left`/`right` names.
- The last balise-to-END edge has confirmed line attribution and endpoint mileage.
- Both physical directions remain available outside each boundary.
- A named balise beyond END does not pull the line attribution across the boundary.
- For depot:C, both ORIGIN-to-balise and balise-to-END have continuous mileage.
- Reloading the rebuilt graph preserves attribution; no automatic ATP is added.
