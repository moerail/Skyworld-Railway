# SkyRail Suite

**Joueurs : commencez par le [manuel de conduite](doc/driver/README.fr.md)** pour la montée à bord, la prise de conduite, la barre rapide, la MA fantôme et l'arrêt.

## 4.0.2 : Alertes et rétablissement du service

La plage sonore dépend de la limite ATP actuelle, pas de la vitesse réelle. Pour une limite **≤40 km/h**, y compris le plafond SH par défaut, l'alerte commence **5 km/h sous la limite** et cesse à **8 km/h sous la limite** ou moins. Au-delà de 40, les nouveaux réglages par défaut sont **15 / 18 km/h** ; les réglages existants de la plage normale sont conservés. La priorité de survitesse, le silence à très basse vitesse et le freinage ATP ne changent pas.

Après un arrêt du service MA dû à une erreur E/S, l'administrateur peut utiliser `/stcs admin ma restart` (`stcs.admin`, console acceptée). Le rétablissement vérifie les sources, exige des observations récentes à l'arrêt pour les autorisations exécutables restantes, sauvegarde le registre et le fichier temporaire, puis vérifie une écriture réelle. L'occupation est conservée ; les conducteurs doivent redemander une MA. Aucun frein n'est desserré. Une erreur persistante maintient le service arrêté ; les autres erreurs exigent une investigation et un redémarrage du serveur.

```yaml
shadow-atp:
  warning:
    enter-gap-kmh: 15.0
    clear-gap-kmh: 18.0
    low-speed-limit-kmh: 40.0
    low-speed-enter-gap-kmh: 5.0
    low-speed-clear-gap-kmh: 8.0
```

## M3 expérimental : protection des trains manuels

**Préversion `suite-v4.0.2`.** Les vérifications automatiques ont réussi ; les derniers correctifs attendent une nouvelle validation sur serveur. [Notes de version](doc/releases/suite-v4.0.2.md).

Utiliser ensemble STF **4.0.2**, STCS **4.0.2**, STA **2.0.0** et SkyPCC **2.0.0**. Arrêter le serveur et sauvegarder RailGraph et les registres d'occupation avant de remplacer les composants ; ne pas mélanger les versions. STF reste autonome, mais `Enforced` exige STA et STCS. Le service fantôme v5 reste consultatif ; l'autorisation opérationnelle STA v6 est un canal distinct.

Seuls les **trains manuels** entrent dans l'automate `SB/FS/SH/SR/TR/PT`. Après prise de conduite, le train est en `SB`. `/stcs ma demand` demande `FS`, `/stcs ma sh` une autorisation limitée de manœuvre et `/stcs ma sr` attend l'approbation du PCC ou d'un administrateur jusqu'à un équipement choisi. Une demande acceptée n'est pas encore une MA exécutable. Après un dépassement de l'EoA (`TR`), arrêter le train, utiliser `/stcs ma ack` pour passer en `PT`, puis `/stcs ma release` avant une nouvelle demande. L'administrateur change explicitement de canal à l'arrêt avec `/stcs admin enforce true|false` ; `false` revient à `RECOVERING` avec frein maintenu. Le tableau de bord affiche le canal traduit et le code de mode invariant, par exemple **`Protection active | SR`**.

Les trains automatiques du pseudo-ATO intégré à STF **n'entrent jamais dans cet automate** ; les commandes de mode ou de MA d'un passager ne les convertissent pas en trains `FS/SH/SR`. `shadow-atp` reste en lecture seule ; `active-atp` propose des réglages souples et stricts. Cette fonction expérimentale attend encore une validation sur serveur : **ce n'est ni un ATP certifié, ni un enclenchement certifié, ni une implémentation ETCS**. Un état inconnu ne prouve jamais que la voie est libre. L'approbation SR est un appel de service et un événement SkyRail, pas un nouveau télégramme ETCS ; la MA v6 réutilise Message 1003 / Packet 1015 privés.

### Comportement M3 actuel

En `Enforced`, SH/SR respectent le plafond du mode, fixé par défaut à 40 km/h dans STCS (`ma.sh.speed-kmh` / `ma.sr.speed-kmh`). Son dépassement au-delà d'une faible tolérance déclenche B7 sans attendre le délai de survitesse assoupli de FS ; la courbe vers l'EoA peut imposer une vitesse inférieure. Les avertissements d'approche de limite et de survitesse fonctionnent aussi dans ce canal. Une intervention ATP affiche `ATP B7` ou `ATP EB` dans le panneau latéral et la BossBar du conducteur. Un dépassement d'EoA provoque TR, un message traduit et le signal sonore de freinage d'urgence. Les sons STF se règlent sous `ma-sounds.atp-service` et `ma-sounds.atp-emergency`.

L'approbation SR distingue l'accessibilité de la cible dans le RailGraph sauvegardé de la fenêtre MA actuelle. Dans STCS, `ma.sr.max-target-distance-meters` vaut 5000 m par défaut pour la recherche de cible ; `ma.sr.max-distance-meters` limite chaque autorisation glissante à 120 m par défaut. L'approbation ne réserve ni n'autorise tout le parcours jusqu'à la cible. La géométrie sauvegardée d'une voie simple peut être vérifiée à travers des chunks déchargés ; les arêtes manquantes, aiguilles inconnues, occupations incertaines et conflits continuent de limiter l'attribution ou son extension. Les réponses SR/Trip et les motifs de refus PCC sont traduits en chinois, anglais, français et japonais.

## Mise à jour incompatible précédente : STA v5

L'ancien ensemble v5 utilisait STF **3.0.0**, STCS **3.0.0**, STA **1.0.0** et SkyPCC **1.0.0**. Ce sont des versions historiques, non la cible d'installation actuelle. STF reste utilisable seul ; toujours conserver RailGraph et les registres d'occupation lors d'une mise à niveau.

