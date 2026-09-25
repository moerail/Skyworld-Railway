# Manuel de conduite SkyRail

[中文](README.zh.md) | [English](README.en.md) | [Français](README.fr.md) | [日本語](README.ja.md) · [Accueil du projet](../../README.md)

Pour les joueurs à bord d'un train manuel. Versions : STF/STCS 4.0.0, STA/SkyPCC 2.0.0. La création des trains, les voies et la configuration relèvent des administrateurs.

> **En cas de danger : `/st eb`.** La MA fantôme et sa limite restent consultatives. Le canal expérimental distinct `Enforced` ne peut freiner un train manuel qu'après activation explicite par un administrateur et émission d'une MA exécutable par STCS. Ne supposez ni son activation ni une validation sur serveur. Ce guide concerne un jeu, pas l'exploitation ferroviaire réelle.

## Quelques termes avant de conduire

**La MA indique jusqu'où vous êtes actuellement autorisé à avancer ; l'EoA en est la fin ; ATP limit est la vitesse limite actuelle.** Prendre la conduite, déposer une demande et recevoir une MA valide sont trois étapes distinctes.

| Terme | Sens pour le conducteur |
| --- | --- |
| MA · Movement Authority | Autorisation de mouvement sur une portion limitée de voie. Elle évolue selon les conditions et ne constitue pas un itinéraire complet jusqu'à destination. La MA fantôme est consultative. |
| EoA · End of Authority | Fin de cette autorisation : arrêtez-vous avant elle. La présence de rails au-delà ne permet pas de continuer. |
| ATP · Automatic Train Protection | Protection automatique du train. Le canal Shadow informe ; le canal Enforced, activé par un administrateur, assure une surveillance expérimentale avec freinage. |
| ATO · Automatic Train Operation | Conduite automatique. Les trains pseudo-ATO à panneaux de STF sont distincts de cet automate de conduite manuelle. |
| HMI / PCC | HMI désigne l'affichage embarqué ; PCC, l'interface web de régulation. Interprétez les indications avec leur canal et leur validité. |
| P / N / B / EB | Cran de traction, neutre du manipulateur, cran de frein de service et freinage d'urgence. B7 est le cran de service maximal. |
| Balise | Repère de position du graphe, utilisé pour la ligne et le kilométrage ; sa présence n'autorise pas le mouvement. |

Les codes restent identiques : `SB` attente, `FS` supervision complète, `SH` manœuvre, `SR` responsabilité du conducteur (avec accord du PCC dans SkyRail), `TR` maintien après dépassement et déclenchement, `PT` état après arrêt et acquittement. Ce sont des modes SkyRail simplifiés et expérimentaux.

## Modes des trains manuels

La ligne ATP du tableau de bord comporte **canal | mode d'exploitation**, par exemple `Protection active | SR`. Le canal est traduit ; les codes `SB/FS/SH/SR/TR/PT` restent identiques dans toutes les langues. En canal actif, après `/st drive`, le train manuel est en `SB` (frein maintenu). `/stcs ma demand` demande `FS` ; à l'arrêt, `/stcs ma sh` demande une autorisation de manœuvre limitée et `/stcs ma sr` attend l'accord du PCC ou d'un administrateur jusqu'à une balise, une aiguille, un origin ou un end. Une demande en attente n'autorise pas le départ. Après franchissement d'EoA et passage en `TR`, arrêtez-vous, entrez `/stcs ma ack` pour `PT`, puis `/stcs ma release` avant une nouvelle demande. Le canal fantôme ne surveille pas ces modes. Si la MA disparaît, contactez la régulation et arrêtez-vous avant la dernière EoA confirmée.

**Cet automate concerne uniquement les trains manuels.** Le pseudo-ATO intégré à STF continue de suivre ses panneaux ; monter dans un train automatique ou saisir des commandes MA/mode ne le convertit pas en train `FS/SH/SR`.

Dans le canal actif, SH/SR sont limités par défaut à **40 km/h**, réglables par l'administrateur. Un dépassement au-delà d'une faible tolérance applique B7 sans le délai de survitesse assoupli de FS. Près de l'EoA, respectez la limite ATP inférieure ; 40 km/h n'est pas une vitesse garantie sur toute l'autorisation.

La cible SR approuvée peut être plus éloignée que la MA actuelle. Chaque autorisation glissante est limitée par défaut à 120 m, évolue selon la situation en avant et ne permet pas de dépasser la cible approuvée. **Respectez uniquement la MA actuellement valide.** L'accord ne signifie pas que toutes les aiguilles et sections sont ouvertes. Une géométrie sauvegardée peut couvrir des chunks déchargés, mais des aiguilles inconnues, une occupation incertaine ou des lacunes du graphe peuvent empêcher la prolongation.

