# SkyRail Suite — Uitgebreide Nederlandse handleiding

## **[2026-11-24] Toegevoegd / hersteld: PCC 2.4.1 kaartweergave**

~~De standaard focusrand van de browser rond geselecteerde sporen en objecten.~~ De markering volgt nu de echte spoorlijn of een kleine ring rond het object, met een vaste lijndikte op het scherm. Inzoomen vergroot de rand niet; muisselectie en toetsenbordfocus blijven zichtbaar.

Naast A− / A+ staan **H− / H+** voor de markeringsgrootte (50%–250%). Klik op het percentage om100% te herstellen; kies een kleur via het kleurvak. Grootte en kleur worden in deze browser bewaard; bediening blijft mogelijk zonder opslag.

Onder infrastructuur staan **Balise, END, ORIGIN en Wissel**. Het hoofdvak schakelt alles in of uit; de onderliggende vakken zijn afzonderlijk instelbaar. Een gedeeltelijke selectie geeft een gemengde hoofdstatus. Symbolen, labels en wisselrichtingspijlen worden samen gefilterd. Spoorbasis en SAL-laag blijven onafhankelijk.

Dit verandert alleen de weergave, niet RailGraph, objecten, routes, MA of bezetting. Vernieuw een oude pagina met Ctrl+F5.


## **[NIEUW / GEWIJZIGD · 2026-11-24] Eén PCC-kaart en lijnbewerking**

Eén RailGraph met afzonderlijk aanklikbare infrastructuur-, SAL-, exploitatie- en livelagen. Open de editor met het bestaande wisseltoken, kies een lijn of maak een concept met minstens één geregistreerd depot.

Eerst depot registreren. Klik bakens, wissels, gerichte graafranden en stations van deze lijn; wijzig volgorde, wachttijd en keren zonder borden te herschrijven. Opslaan, controleren en publiceren zijn aparte stappen. Lopende taken behouden hun eigen gepubliceerde versie.

Gelijktijdige spel/PCC-bewerking bevriest beide kanten, ook na herstart. Opslaan/controleren/publiceren/verwijderen wordt geweigerd tot handmatige oplossing. Een voorstel kiezen publiceert niets. Sluit spelbewerking met `/sal route close <lijn>`; succesvolle publicatie sluit de spelsessie.

`/sal route create Line1 maintown` · `/sal route depot Line1 maintown,otherdepot` · `/sal route resolve Line1 DISCARD`

[Eén PCC-kaart en lijnbewerking](doc/PCC-ROUTE-EDITING.en.md)


[中文详细手册](README.zh.md) · [Detailed English manual](README.en.md) · [Manuel détaillé français](README.fr.md) · [Uitgebreide Nederlandse handleiding](README.nl.md) · [日本語詳細マニュアル](README.ja.md) · [Подробное руководство на русском](README.ru.md) · [文語體鐵道運轉詳細規程](README.ja.classical.md)

Distributie van2026-11-24, gebaseerd op de vorige uitgebreide Manuel en de definitieve implementatie. Ongewijzigde uitleg blijft staan. Vetgedrukte labels markeren toevoegingen, wijzigingen, verwijderingen en opvraagfuncties; doorgehaalde tekst bewaart vervallen instructies.

## Lees eerst de incompatibele wijzigingen

- De oude STF-uitvoeringsketen voor station/cruise/start/stop/spawn/destroy/property en bordcontrole is verwijderd, zonder herinschakeloptie. Oude borden en bestanden blijven bestaan maar sturen geen automatische ritten meer.
- ~~`/st property demo mode auto` / `mode automatic`~~ is verwijderd. SAL beheert automatisch rijden; STF behoudt handbediening, fysieke uitvoering en infrastructuur.
- ~~Alleen `/st release` geeft toestemming voor automatische overname~~: eerst SAL-overdracht bevestigen op een depotplaats, daarna dezelfde bestuurderslease expliciet vrijgeven.
- ~~Standaard schaduw-MA, geen ATP-ingreep~~: nieuwe sessies vragen standaard FS aan met uitvoerbare beveiliging. SAL gebruikt dezelfde bescherming. Zonder geldige MA blijven remmen vast; schaduwmodus blijft alleen expliciet testen.
- ~~`scan.only-loaded-chunks: false` laadt chunks bij het opbouwen~~: onderhoud scant alleen geladen gebieden en migreert deze waarde naar true. Ontladen bewijst geen breuk of vrijgave.
- Werk de vijf plugins samen bij. Bewaar werelden, samenstellingen, RailGraph, SAL-status, vernietigingsbewijzen en alle bezettingsregisters. Herstel bij terugdraaien één gezamenlijke back-up.

**[NIEUW · 2026-11-21]** Opgeleverd: SAL-ritten en depotbewegingen, handmatig/automatisch overdragen, incrementele RailGraph, uitvoerbare MA/ATP-weergave en afzonderlijke PCC-exploitatiekaart. SAL heeft blauw `[SkyATO-Lite]`, STF grijs en STCS geel. Oude standaardprefixen migreren; eigen prefixen blijven. De versiepagina toont SAL en de juiste ATP-status.

| Bestand / component | Versie | Taak |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | Getypeerde contracten, telemetrie en bedieningsdiensten |
| SkyTrainFolia / STF | **5.0.2** | Samenstellingen, fysica, chunktickets, handbediening, TIMS, ATP en uitvoering |
| STCS | **4.3.5** | Infrastructuur, grafiek, lokalisatie, bezetting, reservering, MA en rijwegarbitrage |
| SkyATO-Lite / SAL | **0.5.0** | Exploitatieplannen, oproepen, haltes, passagierstitels, depot en overdracht; geen directe entiteitsbediening |
| SkyPCC / PCC | **2.4.1** | Webkaarten, depotlijsten en gecontroleerde verkeersleiding |

## Inhoud

