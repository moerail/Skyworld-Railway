# SkyRail Suite

[Home](README.md) | [中文](README.zh.md) | [English](README.en.md) | **Nederlands**

**Nederlandse uitgave. Zelfstandig leesbaar: aanvullende documentatie is niet nodig om deze handleiding te gebruiken.**

Een suite voor treinbesturing, spoorweginfrastructuur, treinbeveiliging in schaduwbedrijf en verkeersleidingsweergave in Minecraft / Folia. SkyRail Suite is de nieuwe repositorynaam voor de SkyTrain Suite-ontwikkellijn; bestaande pluginnamen, commando's en gegevensmappen blijven gelijk.

Deze handleiding beschrijft de lokale broncodebasis die op **13 september 2026** is gedocumenteerd. De Nederlandse versie is opgesteld op **14 september 2026**, voor serverbeheerders, machinisten, spoorbouwers en pluginontwikkelaars. Besproken toekomstplannen zijn niet automatisch gerealiseerde functies.

> **Beperking van deze ontwikkelversie:** de suite berekent, verdeelt en toont MA/EoA in schaduwbedrijf, maar ATP grijpt op basis daarvan niet in op de remmen. Een geaccepteerde aanvraag, een ogenschijnlijk vrij spoor op PCC of een online RBC-indicator biedt geen garantie dat doorrijden veilig is. Noodremming bij verlies van de machinist, handmatige noodremming en het vasthouden van de rem in RECOVERING zijn afzonderlijke, wel actieve besturingsfuncties.

**Voor TrainCarts-ontwikkelaars:** TrainCarts dient als referentie voor delen van de bordinterface en de bediening. Dit document claimt geen volledige TrainCarts-compatibiliteit, gelijkwaardige fysica of ondersteuning van iedere TC-uitbreiding. STF `switch` is niet TC `switcher`. TC en BKCommonLib zijn geen vereiste; laat niet twee plugins dezelfde mijnkar tegelijk besturen.

De code en documentatie van SkyRail Suite vallen onder de MIT-licentie: Copyright (c) 2026 Skyworld Minecraft Server contributors. De beheerder is bereikbaar via GitHub-account moerail. De volledige licentie wordt meegeleverd met het bronpakket en alle vier plugin-JAR's. Logo's en personageafbeeldingen, waaronder day_logo.png en night_logo.png, vallen niet onder deze codelicentie; er wordt geen nieuwe toestemming verleend voor hergebruik of verspreiding van deze afbeeldingen. Controleer de beeldrechten voordat pakketten met deze afbeeldingen worden gepubliceerd.

De bestaande STF-resources bevatten META-INF/NOTICE-TrainCarts.txt met de behouden MIT-verklaring en referentiecommit 9813810aa7e751d3a00d3d087186d44f6e90df10 voor het compatibiliteitswerk aan borden. Deze verklaring blijft ongewijzigd behouden in de broncode en STF-JAR; de copyrightvermelding van de suite vervangt deze niet.

## Inhoud