## 1. Monter et prendre la conduite

Choisissez un train manuel enregistré que vous êtes autorisé à conduire. Dans un service automatique, n’utilisez pas `/st drive` sans autorisation : cette commande n’est pas un mode dédié à la simple supervision.

1. Asseyez-vous dans un wagonnet et restez à bord.
2. Entrez `/st lang fr` ; `/st speedunit kph` permet l’affichage en km/h.
3. Entrez `/st drive`, attendez la confirmation et vérifiez le nom du train.
4. Entrez `/st b7`, appliquez le frein de service et vérifiez que la vitesse atteint zéro.
5. À l’arrêt, sélectionnez `/st forward` ou `/st backward` selon le sens voulu.

Le sens avant/arrière est celui du train, pas celui de la caméra. Pour un premier essai, vérifiez le sens avec un bref mouvement en P1 sur une voie d’essai libre fournie par l’administrateur.

Chaque nouvelle montée nécessite `/st drive`. Les autres commandes ne prennent pas implicitement la conduite. Aucun passager ne devient automatiquement conducteur ; contactez le conducteur actuel ou un administrateur si le poste est occupé. Il faut `skytrain.use` pour la conduite et `stcs.ma` pour demander une MA. Ces permissions sont accordées par défaut aux joueurs, mais le serveur peut les modifier.

## 2. Le serveur utilise-t-il une MA ?

**STF seul :** traction, freinage, cabine et barre rapide fonctionnent, mais sans MA, EoA ou courbe ATP fantôme de STCS. Vérifiez la voie et les aiguilles selon les consignes locales ; aucune protection automatique contre les collisions n’est à attendre.

**Avec STCS/STA :** après la prise de conduite, à l’arrêt et après sélection du sens, entrez :

```text
/stcs ma demand
```

C’est une demande, pas une garantie d’attribution. Vérifiez la MA restante, l’EoA, le motif d’attribution et l’état des infrastructures avant de partir conformément aux règles du serveur. Le droit de conduire est distinct de la MA ; celle-ci n’assure ni recherche d’itinéraire vers une destination ni conduite automatique.

Attente, absence de MA, expiration, position inconnue, `SWITCH_UNKNOWN` ou `RESOURCE_CONFLICT` ne prouvent pas que la voie est libre. Restez à l’arrêt et contactez la régulation ou un administrateur. N’isolez pas la protection, n’effacez pas les registres et ne franchissez pas l’EoA pour contourner le problème. Une panne de STCS sur un serveur qui l’utilise n’autorise pas à poursuivre comme si STF était installé seul.

## 3. Démarrer et s’arrêter

| Commande | Fonction |
| --- | --- |
| `/st p1` à `/st p4` | Crans de traction croissants, pas des vitesses fixes |
| `/st n` | Manipulateur au neutre : traction coupée et frein commandé desserré ; le train peut continuer sur son erre |
| `/st b1` à `/st b7` | Crans de freinage de service croissants |
| `/st eb` | Freinage d’urgence ; une distance d’arrêt reste nécessaire |
| `/st neutral` | Inverseur au neutre ; différent de `/st n`, ce n’est pas un frein |

Lorsque le départ est autorisé selon les règles locales, entrez séparément `/st p1` et surveillez la vitesse avant d’augmenter la traction. Freinez suffisamment tôt, adaptez les crans B1/B3 ou autres, puis maintenez B7 à l’arrêt. N’attendez pas l’EoA ou le point d’arrêt en gare pour freiner.

**Les crans P desserrent le frein commandé ; N desserre également ce frein et l’EB commandé au manipulateur.** Ils ne suppriment pas un maintien de freinage indépendant imposé par la protection. N n’est pas un réarmement qui maintient le train freiné.

Les raccourcis `/p1`, `/n`, `/b7` et `/eb` existent aussi ; utilisez `/st ...` en cas de conflit avec un autre greffon. Arrêtez le train au frein avant d’inverser le sens ; n’utilisez pas la traction inverse pour l’arrêter.

## 4. Cabine et barre rapide

Après la prise de conduite, `/st cab` ouvre la cabine graphique. `/st hotbar` active ou désactive la conduite par barre rapide. **Sélectionnez la case 5 avant l’activation.**

| Case | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Cran | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

