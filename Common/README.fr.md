# SkyRail Suite — Manuel détaillé français

**[AJOUT / CORRECTION · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** Les invitations de transfert sont réessayées et les codes expirés renouvelés. Sortie/prise en charge et retour partagent un itinéraire segmenté avec rebroussements, libération MA par STCS et inversion via STA. Le chargement des chunks conserve le cran manuel sans autoriser la traction. [Details](doc/SAL-MANEUVERS-AND-HANDOVER.en.md)

**[CORRECTION · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** Les retours simultanés conservent itinéraire et place ; STCS gère le trafic temporaire par la MA. L’alignement automatique ne retire plus la MA d’un autre train. Dégagement de la place, obstacles stationnés et contrôles de défaut conservés. [Concurrent returns](doc/SAL-RETURN-TRAFFIC.en.md)

**[MODIFIÉ · STF 5.1.3 · 2026-11-26]** La conduite automatique remplace P2/B4 fixes par un départ P4, une traction réduite à proximité de la vitesse cible, le freinage B6 et le maintien B7. Crans configurables ; contraintes MA, ATP, TIMS et véhicule conservées. [Automatic cab handles](doc/AUTOMATIC-DRIVING-HANDLES.en.md)

**[CORRECTION · SAL0.7.6 · 2026-11-26]** Les attentes de retour renouvellent l’itinéraire déjà accepté pour éviter l’expiration1500ms. Vérification2s par défaut sans perdre la cible ; une géométrie corporelle inconnue n’est plus dite changement topologique. Incertitude persistante : FAULT ; vrai changement/espace insuffisant : nouvelle place après arrêt. Motifs détaillés et historique séparé. [Return rechecks](doc/SAL-RETURN-RECHECK.en.md)

**[AJOUT / CORRECTION · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** Coordonnées et direction proviennent du même relevé STF. Incertitude : freinage immédiat et vérification2s ; tolérances bornées configurables de0,75bloc aux limites et1,5bloc pour la projection. Deux relevés indépendants d’une autre branche déclenchent la reprise plus tôt. Aucune nouvelle MA. La cause initiale persiste dans /sal status et les événements PCC. [Localisation policy](doc/SAL-LOCALISATION-TOLERANCE.en.md)

**[AJOUT / CORRECTION · 2026-11-26]** STA2.9.2 / STF5.1.2 / SAL0.7.6 : /sal override <nom ou UUID> est réservé aux administrateurs : freinage, confirmation du retrait MA, sortie SAL puis /st drive. Occupation, TIMS et TR/PT restent conservés. En pente, la localisation utilise railXYZ observé par STF via STA, sans arrondir les coordonnées de l’entité. [Override/slope contract](doc/SAL-OVERRIDE-AND-SLOPES.en.md)

**[AJOUT · SAL0.7.6 · 2026-11-26]** Les appels expliquent l’éligibilité de chaque train, les deux sens, le plafond de temps, les autres trains aux observations périmées/corps inconnus/hors graphe, les voies obstruées et les résultats périmés. Les protections restent inchangées ; le seul maintien TIMS d’un autre train ne bloque pas tout le réseau. Diagnostic borné, affiché par PCC existant.

**[MODIFICATION · SAL0.7.6 / PCC2.6.4 · 2026-11-26]** Les visites utilisent le plus court chemin ferroviaire orienté autorisé ; un long détour ne provoque plus de rejet pour ambiguïté. Les équipements/arêtes imposés restent contraignants. Sens, défauts du graphe, fermeture et MA restent vérifiés. PCC suit les rails réels, même pour le préfixe confirmé d’un brouillon inachevé ; seuls les liens non confirmés sont estimés. [Path policy](doc/SAL-ROUTE-PATHS.en.md)

**[AJOUT / MODIFICATION · 2026-11-26]** PCC2.6.4 / SAL0.7.6 / STA2.9.2 : la validation désigne le premier segment en défaut par indices, noms et ID. Les deux visites sont marquées ; cliquer le diagnostic pour y accéder. Corriger puis vérifier à nouveau. Réordonner par la poignée ≡ à droite, souris ou tactile ; un seul envoi au relâchement, aucun en cas d’annulation. Les flèches clavier fonctionnent sur la poignée. UUID, arrêt et rebroussement restent liés à la visite. Enregistrer→Valider→Publier reste nécessaire.

**[CORRECTION · PCC2.6.4 · 2026-11-26]** Le rafraîchissement conserve les cibles SAL et la liste inchangées. Sur écran étroit, l’éditeur reste visible sans train sélectionné. Les clics SAL ne déclenchent plus le déplacement de carte ; l’aperçu ne masque plus les arrêts. Une panne temporaire conserve le travail et la saisie ; les données périmées désactivent l’écriture. Les vrais conflits exigent toujours une résolution manuelle. Après remplacement de PCC : Ctrl+F5.

## **[RUPTURE DE COMPATIBILITÉ · 2026-11-26] Arrêts publics et affectation partagée**

STA2.9.2 / STF5.1.4 / STCS4.3.6 / SAL0.8.0 / PCC2.6.4.

La ligne4 de station devient un nom d’arrêt unique ; poser le panneau ne complète plus un itinéraire. awake utilise la ligne3 pour l’itinéraire et la ligne4 pour l’arrêt de départ. Les anciens awake restent désactivés jusqu’à réécriture. Les anciens station reçoivent des noms uniques : vérifier /sal station list. Les panneaux depot et les rattachements existants restent conservés.

Les nouvelles lignes utilisent GLOBAL avec groupes préférés facultatifs. RESTRICTED exige au moins un groupe autorisé ; les anciennes lignes gardent cette restriction. PCC propose les arrêts partagés, le mode, la pénalité, le plafond d’approche et la file des décisions.

Les anciennes consignes sur l’appartenance des station, le dépôt obligatoire et la syntaxe depot/ligne de awake sont remplacées par cette section. [§8](#section-8). [Contrat partagé (anglais)](doc/SAL-SHARED-DISPATCH.en.md) · [Procédure de recette en chinois](../SkyATO-Lite/TEST.zh.md)


**[CORRECTION · 2026-11-25]** STF5.1.2 / SAL0.7.6 : la demande FS/MA attend le choix avant/arrière. Un train arrêté recouvrant le marqueur depot reçoit un code aléatoire à six chiffres après3s ; `/sal handover` le demande immédiatement, `/sal handover <code>` confirme, puis `/st release` transfère. Le retour automatique reste centré.


**[RUPTURE · 2026-11-25]** Le garage aligne désormais le milieu du train. Le centre prévu est ajusté uniquement dans les limites de sa place ; un train trop long doit utiliser une autre place. Les marqueurs voisins partagent la distance suivant la voie avec un intervalle libre. Voie à un accès : places profondes prioritaires ; deux accès : évaluation des deux approches autorisées. Les trains qui obstruent le parcours sont pris en compte. PCC affiche capacité, accès, limites et décalage. Repositionner manuellement les anciens trains arrêtés par leur tête ; station ne change pas.

STA2.9.2 / STF5.1.4 / STCS4.3.6 / SAL0.8.0 / PCC2.6.4. [中文契约](doc/SAL-CENTRE-STABLING.md) · [English contract](doc/SAL-CENTRE-STABLING.en.md)


## **[2026-11-24] Ajout / correction : affichage PCC 2.4.1**

~~Contour de focus natif du navigateur autour des voies et équipements.~~ La sélection suit désormais la polyligne réelle ou un petit cercle autour de l’équipement, avec une épaisseur fixe à l’écran. Le zoom ne grossit plus le contour ; la sélection à la souris et le focus clavier restent visibles.

À côté des commandes A− / A+, **H− / H+** règle la taille de surbrillance (50%–250%). Cliquer sur le pourcentage rétablit100% ; le carré de couleur permet de choisir la couleur. Ces préférences sont conservées dans ce navigateur ; les commandes fonctionnent aussi sans stockage.

La couche des équipements propose **Balise, END, ORIGIN et Aiguillage**. La case principale sélectionne ou désélectionne tout ; chaque sous-case reste indépendante. Une sélection partielle est indiquée sur la case principale. Symboles, libellés et flèches d’aiguillage sont filtrés ensemble ; fond de voie et couche SAL restent indépendants.

Ces réglages modifient uniquement l’affichage, sans changer RailGraph, équipements, lignes, MA ou occupation. Actualiser une ancienne page avec Ctrl+F5.


## **[AJOUT / MODIFICATION · 2026-11-24] Carte PCC unifiée et édition**

Un seul RailGraph et une seule vue, avec couches équipements, SAL, lignes et circulation. Entrer dans l’éditeur avec le jeton des aiguilles ; choisir une ligne à modifier ou au moins un dépôt existant pour créer un brouillon.

Enregistrer depot avant la ligne. Choisir balises, aiguilles, arêtes orientées et stations de cette ligne ; ordonner les visites, arrêts et rebroussements sans modifier les panneaux. Enregistrer, vérifier et publier séparément ; les tâches existantes gardent leur version.

Une édition concurrente jeu/PCC gèle les deux côtés, même après redémarrage. Sauvegarde, vérification, publication et suppression sont refusées jusqu’à résolution manuelle. Adopter une proposition ne la publie pas. Terminer côté jeu par `/sal route close <ligne>` ; publish réussi ferme la session jeu.

`/sal route create Line1 maintown` · `/sal route depot Line1 maintown,otherdepot` · `/sal route resolve Line1 DISCARD`

[Carte PCC unifiée et édition](doc/PCC-ROUTE-EDITING.en.md)


[中文详细手册](README.zh.md) · [Detailed English manual](README.en.md) · [Manuel détaillé français](README.fr.md) · [Uitgebreide Nederlandse handleiding](README.nl.md) · [日本語詳細マニュアル](README.ja.md) · [Подробное руководство на русском](README.ru.md) · [文語體鐵道運轉詳細規程](README.ja.classical.md)

Distribution du 2026-11-24, consolidée à partir du Manuel détaillé précédent et du code final. Les explications inchangées sont conservées. Les libellés en gras signalent les ajouts, modifications, suppressions et consultations ; le texte barré conserve les anciennes instructions abandonnées.

## Changements incompatibles à lire en premier

- La chaîne automatique STF station/cruise/start/stop/spawn/destroy/property et son scrutateur sont supprimés, sans option de réactivation. Les anciens panneaux et fichiers restent présents mais inactifs.
- ~~`/st property demo mode auto` / `mode automatic`~~ est supprimé : SAL gère l’exploitation automatique ; STF conserve conduite manuelle, actionneurs et équipements.
- ~~`/st release` seul autorise la reprise automatique~~ : le conducteur doit confirmer le transfert à SAL sur une place du dépôt, puis libérer explicitement le même bail.
- ~~MA fantôme par défaut, ATP inactif~~ : une nouvelle session demande FS avec protection exécutoire ; SAL est soumis aux mêmes MA/ATP. Sans MA valide, le frein reste maintenu. Le mode fantôme reste un essai explicite.
- ~~`scan.only-loaded-chunks: false` charge les chunks pendant la construction~~ : maintenance uniquement dans les chunks chargés, ancienne valeur migrée vers true. Déchargement ne signifie ni rupture ni libération.
- Remplacer les cinq plugins ensemble. Conserver mondes, compositions, RailGraph, état SAL, reçus de destruction et tous les registres d’occupation. Restaurer programmes et données du même instant en cas de retour arrière.

**[AJOUT · 2026-11-21]** Livrables : parcours SAL, sorties/rentrées au dépôt, transfert manuel/automatique, RailGraph incrémental, affichage MA/ATP exécutoire et carte d’exploitation PCC. Commandes : préfixe SAL bleu `[SkyATO-Lite]`, STF gris, STCS jaune. Seuls les anciens préfixes par défaut sont migrés. La page version inclut SAL et corrige l’ancienne annonce ATP inactif.

| Fichier / composant | Version | Responsabilité |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.9.2** | Contrats typés, télémétrie et services de conduite/régulation |
| SkyTrainFolia / STF | **5.1.4** | Compositions, physique, tickets de chunks, conduite, TIMS, ATP et actionneurs |
| STCS | **4.3.6** | Infrastructure, graphe, localisation, occupation, réservations, MA et arbitrage des itinéraires |
| SkyATO-Lite / SAL | **0.8.0** | Plans d’exploitation, appel, arrêts, titres passagers, dépôt et transfert ; aucun accès direct aux entités |
| SkyPCC / PCC | **2.6.4** | Cartes Web infrastructure/exploitation, listes de dépôt et opérations contrôlées |

## Sommaire

1. [Objectif, composants et livrables](#section-1)
2. [Installation, migration et retour arrière](#section-2)
3. [Permissions et langues](#section-3)
4. [Premier train manuel](#section-4)
5. [Référence des commandes](#section-5)
6. [Profils et propriétés](#section-6)
7. [Lignes et appareils de voie](#section-7)
8. [SAL : lignes, panneaux et transfert de conduite](#section-8)
9. [MA exécutoire, FS par défaut et ATP](#section-9)
10. [Cartes infrastructure et exploitation SkyPCC](#section-10)
11. [Sons et IHM](#section-11)
12. [Contrats STA et interfaces HTTP](#section-12)
13. [Persistance et dépannage](#section-13)
14. [Réception et limites](#section-14)
15. [Développement, sources et distribution](#section-15)

<a id="section-1"></a>

## 1. Objectif, composants et livrables

**[MODIFICATION · 2026-11-21]** Une ligne d’exploitation SAL est un plan ordonné d’arrêts et de parcours ; une ligne d’infrastructure STCS porte attribution et kilométrage. SAL consomme RailGraph ; STCS arbitre les itinéraires/MA et fait exécuter les actions via STA/STF. Cet ATP de jeu n’est pas un système ETCS certifié.

| Fichier / composant | Version | Responsabilité |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.9.2** | Contrats typés, télémétrie et services de conduite/régulation |
| SkyTrainFolia / STF | **5.1.4** | Compositions, physique, tickets de chunks, conduite, TIMS, ATP et actionneurs |
| STCS | **4.3.6** | Infrastructure, graphe, localisation, occupation, réservations, MA et arbitrage des itinéraires |
| SkyATO-Lite / SAL | **0.8.0** | Plans d’exploitation, appel, arrêts, titres passagers, dépôt et transfert ; aucun accès direct aux entités |
| SkyPCC / PCC | **2.6.4** | Cartes Web infrastructure/exploitation, listes de dépôt et opérations contrôlées |

**[AJOUT · 2026-11-21]** Livrables : parcours SAL, sorties/rentrées au dépôt, transfert manuel/automatique, RailGraph incrémental, affichage MA/ATP exécutoire et carte d’exploitation PCC. Commandes : préfixe SAL bleu `[SkyATO-Lite]`, STF gris, STCS jaune. Seuls les anciens préfixes par défaut sont migrés. La page version inclut SAL et corrige l’ancienne annonce ATP inactif.

~~**[MODIFICATION · 2026-11-21]** Les préférences `<ligne>:up|down`, la réservation complète des sections à voie unique, la recherche générale avec plusieurs rebroussements intermédiaires et le démon Python restent des projets. La recherche actuelle au dépôt peut comparer les deux sens à l’arrêt ; elle ne garantit pas toute manœuvre complexe.~~

**[MISE À JOUR · SAL 0.8.0]** Les parcours de prise en charge/retour avec jusqu’à4 inversions sont implémentés. Préférences up/down, réservation complète de voie unique, manœuvres générales sans borne et démon Python restent des projets. [Details](doc/SAL-MANEUVERS-AND-HANDOVER.en.md)

[TRAFFIC-PLANNING-DAEMON-DESIGN](doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md)

<a id="section-2"></a>

## 2. Installation, migration et retour arrière

**[MODIFICATION · 2026-11-21]** Java25, Shiroha/Folia26.2 ou26.3 adaptés ; navigateur standard pour PCC. Aucun mod client ni TrainCarts/BKCommonLib requis. Toute version Folia n’est pas nécessairement compatible.

1. Extraire tout le ZIP, sauvegarder mondes et plugins, arrêter le serveur. Copier les cinq JAR de plugins/ dans les plugins du serveur et retirer leurs anciennes versions. Ne pas y copier les documents.
2. Démarrer pour générer les valeurs par défaut et contrôler les cinq modules ; arrêter, fusionner la configuration et redémarrer complètement. Pas de déchargement à chaud.
3. Vérifier `/st version fr`, `/stcs status`, `/sal help`. Ouvrir http://127.0.0.1:8765/ sur la machine du serveur et forcer le rechargement du navigateur. Accès distant : proxy et authentification administrés séparément.
4. `/st reload` recharge aussi les trains : tous doivent être arrêtés. Configuration STCS/PCC par redémarrage. Les tâches SAL sauvegardées reprennent en RECOVERY_REQUIRED ; après inspection, resume commence par un retour au dépôt.
5. Ne pas vider de registre pour mettre à jour. SHA256 identifie les fichiers. L’archive Source et les documents/images liés sont inclus ; revenir à une sauvegarde cohérente complète.

```text
SkyworldTrainAPI-2.9.2.jar
SkyTrainFolia-5.1.4.jar
STCS-4.3.6.jar
SkyATO-Lite-0.8.0.jar
SkyPCC-2.6.4.jar
```

[SHA256](../Latest/SHA256.json) · [LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md)

<a id="section-3"></a>

## 3. Permissions et langues

**[AJOUT · 2026-11-21]** Libération seule, descente, déconnexion, mouvement, changement de conducteur/place ou expiration ne transfèrent rien. État manuel persistant. Conducteur : skyatolite.driver et skytrain.use ; administration : skyatolite.admin.

| Permission | Par défaut | Fonction principale |
| --- | --- | --- |
| `skytrain.use` | Tous | Requêtes de base, langue/unités personnelles et commandes normales de conduite |
| `skytrain.admin` | OP | Rames, propriétés, modèles, télécommande, appareils de voie, sauvegarde/rechargement |
| `stcs.use` | Tous | Infrastructure proche et état du graphe |
| `stcs.ma` | Tous | Demande/libération de MA par le conducteur ; prise de conduite valide et mode autorisé également requis |
| `stcs.admin` | OP | Enregistrement de l’infrastructure, reconstruction/export, diagnostics d’occupation/MA, modes de protection et appareils de voie |

Une permission n’est pas une prise de conduite. `stcs.ma` n’autorise pas un joueur à demander une MA pour un autre conducteur. Pour administrer le mode de protection, l’administrateur doit être assis dans le train cible. Les commandes dépendant de la position ne sont pas toutes utilisables depuis la console.

L’accès en écriture du PCC emploie un jeton distinct ainsi que des contrôles de boucle locale et de même origine ; il n’hérite pas des permissions OP de Minecraft. Ne communiquez pas le jeton de commande aux voyageurs ordinaires.

```text
/st lang fr
/st lang en
/st lang zh
/st lang jp
/st help 2 fr
/stcs help 1 fr
/sta version ja
/skypcc help
```

`ja` et `jp` sélectionnent tous deux le japonais. Les quatre commandes racines proposent `help` et `version`. Les textes d’IHM, de protection et de MA suivent la préférence linguistique STF du joueur. Certains anciens diagnostics administratifs restent uniquement en chinois ou en anglais. **La documentation néerlandaise n’ajoute pas le néerlandais aux langues du jeu.**

PCC conserve séparément son choix de langue dans le navigateur.

<a id="section-4"></a>

## 4. Premier train manuel

**[MODIFICATION · 2026-11-21]** Demander FS par défaut n’est pas obtenir FS. Le conducteur prend explicitement son bail ; SAL doit aussi recevoir une MA exécutoire. SB/reprise/indisponibilité maintiennent le frein.

Placez plusieurs wagonnets sur une voie d’essai rectiligne et chargée, sans autre train à proximité. Employez un petit rayon de recherche afin de ne pas inclure un véhicule d’une voie contiguë.

En tant qu’administrateur :

```text
/st scan 6
/st create demo 6
/st info demo
/st property demo trainnumber T001
/st property demo mode manual
```

Montez dans un véhicule, puis :

```text
/st lang fr
/st drive
/st forward
/p1
/n
/b3
/b7
```

- `P1..P4` : crans de traction ; `B1..B7` : crans de frein de service ; `EB` : freinage d’urgence (FU).
- `/n` est la position neutre du manipulateur traction-frein ; `/st neutral` est le neutre de l’inverseur de marche.
- L’inversion du sens est soumise à un contrôle de faible vitesse/arrêt. Elle ne remplace pas le freinage.
- Reprenez la conduite avec `/st drive` après chaque embarquement. Les autres commandes de conduite ne le font pas implicitement.
- Descendre, se déconnecter, mourir, changer de train ou perdre l’entité du siège de conduite révoque la conduite, annule la traction et applique le FU. La conduite n’est pas transmise automatiquement à un autre voyageur.
- `/st release` libère explicitement la conduite et applique le FU. Le fonctionnement automatique exige en plus l’arrêt et le réglage explicite `mode auto`.

### Commande par barre d’accès rapide

Sélectionnez **l’emplacement 5** avant d’activer `/st hotbar`. L’activation ne commande ni N ni le desserrage d’un frein déjà appliqué. Les changements d’emplacement suivants sélectionnent les crans :

| Emplacement | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Cran | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

La désactivation avec `/st hotbar` n’exige pas l’emplacement 5. `/st cab` ouvre le pupitre graphique. Les deux nécessitent d’avoir d’abord pris la conduite.

<a id="section-5"></a>

## 5. Référence des commandes

`<...>` indique un paramètre obligatoire et `[...]` un paramètre facultatif. Voici les formes recommandées, sans énumérer tous les anciens alias.

### Conducteur et requêtes

| Commande | Fonction |
| --- | --- |
| `/st help [page] [language]`, `/st version [language]` | Aide et versions installées |
| `/st list`, `/st info [train]` | Trains, état et motifs de blocage des panneaux automatiques |
| `/st scan [radius]` | Rechercher les wagonnets proches |
| `/st drive`, `/st release` | Prendre/libérer la conduite |
| `/st cab`, `/st hotbar` | Interface de pupitre/barre rapide |
| `/st forward`, `/st neutral`, `/st backward` | Inverseur de marche |
| `/st p1` à `/st p4`, `/st n`, `/st b1` à `/st b7`, `/st eb` | Manipulateur ; raccourcis racines tels que `/p1`, `/n`, `/b7` également disponibles |
| `/st horn`, `/st bell` | Avertisseur/timbre |
| `/st lang zh\|en\|fr\|jp\|ru` | Langue personnelle |
| `/st speedunit kph\|mph\|block/tick` | Unités d’affichage, alias `/st unit` ; ne change pas les unités numériques des commandes d’administration |
| `/st balise [info]`, `/st origin [info]`, `/st end [info]` | Infrastructure proche |
| `/st mileage [train]` | Point kilométrique ; nommer un train exige la permission d’administrateur |

### Administration STF

| Commande | Fonction |
| --- | --- |
| `/st connect [radius]` | Atteler les wagonnets proches |
| `/st create <name> [radius]` | Créer une rame |
| `/st append <train> [radius]` | Ajouter des véhicules ; utilisez des rames fixes pour les premiers essais de contrôle-commande |
| `/st unlink` | Délier le wagonnet le plus proche |
| `/st remove <train>` | Supprimer la définition du train, pas contourner les blocages STCS |
| `/st start <train> [speed]`, `/st stop <train>` | Ancienne commande de vitesse cible/arrêt, pas une approbation de MA |
| `/st reverse <train>` | Inverser le sens, sous réserve des contrôles d’arrêt |
| `/st speed <train> <speed>`, `/st maxspeed <train> <speed>` | Vitesse cible/plafond en blocs par tick |
| `/st spacing <train> <spacing>` | Espacement des véhicules, soumis aux réglages globaux d’espacement serré |
| `/st property <train> <key> [value]` | Lire sans valeur ; écrire avec une valeur |
| `/st tag <train> add\|remove\|list [tag]` | Étiquettes |
| `/st owner <train> add\|remove\|list [player]` | Métadonnées du propriétaire |
| `/st route <train> set\|add\|clear\|list [destination...]` | Liste de destinations, pas un tracé automatique complet des itinéraires |
| `/st savedtrain list` | Lister les modèles |
| `/st savedtrain save <train> <template>` | Enregistrer un modèle |
| `/st savedtrain spawn <template> [train]` | Faire apparaître près du joueur |
| `/st switch list`, `/st switch scan [radius]` | Appareils de voie enregistrés/recherche dans les zones chargées ; 32 blocs par défaut, 128 au maximum |
| `/st switch info`, `/st switch set straight\|diverging` | Interroger/demander l’état de l’appareil de voie le plus proche dans un rayon de 8 blocs |
| `/st switch remove`, `/st switch cleanup` | Supprimer la définition la plus proche/nettoyer les enregistrements invalides ; `remove` laisse les blocs du monde intacts |
| `/st balise list`, `/st origin list`, `/st end list` | Listes de l’infrastructure |
| `/st clearkm <line>` | Effacer l’étalonnage kilométrique, pas l’occupation |
| `/st admin release\|p1..p4\|b1..b7\|n\|eb <train>` | Télécommande administrative explicite |
| `/st syncstatus` | Diagnostics de synchronisation de l’affichage |
| `/st save`, `/st reload` | Sauvegarder/recharger ; arrêter tous les trains avant le rechargement |

La syntaxe des propriétés accepte également `property <train> get <key>` et `property <train> set <key> <value>`. Les exemples emploient la forme courte.

### STCS

| Commande | Permission / signification |
| --- | --- |
| `/stcs help [page] [language]`, `/stcs version [language]` | Aide/version |
| `/stcs inspect` | `stcs.use` ; repère dans un rayon de 3 blocs |
| `/stcs status` | `stcs.use` ; nœuds/arêtes/révision du graphe, pas le mode ATP du train |
| `/stcs ma fs` | **[MODIFICATION]** Demander FS par défaut n’est pas obtenir FS. Le conducteur prend explicitement son bail ; SAL doit aussi recevoir une MA exécutoire. SB/reprise/indisponibilité maintiennent le frein. |
| `/stcs ma release` | `stcs.ma` et conducteur actuel ; libérer les réservations en avant, pas l’occupation du train |
| `/stcs ma status` | `stcs.admin` ; diagnostics d’autorisation/blocage |
| `/stcs admin status` | `stcs.admin` ; état de protection du train occupé |
| `/stcs admin isolate true\|false` | Isolement du contrôle-commande |
| `/stcs admin bypass true\|false` | Contournement de la surveillance |
| `/stcs admin shadow true\|false` | Essais fantômes |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin` ; occupation conservée/observations des véhicules |
| `/stcs rebuild` | **[MODIFICATION]** STCS est propriétaire du graphe ; changements locaux de voies/équipements déclenchent une mise à jour locale. L’état courant d’une aiguille ne change pas sa topologie. rebuild commence près de l’administrateur, uniquement dans les zones chargées. |
| `/stcs export` | `stcs.admin` ; exporter le graphe actuel, sans nouvelle recherche |
| `/stcs switch info` | `stcs.use` ; appareil de voie le plus proche dans un rayon de 8 blocs |
| `/stcs switch change` | `stcs.admin` ; manœuvrer l’appareil de voie proche |

~~La commande du joueur est bien **`demand`, et non `request`**. Les événements internes peuvent toujours s’appeler `MA_REQUESTED`. `/sta` et `/skypcc` exposent principalement l’aide et la version, et non une seconde série de commandes de conduite.~~

**[MODIFICATION · 2026-11-21]** `/stcs ma fs` · `/stcs ma demand` (alias)

**[CONSULTATION · 2026-11-21]** `/sal help` · `/sal status` · `/sal route list Line1` · `/sal train home`

Libération seule, descente, déconnexion, mouvement, changement de conducteur/place ou expiration ne transfèrent rien. État manuel persistant. Conducteur : skyatolite.driver et skytrain.use ; administration : skyatolite.admin.

<a id="section-6"></a>

## 6. Profils et propriétés

### Profil commun au serveur

Dans `plugins/SkyTrainFolia/config.yml` :

```yaml
settings:
  default-vehicle-profile: minecraft-comfort
  server-speed-limit-kmh: 420.0
```

Les fichiers se trouvent dans `plugins/SkyTrainFolia/vehicles/<id>.yml` :

| ID | Référence |
| --- | --- |
| `crh380b` | CRH380B |
| `cr400bf` | CR400BF |
| `keikyu-n1000` | Série Keikyu New 1000 |
| `minecraft-comfort` | Profil fictif orienté jeu |

Les profils réels sont des approximations de réglage, pas des données de performances certifiées. La sélection actuelle s’applique **à tout le serveur** ; il n’existe pas de commande par train `/st property <train> profile ...`.

Un profil représente un train de référence complet. Le nombre de wagonnets Minecraft ne redimensionne pas automatiquement sa masse ou ses performances de référence. La longueur réelle de la rame compte néanmoins pour l’occupation, l’arrêt en gare et la capacité requise au-delà d’une bifurcation.

| Champ du profil | Effet |
| --- | --- |
| `physics-mode` | Modèle physique ; les profils fournis utilisent `force` |
| `mass-tonnes` | Masse totale du train de référence |
| `max-speed-kmh` | Plafond du véhicule, sans garantie que la traction puisse l’atteindre |
| `traction.acceleration-mps2.p1` à `p4` | Accélération brute à basse vitesse avant résistances |
| `traction.base-speed-kmh` | Vitesse de base de la courbe de traction |
| `traction.field-weakening-speed-kmh`, `minimum-ratio` | Réduction de traction à grande vitesse/rapport minimal |
| `brake.deceleration-mps2.b1` à `b7` | Décélération brute du frein de service |
| `brake.emergency-mps2` | Contribution du freinage d’urgence |
| `resistance.rolling-mps2` | Résistance au roulement |
| `resistance.air-mps2-at-100-kmh` | Résistance aérodynamique de référence à 100 km/h, proportionnelle au carré de la vitesse |
| `resistance.grade-mps2` | Facteur d’accélération dû à la déclivité |
| `control.direction-change-speed-kmh` | Seuil de faible vitesse pour l’inversion du sens |
| `automatic.*` | **[MODIFICATION]** Compositions, physique, tickets de chunks, conduite, TIMS, ATP et actionneurs / V_target |


Les anciennes clés dispersées telles que `drive-power-acceleration-p1` ne constituent pas l’interface actuelle d’édition des profils. Sauvegardez avant toute modification et arrêtez tous les trains avant de recharger, ou modifiez à l’arrêt puis redémarrez.

### Pourquoi un train peut rester limité à 130 km/h

Le plafond du véhicule, celui du serveur et le `maxspeed` persistant de chaque train peuvent tous limiter la vitesse. Changer le profil par défaut n’efface pas le plafond existant d’un train.

À l’échelle nominale de 1 bloc = 1 mètre et 20 TPS, `km/h = blocs/tick × 72` :

```text
/st property demo maxspeed
/st maxspeed demo 5
```

Ici, `5` signifie 360 km/h, et non 5 km/h ni la chaîne `5kmh`. Des plafonds véhicule/serveur inférieurs restent applicables, et l’équilibre traction/résistances peut empêcher d’atteindre le plafond. Les unités d’affichage personnelles ne changent pas l’analyse des commandes.

### Identité du train et numéro de circulation

```text
/st property demo name test01
/st property test01 displayname Test Train
/st property test01 trainnumber G001
/st property test01 trainnumber
/st property test01 trainnumber clear
/st property test01 mode auto
/st property test01 pushable true
```

- `name` identifie le train géré dans les commandes. Utilisez le nouveau nom après renommage.
- `trainnumber` est un numéro de circulation distinct. Les zéros initiaux sont conservés ; 32 caractères au maximum ; aucun caractère de commande ni `|` ; l’unicité n’est pas imposée. `clear` ou `-` le supprime.
- `displayname` est une métadonnée de présentation, pas l’UUID interne.
- Autres propriétés : `destination`, `collision`, `playersenter`, `playersexit`, `pickupitems`, `invincible`, `allowplayertake`, `requirepoweredcart`, `sound`, `keepchunksloaded`, `gravity`, `friction`, `waitticks`, `speed`, `maxspeed`, `spacing`.
- `pushable=true` ne suffit pas à lui seul : le conducteur, une reprise manuelle non libérée ou l’état de marche peuvent encore interdire la poussée. Consultez `/st info`.
- Les métadonnées de propriétaire, d’étiquette ou d’itinéraire n’impliquent ni routage complet, ni régulation, ni isolement des accès selon le propriétaire.

<a id="section-7"></a>

## 7. Lignes et appareils de voie

**[MODIFICATION · 2026-11-21]** STCS est propriétaire du graphe ; changements locaux de voies/équipements déclenchent une mise à jour locale. L’état courant d’une aiguille ne change pas sa topologie. rebuild commence près de l’administrateur, uniquement dans les zones chargées.

### Topologie physique et affectation de ligne

RailGraph conserve les nœuds, ports, transitions permises, arêtes orientées et géométries. Les noms de ligne et points kilométriques sont des annotations ; ils ne doivent pas se propager arbitrairement par tous les appareils de voie raccordés.

- Utilisez systématiquement des noms de ligne uniques, tels que `test_up` et `test_down`, sur les balises concernées.
- Origin/End délimitent l’affectation à la ligne ; ils ne sont pas nécessairement des extrémités physiques de voie. La voie au-delà doit rester physiquement raccordée.
- Une balise de voie de service sans nom de ligne marque cette voie sans propager indéfiniment l’identité de la voie principale.
- Si plusieurs chemins relient les mêmes ancrages nommés, ajoutez une balise nommée sur la branche de voie principale voulue. La position actuelle d’un appareil de voie ne définit pas définitivement l’affectation de ligne.
- Un point kilométrique inconnu reste inconnu ; la proximité d’un autre repère ne suffit pas à l’inventer.

### Panneaux STCS à quatre lignes

Placez les panneaux de manière à associer sans ambiguïté le rail voulu, notamment près de voies parallèles.

Origine :

```text
[STCS]
origin
test_up
right
```

Balise de voie principale :

```text
[STCS]
balise
test_up
0010
```

Fin :

```text
[STCS]
end
test_up
right
```

Balise de voie de service, ligne 3 vide :

```text
[STCS]
balise

5010
```

La ligne 4 d’Origin pointe dans le sens positif de la ligne ; celle d’End pointe vers l’extérieur, l’intérieur de la ligne étant opposé. Les directions relatives s’interprètent par rapport à la face du panneau, pas à la gauche/droite de l’écran PCC. `signal` peut être enregistré comme nœud, mais n’implique pas un ATP fondé sur la signalisation.

### Panneaux d’appareil de voie STF

STCS importe les appareils de voie STF enregistrés, et non chaque rail courbe. L’enregistrement reconnaît `[stf]`, `[+stf]`, `[SkyTrain]`, `[+SkyTrain]` sans tenir compte de la casse. L’activation redstone et l’existence de l’infrastructure sont distinctes : ajouter `+` ne corrige pas généralement une arête manquante.

Exemple avec une disposition frontale existante :

```text
[SkyTrain]
switch
fl
S01
```

La ligne 4 nomme l’appareil de voie ; l’UUID reste son identité. **Le `switch` STF n’est pas le `switcher` TC.**

Les panneaux muraux prennent en charge les dispositions latérales/sous la voie existantes. La disposition frontale `fl/fr` attend le rail pivot deux blocs au-dessus du bloc support du panneau. Un panneau suspendu au plafond admet panneau → bloc support au-dessus → rail pivot encore au-dessus, aligné selon une direction cardinale et non en diagonale. Employez un rail ordinaire apte à changer de position comme pivot. Un levier lié suit les changements de position et notifie les blocs redstone voisins ; validez en jeu les circuits complexes.

Après la construction :

```text
/st switch scan 32
/st switch list
/stcs inspect
/stcs rebuild
/stcs status
/stcs export
```

La reconstruction part de l’infrastructure enregistrée ; ce n’est pas une recherche illimitée dans le monde. Vérifiez les enregistrements après WorldEdit. Par défaut, seules les zones chargées sont analysées, la topologie existante étant conservée lorsque l’analyse est incomplète. Une voie jamais observée ne peut être reconstruite à partir de rien.

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

Cette distance d’analyse n’est pas la distance d’anticipation MA. Les longues sections nécessitent des ancrages adaptés ou une limite d’analyse supérieure. Gardez des échelles de distance STF/STCS cohérentes, normalement 1 bloc par mètre.

<a id="section-8"></a>

## 8. SAL — Arrêts publics et affectation partagée

**[RUPTURE DE COMPATIBILITÉ · 2026-11-26]** La ligne4 de station devient un nom d’arrêt unique ; poser le panneau ne complète plus un itinéraire. awake utilise la ligne3 pour l’itinéraire et la ligne4 pour l’arrêt de départ. Les anciens awake restent désactivés jusqu’à réécriture. Les anciens station reçoivent des noms uniques : vérifier /sal station list. Les panneaux depot et les rattachements existants restent conservés.

Les nouvelles lignes utilisent GLOBAL avec groupes préférés facultatifs. RESTRICTED exige au moins un groupe autorisé ; les anciennes lignes gardent cette restriction. PCC propose les arrêts partagés, le mode, la pénalité, le plafond d’approche et la file des décisions.

Enregistrer les station/depot publics, ouvrir l’éditeur PCC avec le Token existant, choisir équipements, sens et visites, puis Enregistrer→Valider→Publier. Préférences facultatives en GLOBAL, groupes obligatoires en RESTRICTED. Le conflit jeu/PCC gèle toujours les deux côtés jusqu’à résolution manuelle.

| Sign | 1 | 2 | 3 | 4 |
| --- | --- | --- | --- | --- |
| Depot | [SAL] | depot | maintown | L5-1 |
| Public stop | [SAL] | station | 15 | MainStart |
| Call | [SAL] | awake | MainTour | MainStart |
| Title | [SAL] | message | Welcome | 5 |
| Cruise | [SAL] | property | v_target | 40kmh |

`[SkyATOL]` = `[SAL]`.

Ancienne syntaxe supprimée: ~~station: 15 / Line1 (route ownership)~~; ~~awake: maintown / Line1~~; ~~/sal awake maintown Line1~~.

Un appel à B transforme A→B→C→A en B→C→A→B, puis retour au dépôt. Pour un arrêt répété, copier nom@visitUUID depuis PCC ; la dernière répétition du premier arrêt n’est qu’un alias de fermeture. Les tâches en cours conservent leur publication.

```text
/sal station list
/sal route create MainTour
/sal route direction MainTour east
/sal route bind MainTour <deviceUUID>
/sal route choose 1
/sal route list MainTour
/sal route remove MainTour 3
/sal route supply MainTour global 30 120
/sal route depot MainTour maintown
/sal route depot MainTour none
/sal route preview MainTour
/sal route validate MainTour
/sal route publish MainTour
/sal route close MainTour
/sal route resolve MainTour DISCARD
/sal route retire MainTour
/sal route delete MainTour
/sal train home maintown
/sal awake MainTour MainStart
/sal status
/sal stop <trainUUID>
/sal resume <trainUUID>
/sal finish <trainUUID>
```

Après500ms par défaut, réutiliser une prise en charge de même version/départ, sinon affecter les trains SAL disponibles par appariement maximal de coût minimal. Chemin praticable, obstacles, observation complète récente, conducteur et tâches sont des contraintes impératives. Coût : secondes d’approche estimées + pénalité hors préférence (30s) + pénalité de stock facultative ; plafond120s. L’attente influence la priorité ; expiration après120s avec motif.

Un thread JVM dédié ne calcule que des instantanés immuables. Avant engagement, SAL revérifie graphe, publication, validité, position/sens/composition, groupe et bail de conduite. STCS reste seul arbitre MA/itinéraires ; STA transmet et STF agit sur les entités. Aucun démon Python externe.

Les trains SAL déjà en attente ne demandent pas de nouveau consentement. Un train manuel arrêté sur depot obtient un code à six chiffres après3s, ou immédiatement avec /sal handover. Confirmer /sal handover <code>, puis saisir /st release. Une simple libération ne vaut pas consentement. /sal release puis /st drive rend la conduite manuelle après retour.

Un redémarrage annule les demandes non engagées et maintient les tâches en RECOVERY_REQUIRED. Ni défaut de stockage/localisation ni absence de MA ne sont contournés. GLOBAL peut rentrer dans tout groupe adapté ; RESTRICTED reste dans les groupes autorisés. Centrage et contrôle du gabarit complet sont conservés. Estimation sans modèle complet de congestion/accélération ; pas de redistribution préventive ni de rebroussements multiples arbitraires.

[Contrat partagé (anglais)](doc/SAL-SHARED-DISPATCH.en.md) · [Procédure de recette en chinois](../SkyATO-Lite/TEST.zh.md)

<a id="section-9"></a>

## 9. MA exécutoire, FS par défaut et ATP

**[MODIFICATION · 2026-11-21]** Demander FS par défaut n’est pas obtenir FS. Le conducteur prend explicitement son bail ; SAL doit aussi recevoir une MA exécutoire. SB/reprise/indisponibilité maintiennent le frein.

FS surveille vitesse/EoA ; SH/SR plafonnés par défaut à40km/h, ou moins selon courbe. TR arrêté : ack→PT, release→SB, inspection puis nouvelle demande. TIMS doit d’abord être réellement rétabli et confirmé.

Une demande initiale par chemin puis renouvellement silencieux ; STCS prolonge MA. Changement de chemin/version, bail expiré ou contrôle perdu exigent une demande valide ; un message tardif ne ressuscite pas la précédente.

Essai fantôme uniquement : simulationOnly=true, executable=false. BYPASS/ISOLATED/RECOVERING conservent leurs verrouillages.

Libérer MA conserve l’occupation du train. Déchargement/délai/déconnexion ne prouvent pas la libération. Suppression explicite : reçu STF complet et session virtuelle nettoyée avant libération ciblée, sans toucher les autres trains.

```text
/stcs ma fs
/stcs ma status
/stcs ma ack
/stcs ma release
```

### Configuration et graphe local

STCS est propriétaire du graphe ; changements locaux de voies/équipements déclenchent une mise à jour locale. L’état courant d’une aiguille ne change pas sa topologie. rebuild commence près de l’administrateur, uniquement dans les zones chargées.

Aux frontières déchargées, conserver les liaisons déjà établies. Une direction jamais vérifiée reste inconnue ; une rupture connue reste bloquée après déchargement. Occupation, état physique et MA restent distincts.

Contrôle chargé toutes les60secondes par défaut, plage1–86400, lot1–64sources. Un contrôle sain ne change pas revision. SAL revérifie les seules dépendances touchées ; les MA gardent leur barrière de version globale.

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

## 10. Cartes infrastructure et exploitation SkyPCC

~~**[AJOUT · 2026-11-21]** Infrastructure : équipements, kilométrage, occupation, MA et commandes. Couche Exploitation : autre canevas et autre vue, sans balises/aiguilles/origines ; sélectionner une ligne SAL ou toutes.~~

**[AJOUT / MODIFICATION]** Un seul RailGraph et une seule vue, avec couches équipements, SAL, lignes et circulation. Entrer dans l’éditeur avec le jeton des aiguilles ; choisir une ligne à modifier ou au moins un dépôt existant pour créer un brouillon.

DRAFT pointillé ; VALIDATED tirets longs si validation/dépendances valides ; PUBLISHED continu si publié et valide. Connecteurs de brouillon estimés explicitement non confirmés.

Choix du dépôt : toutes les places surlignées. Total inclut places invalides signalées. Stationné : train complet, récent, sans TIMS, sur une seule place valide. En exploitation : tâche SAL, même en arrêt, pause ou récupération.

Autres/inconnus : manuel, attente ailleurs ou observation absente. Contour vide ≠ autorisation de place libre. Données périmées/indisponibles affichées comme telles, jamais comme zéro train ni position actuelle.

Clic place pour localiser, nom de train pour détails ; zoom, déplacement, taille, thème et mondes séparés. PCC : zh/en/fr/ja ; STF ajoute ru. Manuels néerlandais et japonais classique sans locale de jeu supplémentaire.

Écritures : web.control-enabled et jeton Bearer ; web.require-https-for-control=true par défaut. false autorise HTTP mais conserve l’authentification. Aucun secret fourni.

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  require-https-for-control: true
```

[PCC-OPERATING-MAP](doc/PCC-OPERATING-MAP.md)

<a id="section-11"></a>

## 11. Sons et IHM

La BossBar réservée au conducteur affiche les informations MA puis `Limite ATP: xx km/h`. La même limite en mode ombre figure sur la deuxième ligne latérale ; une courbe invalide affiche `--`. `settings.cab-ma-bar-range-meters` vaut 300 m par défaut et ne modifie que l’échelle.

### Configuration sonore MA

Au **niveau racine** de la configuration STF, et non sous `settings` :

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

`changed`, `released`, `shrinking`, `low` acceptent les mêmes champs. Fusionnez-les dans la section existante ; ne créez pas de clés YAML en double.

| Indication | Son par défaut | Comportement |
| --- | --- | --- |
| granted | `minecraft:block.anvil.land` | Autorisation demandée accordée ; hauteur 2, deux fois |
| changed | `minecraft:block.anvil.land` | Variation importante ; hauteur 2, une fois |
| released | `minecraft:block.iron_trapdoor.close` | Libération |
| shrinking | `minecraft:block.note_block.bit` | La MA glissante restante commence à diminuer en marche |
| low | `minecraft:block.note_block.pling` | Faible distance restante |

Utilisez les identifiants Java Edition `namespace:path` ; si l’espace de noms est omis, `minecraft` est utilisé. Les identifiants personnalisés nécessitent un paquet de ressources client. Plages : volume 0..4, hauteur 0,5..2, répétitions 1..16, `interval-ticks` 1..200.

Réglages de déclenchement STCS :

```yaml
ma:
  sound:
    enabled: true
    jump-threshold-meters: 20.0
    cooldown-ms: 1500
    low-remaining-meters: 50.0
```

Zéro désactive le seuil de faible distance. Une hystérésis évite les avertissements répétés à la frontière. Une extension régulière normale ne doit pas répéter l’indication de variation. Les indications retardées revérifient la prise de conduite au lieu de continuer à s’adresser à un ancien conducteur.

### Sons de roulement et de freinage

Le volume et la hauteur du roulement varient avec la vitesse : par défaut, silence sous 10 km/h et plafond configuré atteint à 120 km/h. Voir `settings.trackside-running-sound-*`.

Augmenter le freinage ou passer de N/traction au freinage produit un son de serrage ; quitter complètement le freinage produit un son de desserrage. Une réduction partielle du frein ne produit pas un son de desserrage à chaque cran. Voir `settings.brake-sound-*`. Aucun son de moteur de traction n’est actuellement inclus.

Arrêtez tous les trains avant `/st reload` ; cette commande recharge les données, pas seulement l’audio.

<a id="section-12"></a>

## 12. Contrats STA et interfaces HTTP

**[MODIFICATION · 2026-11-21]** STA est un contrat Java local au processus, pas un RBC Python/TCP déployé. Message, Packet et famille sont distincts ; vérifier session, séquence, version et fraîcheur.

Message ATO privé6508 ; SUBSET_139 Packet1031/1032 = hommage31/32+1000. Packet privés5115–5118 : renouvellement, stationnement, transfert. MA : Message1003/Packet1015 ; télémétrie1136.

v8 OperatingNetworkService : affichage en lecture seule, schemaVersion1, jamais une MA. v9 TopologyDependency : dépendances d’arêtes. SAL publie, PCC met en cache hors des requêtes HTTP ; versions JAR/DTO/URL distinctes.

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

## 13. Persistance et dépannage

**[MODIFICATION · 2026-11-21]** state.json SAL conserve panneaux, lignes, home, attente manuelle et tâches avec .bak. Consentements incomplets non conservés au redémarrage. Échec de sauvegarde : appels bloqués. occupancy-ledger.json et shadow-occupancy.json sont distincts ; préserver aussi confirmed-destructions.json.

Chemins relatifs au répertoire du serveur :

| Chemin | Données |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`, `plugins/SkyTrainFolia/vehicles/` | Configuration globale/audio/profils |
| `plugins/SkyTrainFolia/trains.yml` | Rames, propriétés, modes de protection |
| `plugins/SkyTrainFolia/savedtrains.yml` | Modèles |
| `plugins/SkyTrainFolia/switches.yml` | Enregistrements des appareils de voie |
| `plugins/SkyTrainFolia/infrastructure.yml` | Infrastructure/étalonnage STF |
| `plugins/SkyTrainFolia/stations.yml`, `plugins/SkyTrainFolia/automatic-signs.yml` | Enregistrements de gares/panneaux |
| `plugins/STCS/markers.yml` | Enregistrements STCS |
| `plugins/STCS/railgraph.json` | Instantané du graphe, pas registre d’occupation |
| `plugins/STCS/occupancy-ledger.json` | Éléments conservés ; ne jamais supprimer pour forcer l’attribution d’une MA |
| `plugins/SkyPCC/config.yml` | Réglages Web/identifiants privés |

Le graphe reste un graphe logique/export JSON unique, et non un stockage de production fragmenté en un fichier par ligne. Ne coupez pas les raccordements physiques entre lignes en fonction de leur nom. Analysez des copies exportées au lieu de modifier les fichiers actifs du graphe/registre.

| Symptôme | Première vérification |
| --- | --- |
| auto affiché mais gare inactive | Conducteur, libération explicite, mode de protection, redstone, association à la colonne de panneaux et motif de blocage |
| Demande acceptée mais aucune autorisation sur l’IHM | `/stcs ma status` et motif PCC ; acceptation n’est pas attribution |
| SWITCH_UNKNOWN | Validation physique, région chargée/propriétaire, enregistrement et position ; l’existence dans le graphe ne suffit pas |
| FLEET_UNCERTAIN / train conservé non localisé | Registre/UUID ; l’absence sur le plan ne supprime pas ses éléments |
| NO_EXIT_CAPACITY | Espace de sortie pour le train complet, plafond MA, chemin absent/incorrect |
| Des itinéraires parallèles se bloquent | Chemin réel, intervalles spatiaux, anciennes ressources conservées, observations récentes de la rame complète |
| Point kilométrique absent/incorrect | Sens Origin/End, balises nommées, limites des voies de service, ambiguïté du chemin ; charger et reconstruire |
| PCC INVALID_REQUEST | Format/révision/état attendu/position et version interface/serveur, pas nécessairement le jeton |
| Écritures PCC désactivées | Indicateur d’activation, longueur du jeton, boucle locale, origine, dépendances et motif de rejet exact |
| PENDING persistant | Chargement asynchrone et nouvelle validation physique/journaux, pas un succès |
| Problèmes de wagonnets à grande vitesse | Adaptation serveur, mouvements entre régions, autres contrôleurs/suppressions d’entités, pas seulement le plafond de vitesse |

Le déchargement ne prouve pas le dégagement de la queue ; des éléments restaurés ne sont pas une position récente. La qualité RECOVERING du registre diffère également du mode embarqué RECOVERING.

Les rapports doivent comprendre toutes les versions des greffons/du serveur, l’heure, le nom/UUID du train, les identifiants des équipements, les étapes de reproduction, les journaux, le graphe exporté et les diagnostics d’occupation. Supprimez les jetons avant de partager la configuration.

- Appel impossible : publication, front awake, noms, attente manuelle, TIMS, fraîcheur et tâches ; sens initial opposé ne doit pas imposer un retournement manuel.
- Arrêt non relié : RailGraph/routingIssues, rescan local chargé. chunk_not_loaded seul ne prouve pas une rupture.
- Rentrée bloquée : place, longueur complète, chemin, aiguille confirmée et motif MA ; ne pas vider de registre.
- Occupation après suppression : UUID et reçu complet, nettoyage du conducteur virtuel. Entité inconnue ≠ suppression sûre.
- Affichage indisponible : versions/services SAL/STA, revision et fraîcheur ; recharger le navigateur.

<a id="section-14"></a>

## 14. Réception et limites

**[MODIFICATION · 2026-11-21]** Régressions Java indépendantes26.2/26.3 ; vérification de la nouvelle présentation/version et aide SAL. Résultats fournis avec le ZIP.

Déjà vérifiés : service vide unique26.3 ; vraie page26.2 avec line1 publiée et deux places maintown. Régression navigateur : styles, filtres, listes, péremption ;363clés quadrilingues. Pas une réception de toutes les configurations.

À tester sur place : appel sens inverse, aiguilles sortie/rentrée, réemploi première station, concurrence L1/L2, dégagement complet/rebroussement, serveur vide, Title, TIMS/déconnexion/redémarrage, double confirmation et suppression ciblée.

[VALIDATION](../VALIDATION.md)

<a id="section-15"></a>

## 15. Développement, sources et distribution

**[MODIFICATION · 2026-11-21]** Archive Source : cinq modules, ressources, code partagé et tests. Exécuter Common/build.ps1 après extraction avec JavaHome et deux serveurs de test préparés. Latest publié après les deux validations. Aucun cœur Minecraft, monde, jeton, certificat ou configuration privée.

```powershell
./Common/build.ps1 -ServerRoot <server26.2> -ServerRoot26_3 <server26.3> -JavaHome <jdk25>
node SkyPCC/tests/edge-line.cjs
node SkyPCC/tests/i18n.cjs
node SkyPCC/tests/operating-map-browser.cjs
./Common/package-source.ps1
```

Licence MIT du code/des documents et autorisations distinctes des images incluses. Les thèmes ne changent aucune décision MA, action physique ou frontière de thread.

[LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md) · [TrainCarts](https://github.com/bergerhealer/TrainCarts) · [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)

## Ancien manuel et instructions supprimées

**[SUPPRESSION · 2026-11-21]** Anciennes instructions STF supprimées : le texte barré ne doit plus être exécuté.

~~L’interface station/spawn/destroy suit des conventions inspirées de TC. L’association au rail suit la colonne de panneaux sous le rail et les relations d’attache, pas une sphère arbitraire alentour. **La prise en charge de ces formes ne signifie pas une compatibilité complète avec les expressions, panneaux distants ou extensions TC.**~~

~~Un train sous conduite manuelle n’est pas repris par station/destroy. Arrêtez-le, faites exécuter `/st release` au conducteur, puis réglez explicitement `/st property <train> mode auto`. ISOLATED, RECOVERING ou une reprise manuelle non libérée peuvent aussi interdire le traitement automatique.~~

~~En-têtes courants : `[stf]` utilise l’activation redstone, `[+stf]` est constamment actif, `[!stf]` est inversé, `[-stf]` est désactivé. L’analyseur comprend également des formes sur front montant/descendant ; le déclenchement réel dépend de l’action.~~

**Gare**


~~`[+stf]`~~
~~`station`~~
~~`5`~~
~~`continue 40kmh`~~


~~| Ligne | Signification |~~
~~| --- | --- |~~
~~| 1 | En-tête/activation |~~
~~| 2 | station, avec en option les paramètres pris en charge de distance/temps/accélération de lancement et de décalage d’arrêt |~~
~~| 3 | Temps de stationnement ; un nombre seul est en secondes, des formes comme `5s`, `100t`, `00:05` sont aussi admises |~~
~~| 4 | Direction/vitesse, p. ex. `continue 40kmh` ou `reverse 0.4` ; sans unité, la vitesse est en blocs/tick |~~

~~Les trains automatiques peuvent désormais détecter les panneaux Station en suivant les rails réels, sans nom de ligne, balise ni étalonnage STCS. La recherche suit la branche sélectionnée des aiguillages, sans charger de chunks ni détecter les trains précédents. Ce n’est ni un ATP ni une protection anticollision.~~

~~Le repère d’arrêt est le centre du wagonnet de tête dans le sens de marche, augmenté du décalage du panneau ; aucune demi-longueur de rame n’est ajoutée. Depuis 2.1.3, le conducteur virtuel adapte le freinage à la distance restante et laisse rouler sous la courbe. Une reprise lente avec hystérésis intervient uniquement après un arrêt trop court. Le stationnement et HOLD conservent B7 ; la conduite manuelle est inchangée.~~

~~Dans la section STF `settings` existante, ajoutez `station-local-look-ahead-blocks: 256.0`. Plage : 0–1024 blocs ; 0 désactive la recherche locale. Une recherche est effectuée au maximum toutes les 200 ms, uniquement sur les rails chargés relevant du thread Folia courant. Il s’agit d’une portée maximale, pas d’une distance de freinage garantie. L’avis facultatif du graphe STA/STCS conserve `station-look-ahead-blocks` (8192 par défaut). Commencez à basse vitesse avec suffisamment de voie chargée.~~

**Panneau de propriété V_target (Depuis 2.1.2)**

~~Ce panneau modifie la vitesse cible des trains automatiques, pas leur vitesse maximale ni leur mode de conduite. Les trains manuels l’ignorent. Seul `V_target` est autorisé sur ces panneaux ; les autres propriétés passent par les commandes administrateur.~~


~~`[+stf]`~~
~~`property`~~
~~`V_target`~~
~~`60km/h`~~


~~Sans unité, la valeur est en blocs/tick : `0.4`, `8m/s` et `28.8km/h` sont équivalents à 20 ticks/s et un bloc par mètre. La propriété est enregistrée, respecte les plafonds du train/profil et ne démarre pas seule un train arrêté. Une vitesse de départ explicite du panneau Station est prioritaire ; sinon V_target s’applique, puis `settings.station-launch-speed` (0.4 par défaut). `[+stf]` est toujours actif ; `[stf]` dépend du redstone. Commandes administrateur : `/st property demo V_target 60km/h` pour régler et `/st property demo get V_target` pour lire.~~

**Apparition et destruction**


~~`[stf]`~~
~~`spawn 0.0`~~
~~`mmm`~~



~~Utilisez une commande redstone maîtrisée pour faire apparaître trois wagonnets normaux. La ligne 2 accepte `spawn [velocity] [interval]`, avec un intervalle comme `00:30` ; les lignes 3 et 4 sont concaténées en motif d’apparition. Symboles de base : `m` normal, `s` coffre, `p` motorisé, `h` entonnoir, `t` TNT, ainsi que les motifs de modèle pris en charge. N’utilisez pas de TNT pour les premiers essais.~~

~~Les modèles d’apparition automatique doivent être explicitement enregistrés en mode auto. Ne faites pas apparaître continuellement des trains sur une ligne non protégée.~~


~~`[+stf]`~~
~~`destroy`~~




~~Destroy supprime réellement les entités. Testez sur une voie séparée et sauvegardée ; un train sous conduite manuelle ne devrait pas être détruit. Validez d’abord le traitement en gare, puis spawn/destroy séparément.~~

[Ancien manuel complet — référence historique uniquement](doc/manual-baseline-20261120/README.fr.md)

## Complément d’utilisation · SAL 0.8.0 / STF 5.1.4

Arrêtez toute partie du train sur depot pendant3secondes, ou demandez un code par /sal handover. Confirmez sous30secondes avec /sal handover CODE, puis explicitement /st release. Une libération seule ne vaut pas consentement. Les notifications non reçues sont réessayées et les codes expirés renouvelés. Prise en charge/retour : jusqu’à4 inversions avec dégagement de toute la rame ; voies courtes/occupées et protections restent bloquantes. Après redémarrage, confirmation administrateur requise. Le chargement des chunks conserve le cran sans traction effective ; une nouvelle commande neutre/frein reste prioritaire.