1. [Doel, componenten en eindresultaat](#section-1)
2. [Installatie, migratie en terugdraaien](#section-2)
3. [Rechten en talen](#section-3)
4. [De eerste handmatige trein](#section-4)
5. [Commando-overzicht](#section-5)
6. [Voertuigprofielen en eigenschappen](#section-6)
7. [Lijnen en wissels bouwen](#section-7)
8. [SAL-lijnen, borden en bestuursoverdracht](#section-8)
9. [Uitvoerbare MA, standaard FS en ATP](#section-9)
10. [SkyPCC-infrastructuur- en exploitatiekaart](#section-10)
11. [Geluiden en HMI](#section-11)
12. [STA-contracten en HTTP](#section-12)
13. [Opslag en probleemoplossing](#section-13)
14. [Acceptatie en beperkingen](#section-14)
15. [Ontwikkeling, broncode en distributie](#section-15)

<a id="section-1"></a>

## 1. Doel, componenten en eindresultaat

**[GEWIJZIGD · 2026-11-21]** Een SAL-exploitatielijn is een geordend rit-/halteplan. Een STCS-infrastructuurlijn bevat toewijzing en kilometrering. SAL gebruikt RailGraph; STCS bepaalt rijweg/MA en laat STA/STF fysiek uitvoeren. Dit is spel-ATP, geen gecertificeerd ETCS-product.

| Bestand / component | Versie | Taak |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | Getypeerde contracten, telemetrie en bedieningsdiensten |
| SkyTrainFolia / STF | **5.0.2** | Samenstellingen, fysica, chunktickets, handbediening, TIMS, ATP en uitvoering |
| STCS | **4.3.5** | Infrastructuur, grafiek, lokalisatie, bezetting, reservering, MA en rijwegarbitrage |
| SkyATO-Lite / SAL | **0.5.0** | Exploitatieplannen, oproepen, haltes, passagierstitels, depot en overdracht; geen directe entiteitsbediening |
| SkyPCC / PCC | **2.4.1** | Webkaarten, depotlijsten en gecontroleerde verkeersleiding |

**[NIEUW · 2026-11-21]** Opgeleverd: SAL-ritten en depotbewegingen, handmatig/automatisch overdragen, incrementele RailGraph, uitvoerbare MA/ATP-weergave en afzonderlijke PCC-exploitatiekaart. SAL heeft blauw `[SkyATO-Lite]`, STF grijs en STCS geel. Oude standaardprefixen migreren; eigen prefixen blijven. De versiepagina toont SAL en de juiste ATP-status.

**[GEWIJZIGD · 2026-11-21]** `<lijn>:up|down`-voorkeursrichting, volledige enkelsporige sectiereservering, algemeen zoeken met meerdere tussentijdse keerbewegingen en de Python-daemon zijn uitsluitend ontwerp. De huidige depotplanner vergelijkt beide startrichtingen bij stilstand, geen willekeurige meermaals kerende route.

[TRAFFIC-PLANNING-DAEMON-DESIGN](doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md)

<a id="section-2"></a>

## 2. Installatie, migratie en terugdraaien

**[GEWIJZIGD · 2026-11-21]** Java25 en ondersteunde Shiroha/Folia26.2 of26.3; gewone browser voor PCC. Geen clientmod of TrainCarts/BKCommonLib vereist; niet elke Folia-versie is compatibel.

1. Pak alles uit, maak back-ups van wereld/plugins en stop de server. Kopieer vijf JARs uit plugins/ naar serverplugins en verwijder oude versies. Documenten zijn geen plugins.
2. Start voor standaardbestanden, controleer vijf modules, stop en voeg configuratie samen, daarna volledig herstarten. Geen hete verwijdering.
3. Controleer `/st version en`, `/stcs status`, `/sal help`; open lokaal http://127.0.0.1:8765/ en ververs grondig. Externe toegang vereist eigen proxy/authenticatie.
4. `/st reload` herlaadt ook treingegevens: eerst alle treinen stoppen. STCS/PCC via herstart. SAL-taken starten als RECOVERY_REQUIRED; na controle brengt resume eerst terug naar depot.
5. Wis nooit registers voor een update. SHA256 controleert bestanden. Broncode onder Source en alle gelinkte documenten/afbeeldingen zijn bijgevoegd.

```text
SkyworldTrainAPI-2.7.0.jar
SkyTrainFolia-5.0.2.jar
STCS-4.3.5.jar
SkyATO-Lite-0.5.0.jar
SkyPCC-2.4.1.jar
```

[SHA256](../Latest/SHA256.json) · [LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md)

<a id="section-3"></a>

## 3. Rechten en talen

**[NIEUW · 2026-11-21]** Alleen release, uitstappen, verbreken, bewegen, andere bestuurder/plaats of verlopen bevestiging draagt niet over. Handmatige toestand blijft bewaard. Rechten: skyatolite.driver+skytrain.use; beheer skyatolite.admin.

| Recht | Standaard | Doel |
| --- | --- | --- |
| `skytrain.use` | Iedereen | Basisinformatie, persoonlijke taal/eenheden en normale rijcommando's |
| `skytrain.admin` | OP | Samenstellingen, eigenschappen, sjablonen, afstandsbediening, wisselbeheer, opslaan/herladen |
| `stcs.use` | Iedereen | Nabije infrastructuur en graafstatus |
| `stcs.ma` | Iedereen | MA aanvragen/vrijgeven; geldige besturing en toegestane modus blijven vereist |
| `stcs.admin` | OP | Registratie, rebuild/export, bezettings-/MA-diagnose, modi en wisselbeheer |

Een permissie is geen besturingsrecht voor een trein. Met `stcs.ma` kan een speler niet zomaar voor een andere machinist aanvragen. Voor modusbeheer moet de beheerder in de betreffende trein zitten. Commando's die een positie vereisen zijn niet allemaal vanuit de console bruikbaar.

Schrijftoegang via PCC gebruikt een apart token, loopback- en same-origin-controles. Minecraft-OP-rechten worden niet overgenomen. Deel het controletoken niet met gewone passagiers.

```text
/st lang en
/st lang zh
/st lang fr
/st lang jp
/st help 2 en
/stcs help 1 fr
/sta version ja
/skypcc help
```

Zowel `ja` als `jp` kiest Japans. Alle vier hoofdcommando's bieden `help` en `version`. HMI-, modus- en MA-teksten volgen de STF-taalvoorkeur. Sommige oudere beheerdiagnoses blijven vast Chinees of Engels. **Deze Nederlandse handleiding voegt geen Nederlandse spelinterface toe.**

PCC bewaart zijn taalkeuze afzonderlijk in de browser.

<a id="section-4"></a>

## 4. De eerste handmatige trein

**[GEWIJZIGD · 2026-11-21]** Standaard FS aanvragen is geen verleende FS. Handmatige bestuurder neemt expliciet lease; SAL vereist ook uitvoerbare MA. SB/herstel/ontbrekende MA houden remmen.

Plaats enkele mijnkarren op een recht, geladen testspoor zonder andere treinen in de buurt. Houd de scanstraal klein om geen kar op het naastgelegen spoor mee te nemen.

Als beheerder:

```text
/st scan 6
/st create demo 6
/st info demo
/st property demo trainnumber T001
/st property demo mode manual
```

Stap in en voer uit:

```text
/st lang en
/st drive
/st forward
/p1
/n
/b3
/b7
```

- `P1..P4`: tractiestanden; `B1..B7`: bedrijfsremstanden; `EB`: noodrem.
- `/n` zet de rij-/remhendel neutraal; `/st neutral` zet de rijrichtingkeuze neutraal.
- Van richting wisselen is afhankelijk van een lage-snelheids-/stilstandscontrole, geen alternatief voor remmen.
- Neem na iedere instap opnieuw de besturing over met `/st drive`. Andere rijcommando's doen dat niet impliciet.
- Uitstappen, verbinding verliezen, overlijden, naar een andere trein gaan of verlies van het zitplaatsobject trekt de besturing in, schakelt tractie uit en activeert EB. Een andere passagier neemt niet automatisch over.
- `/st release` geeft de besturing expliciet vrij en activeert EB. Automatisch rijden vereist daarnaast stilstand en expliciet `mode auto`.

### Hotbar-besturing

Selecteer **vak 5** voordat `/st hotbar` wordt ingeschakeld. Inschakelen geeft op zichzelf geen N-commando en lost een bestaande remming niet. Daarna kiezen vakwisselingen de standen:

| Vak | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Stand | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

Uitschakelen met `/st hotbar` mag vanuit ieder vak. `/st cab` opent de grafische cabine. Beide vereisen eerst besturingsrechten.

<a id="section-5"></a>

## 5. Commando-overzicht

`<...>` is verplicht, `[...]` optioneel. Hieronder staan de aanbevolen vormen, niet iedere oude alias. Commando's en configuratiesleutels worden niet vertaald.

### Machinist en informatie

| Commando | Doel |
| --- | --- |
| `/st help [page] [language]`, `/st version [language]` | Hulp en geïnstalleerde versies |
| `/st list`, `/st info [train]` | Treinen, status en blokkeerredenen voor automatische borden |
| `/st scan [radius]` | Nabije mijnkarren zoeken |
| `/st drive`, `/st release` | Besturing overnemen/vrijgeven |
| `/st cab`, `/st hotbar` | Cabine-/hotbar-interface |
| `/st forward`, `/st neutral`, `/st backward` | Rijrichtingkeuze |
| `/st p1` t/m `/st p4`, `/st n`, `/st b1` t/m `/st b7`, `/st eb` | Rij-/remhendel; verkorte hoofdcommando's zoals `/p1`, `/n`, `/b7` bestaan ook |
| `/st horn`, `/st bell` | Hoorn/bel |
| `/st lang zh\|en\|fr\|jp\|ru` | Spelertaal |
| `/st speedunit kph\|mph\|block/tick` | Weergave-eenheden, alias `/st unit`; wijzigt geen numerieke beheereenheden |
| `/st balise [info]`, `/st origin [info]`, `/st end [info]` | Nabije infrastructuur |
| `/st mileage [train]` | Kilometrering; een specifieke trein opgeven vereist beheerrechten |

### STF-beheer

| Commando | Doel |
| --- | --- |
| `/st connect [radius]` | Nabije karren koppelen |
| `/st create <name> [radius]` | Samenstelling aanmaken |
| `/st append <train> [radius]` | Voertuigen toevoegen; gebruik eerst vaste samenstellingen voor beveiligingstests |
| `/st unlink` | Dichtstbijzijnde kar ontkoppelen |
| `/st remove <train>` | Treindefinitie verwijderen, niet bedoeld om STCS-blokkades te omzeilen |
| `/st start <train> [speed]`, `/st stop <train>` | Bestaande doelsnelheidsregeling/stop, geen MA-goedkeuring |
| `/st reverse <train>` | Rijrichting omkeren, met stilstandscontrole |
| `/st speed <train> <speed>`, `/st maxspeed <train> <speed>` | Doelsnelheid/limiet in blokken per tick |
| `/st spacing <train> <spacing>` | Voertuigafstand, onderhevig aan globale instellingen voor korte koppeling |
| `/st property <train> <key> [value]` | Zonder waarde lezen, met waarde instellen |
| `/st tag <train> add\|remove\|list [tag]` | Tags |
| `/st owner <train> add\|remove\|list [player]` | Eigenaarsmetadata |
| `/st route <train> set\|add\|clear\|list [destination...]` | Bestemmingenlijst, geen volledige automatische rijweginstelling |
| `/st savedtrain list` | Sjablonen tonen |
| `/st savedtrain save <train> <template>` | Sjabloon opslaan |
| `/st savedtrain spawn <template> [train]` | Nabij de speler genereren |
| `/st switch list`, `/st switch scan [radius]` | Geregistreerde wissels/scan van geladen gebied; standaard 32, maximaal 128 blokken |
| `/st switch info`, `/st switch set straight\|diverging` | Dichtstbijzijnde wissel binnen 8 blokken opvragen/stand aanvragen |
| `/st switch remove`, `/st switch cleanup` | Nabije definitie verwijderen/ongeldige registraties opruimen; remove laat wereldblokken staan |
| `/st balise list`, `/st origin list`, `/st end list` | Infrastructuurlijsten |
| `/st clearkm <line>` | Kilometerkalibratie wissen, niet bezetting |
| `/st admin release\|p1..p4\|b1..b7\|n\|eb <train>` | Expliciete beheerbesturing op afstand |
| `/st syncstatus` | Diagnose van weergavesynchronisatie |
| `/st save`, `/st reload` | Opslaan/herladen; eerst alle treinen stilzetten |

Eigenschappen accepteren ook `property <train> get <key>` en `property <train> set <key> <value>`. De voorbeelden gebruiken de korte vorm.

### STCS

| Commando | Recht / betekenis |
| --- | --- |
| `/stcs help [page] [language]`, `/stcs version [language]` | Hulp/versie |
| `/stcs inspect` | `stcs.use`; markering binnen 3 blokken |
| `/stcs status` | `stcs.use`; knopen/verbindingen/graafrevisie, niet de ATP-modus |
| `/stcs ma fs` | **[GEWIJZIGD]** Standaard FS aanvragen is geen verleende FS. Handmatige bestuurder neemt expliciet lease; SAL vereist ook uitvoerbare MA. SB/herstel/ontbrekende MA houden remmen. |
| `/stcs ma release` | `stcs.ma` en huidige machinist; reserveringen vóór de trein vrijgeven, niet de bezetting onder de trein |
| `/stcs ma status` | `stcs.admin`; MA-/blokkeerdiagnose |
| `/stcs admin status` | `stcs.admin`; beveiligingsstatus van de trein waarin men zit |
| `/stcs admin isolate true\|false` | Treinbeveiligingskanaal isoleren |
| `/stcs admin bypass true\|false` | Bewaking overbruggen |
| `/stcs admin shadow true\|false` | Schaduwtestmodus |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`; bewaarde bezetting/voertuigwaarnemingen |
| `/stcs rebuild` | **[GEWIJZIGD]** STCS bezit fysieke grafiek; spoor/apparaatwijzigingen lokaal. Wisselstand is niet topologie. rebuild begint rond beheerder, alleen geladen bronnen. |
| `/stcs export` | `stcs.admin`; huidige graaf exporteren, niet opnieuw scannen |
| `/stcs switch info` | `stcs.use`; dichtstbijzijnde wissel binnen 8 blokken |
| `/stcs switch change` | `stcs.admin`; nabije wissel omzetten |

~~Het spelerscommando is **`demand`, niet `request`**. Interne gebeurtenissen kunnen nog `MA_REQUESTED` heten. `/sta` en `/skypcc` bieden vooral hulp/versie, geen tweede verzameling rijcommando's.~~

**[GEWIJZIGD · 2026-11-21]** `/stcs ma fs` · `/stcs ma demand` (alias)

**[OPVRAGEN · 2026-11-21]** `/sal help` · `/sal status` · `/sal route list Line1` · `/sal train home`

Alleen release, uitstappen, verbreken, bewegen, andere bestuurder/plaats of verlopen bevestiging draagt niet over. Handmatige toestand blijft bewaard. Rechten: skyatolite.driver+skytrain.use; beheer skyatolite.admin.

<a id="section-6"></a>

## 6. Voertuigprofielen en eigenschappen

### Serverbreed profiel

In `plugins/SkyTrainFolia/config.yml`:

```yaml
settings:
  default-vehicle-profile: minecraft-comfort
  server-speed-limit-kmh: 420.0
```

De bestanden staan in `plugins/SkyTrainFolia/vehicles/<id>.yml`:

| ID | Referentie |
| --- | --- |
| `crh380b` | CRH380B |
| `cr400bf` | CR400BF |
| `keikyu-n1000` | Keikyu New 1000-serie |
| `minecraft-comfort` | Fictief, op speelbaarheid gericht profiel |

De echte voertuigen dienen als benadering voor afstelling, niet als gecertificeerde prestatiedata. De huidige selectie is **serverbreed**; een commando `/st property <train> profile ...` per trein bestaat niet.

Een profiel beschrijft een complete referentietrein. Het aantal Minecraft-karren schaalt massa/prestaties niet automatisch. De echte samenstellingslengte blijft wel relevant voor bezetting, stoppen en benodigde ruimte voorbij een wisselgebied.

| Profielveld | Effect |
| --- | --- |
| `physics-mode` | Fysicamodel; meegeleverde profielen gebruiken `force` |
| `mass-tonnes` | Massa van de complete referentietrein |
| `max-speed-kmh` | Voertuiglimiet, geen garantie dat de tractie die snelheid bereikt |
| `traction.acceleration-mps2.p1` t/m `p4` | Bruto tractieversnelling bij lage snelheid, vóór weerstand |
| `traction.base-speed-kmh` | Basissnelheid van de tractiecurve |
| `traction.field-weakening-speed-kmh`, `minimum-ratio` | Tractiereductie bij hoge snelheid/minimumverhouding |
| `brake.deceleration-mps2.b1` t/m `b7` | Bruto bedrijfsremvertraging |
| `brake.emergency-mps2` | Noodrembijdrage |
| `resistance.rolling-mps2` | Rolweerstand |
| `resistance.air-mps2-at-100-kmh` | Luchtweerstand bij 100 km/h, schaalt met het kwadraat van de snelheid |
| `resistance.grade-mps2` | Factor voor hellingsversnelling |
| `control.direction-change-speed-kmh` | Lage-snelheidsgrens voor richtingwisselen |
| `automatic.*` | **[GEWIJZIGD]** Samenstellingen, fysica, chunktickets, handbediening, TIMS, ATP en uitvoering / V_target |


Oude losse sleutels zoals `drive-power-acceleration-p1` zijn niet de huidige profielinterface. Maak een back-up en zet alle treinen stil vóór herladen, of wijzig terwijl de server uitstaat en herstart.

### Waarom nog een limiet van 130 km/h?

Voertuiglimiet, serverlimiet en opgeslagen `maxspeed` per trein kunnen alle drie begrenzen. Een ander standaardprofiel wist een bestaande treinlimiet niet.

Bij de nominale schaal van 1 blok = 1 meter en 20 TPS geldt `km/h = blocks/tick × 72`:

```text
/st property demo maxspeed
/st maxspeed demo 5
```

`5` betekent hier 360 km/h, niet 5 km/h of de tekst `5kmh`. Lagere voertuig-/serverlimieten blijven gelden. Ook de balans tussen tractie en weerstand kan de haalbare snelheid beperken. Persoonlijke weergave-eenheden veranderen de invoerinterpretatie niet.

### Treinidentiteit en ritnummer

```text
/st property demo name test01
/st property test01 displayname Test Train
/st property test01 trainnumber G001
/st property test01 trainnumber
/st property test01 trainnumber clear
/st property test01 mode auto
/st property test01 pushable true
```

- `name` is de beheersnaam van de trein. Gebruik na hernoemen de nieuwe naam in commando's.
- `trainnumber` is het afzonderlijke trein-/ritnummer voor de dienst. Voorloopnullen blijven behouden; maximaal 32 tekens; geen besturingstekens of `|`; uniekheid wordt niet afgedwongen. `clear` of `-` wist het.
- `displayname` is weergavemetadata, niet de interne UUID.
- Andere eigenschappen zijn `destination`, `collision`, `playersenter`, `playersexit`, `pickupitems`, `invincible`, `allowplayertake`, `requirepoweredcart`, `sound`, `keepchunksloaded`, `gravity`, `friction`, `waitticks`, `speed`, `maxspeed`, `spacing`.
- `pushable=true` alleen is onvoldoende: machinistbesturing, niet-vrijgegeven handmatige overname of rijstatus kunnen duwen blokkeren. Controleer `/st info`.
- Owner/tag/route-metadata impliceert geen complete routering, verkeersleiding of toegangsisolatie per eigenaar.

<a id="section-7"></a>

## 7. Lijnen en wissels bouwen

**[GEWIJZIGD · 2026-11-21]** STCS bezit fysieke grafiek; spoor/apparaatwijzigingen lokaal. Wisselstand is niet topologie. rebuild begint rond beheerder, alleen geladen bronnen.

### Fysieke topologie en lijntoewijzing

RailGraph bevat knopen, poorten, toegestane overgangen, gerichte verbindingen en geometrie. Lijnnamen/kilometrering zijn annotaties die niet willekeurig door iedere aangesloten wissel mogen doorlopen.

- Gebruik unieke, consequent gespelde lijnnamen, bijvoorbeeld `test_up` en `test_down`.
- Origin/End begrenzen de lijntoewijzing, niet noodzakelijk het fysieke spoor. Spoor daarbuiten hoort fysiek verbonden te blijven.
- Een balise zonder lijnnaam markeert een zijspoor zonder de hoofdlijnidentiteit eindeloos door te geven.
- Verbinden meerdere paden dezelfde benoemde ankers, plaats dan een benoemde balise op de bedoelde hoofdlijntak. De actuele wisselstand bepaalt geen permanente lijntoewijzing.
- Onbekende kilometrering blijft onbekend; nabijheid van een ander anker is onvoldoende bewijs.

### STCS-borden met vier regels

Plaats borden ondubbelzinnig bij het bedoelde spoor, vooral bij parallelle sporen.

Origin:

```text
[STCS]
origin
test_up
right
```

Hoofdlijnbalise:

```text
[STCS]
balise
test_up
0010
```

End:

```text
[STCS]
end
test_up
right
```

Zijspoorbalise, regel 3 leeg:

```text
[STCS]
balise

5010
```

Regel 4 van Origin wijst vanaf kilometer nul de lijn in. Bij End wijst deze naar de kant waaruit de kilometrering wordt ontvangen; End begrenst expliciet de lijn, niet het fysieke spoor. Beide gebruiken `left`/`right`, nooit een naam. Relatieve richtingen volgen het bordvlak, niet links/rechts op PCC. `signal` kan als knoop worden geregistreerd, maar betekent niet dat ATP op seinbeelden is gerealiseerd.

### STF-wisselborden

STCS importeert geregistreerde STF-wissels, niet iedere gebogen rail. Registratie herkent `[stf]`, `[+stf]`, `[SkyTrain]` en `[+SkyTrain]` ongeacht hoofdletters. Redstone-activering en het bestaan van infrastructuur zijn verschillende zaken: `+` toevoegen is geen algemene oplossing voor ontbrekende verbindingen.

Voorbeeld van een bestaande frontmontage:

```text
[SkyTrain]
switch
fl
S01
```

Regel 4 benoemt de wissel; de UUID blijft de identiteit. **STF `switch` is niet TC `switcher`.**

Wandborden ondersteunen de bestaande zij-/ondermontage. Bij frontmontage `fl/fr` ligt de wisselrail twee blokken boven het blok waaraan het bord hangt. Voor een plafondhangbord geldt: bord → draagblok erboven → wisselrail daarboven, gericht op een windrichting en niet diagonaal. Gebruik gewone omzetbare rails voor de wissel. Een gekoppelde hendel volgt de wissel en meldt de verandering aan naburige redstoneblokken; test ingewikkelde schakelingen in-game.

Na aanleg:

```text
/st switch scan 32
/st switch list
/stcs inspect
/stcs rebuild
/stcs status
/stcs export
```

Rebuild vertrekt vanuit geregistreerde infrastructuur, niet vanuit een onbeperkte wereldscan. Controleer registratie na WorldEdit. Standaard worden alleen geladen chunks gescand; bij onvolledige scans blijft eerder bekende topologie behouden. Niet eerder waargenomen spoor kan niet uit niets worden aangevuld.

```yaml
scan:
  max-distance-meters: 256.0
  blocks-per-meter: 1.0
  marker-rail-search-radius: 3.0
  only-loaded-chunks: true
graph:
  file: railgraph.json
  pretty-print: true
```

De scanafstand is niet de MA-vooruitkijkafstand. Lange secties vereisen passende ankers of een grotere scanlimiet. Houd de STF/STCS-schalen gelijk, normaal 1 blok per meter.

<a id="section-8"></a>

## 8. SAL-lijnen, borden en bestuursoverdracht

**[NIEUW · 2026-11-21]** Eén oproep geeft één rit, geen eindeloze lus of dienstregeling. Eerste/laatste bezoek zijn stations; herhaling mag. Bind tussenliggende bakens/wissels om vertakkingen eenduidig te maken. SAL maakt geen trein aan: eerst STF-samenstelling en depot.

### Commando en selectie

1. Maak Line1 in de vertrekrichting, of stel de windrichting in.
2. Plaats eerste station en bind apparatuur in volgorde. Meerdere kandidaten binnen8blokken: choose binnen60seconden; expliciete UUID/station ID mag.
3. Nieuwe stations worden op plaatsingsvolgorde toegevoegd. Controleer list; remove verwijdert bezoek vanaf index1. Bind het eerste station opnieuw als eindpunt.
4. reverse na een stationbinding keert na dat bezoek; geen automatische omkering op elke willekeurige plek.
5. preview duurt twee minuten, groen bij gesloten route, anders rood. Groen is geen vrije baan of MA. validate bewaart validatie; publish maakt oproepen mogelijk.
~~6. Een lijn met taak is niet bewerkbaar. Alleen gewijzigde afhankelijkheden vereisen hercontrole; ongerelateerde grafiekwijzigingen houden publicatie.~~

**[NIEUW / GEWIJZIGD]** Eerst depot registreren. Klik bakens, wissels, gerichte graafranden en stations van deze lijn; wijzig volgorde, wachttijd en keren zonder borden te herschrijven. Opslaan, controleren en publiceren zijn aparte stappen. Lopende taken behouden hun eigen gepubliceerde versie.
7. Plaats depot/snelheid/message, maak en bind trein en roep via knop/awake.

```text
/sal route create Line1
/sal route direction Line1 east
/sal route bind Line1
/sal route choose 1
/sal route bind Line1 <deviceUUID>
/sal route bind Line1 <stationID> reverse
/sal route list Line1
/sal route remove Line1 <index>
/sal route preview Line1
/sal route preview Line1 off
/sal route validate Line1
/sal route publish Line1
```

### Borden per regel

`[SAL]` = `[SkyATOL]`

#### depot

```text
[SAL]
depot
maintown
L1
```

depot: regel3 maintown, regel4 plaats L1. L2/L3 elk eigen bord, eventueel verspreid. Depot is geen vertrekstation. Behalve awake koppelen aan een geregistreerde RailGraph-spoorkolom.

#### station

```text
[SAL]
station
15
Line1
```

station: regel3 wachttijd in seconden, regel4 SAL-lijn. Eerst lijn maken. Geen apart naamveld; PCC toont lijn en volgorde.

#### awake

```text
[SAL]
awake
maintown
Line1
```

awake: regel3 depot, regel4 lijn. Stijgende redstoneflank op bord/steun roept; spoor eronder niet nodig. Permanente voeding/start roept niet, dubbele oproepen worden samengevoegd.

#### property / v_target

```text
[SAL]
property
V_target
36km/h
```

property alleen V_target met snelheid regel4. v_target kort: snelheid regel3, regel4 leeg. km/h, kmh, m/s, m/t, b/t, mph; geen eenheid=blokken/tick. Voertuig-/treinlimiet en ATP blijven gelden.

```text
[SkyATOL]
v_target
36km/h

```

#### message

```text
[SAL]
message
Skyworld
5
```

message: letterlijke tekst regel3, seconden regel4. Centrale Title voor eigen passagiers; nieuwe tekst overschrijft direct, geen wachtrij of commando-uitvoering.

### Treinen en uitvoering

**[GEWIJZIGD]** Cirno — SAL

Beheerder in samengestelde stilstaande trein stelt home in: depot, geen vaste L1. Nieuwe bestuurderloze trein kan op zijn depotmarkering worden geduwd voor langzame STF-parkeerovername. Oproep vereist verse complete waarneming, geen TIMS-houding, menselijke bestuurder of andere taak.

```text
/st create demo 6
/sal train home maintown
/sal train home
/sal awake maintown Line1
/sal status
```

Oproep→uit depot→eerste station→rit/haltes→laatste station→passende plaats reserveren→terug en stoppen. Ook zonder instappers één rit. Passagiers mogen mee terug; volgende ronde vraagt nieuwe oproep. Geschikte trein van dezelfde lijn nog op eerste station wordt hergebruikt: terugkeer annuleren, zo nodig veilig keren, volledige wachttijd opnieuw. Niet na vertrek of storing.

Route via RailGraph, onafhankelijk van huidige fysieke wisselstand. STCS legt om via STA/STF. Keren: oude MA vrijgeven, stilstand/omkering bevestigen, opnieuw plannen vanaf echte voorste wagen. Geen ruimte/plaats/pad: wachten.

### Handmatige/automatische overdracht

Automatisch→handmatig: alleen complete stilstaande PARKED-depottrein zonder taak/storingshoud met `/sal release`, daarna wachten en `/st drive` in eindwagen. Niet onderweg naar depot.

Handmatig→automatisch: bestuurderslease,3seconden volledig stil op geldige plaats, SAL bevestigen, binnen30seconden expliciet `/st release` of tweede knop. Werkelijk depot wordt home; geen direct vertrek.

Alleen release, uitstappen, verbreken, bewegen, andere bestuurder/plaats of verlopen bevestiging draagt niet over. Handmatige toestand blijft bewaard. Rechten: skyatolite.driver+skytrain.use; beheer skyatolite.admin.

```text
/sal release
/st drive
```

### Lege treinen en storingen

```text
/sal stop <trainUUID>
/sal resume <trainUUID>
/sal finish <trainUUID>
```

stop houdt; resume na controle herstelt eerst terugkeer; finish vraagt betrouwbare positie en complete stilstand voor MA-vrijgave/einde. Parameter=trein-UUID.

STF-tickets zonder passagier; asynchroon laden en tickets op eigenaarregio. Onklaar chunk blokkeert tractie. Lege-serverpauze apart uitschakelen met pause-when-empty-seconds=-1.

TIMS-herstel pas na echte integriteit, maximaal3pogingen; geen leden wijzigen, opnieuw spawnen of lichaamsbezetting wissen. Echte schade handmatig inspecteren.

Padfout geeft PCC-alarm. Eerst stil, compleet en betrouwbaar gelokaliseerd, dan SR-aanvraag. Elke sectie opnieuw goedkeuren; geen eindeloze automatische SR.

[SAL-MANUAL-HANDOVER](doc/SAL-MANUAL-HANDOVER.md) · [SAL-DEPOT-AND-RETIREMENT](doc/SAL-DEPOT-AND-RETIREMENT.md)

<a id="section-9"></a>

## 9. Uitvoerbare MA, standaard FS en ATP

**[GEWIJZIGD · 2026-11-21]** Standaard FS aanvragen is geen verleende FS. Handmatige bestuurder neemt expliciet lease; SAL vereist ook uitvoerbare MA. SB/herstel/ontbrekende MA houden remmen.

FS bewaakt snelheid/EoA; SH/SR standaard maximaal40km/h of lagere curve. TR stil: ack→PT, release→SB, controleren en opnieuw aanvragen. TIMS eerst werkelijk herstellen/bevestigen.

Eerste padaanvraag, daarna stille verlenging terwijl STCS MA uitbreidt. Gewijzigd pad/versie, verlopen lease of verloren controle vraagt nieuw geldig verzoek; laat bericht herstelt niets.

Schaduwtest simulationOnly=true/executable=false is geen uitvoerbare MA. BYPASS/ISOLATED/RECOVERING behouden voorwaarden.

MA-vrijgave bewaart lichaamsbezetting. Ontladen/tijd/verbreking bewijzen geen uitrijden. Expliciete verwijdering vereist volledig STF-bewijs en opgeruimde virtuele bestuurder; vrijgave alleen betreffende trein.

```text
/stcs ma fs
/stcs ma status
/stcs ma ack
/stcs ma release
```

### Configuratie en lokale grafiek

STCS bezit fysieke grafiek; spoor/apparaatwijzigingen lokaal. Wisselstand is niet topologie. rebuild begint rond beheerder, alleen geladen bronnen.

Ontladen bewaart bewezen verbindingen. Nooit waargenomen richting blijft onbekend; bekende breuk blijft fout na ontladen. Bezetting, fysieke bevestiging en MA apart.

Standaard elke60seconden, bereik1–86400, batch1–64bronnen. Gezonde controle verhoogt revision niet. SAL controleert geraakte afhankelijkheden; MA houdt globale versiecontrole.

```yaml
graph:
  review-interval-seconds: 60
  sources-per-tick: 4
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  check-exit-capacity: true
```

`look-ahead` ≠ `lock-distance` ≠ `max-authority-distance`

`FS lock = max(lock-distance, v²/(2a) + v×lead + buffer)`

`a = 1 m/s²; lead = 5 s; buffer = 25 m; v = m/s`

[RAILGRAPH-INCREMENTAL](doc/RAILGRAPH-INCREMENTAL.md) · [STCS-DYNAMIC-SWITCH-LOCK](doc/STCS-DYNAMIC-SWITCH-LOCK.zh.md) · [CONFIGURATION-SWITCHES](doc/CONFIGURATION-SWITCHES.md)

<a id="section-10"></a>

## 10. SkyPCC-infrastructuur- en exploitatiekaart

~~**[NIEUW · 2026-11-21]** Infrastructuurkaart houdt apparatuur, kilometrering, bezetting, MA en bediening. Exploitatiekaart heeft apart canvas/zicht zonder bakens/wissels/origins; alle of één SAL-lijn.~~

**[NIEUW / GEWIJZIGD]** Eén RailGraph met afzonderlijk aanklikbare infrastructuur-, SAL-, exploitatie- en livelagen. Open de editor met het bestaande wisseltoken, kies een lijn of maak een concept met minstens één geregistreerd depot.

DRAFT gestippeld; VALIDATED lange streep na geldige controle; PUBLISHED doorgetrokken na geldige publicatie. Geschatte conceptverbinding is expliciet onbevestigd.

Depot kiezen markeert alle plaatsen. Totaal bevat gemarkeerde ongeldige plaatsen. Geparkeerd=vers/compleet, geen TIMS, op precies één geldige plaats. Rijdend=SAL-taak, inclusief wachttijd, pauze en herstel.

Overig/onbekend: handmatig, elders wachtend, ontbrekende waarneming. Lege omtrek is geen vrijeplaatsgarantie. Verouderde data wordt onbekend, geen nul of actuele oude positie.

Klik plaats voor locatie, trein voor details; aparte zoom, labels, thema en wereldkeuze. PCC zh/en/fr/ja; STF ook ru. Nederlandse handleiding voegt geen speltaal toe.

Schrijven vereist web.control-enabled en Bearer; web.require-https-for-control standaardtrue. false versoepelt alleen HTTPS. Geen geheimen inbegrepen.

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  require-https-for-control: true
```

[PCC-OPERATING-MAP](doc/PCC-OPERATING-MAP.md)

<a id="section-11"></a>

## 11. Geluiden en HMI

~~De BossBar voor de machinist toont MA-informatie en de schaduwsnelheidslimiet, die ook op de tweede zijbalkregel staat. Ongeldige curves tonen `--`. De interface ondersteunt Chinees, Engels, Frans en Japans. `settings.cab-ma-bar-range-meters` is standaard 300 m en bepaalt alleen de schaal.~~

**[GEWIJZIGD]** Standaard FS aanvragen is geen verleende FS. Handmatige bestuurder neemt expliciet lease; SAL vereist ook uitvoerbare MA. SB/herstel/ontbrekende MA houden remmen. FS bewaakt snelheid/EoA; SH/SR standaard maximaal40km/h of lagere curve. TR stil: ack→PT, release→SB, controleren en opnieuw aanvragen. TIMS eerst werkelijk herstellen/bevestigen.

### MA-geluiden configureren

Op het **hoogste niveau** van STF-configuratie, niet onder `settings`:

```yaml
ma-sounds:
  enabled: true
  granted:
    enabled: true
    sound: minecraft:block.anvil.land
    category: MASTER
    volume: 1.0
    pitch: 2.0
    count: 2
    interval-ticks: 5
```

`changed`, `released`, `shrinking`, `low` gebruiken dezelfde velden. Voeg samen met de bestaande sectie, zonder dubbele YAML-sleutels.

| Melding | Standaardgeluid | Gedrag |
| --- | --- | --- |
| granted | `minecraft:block.anvil.land` | Aangevraagde MA toegekend; pitch 2, tweemaal |
| changed | `minecraft:block.anvil.land` | Duidelijke sprong; pitch 2, eenmaal |
| released | `minecraft:block.iron_trapdoor.close` | Vrijgave |
| shrinking | `minecraft:block.note_block.bit` | Resterende meeschuivende MA begint tijdens rijden af te nemen |
| low | `minecraft:block.note_block.pling` | Weinig resterende afstand |

Gebruik Java Edition-ID's `namespace:path`; zonder namespace geldt minecraft. Eigen ID's vereisen een resourcepack bij de client. Grenzen: volume 0..4, pitch 0.5..2, count 1..16, interval-ticks 1..200.

STCS-triggerinstellingen:

```yaml
ma:
  sound:
    enabled: true
    jump-threshold-meters: 20.0
    cooldown-ms: 1500
    low-remaining-meters: 50.0
```

Nul schakelt de lage-afstandsdrempel uit. Hysterese voorkomt herhalen rond de grens. Normale vloeiende verlenging hoort niet steeds de sprongmelding te geven. Vertraagde geluiden controleren opnieuw wie bestuurt.

### Rol- en remgeluid

Volume/toonhoogte van rolgeluid volgen de snelheid: standaard stil onder 10 km/h en op het ingestelde maximum bij 120 km/h. Zie `settings.trackside-running-sound-*`.

Meer remming of remmen vanuit N/tractie geeft een aanleggeluid; volledig lossen geeft een losgeluid. Gedeeltelijk minder remmen geeft niet bij iedere stand een losgeluid. Zie `settings.brake-sound-*`. Er is momenteel geen tractiemotorgeluid.

Stop alle treinen vóór `/st reload`; dit herlaadt gegevens, niet alleen geluid.

<a id="section-12"></a>

## 12. STA-contracten en HTTP

**[GEWIJZIGD · 2026-11-21]** STA is een Java-contract binnen hetzelfde proces, geen actieve Python/TCP RBC. Message, Packet en interfacefamilie onderscheiden; sessie, volgnummer, versie en tijd controleren.

ATO privé-Message6508; SUBSET_139 Packet1031/1032 als31/32+1000. Privé5115–5118 voor verlengen/parkeren/overdracht. MA Message1003/Packet1015; telemetrie1136.

v8 OperatingNetworkService alleen weergave, schemaVersion1, geen MA. v9 TopologyDependency voor randen. SAL publiceert, PCC cachet op achtergrond; HTTP leest geen entiteiten. JAR/DTO/URL-versies verschillen.

```text
GET /api/v5/graph
GET /api/v5/trains
GET /api/v5/messages
GET /api/v5/railway-events
GET /api/v5/shadow-ma
GET /api/v5/config
GET /api/v5/events
GET /api/v6/operational-ma
GET /api/v7/station-areas
GET /api/v8/operating-network
POST /api/v5/switch
POST /api/v6/sr
POST /api/v6/ma/revoke
```

[STA-V8-ATO-PROTOCOL](doc/STA-V8-ATO-PROTOCOL.md) · [STA-V5-MIGRATION](doc/STA-V5-MIGRATION.md)

<a id="section-13"></a>

## 13. Opslag en probleemoplossing

**[GEWIJZIGD · 2026-11-21]** SAL state.json bevat borden, routes, home, handmatige wachtstatus en taken, plus .bak. Niet-afgeronde toestemming overleeft herstart niet. Opslagfout blokkeert oproep. occupancy-ledger.json en shadow-occupancy.json apart; confirmed-destructions.json bewaren.

Paden ten opzichte van de servermap:

| Pad | Gegevens |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`, `plugins/SkyTrainFolia/vehicles/` | Globale configuratie, geluid, profielen |
| `plugins/SkyTrainFolia/trains.yml` | Samenstelling, eigenschappen, modi |
| `plugins/SkyTrainFolia/savedtrains.yml` | Sjablonen |
| `plugins/SkyTrainFolia/switches.yml` | Wisselregistratie |
| `plugins/SkyTrainFolia/infrastructure.yml` | STF-infrastructuur/kalibratie |
| `plugins/SkyTrainFolia/stations.yml`, `plugins/SkyTrainFolia/automatic-signs.yml` | Station-/bordgegevens |
| `plugins/STCS/markers.yml` | STCS-registratie |
| `plugins/STCS/railgraph.json` | Graafmomentopname, geen bezettingsregister |
| `plugins/STCS/occupancy-ledger.json` | Bewaard bewijs; nooit wissen om MA af te dwingen |
| `plugins/SkyPCC/config.yml` | Webinstellingen/geheime toegang |

De graaf is nog één logische graaf/JSON-export, geen productieopslag met een bestand per lijn. Knip fysieke lijnoverschrijdende verbindingen niet op basis van lijnnamen door. Analyseer exportkopieën, niet live graaf-/registerbestanden.

| Verschijnsel | Eerst controleren |
| --- | --- |
| ~~auto maar station doet niets~~ | ~~Machinist, expliciete release, modus, redstone, bordkolom en blokkeerreden~~ |
| Aanvraag geaccepteerd, HMI zonder MA | `/stcs ma status` en PCC-reden; acceptatie is geen toewijzing |
| SWITCH_UNKNOWN | Fysieke validatie, geladen/verantwoordelijke regio, registratie en positie |
| FLEET_UNCERTAIN / onvindbare bewaarde trein | Register/UUID; geen getekende trein betekent niet geen bewijs |
| NO_EXIT_CAPACITY | Ruimte voor de complete trein, MA-limiet, ontbrekend/onjuist pad |
| Parallelle rijwegen blokkeren | Echt pad, intervallen, oudere bewaarde resources, recente volledige waarneming |
| Ontbrekende/onjuist doorlopende kilometrering | Origin/End-richting, benoemde balises, zijspoorgrenzen, ambiguïteit; laden en rebuild |
| PCC INVALID_REQUEST | Formaat/revisie/verwachte stand/positie, frontend-/backendversie; niet noodzakelijk token |
| PCC-bediening uit | Schakelaar, tokenlengte, loopback, origin, afhankelijkheden en precieze afwijzing |
| Blijvend PENDING | Asynchroon laden, fysieke hervalidatie/logs; geen succes |
| Problemen bij hoge snelheid | Serveraanpassing, regiobeweging, andere entiteitsbesturing/verwijdering, niet alleen snelheidslimiet |

Unload is geen vrijmelding van de treinachterzijde; hersteld bewijs is geen recente positie. RECOVERING als kwaliteit in het register is bovendien een andere toestandsmachine dan RECOVERING aan boord.

Meldingen horen plugin-/serverversies, tijd, treinnaam/UUID, infrastructuur-ID's, stappen, logs, graafexport en bezettingsdiagnose te bevatten. Verwijder tokens voordat configuratie gedeeld wordt.

- Geen oproep: publicatie, awake-flank, namen, handmatige status, TIMS, observatie en taken. Tegengestelde richting op zichzelf vraagt geen handmatig draaien.
- Geen verbinding: RailGraph/routingIssues, lokale scan van geladen spoor. chunk_not_loaded alleen bewijst geen breuk.
- Terugkeer houdt: plaats, hele trein, pad, wisselbevestiging en MA-reden; geen registers wissen.
- Bezetting na verwijderen: UUID, volledig bewijs en virtuele bestuurder nakijken; onbekend is niet veilig verwijderd.
- Exploitatiegegevens weg: diensten/versies SAL/STA, revision en leeftijd, browser verversen.

<a id="section-14"></a>

## 14. Acceptatie en beperkingen

**[GEWIJZIGD · 2026-11-21]** Onafhankelijke Java-regressie26.2/26.3, inclusief nieuwe versie-/ATP-tekst en SAL-presentatie. Resultaten in distributie.

Eerder gecontroleerd: één lege rit26.3, echte PCC26.2 met line1 en twee maintown-plaatsen; browserstijlen/filter/lijsten/veroudering en363viertalige sleutels. Geen acceptatie van alle layouts.

Ter plaatse: omgekeerde oproep, depotwissels, hergebruik eerste station, L1/L2-concurrentie, hele trein vrij van wissel/keren, lege server, Title, TIMS/uitval/herstart, tweestapsoverdracht en gerichte verwijdering.

[VALIDATION](../VALIDATION.md)

<a id="section-15"></a>

## 15. Ontwikkeling, broncode en distributie

**[GEWIJZIGD · 2026-11-21]** Bronarchief bevat vijf modules, gedeelde code, standaardresources en tests. Na uitpakken Common/build.ps1 uitvoeren met JavaHome en twee voorbereide testservers. Latest pas na beide controles. Geen Minecraft-kern, wereld, privéconfiguratie, token of certificaat.

```powershell
./Common/build.ps1 -ServerRoot <server26.2> -ServerRoot26_3 <server26.3> -JavaHome <jdk25>
node SkyPCC/tests/edge-line.cjs
node SkyPCC/tests/i18n.cjs
node SkyPCC/tests/operating-map-browser.cjs
./Common/package-source.ps1
```

MIT-code/documenten en afzonderlijke beeldrechten bijgevoegd. Presentatie verandert geen rijweg-, MA- of fysieke uitvoeringsgrenzen.

[LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md) · [TrainCarts](https://github.com/bergerhealer/TrainCarts) · [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)

## Vorige handleiding en verwijderde instructies

**[VERWIJDERD · 2026-11-21]** Verwijderde STF-instructies hieronder zijn doorgestreept en niet meer geldig.

~~Station/spawn/destroy volgen op TC geïnspireerde conventies. De railkoppeling gebruikt de bordkolom onder de rail en bevestigingsrelaties, niet een willekeurige bol rondom de rail. **Ondersteuning van deze vormen betekent geen volledige compatibiliteit met TC-expressies, afstandsborden of uitbreidingen.**~~

~~Een handmatig bestuurde trein wordt niet door station/destroy overgenomen. Stop, laat de machinist `/st release` uitvoeren en stel expliciet `/st property <train> mode auto` in. ISOLATED, RECOVERING en niet-vrijgegeven handmatige overname kunnen automatisch gebruik eveneens blokkeren.~~

~~Gebruikelijke koppen: `[stf]` gebruikt redstone, `[+stf]` is continu actief, `[!stf]` geïnverteerd, `[-stf]` uitgeschakeld. De parser kent ook vormen voor stijgende/dalende flanken; de daadwerkelijke activering hangt af van de actie.~~

**Station**


~~`[+stf]`~~
~~`station`~~
~~`5`~~
~~`continue 40kmh`~~


~~| Regel | Betekenis |~~
~~| --- | --- |~~
~~| 1 | Kop/activering |~~
~~| 2 | station, eventueel ondersteunde vertrekafstand/-tijd/-versnelling en stopoffset |~~
~~| 3 | Halteertijd; kaal getal in seconden, ook `5s`, `100t`, `00:05` |~~
~~| 4 | Richting/snelheid, bijvoorbeeld `continue 40kmh` of `reverse 0.4`; zonder eenheid blokken/tick |~~

~~Automatische treinen kunnen Station-borden vooruit ontdekken door de werkelijke rails te volgen, zonder lijnnaam, balises of STCS-kalibratie. De zoekactie volgt de ingestelde wisseltak, laadt geen chunks en detecteert geen voorliggende treinen. Dit is geen ATP of botsbeveiliging.~~

~~Het stoppunt is het midden van de voorste mijnkar in de rijrichting, plus de stationoffset; er wordt geen halve treinlengte meer toegevoegd. Vanaf 2.1.3 kiest de virtuele machinist de remstand op basis van de resterende afstand. Onder de remcurve wordt uitgerold; alleen bij werkelijk te vroeg stoppen volgt langzaam bijrijden met hysterese. Bij stilstand en HOLD blijft B7 actief; handmatige bediening verandert niet.~~

~~Voeg onder de bestaande STF-sectie `settings` de instelling `station-local-look-ahead-blocks: 256.0` toe. Bereik: 0–1024 blokken; 0 schakelt lokaal zoeken uit. Er wordt hoogstens iedere 200 ms gezocht, uitsluitend op geladen rails binnen de huidige Folia-thread. Dit is een maximale zoekafstand, geen gegarandeerde remweg. Optioneel STA/STCS-graafadvies gebruikt afzonderlijk `station-look-ahead-blocks` (standaard 8192). Test eerst langzaam met voldoende geladen spoor.~~

**V_target-eigenschapsbord (Vanaf 2.1.2)**

~~Dit bord wijzigt de doelsnelheid van automatische treinen, niet hun maximumsnelheid of besturingsmodus. Handmatige treinen negeren het. Alleen `V_target` is toegestaan op eigenschapsborden; andere eigenschappen worden via beheerderscommando's ingesteld.~~


~~`[+stf]`~~
~~`property`~~
~~`V_target`~~
~~`60km/h`~~


~~Zonder eenheid geldt blokken/tick: `0.4`, `8m/s` en `28.8km/h` zijn gelijk bij 20 ticks/s en één blok per meter. De eigenschap wordt opgeslagen, respecteert trein- en profielmaxima en start een stilstaande trein niet zelfstandig. Een expliciete stationvertreksnelheid heeft voorrang; anders geldt V_target, met `settings.station-launch-speed` (standaard 0.4) als terugval. `[+stf]` is altijd actief; `[stf]` gebruikt redstone. Beheerders gebruiken `/st property demo V_target 60km/h` om in te stellen en `/st property demo get V_target` om uit te lezen.~~

**Spawn en Destroy**


~~`[stf]`~~
~~`spawn 0.0`~~
~~`mmm`~~



~~Genereer met gecontroleerde redstone drie gewone mijnkarren. Regel 2 accepteert `spawn [velocity] [interval]`, met bijvoorbeeld `00:30` als interval; regels 3 en 4 vormen samen het patroon. Basissymbolen: `m` gewoon, `s` kist, `p` oven, `h` trechter, `t` TNT, naast ondersteunde sjabloonpatronen. Gebruik aanvankelijk geen TNT.~~

~~Sjablonen voor automatisch genereren moeten expliciet in auto zijn opgeslagen. Laat niet continu treinen op een onbeveiligde lijn verschijnen.~~


~~`[+stf]`~~
~~`destroy`~~




~~Destroy verwijdert daadwerkelijk entiteiten. Test op een apart spoor met back-up; een handmatig bestuurde trein hoort niet te worden verwijderd. Test eerst station, daarna spawn/destroy afzonderlijk.~~

[Volledige vorige handleiding — alleen historische referentie](doc/manual-baseline-20261120/README.nl.md)