Message et Packet ont des espaces de numérotation distincts : MA fantôme attribuée **Message 1003 / Packet 1015**, télémétrie **Message 1136**. Les messages privés de suppression, localisation et attente/inactivité utilisent **2001 / 2002 / 2003**. Une suppression ne prouve pas la libération de la voie. Il s’agit d’un clin d’œil conceptuel à SUBSET-026, sans encodage ETCS ni conformité revendiquée. Ce paragraphe décrit l'ancienne base v5, non le canal opérationnel v6. [Migration](doc/STA-V5-MIGRATION.md).

## Diagnostic des passages aux nœuds

`/stcs integrity [nom-du-train|uuid]` nécessite `stcs.admin`. La commande montre la répartition des véhicules entre arêtes, les passages déduits et les incertitudes liées aux observations manquantes. Un arrêt ne fait pas expirer l’occupation. Ce diagnostic en lecture seule reste en mémoire et repart d’une nouvelle référence après redémarrage ; il ne prouve pas le dégagement de la queue du train, ne libère pas l’occupation et n’accorde pas de MA. Ce n’est pas un compteur d’essieux virtuel achevé. Les tests locaux ont réussi ; la validation sur serveur reste nécessaire.

## Mise à jour antérieure : STF 2.1.5 (2026-09-23)

Les courbes en mode ombre ne commandent ni traction ni freinage. Par défaut : arrêt 1 m avant EoA, au plus 5 km/h dans les derniers 5 m, puis diminution continue à zéro. Le modèle en palier utilise les paramètres B7 et les vitesses mesurées, sans compenser l’âge des données par la vitesse maximale théorique.

Configuration dans `shadow-atp` ; `enforcement-enabled: true` est refusé. `shadow-atp.warning` : alerte continue selon les deux plages ci-dessus ; silence sous 0.5 km/h. L'alarme de survitesse prend la priorité au-dessus de la limite et revient à l'alerte normale à 1 km/h sous celle-ci. Seuils configurables ; l'ancien `cooldown-millis` est ignoré. `ma-sounds.near-limit` utilise `minecraft:block.note_block.flute` et `ma-sounds.overspeed` des impulsions plus rapides de `minecraft:block.note_block.bit`, sans pause entre les salves. Arrêter les trains avant `/st reload`.