1. [Doel en componenten](#1-doel-en-componenten)
2. [Installatie en updates](#2-installatie-en-updates)
3. [Rechten en talen](#3-rechten-en-talen)
4. [De eerste handmatig bestuurde trein](#4-de-eerste-handmatig-bestuurde-trein)
5. [Commando-overzicht](#5-commando-overzicht)
6. [Voertuigprofielen en eigenschappen](#6-voertuigprofielen-en-eigenschappen)
7. [Lijnen en wissels bouwen](#7-lijnen-en-wissels-bouwen)
8. [Automatische borden en stationshaltes](#8-automatische-borden-en-stationshaltes)
9. [Schaduw-MA en beveiligingsmodi](#9-schaduw-ma-en-beveiligingsmodi)
10. [SkyPCC-webinterface](#10-skypcc-webinterface)
11. [Geluiden en HMI](#11-geluiden-en-hmi)
12. [STA-contracten en berichten](#12-sta-contracten-en-berichten)
13. [Gegevensopslag en probleemoplossing](#13-gegevensopslag-en-probleemoplossing)
14. [Acceptatietests en vervolgstappen](#14-acceptatietests-en-vervolgstappen)
15. [Technische toelichting voor ontwikkelaars](#15-technische-toelichting-voor-ontwikkelaars)

## 1. Doel en componenten

SkyTrain Suite gebruikt Minecraft als interactieve spoorwegomgeving. Spelers bouwen fysieke sporen en besturen treinsamenstellingen van mijnkarren. De plugins regelen de beweging, herkennen het spoornet en tonen positie, bezetting en rijtoestemming in de cabine en op een verkeersleidingsscherm.

Het project introduceert expliciete besturingsrechten, spoorbezetting, strijdige rijwegen, Movement Authority (MA), End of Authority (EoA) en het bewaren van onzekere waarnemingen. De scheiding van verantwoordelijkheden is geïnspireerd op ETCS. Het is echter **geen SUBSET-026-implementatie, gecertificeerde interlocking of beveiligingssysteem voor echte spoorwegen**.

| Component | Versie | Verantwoordelijkheid |
| --- | --- | --- |
| SkyTrainFolia / STF | `2.1.0-alpha.8` | Treinsamenstelling, beweging en bochten, besturingsrechten, tractie/remming, profielen, borden, fysieke wisselbediening, HMI en geluid |
| STCS | `2.2.0-alpha.1` | Infrastructuur, gerichte RailGraph, lijnkilometrering, plaatsbepaling, bewaard bezettingsregister, schaduw-MA/EoA en lokale wisselcontroles |
| SkyworldTrainAPI / STA | `0.8.0` | Versiegebonden plugincontracten, telemetrie, voertuigwaarnemingen, cabinestatus, rijtoestemmingen en gebeurtenissen |
| SkyPCC | `0.8.1` | Webspoorschema, inspectiepaneel voor treinen/infrastructuur, bezetting/reserveringen, gebeurtenissenlog en geauthenticeerde wisselbediening |

```text
Minecraft spelers / mijnkarren / rails / redstone
                       |
                      STF   Beweging, besturing en uitvoering
                       |
                      STA   Contracten, telemetrie en gebeurtenissen
                       |
                     STCS   Graaf, plaatsbepaling, bezetting, schaduw-MA
                       |
                     SkyPCC Waarneming en bediening via het web
```

Dit schema toont verantwoordelijkheden, geen verplichte opeenvolging van alle aanroepen. Interpolatie in de browser is geen bron voor veilige plaatsbepaling. De browser beslist niet welke spoorresources mogen worden toegewezen.

### Gerealiseerd en gepland

| Onderdeel | Huidige status |
| --- | --- |
| Handmatige besturing, expliciete overname, EB bij verlies van machinist | Gerealiseerd; na iedere instap opnieuw besturing aanvragen |
| Samenstelling, beweging in spoorcoördinaten, weergave bij hoge snelheid | Gerealiseerd; hoge snelheid, regiowissels en combinaties met andere plugins vereisen servertests |
| Pseudo-automatische stationsbediening | MVP met tractie-/remstanden van het profiel, geen complete ATO |
| Graaf, lijntoewijzing, kilometrering en bewaarde bezetting | Gerealiseerd; timeout of chunk-unload bewijst geen vrijgave |
| Online MA/EoA en ruimtelijke reserveringen | Schaduwimplementatie, zonder ATP-remingreep |
| Wisselbediening via het web | Authenticatie, lokale controles en asynchrone PENDING gerealiseerd |
| Snelheidscurves aan boord en ATP-ingreep bij te hoge snelheid/EoA | Niet gerealiseerd |
| Volledige bestemmingsroutering en dienstregeling-ATO | Niet als compleet systeem gerealiseerd; routegegevens zijn geen ingestelde rijweg |
| ETCS-modi FS/SR/SH/SB/TR/PT | Niet gerealiseerd; huidige modi zijn geen volledige vervangers |
| Aparte SIR-, SkyCBI- of Python-RBC-service | Architectuurideeën, geen huidige installeerbare onderdelen |

## 2. Installatie en updates

### Vereisten

- De huidige aanpassingsbasis is **Shiroha / Folia 26.2 met Java 25**. Gebruik een serverbuild die met deze suite is getest.
- STF bevat versieafhankelijke bewegings-/weergavekoppelingen. `folia-supported: true` garandeert niet alle Folia-versies; `api-version: 1.13` betekent niet dat deze binary op Minecraft 1.13 draait.
- PCC werkt in een gewone browser, zonder verplichte clientmod.
- TC/BKCommonLib zijn niet vereist. Voorkom dat meerdere plugins dezelfde mijnkar besturen.

### Eerste installatie

1. Maak een back-up van de wereld en de volledige map `plugins`. Begin op een testserver.
2. Stop de server, plaats de vier onderstaande JAR-bestanden in `plugins` en verwijder oudere JAR-versies van dezelfde plugins.
3. Start eenmaal om de standaardconfiguratie te genereren en controleer of alle vier plugins actief worden.
4. Stop, pas de configuratie aan en start volledig opnieuw. Vervang deze plugins niet via hot-unload.
5. Voer `/st version` en `/stcs status` uit. Open op de servercomputer `http://127.0.0.1:8765/`.

Huidige installatiebestanden:

```text
SkyTrainFolia-2.1.0-alpha.8.jar
STCS-2.2.0-alpha.1.jar
SkyworldTrainAPI-0.8.0.jar
SkyPCC-0.8.1.jar
```

STF/STCS declareren STA als zachte afhankelijkheid, maar installeer alle vier voor de volledige suite. PCC vereist STCS en STA. STF alleen levert niet de volledige graaf-, MA- en verkeersleidingsfunctionaliteit.

### Updateregels

- Gebruik een passende combinatie, vooral als STA-contracten wijzigen. Installeer niet alle historische JAR-bestanden uit `artifacts`.
- Bewaar bestaande gegevens en voeg nieuwe configuratiesleutels toe vanuit de huidige standaardconfiguratie. Ontbrekende sleutels worden niet noodzakelijk automatisch in een bestaand bestand geschreven.
- `/st reload` herlaadt ook treingegevens: **zet eerst alle treinen stil**. Het is geen afzonderlijke geluidsreload.
- Pas STCS/PCC-configuratie toe via een volledige herstart. Deze handleiding introduceert geen niet-bestaande `/stcs reload` of `/skypcc reload`.
- Na wijzigingen in topologie of graafherkenning: laad het betrokken spoor en voer `/stcs rebuild` uit. Een update van alleen geluid of webinterface vereist normaal geen rebuild.
- Vernieuw de browsercache na webupdates. Zet bij terugdraaien binaries, wereld en gegevens uit dezelfde back-up terug, geen willekeurige combinatie van registerversies.

## 3. Rechten en talen

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

## 4. De eerste handmatig bestuurde trein

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
| `/st lang zh\|en\|fr\|jp` | Spelertaal |
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
| `/stcs ma demand` | `stcs.ma` en huidige machinist; schaduw-MA aanvragen |
| `/stcs ma release` | `stcs.ma` en huidige machinist; reserveringen vóór de trein vrijgeven, niet de bezetting onder de trein |
| `/stcs ma status` | `stcs.admin`; MA-/blokkeerdiagnose |
| `/stcs admin status` | `stcs.admin`; beveiligingsstatus van de trein waarin men zit |
| `/stcs admin isolate true\|false` | Treinbeveiligingskanaal isoleren |
| `/stcs admin bypass true\|false` | Bewaking overbruggen |
| `/stcs admin shadow true\|false` | Schaduwtestmodus |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`; bewaarde bezetting/voertuigwaarnemingen |
| `/stcs rebuild` | `stcs.admin`; graaf uit geregistreerde infrastructuur herbouwen |
| `/stcs export` | `stcs.admin`; huidige graaf exporteren, niet opnieuw scannen |
| `/stcs switch info` | `stcs.use`; dichtstbijzijnde wissel binnen 8 blokken |
| `/stcs switch change` | `stcs.admin`; nabije wissel omzetten |

Het spelerscommando is **`demand`, niet `request`**. Interne gebeurtenissen kunnen nog `MA_REQUESTED` heten. `/sta` en `/skypcc` bieden vooral hulp/versie, geen tweede verzameling rijcommando's.

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
| `automatic.*` | Parameters van de bestaande doelsnelheidsregeling, niet de complete stationstrategie |

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

## 7. Lijnen en wissels bouwen

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

## 8. Automatische borden en stationshaltes

Station/spawn/destroy volgen op TC geïnspireerde conventies. De railkoppeling gebruikt de bordkolom onder de rail en bevestigingsrelaties, niet een willekeurige bol rondom de rail. **Ondersteuning van deze vormen betekent geen volledige compatibiliteit met TC-expressies, afstandsborden of uitbreidingen.**

Een handmatig bestuurde trein wordt niet door station/destroy overgenomen. Stop, laat de machinist `/st release` uitvoeren en stel expliciet `/st property <train> mode auto` in. ISOLATED, RECOVERING en niet-vrijgegeven handmatige overname kunnen automatisch gebruik eveneens blokkeren.

Gebruikelijke koppen: `[stf]` gebruikt redstone, `[+stf]` is continu actief, `[!stf]` geïnverteerd, `[-stf]` uitgeschakeld. De parser kent ook vormen voor stijgende/dalende flanken; de daadwerkelijke activering hangt af van de actie.

### Station

```text
[+stf]
station
5
continue 40kmh
```

| Regel | Betekenis |
| --- | --- |
| 1 | Kop/activering |
| 2 | station, eventueel ondersteunde vertrekafstand/-tijd/-versnelling en stopoffset |
| 3 | Halteertijd; kaal getal in seconden, ook `5s`, `100t`, `00:05` |
| 4 | Richting/snelheid, bijvoorbeeld `continue 40kmh` of `reverse 0.4`; zonder eenheid blokken/tick |

Een vrijgegeven, automatische en duwbare trein kan op het station worden geduwd en daarna vertrekken. Vooruitmelding vereist bruikbare STA/STCS-lijn-/stationqueries. Op het bord stoppen bewijst niet dat vooruitdetectie is gelukt.

De MVP gebruikt voertuigstanden: hogere tractie bij vertrek, N/P1 nabij de doelsnelheid, geleidelijk minder remming bij nadering en B7 in stilstand. Geen onmiddellijke snelheidstoewijzing en geen garantie van nauwkeurig stoppen voor ieder profiel en iedere halteafstand.

STF `settings.station-look-ahead-blocks` is standaard 8192; `station-launch-speed` 0.4 blokken/tick. Het laatste deel van het stoppen heeft aparte dockingparameters. Dit staat los van STCS MA-vooruitkijken. Test eerst langzaam met ruime halteafstanden.

### Spawn en Destroy

```text
[stf]
spawn 0.0
mmm

```

Genereer met gecontroleerde redstone drie gewone mijnkarren. Regel 2 accepteert `spawn [velocity] [interval]`, met bijvoorbeeld `00:30` als interval; regels 3 en 4 vormen samen het patroon. Basissymbolen: `m` gewoon, `s` kist, `p` oven, `h` trechter, `t` TNT, naast ondersteunde sjabloonpatronen. Gebruik aanvankelijk geen TNT.

Sjablonen voor automatisch genereren moeten expliciet in auto zijn opgeslagen. Laat niet continu treinen op een onbeveiligde lijn verschijnen.

```text
[+stf]
destroy


```

Destroy verwijdert daadwerkelijk entiteiten. Test op een apart spoor met back-up; een handmatig bestuurde trein hoort niet te worden verwijderd. Test eerst station, daarna spawn/destroy afzonderlijk.

## 9. Schaduw-MA en beveiligingsmodi

| Term | Betekenis in deze suite |
| --- | --- |
| MA / Movement Authority | Schaduwrijtoestemming langs een toegestaan gericht pad |
| EoA / End of Authority | Einde van de huidige rijtoestemming, niet noodzakelijk lijneinde |
| Credit | Resterende padafstand tot EoA, niet de rechte afstand |
| Occupancy | Spoorbezetting volgens voertuigwaarnemingen/bewaard bewijs |
| Reservation | Reservering vóór de trein, geen gecertificeerde rijwegvergrendeling |
| RBC link | Status van het schaduwinformatiekanaal, geen bewijs van een radio-RBC of werkzame ATP |

### Werkwijze voor de machinist

1. Controleer topologie, wissels, plaatsbepaling en het echte testtraject.
2. Stap in, `/st drive`, stop en kies richting in een modus die aanvragen toestaat.
3. `/stcs ma demand`; controleer resultaat/reden, niet alleen acceptatie van de aanvraag.
4. De BossBar toont de resterende MA, de HMI EoA-lijn/kilometrering en PCC de gereserveerde intervallen.
5. Regel snelheid en stoppen handmatig. `/stcs ma release` geeft de reserveringen vóór de trein vrij.

Treinen zonder machinist verkrijgen niet actief nieuwe MA. Een stilstaande trein met machinist is iets anders. MA vrijgeven wist geen bezetting onder de trein en is geen stopcommando.

### Toestandsmachine

| Status | MA/kanaal | Actief gedrag |
| --- | --- | --- |
| SHADOW | Schaduwaanvragen/herkenning toegestaan | Geen ATP-ingreep op MA/snelheid |
| BYPASS | Kanaal blijft; schaduw-MA kan behouden/aangevraagd worden | Bewaking overbrugd, communicatie niet geïsoleerd |
| ISOLATED | Nieuwe aanvragen geweigerd, kanaal aan boord geïsoleerd | Alleen-lezen kilometrering en STA-telemetrie blijven, automatische borden uit |
| RECOVERING | Geen nieuwe geldige MA aan boord | Rem vasthouden, tractie geweigerd, expliciete volgende modus vereist |

Modi worden met treingegevens opgeslagen. Een toepasselijke schakelaar op `false` zet RECOVERING. Een doelmodus op `true` vereist RECOVERING, recente waarnemingen van de complete trein en stilstand. Niet zomaar tijdens het rijden naar een andere modus schakelen.

Beheerder in de betreffende trein, bijvoorbeeld:

```text
/stcs admin shadow false
/stcs admin status
```

Na stilstand van de complete trein:

```text
/stcs admin bypass true
```

Teruggaan: `bypass false`, stoppen, `shadow true`. Een moduswijziging schakelt tractie uit/remt, maar veroorzaakt geen automatisch vertrek. Na isolatie bewaarde grondreserveringen zijn geen bruikbare MA aan boord en geen grond voor verlenging.

### Drie afstandsinstellingen

In STCS:

```yaml
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  margin-meters: 2.0
```

| Sleutel | Betekenis |
| --- | --- |
| `look-ahead-meters` | Zoeklimiet; niet alles wat gevonden wordt, wordt gereserveerd |
| `lock-distance-meters` | Naderingsafstand waarbinnen schaduw-MA een wissel mag passeren; verder weg stopt die ervoor, geen bewijs van fysieke vergrendeling |
| `max-authority-distance-meters` | Werkelijke Credit-limiet, ook beperkt door vooruitkijken |
| `margin-meters` | Marge vóór een obstakel/conflict, geen complete remweg |

De oude `ma.horizon-meters` levert compatibiliteitswaarden voor ontbrekende nieuwe sleutels. Stel de drie expliciet in bij nieuwe configuraties. STF `switch-approach-distance`, stationvooruitkijken en BossBar-schaal blijven afzonderlijk.

### Opvolgen, parallelle rijwegen en wisselgebieden

Ruimtelijke MA gebruikt fysieke railcellen en intervallen binnen verbindingen, niet een complete balise-tot-balise-verbinding als exclusief bezit. Tegengestelde gerichte verbindingen delen dezelfde fysieke resource.

- Opvolgende treinen horen begrensd te worden door de voorafgaande bezettings-/reserveringsgrens, niet steeds door de vorige balise.
- Parallelle rijwegen zonder gedeelde resources/conflicten horen samen te kunnen bestaan; niet het hele wisselgebied vergrendelen wegens nabijheid.
- De niet-bereden wisseltak hoort niet automatisch onder de treinbezetting te vallen.
- Vóór binnengaan moet voorbij het wisselgebied ruimte zijn voor de hele trein; anders wachten vóór de ingang.
- Onbekende graaf-/wisselstatus, ontbrekende voertuigen en bewaard bewijs kunnen toewijzing blijven blokkeren. Een visueel vrij spoor rechtvaardigt niet het overslaan van controles.

Voertuigprojectie en asynchrone consistentie blijven conservatief, geen bewezen oplossing voor treinintegriteit of de volledige door de trein bestreken ruimte. Obstakels worden langs het pad per kwart blok bemonsterd, maar de resources zijn fysieke railcellen. Dit is schaduwtoewijzing op blokschaal, geen gecertificeerde continue moving-block-ATP. Reserveringen eindigen bij het toegekende interval. Een gedeelde wisselcel kan een kort uiteinde van een niet-gebruikte tak kleuren zonder die hele tak te reserveren.

Resource-ID's gebruiken `cell@world:x:y:z`. Oude bezettingsrecords voor hele verbindingen blijven conservatief blokkeren totdat een complete, recente waarneming ze vervangt. Unload, herstart, ontbrekende voertuigen en een afwijkende graaf wissen het bewijs niet vanzelf. Een offline trein met oude records kan dus nog een groter gebied blokkeren. Gebruik bij teruggaan naar een oudere versie niet zomaar een nieuwer register zonder bijpassende back-up.

## 10. SkyPCC-webinterface

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  control-enabled: false
  control-token: ''
  update-mode: auto
  poll-interval-millis: 1000
  sse-keepalive-seconds: 15
```

Open `http://127.0.0.1:8765/` op de servercomputer. Op een andere computer verwijst dat adres naar die computer zelf. Gebruik voor afstandsbediening een lokale SSH-tunnel; publiek luisteren is geen beveiligingsoplossing.

### Weergave

- Chinees/Engels/Frans/Japans, lichte/donkere thema's en meegeleverde dag-/nachtlogo's.
- Lijnfilter, zoom, passend beeld en tekstschaal.
- Treinen die op plaatsbepaling wachten blijven in de lijst; bewaarde regels zijn geen recente posities.
- Kaartlabels: `<ritnummer> | <treinnaam> | <snelheid> km/h`, met een aanduiding als het ritnummer ontbreekt.
- Treininspectie en camera volgen/annuleren: ATP-modus, MA/EoA, reden, richting, rijrichtingkeuze, hendel, machinist, kilometrering, verbinding en gegevensleeftijd.
- Wisselnamen hebben vet zwart op geel; aparte pijlen: paars rechtdoor, oranjegeel afbuigend.
- Klik op balises, wissels of verbindingen voor infrastructuurinspectie. Bevestigde hoofdlijnkilometrering wordt getoond; anders afstanden naar aangrenzende graafknopen/poorten zonder kilometrering te verzinnen.
- Wisselstatus is een ontvangen momentopname, geen nieuwe fysieke controle door de browser.
- Bezet, bevroren/onzeker, schaduwgereserveerd en niet-toegewezen hebben eigen kleuren. Grijs betekent niet bewezen vrij.
- Operations log ondersteunt ernstfiltering, inklappen en naar boven vergroten.

### Wissels bedienen

Zet `control-enabled` aan, stel een privé willekeurig token van **minstens 32 tekens** in en herstart. Voer het via de bedieningsinterface in, nooit via URL, screenshot of openbaar log.

Selecteer en inspecteer een wissel, bevestig daarna omzetten. De aanvraag bevat graafrevisie, identiteit, verwachte/doelstand en positie. STCS controleert lokaal; STF voert uit.

- Onverwante verre treinen blokkeren niet automatisch, maar lokale bezetting, reserveringsconflicten, onzekerheid en afwijkende stand/positie kunnen dat wel.
- Een ongeladen locatie kan PENDING worden, waarna asynchroon laden en opnieuw valideren in de verantwoordelijke regio volgt. PENDING is geen succes en geen toestemming om door te rijden.
- Alleen een correct token is onvoldoende: loopback en same-origin worden eveneens gecontroleerd.
- Publieke alleen-lezen toegang vraagt eigen toegangscontrole; waarnemingstoegang en bedieningsgeheimen zijn verschillende zaken.

### Gebeurtenissen

Het log bevat overname/vrijgave/verlies van besturing, wisselomzettingen, vermoedelijk openrijden van wissels, EB-activering, MA-aanvragen/vrijgaven en moduswijzigingen. Modusgebeurtenissen noemen uitvoerder en oude/nieuwe status; de beheerder is niet noodzakelijk de machinist.

STA bewaart standaard de laatste 500 gebeurtenissen van de huidige sessie, geen permanent auditarchief. Niet iedere vloeiende MA-update wordt een gebeurtenis. Vermoedelijk openrijden is geen volledig bewezen foutdiagnose.

## 11. Geluiden en HMI

Alleen de machinist krijgt de MA-BossBar. STF `settings.cab-ma-bar-range-meters` is standaard 300 m en wijzigt alleen de schaal; de tekst toont de echte afstand. De ATP-snelheidslimiet in de zijbalk blijft een aanduiding van een nog niet gerealiseerde functie, geen bestaande snelheidscurve.

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
| shrinking | `minecraft:entity.experience_orb.pickup` | Resterende meeschuivende MA begint tijdens rijden af te nemen |
| low | `minecraft:block.note_block.pling` | Weinig resterende afstand |

Gebruik Java Edition-ID's `namespace:path`; zonder namespace geldt minecraft. Eigen ID's vereisen een resourcepack bij de client. Grenzen: volume 0..4, pitch 0.5..2, count 1..5, interval-ticks 1..200.

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

## 12. STA-contracten en berichten

STA biedt voornamelijk Java-services binnen de server-JVM, niet automatisch een Python TCP/WebSocket-RBC. PCC levert de bestaande HTTP/SSE-waarneming en beperkte wisselbedieningsgateway.

| Contract/gegevens | Hoofdproducent | Hoofdafnemer | Betekenis |
| --- | --- | --- | --- |
| v2 TELEMETRY_REPORT / 1001 | STF | STCS/abonnees | Fysieke telemetrie |
| v2 TRACK_REPORT / 1002 | STCS | PCC/abonnees | Graafpositie gekoppeld aan de oorspronkelijke waarneming |
| v2 TRAIN_REMOVED / 1003 | Opruiming bij de bron | Registers/abonnees | Geen toestemming om bezettingsbewijs te wissen |
| v2 RailNetworkService | STCS | STF/anderen | Graaf, navigatie, positie, stationvooruitkijken |
| v3 ConsistObservation | STF | STCS | Voertuigwaarnemingen/levenscyclus |
| v3 RailwayEvent | STF/STCS | PCC/abonnees | Bedrijfsgebeurtenissen |
| v4 DriverDeskService | STF | STCS | Machinist, besturingsrecht, ATP-modus |
| v4 ShadowAuthorityService | STCS | STF/PCC | Niet-uitvoerbare MA/EoA en secties |
| v4 SwitchControl | Aanvrager zoals PCC; STCS controleert, STF voert uit | Aanvrager | Versie-/stand-/positiecontrole en PENDING |

Er zijn drie generieke v2-berichttypen, maar de API bevat daarnaast afzonderlijke momentopnamen, gebeurtenissen en services. Die vallen niet allemaal onder die drie typen.

v2-headers bevatten version, kind, source, sessionId, sequence, emittedAt en trainId. Kwaliteitswaarden zijn onder andere VALID, UNLOCATED, STALE, EXPIRED, GRAPH_CHANGED, SOURCE_UNAVAILABLE en SCALE_MISMATCH. Gebruik geen coördinaten zonder identiteit, volgorde en kwaliteit mee te nemen.

v4-schaduwmomentopnamen hebben `simulationOnly=true` en `executable=false`. Een MA bevat pad, EoA-verbinding/offset, resterende afstand en waarnemingsherkomst. Secties kunnen `fromMeters/toMeters` bevatten; één verbinding kan meerdere intervallen hebben. Een interval is geen bezetting van de hele verbinding.

Het midden van de leidende kar is geen bewezen treinfront; nominale treinlengte is geen integriteitsbewijs; snelheid bij nominale 20 TPS is geen werkelijke snelheid in kloktijd bij lag. De contracten zijn geïnspireerd op ETCS, **geen SUBSET-026-berichtcodering of interoperabiliteit**.

### PCC HTTP-eindpunten

| Eindpunt | Inhoud |
| --- | --- |
| `GET /api/v1/graph` | RailGraph |
| `GET /api/v1/trains` | Treinweergave |
| `GET /api/v2/messages` | Telemetriemomentopname |
| `GET /api/v3/railway-events` | Gebeurtenissen |
| `GET /api/v4/shadow-ma` | Schaduw-MA/secties |
| `GET /api/v1/config` | Openbare webinstellingen, niet het controletoken |
| `GET /api/v1/events` | SSE |
| `POST /api/v4/switch` | Geauthenticeerde wisselbediening |

Eindpunt- en JAR-versies verschillen. Speel weergavemomentopnamen niet opnieuw af als uitvoerbare rijtoestemming. Dit is een interface-overzicht, geen volledig gegenereerde SDK-/schemareferentie. Een externe client moet het exacte berichtformaat en de contracten van de gebruikte build valideren vóór het verzenden van commando's. Lege sectie-eindpunten behouden met name de oude betekenis van een hele verbinding; expliciete eindpunten begrenzen een interval.

## 13. Gegevensopslag en probleemoplossing

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
| auto maar station doet niets | Machinist, expliciete release, modus, redstone, bordkolom en blokkeerreden |
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

## 14. Acceptatietests en vervolgstappen

### Minimale regressiechecklist

1. Vier plugins laden, versies/hulp en configuratie kloppen.
2. Iedere instap vereist drive; passagiers erven geen besturing; machinistverlies geeft EB; herverbinden herstelt geen oude tractie.
3. Modus false geeft herstel; herstel blokkeert tractie; doel true vereist stilstand; herstart bewaart status.
4. Test recht spoor, zijspoor, overloopwissels, rug-aan-rug origins, spoor voorbij End en hangborden; lijntoewijzing blijft lokaal.
5. Test rijdend/stilstaand/ongeladen/herstart; bewaard bewijs verdwijnt niet zonder grond.
6. Test zonder machinist, demand/release, opvolgen, tegenrijconflicten, parallelle rijwegen, wisselgebiedcapaciteit en achterzijdevrijgave.
7. Test lokale/PCC-omzetting, conflictweigering, ongeladen PENDING, echte railvorm en hendel/redstone.
8. Controleer machinist-BossBar, EoA-kilometrering, intervalkleuren, infrastructuurinspectie, logvergroting, vier talen/thema's.
9. Controleer toekenning/sprong/afname/lage afstand/vrijgave, geen herhaalde vloeiende verlengingsmelding, geen vertraagd geluid naar een vorige machinist.
10. Handmatig rijden wordt niet automatisch overgenomen; release + auto activeert station, spawn/destroy apart testen.

De offline Python-testbank test topologie, richting, bezetting, reserveringen en EoA, maar niet Bukkit-levenscycli, Folia-planning, netwerk of echt remgedrag. Dit zijn spelregressies, geen spoorwegcertificering.

M0-contracten/modi en M1-waarneming/behoud zijn geïmplementeerd en met spelers getest. M2 bevat nu online schaduw-MA en ruimtelijke verfijningen. Eerdere acceptatie vervangt geen regressietest en bewijst geen ATP-gereedheid.

Een volgende stap is **snelheidscurves in schaduwbedrijf aan boord**: MA/EoA en profielremparameters gebruiken voor weergave/logging, zonder remingreep. Ingrijpen volgt pas na validatie.

Voor echte ATP blijven nodig: volledige treinbegrenzing/consistentie, MA-identiteit/bevestiging/intrekking, beleid bij contactverlies/bevriezen/bypass, snelheidsbeperkingen/remmodellen, onderbouwde resourcevrijgave en foutinjectietests.

## 15. Technische toelichting voor ontwikkelaars

De repository bevat vier modulemappen: `SkyTrainFolia`, `STCS`, `STA` en `SkyPCC`. Gedeelde gecompileerde code staat in `shared`, ontwikkelnotities in `doc`. Raadpleeg per module `plugin.yml` of het version-commando; de repositorynaam wijzigt de pluginnamen niet.

### Beweging en Folia-grenzen

STF gebruikt spoorcoördinaten en voertuigafstanden in plaats van uitsluitend de snelheid van de leidende kar naar volgende karren te kopiëren. Positiecorrectie, vloeiende passagiersbeweging en weergavesynchronisatie zijn afzonderlijke onderdelen. Dit garandeert geen onbeperkte veilige snelheid: bochten, hellingen, regiowissels en entiteitsverwijdering blijven belangrijke tests.

Fysieke handelingen aan entiteiten/blokken moeten Folia's verantwoordelijke entiteits-/regioschedulers respecteren. Asynchrone graaf-/MA-berekeningen gebruiken momentopnamen; dat geeft geen vrijbrief om vanuit werkthreads willekeurige Bukkit-objecten te benaderen. Chunkbeschikbaarheid, graafkennis en actuele fysieke wisselvalidatie zijn verschillende statussen. Een geladen chunk bewijst niet dat een eerder onbekende wissel al gevalideerd is.

Minecraft-updates kunnen entiteitsinternals, pakket-/weergavekoppelingen en planningsaannames wijzigen terwijl gewone commando-/configuratiecode gelijk blijft. De betreffende adapters moeten tegen de nieuwe serverbuild worden gecontroleerd en getest. Abstractie verkleint het wijzigingsgebied, maar maakt de suite niet onderhoudsvrij.

### Afbakening ten opzichte van TrainCarts

- Het huidige bordwerk betreft station, spawn en destroy, met STF/SkyTrain-koppen en de hierboven beschreven interpretatie-/activeringsregels.
- De rail-bordkoppeling volgt een bordkolom-/bevestigingsmodel, zonder aanspraak op iedere TC-bordactie of expressie.
- STF-wisseldefinities beschrijven geometrie/poorten en een uitvoerder. Het zijn geen TC-switcher-expressies voor routekeuze.
- Een vertrouwde stationinterface betekent geen identieke onderliggende regelaar: deze MVP stuurt voertuigstanden aan, met afzonderlijk gedrag voor het laatste deel van de stop.
- Bestemmingsmetadata, adviserende stationvooruitblik, reserveringen en uitvoerbare rijtoestemming zijn verschillende lagen. De aanwezigheid van de ene betekent niet dat de andere af is.

### Build- en validatieomvang

De bronboom bevat `build.ps1` voor build en regressie. Geef `-ServerRoot /path/to/prepared-server` en `-JavaHome /path/to/jdk-25` op, of stel JAVA_HOME in. Het script leest `versions/26.2/shiroha-26.2.jar` en `libraries/` van de server als afhankelijkheden, bouwt eerst STA en daarna de andere modules, en voert Java-tests uit. Het start of wijzigt de server niet en installeert niets op de server. Afhankelijkheden zijn niet meegeleverd. Voor installatie van JAR-bestanden hoeft de broncode niet gecompileerd te worden. Uitvoer gaat naar genegeerde mappen `artifacts/` en `target/`. `package-source.ps1` maakt een broncode-ZIP onder `dist/`.

Automatische controles testen contracten, modusovergangen, ruimtelijke conflicten/behoud en browserweergave. Servertests blijven noodzakelijk voor instappen/machinistlevenscyclus, Folia-regioverantwoordelijkheid, redstone, chunkladen en beweging. Een documentvertaling is geen nieuwe runtimevalidatie of release.

Dit bestand is bewust zelfstandig. Installatie, rechten, ondersteunde commando's, configuratie, bediening, API-grenzen en bekende beperkingen staan hierin zonder afhankelijkheid van een andere README of historische uitgavenotitie.