L’activation ne modifie pas le manipulateur et ne desserre pas les freins. Les changements de case suivants commandent les crans ; préférez les touches numériques pour éviter une action involontaire de la molette. La touche d’échange des mains (F par défaut) commande alors l’EB au lieu d’échanger les objets.

Répétez `/st hotbar` pour désactiver ce mode ; la case 5 n’est pas requise pour la désactivation. Désactiver l’interface ne freine pas et ne rend pas la conduite : arrêtez-vous d’abord au frein. `/st eb` reste utilisable en cas d’urgence.

## 5. Lire les indications

- Panneau latéral : vitesse, limite ATP du canal actuel en deuxième ligne, ligne/kilométrage, inverseur, manipulateur, conducteur et état de protection. Une intervention active affiche aussi ATP B7/EB et sa cause.
- BossBar réservée au conducteur : MA restante du canal actuel et limite ATP, avec ATP B7/EB lors d'une intervention active. Les autres passagers ne voient pas cette barre de conduite.
- L’EoA est la fin d’autorisation de mouvement, pas votre destination. Une calibration valide permet l’affichage de la ligne et du point kilométrique ; sinon, une arête/position ou une indication d’indisponibilité peut apparaître.
- `--`, non attribué ou expiré signifient information indisponible, pas vitesse ou MA illimitée. Par défaut, la courbe fantôme atteint zéro 1 m avant l’EoA ; dans les 5 derniers mètres, elle reste au plus à 5 km/h et descend jusqu’à zéro. Les réglages du serveur peuvent différer ; le freinage reste manuel.
- Des sons peuvent signaler l’attribution, une variation brusque de MA, sa libération ou l’approche de la limite. Sons et seuils sont configurables et peuvent être désactivés. Le silence n’autorise pas le départ.

Les alarmes d'approche de limite et de survitesse fonctionnent en Shadow et Enforced ; la survitesse est prioritaire. Le passage en TR dans Enforced envoie un message au conducteur et un signal sonore d'urgence. L'indication ATP EB de TR reste visible à l'arrêt. Les sons d'intervention de service et d'urgence sont réglables séparément. Résolvez la cause avant de repartir ; pour TR, suivez la procédure TR → PT → SB ci-dessus, sans tenter de la contourner avec N.

## 6. Fin de conduite et urgence

Pour terminer normalement, arrêtez complètement le train et maintenez le freinage. Avec STCS, vous pouvez utiliser `/stcs ma release` pour libérer les réservations en avant, puis `/st release` pour rendre la conduite, avant de descendre.

**Les deux commandes sont différentes :** en `Enforced`, `/stcs ma release` exige l'arrêt, revient en `SB` avec maintien B7 et libère les réservations en avant ; en mode fantôme, elle conserve l'ancien comportement sans freinage. Elle ne rend pas la conduite et n'efface pas l'occupation du train. `/st release` rend la conduite et applique l'EB ; elle ne rétablit pas l'exploitation automatique.

Descendre, se déconnecter, mourir ou perdre l’entité du siège de conduite retire le contrôle et applique l’EB, sans transfert à un passager. Après être remonté, répétez `/st drive`, vérifiez le sens et le freinage et, avec STCS, confirmez ou redemandez une MA.

En urgence, utilisez immédiatement `/st eb` et avertissez la régulation. Ne repartez qu’après l’arrêt, la résolution de la cause et la vérification de l’autorisation. Si `RECOVERING` ou un maintien de freinage persiste, contactez un administrateur ; ne contournez pas cet état par la traction ou l’isolement.

## 7. En cas de problème

| Symptôme | Action |
| --- | --- |
| Train introuvable | Asseyez-vous dans un train enregistré ; un wagonnet isolé n’appartient pas forcément à STF |
| Déclaration de conduite requise | Restez à bord, utilisez `/st drive` et vérifiez la confirmation |
| Cran P sans mouvement | Vérifiez inverseur, EB/maintien et mode ; ne passez pas simplement à P4, transmettez l’IHM |
| MA demandée, toujours en attente | Demande acceptée ne signifie pas MA attribuée ; contactez la régulation avec le motif |
| Permission refusée | Faites vérifier `skytrain.use` / `stcs.ma` ; un conducteur ordinaire n’a pas besoin de droits administrateur |

Communiquez nom/numéro de service du train, heure, position, dernières actions et capture de l’IHM. `/st help` et `/st version` donnent aide et versions. Ne supprimez ni trains ni occupations pour résoudre un problème de conduite.