L'alerte répète six paires Do5-Sol5 par salve jusqu'au franchissement du seuil de désactivation ; une MA qui commence à diminuer produit trois bips courts. `pitch-sequence` remplace `pitch` et `count` répète la séquence (64 notes au maximum). Les anciens réglages sont conservés ; les nouvelles valeurs figurent dans le [guide audio](doc/SHADOW-ATP.md#existing-configurations--旧配置更新).

L’inspecteur du banc lit railgraph et shadow-occupancy.json correspondants sans les modifier ni prouver la libération. Après vérification de la disparition de toute la rame d’origine seulement : `stcs ma clear <UUID-complet> confirm` dans la console. Toutes les preuves en mode ombre de cette identité sont effacées, avec sauvegarde et journal d’audit ; le registre historique M1 reste intact. Un train déchargé n’est pas nécessairement supprimé.

[Accueil](README.md) | [中文](README.zh.md) | [English](README.en.md) | [Nederlands](README.nl.md) | **Français** | [日本語](README.ja.md)

**Édition française complète et autonome : aucun document complémentaire n’est nécessaire pour lire ce manuel.**

Suite d’exploitation ferroviaire, d’infrastructure, de contrôle-commande ferroviaire fantôme et d’affichage de régulation pour Minecraft / Folia. SkyRail Suite est le nouveau nom du dépôt de la branche de développement SkyTrain Suite ; les noms des greffons exécutables, les commandes et les répertoires de données restent inchangés.

Ce manuel décrit l’état du code source local au **23 septembre 2026**. Édition française préparée le **14 septembre 2026**. Il s’adresse aux administrateurs de serveur, conducteurs, constructeurs de lignes et développeurs de greffons. Les idées évoquées pour des développements futurs ne sont pas nécessairement mises en œuvre.

> **Limite de la version de développement :** les MA/EoA fantômes ne commandent aucun freinage. Le canal distinct `Enforced` peut intervenir sur un train manuel uniquement après activation explicite et validation d'une autorisation exécutable ; il reste à valider sur serveur. Une demande acceptée, une voie apparemment libre sur le PCC ou un RBC en ligne ne prouvent pas la sécurité.

**À l’intention des développeurs TrainCarts :** TrainCarts sert de référence pour certaines parties de l’interface des panneaux et des procédures d’exploitation. Ce document ne revendique ni une compatibilité intégrale avec TrainCarts, ni une physique équivalente, ni la compatibilité avec toutes les extensions TC. Le `switch` de STF n’est pas le `switcher` de TC. La suite ne requiert ni TC ni BKCommonLib, et deux systèmes de commande ne doivent pas piloter simultanément le même wagonnet.

Le code et la documentation de SkyRail Suite sont distribués sous licence MIT : Copyright (c) 2026 Skyworld Minecraft Server contributors. Le compte GitHub de contact du mainteneur est [moerail](https://github.com/moerail). La licence complète accompagne le code source et chacun des quatre JAR de greffon. Les logos et illustrations de personnages, notamment `day_logo.png` et `night_logo.png`, sont exclus de cette licence ; aucun nouveau droit de réutilisation ou de redistribution de ces images n’est accordé. Vérifiez leurs autorisations avant de publier un paquet qui les contient.

Les ressources STF existantes comprennent `META-INF/NOTICE-TrainCarts.txt`, qui conserve la notice MIT et le commit de référence `9813810aa7e751d3a00d3d087186d44f6e90df10` pour les travaux de compatibilité des panneaux. Cette notice de tiers est conservée sans modification dans le code source et les JAR STF ; la mention de copyright de la suite ne la remplace pas.

## Sommaire

1. [Objectif et composants](#1-objectif-et-composants)
2. [Installation et mises à niveau](#2-installation-et-mises-à-niveau)
3. [Permissions et langues](#3-permissions-et-langues)
4. [Votre premier train en conduite manuelle](#4-votre-premier-train-en-conduite-manuelle)
5. [Référence des commandes](#5-référence-des-commandes)
6. [Profils de véhicule et propriétés](#6-profils-de-véhicule-et-propriétés)
7. [Construction des lignes et appareils de voie](#7-construction-des-lignes-et-appareils-de-voie)
8. [Panneaux automatiques et arrêts en gare](#8-panneaux-automatiques-et-arrêts-en-gare)
9. [MA fantôme et modes de protection](#9-ma-fantôme-et-modes-de-protection)
10. [Interface Web SkyPCC](#10-interface-web-skypcc)
11. [Sons et IHM](#11-sons-et-ihm)
12. [Contrats et messages STA](#12-contrats-et-messages-sta)
13. [Persistance et dépannage](#13-persistance-et-dépannage)
14. [Essais de réception et prochaines étapes](#14-essais-de-réception-et-prochaines-étapes)
15. [Notes d’implémentation pour les développeurs](#15-notes-dimplémentation-pour-les-développeurs)

## 1. Objectif et composants

SkyTrain Suite utilise Minecraft comme environnement ferroviaire interactif. Les joueurs construisent les voies et conduisent des rames de wagonnets ; les greffons gèrent le mouvement, identifient le réseau ferré et présentent la position, l’occupation et les autorisations en cabine et sur un écran de régulation.

Le projet introduit la prise de conduite explicite, l’occupation des ressources, les incompatibilités d’itinéraires, la Movement Authority (MA), l’End of Authority (EoA) et la conservation des observations incertaines. Il s’inspire de la séparation des responsabilités de l’ETCS, mais **n’est ni une implémentation de SUBSET-026, ni un enclenchement certifié, ni un système ferroviaire réel de sécurité**.

| Composant | Version | Fonction principale |
| --- | --- | --- |
| SkyTrainFolia / STF | `4.0.2` | Rames, mouvement, conduite, aiguilles physiques, IHM et exécution ATP expérimentale des trains manuels |
| STCS | `4.0.2` | RailGraph, localisation, occupation conservée, MA/EoA fantômes et opérationnelles expérimentales |
| SkyworldTrainAPI / STA | `2.0.0` | Contrats inter-greffons, télémétrie, autorisations opérationnelles et événements |
| SkyPCC | `2.0.0` | Affichage de régulation, inspections, événements, commande d'aiguilles et approbation SR |

```text
Joueurs Minecraft / wagonnets / rails / redstone
                         |
                        STF   Mouvement, conduite et actionnement
                         |
                        STA   Contrats, télémétrie et événements
                         |
                       STCS   Graphe, localisation, occupation, MA fantôme
                         |
                       SkyPCC Observation Web et interface opérateur
```

Ce schéma illustre les responsabilités, et non une chaîne d’appels série obligatoire. L’interpolation du navigateur n’est pas une source de position de sécurité, et le navigateur ne décide pas quelles ressources de voie peuvent être autorisées.

### Fonctions réalisées et prévues

| Domaine | État actuel |
| --- | --- |
| Conduite manuelle, prise de conduite explicite, FU sur perte du conducteur | Réalisé ; la conduite doit être reprise après chaque embarquement |
| Rames, mouvement en coordonnées de voie, adaptation de l’affichage à grande vitesse | Réalisé ; les grandes vitesses, transferts de région et combinaisons de greffons tiers doivent encore être validés sur serveur réel |
| Conduite pseudo-automatique en gare | MVP utilisant les crans de traction/freinage du véhicule, pas un ATO complet |
| Graphe, affectation de ligne, point kilométrique, occupation conservée | Réalisé ; expiration ou déchargement ne prouvent pas que la voie est libre |
| MA/EoA en ligne et réservations spatiales | Service fantôme conservé ; permissions opérationnelles expérimentales pour trains manuels |
| Commande Web des appareils de voie | Authentification, contrôles locaux et état asynchrone PENDING réalisés |
| Courbes de vitesse embarquées et intervention ATP pour survitesse/EoA | Courbes fantômes consultatives ; intervention expérimentale `Enforced` pour trains manuels, non validée en sécurité sur serveur |
| Tracé automatique complet des itinéraires et ATO à l’horaire | Système complet non réalisé ; les métadonnées d’itinéraire ne constituent pas un itinéraire établi |
| Modes SkyRail SB/FS/SH/SR/TR/PT pour trains manuels | Automate expérimental, sans revendication de conformité ETCS ; pseudo-ATO exclu |
| SIR, SkyCBI ou service RBC Python séparé | Pistes d’architecture, non composants installables actuels |

## 2. Installation et mises à niveau

### Prérequis

- La base d’adaptation actuelle est **Shiroha / Folia 26.2 avec Java 25**. Utilisez une version serveur testée avec cette suite.
- STF comprend une intégration du mouvement et de l’affichage dépendante de la version. `folia-supported: true` ne garantit pas toutes les versions de Folia ; `api-version: 1.13` ne promet pas que ce binaire fonctionne sous Minecraft 1.13.
- PCC fonctionne dans un navigateur ordinaire. Aucun mod client n’est requis.
- TC/BKCommonLib ne sont pas requis. Évitez que différents greffons commandent le même wagonnet.

### Première installation

1. Sauvegardez le monde et tout le répertoire `plugins`. Commencez sur un serveur d’essai.
2. Arrêtez le serveur, placez les quatre JAR ci-dessous dans `plugins` et retirez les anciens JAR des mêmes greffons.
3. Démarrez une fois pour générer les valeurs par défaut et vérifiez que les quatre greffons s’activent correctement.
4. Arrêtez, modifiez la configuration et redémarrez complètement. N’utilisez pas de déchargement à chaud pour remplacer ces binaires.
5. Exécutez `/st version` et `/stcs status`. Sur la machine serveur, ouvrez `http://127.0.0.1:8765/`.

Fichiers d’installation actuels :

```text
SkyTrainFolia-4.0.2.jar
STCS-4.0.2.jar
SkyworldTrainAPI-2.0.0.jar
SkyPCC-2.0.0.jar
```

STF/STCS déclarent STA comme dépendance facultative, mais installez les quatre éléments pour disposer de la suite complète. PCC requiert STCS et STA. STF seul ne fournit pas toutes les fonctions de graphe, d’autorisation et de régulation.

### Règles de mise à niveau

- Utilisez un ensemble compatible, surtout lorsque les contrats STA changent. N’installez pas tous les anciens JAR du répertoire `artifacts`.
- Conservez les données existantes et fusionnez les nouvelles clés depuis la configuration par défaut actuelle. Les clés absentes ne sont pas nécessairement ajoutées automatiquement à un fichier existant.
- `/st reload` recharge les données des trains ainsi que la configuration : **arrêtez d’abord tous les trains**. Ce n’est pas un simple rechargement audio.
- Appliquez la configuration STCS/PCC par un redémarrage complet. Ce manuel n’invente pas de commandes `/stcs reload` ou `/skypcc reload`.
- Après une modification de topologie ou des règles de reconnaissance du graphe, chargez le réseau concerné et exécutez `/stcs rebuild`. Une mise à jour uniquement Web/audio ne nécessite normalement pas de reconstruction.
- Forcez le rafraîchissement du navigateur après une mise à jour Web. Pour revenir en arrière, restaurez ensemble les binaires, le monde et les données d’une même sauvegarde, pas un mélange de versions du registre.

## 3. Permissions et langues

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

## 4. Votre premier train en conduite manuelle

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
| `/st lang zh\|en\|fr\|jp` | Langue personnelle |
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
| `/stcs ma demand` | `stcs.ma` et conducteur actuel ; demander une MA fantôme |
| `/stcs ma release` | `stcs.ma` et conducteur actuel ; libérer les réservations en avant, pas l’occupation du train |
| `/stcs ma status` | `stcs.admin` ; diagnostics d’autorisation/blocage |
| `/stcs admin status` | `stcs.admin` ; état de protection du train occupé |
| `/stcs admin isolate true\|false` | Isolement du contrôle-commande |
| `/stcs admin bypass true\|false` | Contournement de la surveillance |
| `/stcs admin shadow true\|false` | Essais fantômes |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin` ; occupation conservée/observations des véhicules |
| `/stcs rebuild` | `stcs.admin` ; reconstruire le graphe depuis l’infrastructure enregistrée |
| `/stcs export` | `stcs.admin` ; exporter le graphe actuel, sans nouvelle recherche |
| `/stcs switch info` | `stcs.use` ; appareil de voie le plus proche dans un rayon de 8 blocs |
| `/stcs switch change` | `stcs.admin` ; manœuvrer l’appareil de voie proche |

La commande du joueur est bien **`demand`, et non `request`**. Les événements internes peuvent toujours s’appeler `MA_REQUESTED`. `/sta` et `/skypcc` exposent principalement l’aide et la version, et non une seconde série de commandes de conduite.

## 6. Profils de véhicule et propriétés

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
| `automatic.*` | Paramètres de l’ancien régulateur de vitesse cible, pas toute la stratégie des crans en gare |

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

## 7. Construction des lignes et appareils de voie

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

## 8. Panneaux automatiques et arrêts en gare

L’interface station/spawn/destroy suit des conventions inspirées de TC. L’association au rail suit la colonne de panneaux sous le rail et les relations d’attache, pas une sphère arbitraire alentour. **La prise en charge de ces formes ne signifie pas une compatibilité complète avec les expressions, panneaux distants ou extensions TC.**

Un train sous conduite manuelle n’est pas repris par station/destroy. Arrêtez-le, faites exécuter `/st release` au conducteur, puis réglez explicitement `/st property <train> mode auto`. ISOLATED, RECOVERING ou une reprise manuelle non libérée peuvent aussi interdire le traitement automatique.

En-têtes courants : `[stf]` utilise l’activation redstone, `[+stf]` est constamment actif, `[!stf]` est inversé, `[-stf]` est désactivé. L’analyseur comprend également des formes sur front montant/descendant ; le déclenchement réel dépend de l’action.

### Gare

```text
[+stf]
station
5
continue 40kmh
```

| Ligne | Signification |
| --- | --- |
| 1 | En-tête/activation |
| 2 | station, avec en option les paramètres pris en charge de distance/temps/accélération de lancement et de décalage d’arrêt |
| 3 | Temps de stationnement ; un nombre seul est en secondes, des formes comme `5s`, `100t`, `00:05` sont aussi admises |
| 4 | Direction/vitesse, p. ex. `continue 40kmh` ou `reverse 0.4` ; sans unité, la vitesse est en blocs/tick |

Les trains automatiques peuvent désormais détecter les panneaux Station en suivant les rails réels, sans nom de ligne, balise ni étalonnage STCS. La recherche suit la branche sélectionnée des aiguillages, sans charger de chunks ni détecter les trains précédents. Ce n’est ni un ATP ni une protection anticollision.

Le repère d’arrêt est le centre du wagonnet de tête dans le sens de marche, augmenté du décalage du panneau ; aucune demi-longueur de rame n’est ajoutée. Depuis 2.1.3, le conducteur virtuel adapte le freinage à la distance restante et laisse rouler sous la courbe. Une reprise lente avec hystérésis intervient uniquement après un arrêt trop court. Le stationnement et HOLD conservent B7 ; la conduite manuelle est inchangée.

Dans la section STF `settings` existante, ajoutez `station-local-look-ahead-blocks: 256.0`. Plage : 0–1024 blocs ; 0 désactive la recherche locale. Une recherche est effectuée au maximum toutes les 200 ms, uniquement sur les rails chargés relevant du thread Folia courant. Il s’agit d’une portée maximale, pas d’une distance de freinage garantie. L’avis facultatif du graphe STA/STCS conserve `station-look-ahead-blocks` (8192 par défaut). Commencez à basse vitesse avec suffisamment de voie chargée.

### Panneau de propriété V_target (Depuis 2.1.2)

Ce panneau modifie la vitesse cible des trains automatiques, pas leur vitesse maximale ni leur mode de conduite. Les trains manuels l’ignorent. Seul `V_target` est autorisé sur ces panneaux ; les autres propriétés passent par les commandes administrateur.

```text
[+stf]
property
V_target
60km/h
```

Sans unité, la valeur est en blocs/tick : `0.4`, `8m/s` et `28.8km/h` sont équivalents à 20 ticks/s et un bloc par mètre. La propriété est enregistrée, respecte les plafonds du train/profil et ne démarre pas seule un train arrêté. Une vitesse de départ explicite du panneau Station est prioritaire ; sinon V_target s’applique, puis `settings.station-launch-speed` (0.4 par défaut). `[+stf]` est toujours actif ; `[stf]` dépend du redstone. Commandes administrateur : `/st property demo V_target 60km/h` pour régler et `/st property demo get V_target` pour lire.

### Apparition et destruction

```text
[stf]
spawn 0.0
mmm

```

Utilisez une commande redstone maîtrisée pour faire apparaître trois wagonnets normaux. La ligne 2 accepte `spawn [velocity] [interval]`, avec un intervalle comme `00:30` ; les lignes 3 et 4 sont concaténées en motif d’apparition. Symboles de base : `m` normal, `s` coffre, `p` motorisé, `h` entonnoir, `t` TNT, ainsi que les motifs de modèle pris en charge. N’utilisez pas de TNT pour les premiers essais.

Les modèles d’apparition automatique doivent être explicitement enregistrés en mode auto. Ne faites pas apparaître continuellement des trains sur une ligne non protégée.

```text
[+stf]
destroy


```

Destroy supprime réellement les entités. Testez sur une voie séparée et sauvegardée ; un train sous conduite manuelle ne devrait pas être détruit. Validez d’abord le traitement en gare, puis spawn/destroy séparément.

## 9. MA fantôme et modes de protection

| Terme | Signification ici |
| --- | --- |
| MA / Movement Authority | Attribution fantôme le long d’un chemin orienté autorisé |
| EoA / End of Authority | Point limite actuel de l’autorisation, pas nécessairement la fin d’une ligne |
| Crédit | Distance de chemin restante jusqu’à l’EoA, et non distance euclidienne |
| Occupation | Voie occupée selon les observations des véhicules/éléments conservés |
| Réservation | Attribution fantôme en avant, pas un enclenchement d’itinéraire certifié |
| Liaison RBC | État du canal d’information fantôme, pas la preuve d’un RBC radio ou d’un ATP effectif |

### Procédure du conducteur

1. Vérifiez la topologie, la position des appareils de voie, la localisation et l’itinéraire d’essai réel.
2. Montez, exécutez `/st drive`, arrêtez le train et sélectionnez le sens dans un mode qui autorise les demandes.
3. Exécutez `/stcs ma demand` ; examinez le résultat/motif d’attribution, pas seulement l’acceptation de la demande.
4. La BossBar du conducteur affiche la MA restante ; l’IHM, la ligne/le point kilométrique de l’EoA ; le PCC, les intervalles réservés.
5. Gérez manuellement la vitesse et l’arrêt. `/stcs ma release` libère les réservations en avant.

Les trains sans conducteur n’acquièrent pas activement de nouvelle MA. Un train arrêté avec conducteur diffère d’un train sans conducteur. Libérer la MA ne supprime ni l’occupation par le train ni ne commande l’arrêt.

### Machine à états

| État | MA/canal | Comportement actif |
| --- | --- | --- |
| SHADOW | Demandes/reconnaissance fantômes autorisées | Aucune intervention ATP sur MA/vitesse |
| BYPASS | Canal conservé ; MA fantôme conservable/demandable | Surveillance contournée, communication non isolée |
| ISOLATED | Nouvelles demandes rejetées ; canal de commande embarqué isolé | Point kilométrique et télémétrie STA en lecture seule conservés ; panneaux automatiques désactivés |
| RECOVERING | Aucune nouvelle MA embarquée valide | Maintien du frein, traction rejetée, prochain mode à sélectionner explicitement |

Les modes persistent dans les données du train. Régler un commutateur applicable sur `false` fait passer en RECOVERING. L’activation d’une cible avec `true` exige RECOVERING, des observations récentes de la rame complète et l’arrêt. Il est impossible de changer simplement de mode à vitesse élevée.

Exemple avec l’administrateur assis dans le train cible :

```text
/stcs admin shadow false
/stcs admin status
```

Après l’arrêt complet du train :

```text
/stcs admin bypass true
```

Pour revenir, utilisez `bypass false`, arrêtez, puis `shadow true`. Les changements de mode annulent la traction/appliquent le freinage, sans départ automatique. Les réservations/éléments au sol conservés après isolement ne constituent ni une MA embarquée utilisable ni un motif pour la prolonger.

### Trois réglages de distance

Dans la configuration STCS :

```yaml
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  margin-meters: 2.0
```

| Clé | Signification |
| --- | --- |
| `look-ahead-meters` | Limite de recherche ; tout ce qui est trouvé n’est pas réservé |
| `lock-distance-meters` | Distance d’approche à partir de laquelle la MA fantôme peut franchir un appareil de voie ; plus loin, elle s’arrête avant celui-ci, sans prouver un verrouillage physique |
| `max-authority-distance-meters` | Plafond réel du crédit, également limité par l’anticipation |
| `margin-meters` | Marge avant obstacle/conflit, pas une distance de freinage complète |

L’ancien `ma.horizon-meters` fournit des valeurs de compatibilité pour les nouvelles clés absentes. Définissez explicitement les trois dans les nouvelles configurations. `switch-approach-distance` de STF, l’anticipation des gares et l’échelle BossBar sont des réglages distincts.

### Trains suiveurs, itinéraires parallèles et bifurcations

La MA spatiale utilise les cellules de rail physiques et les intervalles des arêtes au lieu d’attribuer exclusivement une arête entière entre balises. Les arêtes orientées opposées partagent la même ressource physique.

- Les trains suiveurs doivent être limités par la frontière d’occupation/réservation précédente, pas toujours par la balise précédente.
- Des itinéraires parallèles sans ressource ni conflit commun doivent coexister, sans verrouiller tout un gril de gare au seul motif de leur proximité.
- La branche inutilisée d’un appareil de voie ne doit pas devenir automatiquement une occupation par le train.
- Avant d’engager un gril, la sortie doit avoir la capacité d’accueillir le train complet ; sinon le train attend avant l’entrée.
- Un graphe ou un appareil de voie d’état inconnu, des véhicules manquants et des éléments conservés peuvent encore bloquer l’attribution. L’apparence seule ne justifie aucun contournement.

La projection de la rame et la cohérence asynchrone restent prudentes, sans constituer une solution prouvée d’intégrité/gabarit balayé. L’implémentation échantillonne les obstacles tous les quarts de bloc le long du chemin, mais attribue des ressources de cellules physiques : c’est une attribution fantôme à l’échelle du bloc, pas un ATP cantonnement mobile continu certifié. Les réservations s’arrêtent à l’intervalle accordé. Une cellule physique partagée d’appareil de voie peut colorer une courte extrémité d’une branche inutilisée sans réserver toute cette branche.

Les identifiants de ressource utilisent `cell@world:x:y:z`. Les anciens enregistrements d’occupation d’une arête entière restent bloquants par prudence jusqu’à leur remplacement par une observation complète et récente. Déchargement, redémarrage, véhicules manquants et divergence de graphe n’effacent pas eux-mêmes les éléments. Un train hors ligne avec d’anciens enregistrements peut donc bloquer une zone plus vaste. Ne revenez pas à une ancienne version en réutilisant un registre plus récent sans sa sauvegarde correspondante.

## 10. Interface Web SkyPCC

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

Ouvrez `http://127.0.0.1:8765/` sur la machine serveur. Sur un autre ordinateur, cette adresse désigne cet autre ordinateur, pas le serveur. Utilisez un tunnel SSH local pour une commande distante ; exposer publiquement un service n’est pas une solution de sécurité.

### Affichage

- Chinois/anglais/français/japonais, thèmes clair/sombre et logos jour/nuit fournis.
- Filtrage par ligne, zoom, ajustement à la vue et échelle du texte.
- Les trains en attente de localisation restent dans la liste ; les entrées conservées ne sont pas des positions récentes.
- Étiquettes de carte : `<numéro de circulation> | <nom du train> | <vitesse> km/h`, avec un substitut si le numéro est absent.
- Inspecteur de train et suivi/annulation de caméra : mode ATP, MA/EoA, motif, direction, inverseur, manipulateur, conducteur, point kilométrique, arête et ancienneté des données.
- Les noms des appareils de voie sont en caractères noirs gras sur fond jaune ; les flèches sont distinctes : violet pour la voie directe, jaune-orangé pour la voie déviée.
- Cliquez sur les balises, appareils de voie ou arêtes pour inspecter l’infrastructure. Le point kilométrique confirmé de la voie principale est affiché ; sinon, les distances des nœuds/ports adjacents du graphe sont utilisées sans inventer le kilométrage d’une voie de service.
- La position de l’appareil de voie est un instantané reçu, pas une validation physique récente par le navigateur.
- Les intervalles occupés, gelés/incertains, réservés fantômes et non attribués ont des couleurs différentes. Gris/non attribué ne prouve pas que la voie est libre.
- Le journal d’exploitation permet le filtrage par gravité, le repliement et le déploiement vers le haut.

### Commande des appareils de voie

Activez `control-enabled`, définissez un jeton aléatoire privé d’**au moins 32 caractères**, puis redémarrez. Saisissez-le dans l’interface Web de commande ; ne le placez jamais dans une URL, une capture d’écran ou un journal public.

Sélectionnez un appareil de voie, inspectez-le et confirmez la manœuvre. La demande comprend la révision du graphe, l’identité, l’état attendu/visé et la position. STCS effectue les contrôles locaux ; STF actionne l’appareil.

- Les trains lointains sans rapport ne bloquent pas automatiquement la demande, mais l’occupation locale, les conflits de réservation, l’incertitude et les discordances d’état/position le peuvent.
- Les emplacements déchargés peuvent passer à PENDING, avec chargement asynchrone et nouvelle validation dans la région propriétaire. PENDING n’est pas un succès et ne doit pas être utilisé comme autorisation de circuler.
- Un jeton correct ne suffit pas : les contrôles de boucle locale et de même origine s’appliquent aussi.
- L’affichage public en lecture seule doit posséder un contrôle d’accès distinct ; accès d’observation et identifiants de commande sont deux sujets différents.

### Événements

Les événements couvrent la prise/libération/perte de conduite, les manœuvres d’appareils de voie, les talonnages présumés, le passage au FU, les demandes/libérations de MA et les changements de mode ATP. Les événements de mode identifient l’acteur et les anciens/nouveaux états ; un acteur administrateur n’est pas nécessairement le conducteur.

Par défaut, STA conserve les 500 derniers événements de la session actuelle, pas une archive d’audit permanente. Les mises à jour régulières de MA ne produisent pas toutes un événement. Un talonnage présumé n’est pas un diagnostic de panne entièrement établi.

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

## 12. Contrats et messages STA

STA expose principalement des services Java dans la JVM du serveur. Ce n’est pas un RBC Python TCP/WebSocket exposé automatiquement. PCC fournit la passerelle HTTP/SSE existante d’observation et de commande limitée des appareils de voie.

| Contrat/donnée | Producteur principal | Consommateur principal | Signification |
| --- | --- | --- | --- |
| v5 TELEMETRY_REPORT / 1136 | STF | STCS/abonnés | Télémétrie physique |
| v5 TRACK_REPORT / 2002 | STCS | PCC/abonnés | Localisation sur le graphe liée à l’observation d’origine |
| v5 TRAIN_REMOVED / 2001 | Nettoyage de la source | Registres/abonnés | N’autorise pas l’effacement de l’occupation conservée |
| v5 RailNetworkService | STCS | STF/autres | Graphe, navigation, position sur arête, anticipation des gares |
| v3 ConsistObservation | STF | STCS | Observations/cycle de vie des véhicules de la rame |
| v3 RailwayEvent | STF/STCS | PCC/abonnés | Événements d’exploitation |
| v5 DriverDeskService | STF | STCS | Conducteur, prise de conduite, mode ATP |
| v5 ShadowAuthorityService | STCS | STF/PCC | MA/EoA et sections non exécutables |
| v5 SwitchControl | Appelant tel que PCC ; contrôlé par STCS, actionné par STF | Appelant | Contrôles de version/état/position et PENDING |

Il existe trois types de messages génériques v5, mais l’API complète comprend aussi des instantanés, événements et services distincts. Ils ne sont pas tous des instances de ces trois messages.

Les en-têtes v5 comprennent version, kind, source, sessionId, sequence, emittedAt et trainId. Les états de qualité comprennent VALID, UNLOCATED, STALE, EXPIRED, GRAPH_CHANGED, SOURCE_UNAVAILABLE et SCALE_MISMATCH. Les consommateurs ne doivent pas employer les coordonnées en ignorant l’identité, l’ordre ou la qualité.

Les instantanés fantômes v5 portent `simulationOnly=true` et `executable=false`. Les données d’autorisation comprennent le chemin, l’arête/décalage EoA, la distance restante et la provenance de l’observation. Les sections peuvent comprendre `fromMeters/toMeters` ; plusieurs intervalles peuvent se trouver sur la même arête. Un intervalle ne doit pas être affiché ou interprété comme l’occupation de toute l’arête.

Le centre du véhicule de tête n’est pas une position prouvée de l’avant du train, la longueur nominale de la rame ne prouve pas l’intégrité, et la vitesse nominale à 20 TPS n’est pas une vitesse en temps réel en cas de latence. Ces contrats sont inspirés de l’ETCS, mais **ne sont ni des messages filaires SUBSET-026 ni une interopérabilité**.

### Points d’accès HTTP PCC

| Point d’accès | Contenu |
| --- | --- |
| `GET /api/v5/graph` | RailGraph |
| `GET /api/v5/trains` | Instantané d’affichage des trains |
| `GET /api/v5/messages` | Instantané de télémétrie |
| `GET /api/v5/railway-events` | Événements |
| `GET /api/v5/shadow-ma` | Autorisations/sections fantômes |
| `GET /api/v5/config` | Réglages Web publics, pas le jeton de commande |
| `GET /api/v5/events` | SSE |
| `POST /api/v5/switch` | Commande authentifiée d’appareil de voie |

Les versions des points d’accès et des JAR diffèrent. Ne rejouez pas des instantanés d’affichage comme autorisations exécutables. Il s’agit d’un aperçu de l’interface, pas d’une référence complète de SDK/schéma généré ; un client externe doit valider les formes exactes des charges utiles et les contrats de la version déployée avant d’envoyer des commandes. En particulier, les extrémités de section nulles conservent l’ancien sens « arête entière », tandis que les extrémités explicites délimitent un intervalle.

## 13. Persistance et dépannage

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

## 14. Essais de réception et prochaines étapes

### Liste minimale de non-régression

1. Les quatre greffons se chargent ; versions/aide et configuration sont valides.
2. Chaque embarquement exige `drive` ; les voyageurs n’héritent pas de la conduite ; la perte du conducteur applique le FU ; la reconnexion ne rétablit pas l’ancienne traction.
3. Mettre un mode à false engage la récupération ; celle-ci interdit la traction ; passer la cible à true exige l’arrêt ; l’état persiste après redémarrage.
4. Essayez voie directe, voie de service, communication, origines dos à dos, voie au-delà de End et panneaux d’appareil suspendus ; l’affectation de ligne reste locale.
5. Essayez des trains en marche/arrêtés/déchargés/redémarrés ; les éléments conservés ne disparaissent pas sans justification.
6. Essayez le traitement sans conducteur, demande/libération, succession, conflits de sens opposés, itinéraires parallèles, capacité du gril et dégagement de la queue.
7. Essayez la manœuvre locale/PCC, le rejet en cas de conflit, PENDING en zone déchargée, la géométrie réelle du rail et le levier redstone.
8. Vérifiez la BossBar réservée au conducteur, le point kilométrique EoA, les couleurs d’intervalle, l’inspecteur d’infrastructure, l’expansion du journal, les quatre langues et les thèmes.
9. Vérifiez les sons d’accord/variation/réduction/faible distance/libération, l’absence de répétition lors d’une extension régulière et l’absence de son retardé adressé à un ancien conducteur.
10. Les trains manuels ignorent la reprise automatique ; libération + auto autorisent le traitement en gare ; essayez spawn/destroy séparément.

Le banc d’essai Python hors ligne couvre topologie, direction, occupation, réservations et EoA, mais pas le cycle de vie des entités Bukkit, l’ordonnancement Folia, le réseau ou le freinage réel. Ce sont des essais de non-régression en jeu, pas une certification ferroviaire.

Les contrats/modes M0 et les observations/conservations M1 sont implémentés et testés par des joueurs. M2 comprend désormais la MA fantôme en ligne et des perfectionnements des ressources spatiales. Une réception antérieure ne remplace pas les essais de non-régression et n’établit pas l’aptitude ATP.

Prochaine étape : valider sur serveur contrôlé la nouvelle chaîne `Enforced` des trains manuels, la fraîcheur de localisation, les changements de graphe/session, le freinage et le flux SR. FS exige un changement explicite de canal à l'arrêt et une autorisation STCS exécutable, non un raccourci de configuration.

Avant un ATP réel restent notamment à réaliser : enveloppes/cohérence du train complet, identité/acquittement/révocation des autorisations, règles de perte de contact/gel/contournement, limitations de vitesse/modèles de freinage, libération justifiée des ressources et essais d’injection de pannes.

## 15. Notes d’implémentation pour les développeurs

Ce dépôt contient quatre répertoires de modules : `SkyTrainFolia`, `STCS`, `STA` et `SkyPCC`. Le code compilé partagé se trouve dans `shared`, les notes de développement dans `doc`. Consultez le `plugin.yml` de chaque module ou sa commande de version pour les versions d’exécution ; le renommage du dépôt ne renomme pas les greffons.

### Mouvement et limites Folia

Le modèle de mouvement STF repose sur les coordonnées de voie et l’espacement de la rame, plutôt que sur la simple copie de la vitesse du véhicule de tête dans les suivants. Correction de position, lissage pour les voyageurs et synchronisation d’affichage sont des sujets distincts. Cela ne garantit pas une vitesse sûre illimitée : courbes, rampes, changements de région et suppression d’entités restent des cas d’essai importants.

Les opérations physiques sur les entités/blocs doivent respecter les ordonnanceurs Folia propriétaires de l’entité/de la région. Les calculs asynchrones du graphe et des autorisations utilisent des instantanés ; ils n’autorisent pas l’accès à des objets Bukkit arbitraires depuis les fils d’exécution auxiliaires. Disponibilité du chunk, connaissance du graphe et validation physique en direct de l’appareil de voie sont des états distincts. Un chunk chargé ne prouve pas à lui seul qu’un appareil jusqu’alors inconnu a été validé.

Les mises à niveau de Minecraft peuvent affecter les composants internes des entités, l’intégration des paquets/de l’affichage et les hypothèses d’ordonnancement, même lorsque le code ordinaire de commande/configuration est inchangé. Ces limites d’adaptation exigent revue et essais de non-régression avec la nouvelle version serveur. Une couche d’abstraction réduit la surface touchée ; elle ne rend pas la suite exempte de maintenance.

### Limite de comparaison avec TrainCarts

- Les travaux actuels sur les panneaux concernent station, spawn et destroy, avec les en-têtes STF/SkyTrain et les règles d’analyse/activation décrites plus haut.
- L’association rail-panneau suit un modèle de colonne/attache. Cela ne revendique pas la prise en charge de toutes les actions ou expressions de panneaux TC.
- Les définitions d’appareil de voie STF décrivent une géométrie/des ports explicites et un actionneur. Elles ne doivent pas être interprétées comme des expressions TC switcher de sélection d’itinéraire.
- L’interface familière d’un panneau de gare n’implique pas un régulateur identique : ce MVP commande les crans du véhicule et possède un comportement d’accostage final distinct.
- Métadonnées de destination, anticipation consultative des gares, réservations de ressources et autorisation de mouvement exécutable sont des couches différentes. La présence de l’une n’implique pas que les autres soient complètes.

### Périmètre de compilation et de validation

Le code source possède un point d’entrée PowerShell de compilation/non-régression nommé `build.ps1`. Fournissez `-ServerRoot /path/to/prepared-server` et `-JavaHome /path/to/jdk-25` (ou définissez `JAVA_HOME`). Il lit comme dépendances le fichier serveur `versions/26.2/shiroha-26.2.jar` et `libraries/`, compile STA en premier, puis les autres modules, et exécute les essais Java. Il ne démarre, ne déploie ni ne modifie le serveur. Les dépendances ne sont pas incluses. L’installation des JAR empaquetés ne nécessite pas de compiler le code source. Les sorties vont dans les répertoires ignorés `artifacts/` et `target/`. `package-source.ps1` crée une archive ZIP contenant uniquement les sources sous `dist/`.

Les contrôles automatisés couvrent les contrats, transitions de mode, conflits spatiaux/conservation et rendu dans le navigateur. Des essais sur serveur réel restent nécessaires pour l’embarquement/le cycle de vie du conducteur, la propriété Folia, la redstone physique, le chargement des chunks et le mouvement. Traduire la documentation n’est ni une nouvelle validation d’exécution ni une nouvelle version.

Ce fichier est volontairement autonome. Installation, permissions, formes de commande prises en charge, configuration, tutoriels d’exploitation, limites d’API et restrictions connues y figurent sans exiger un autre README ni une ancienne note de version.
