# SkyRail — Premier train manuel

**[MODIFICATION · 2026-11-21]** STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1

[Manuel détaillé français](../../README.fr.md) · [README](../../../README.md)

Demander FS par défaut n’est pas obtenir FS. Le conducteur prend explicitement son bail ; SAL doit aussi recevoir une MA exécutoire. SB/reprise/indisponibilité maintiennent le frein.

FS surveille vitesse/EoA ; SH/SR plafonnés par défaut à40km/h, ou moins selon courbe. TR arrêté : ack→PT, release→SB, inspection puis nouvelle demande. TIMS doit d’abord être réellement rétabli et confirmé.

Une demande initiale par chemin puis renouvellement silencieux ; STCS prolonge MA. Changement de chemin/version, bail expiré ou contrôle perdu exigent une demande valide ; un message tardif ne ressuscite pas la précédente.

Essai fantôme uniquement : simulationOnly=true, executable=false. BYPASS/ISOLATED/RECOVERING conservent leurs verrouillages.

Libérer MA conserve l’occupation du train. Déchargement/délai/déconnexion ne prouvent pas la libération. Suppression explicite : reçu STF complet et session virtuelle nettoyée avant libération ciblée, sans toucher les autres trains.


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



## Transfert manuel/automatique

Automatique→manuel : `/sal release` seulement pour un train complet, arrêté, PARKED au dépôt, sans tâche ni défaut ; attendre puis `/st drive` dans une voiture d’extrémité. Pas pendant la rentrée.

Manuel→automatique : bail conducteur actif, arrêt continu3secondes sur place valide, confirmer SAL, puis `/st release` explicite sous30secondes ou second bouton. Le dépôt réel devient home ; attente, sans départ.

Libération seule, descente, déconnexion, mouvement, changement de conducteur/place ou expiration ne transfèrent rien. État manuel persistant. Conducteur : skyatolite.driver et skytrain.use ; administration : skyatolite.admin.

## Permissions et diagnostic

- Appel impossible : publication, front awake, noms, attente manuelle, TIMS, fraîcheur et tâches ; sens initial opposé ne doit pas imposer un retournement manuel.
- Arrêt non relié : RailGraph/routingIssues, rescan local chargé. chunk_not_loaded seul ne prouve pas une rupture.
- Rentrée bloquée : place, longueur complète, chemin, aiguille confirmée et motif MA ; ne pas vider de registre.
- Occupation après suppression : UUID et reçu complet, nettoyage du conducteur virtuel. Entité inconnue ≠ suppression sûre.
- Affichage indisponible : versions/services SAL/STA, revision et fraîcheur ; recharger le navigateur.

~~Shadow MA only; ATP inactive / 默认影子MA，ATP不动作~~

[Ancien manuel complet — référence historique uniquement](../manual-baseline-20261120/driver/README.fr.md)


## **[2026-11-24] Ajout / correction : affichage PCC 2.4.1**

~~Contour de focus natif du navigateur autour des voies et équipements.~~ La sélection suit désormais la polyligne réelle ou un petit cercle autour de l’équipement, avec une épaisseur fixe à l’écran. Le zoom ne grossit plus le contour ; la sélection à la souris et le focus clavier restent visibles.

À côté des commandes A− / A+, **H− / H+** règle la taille de surbrillance (50%–250%). Cliquer sur le pourcentage rétablit100% ; le carré de couleur permet de choisir la couleur. Ces préférences sont conservées dans ce navigateur ; les commandes fonctionnent aussi sans stockage.

La couche des équipements propose **Balise, END, ORIGIN et Aiguillage**. La case principale sélectionne ou désélectionne tout ; chaque sous-case reste indépendante. Une sélection partielle est indiquée sur la case principale. Symboles, libellés et flèches d’aiguillage sont filtrés ensemble ; fond de voie et couche SAL restent indépendants.

Ces réglages modifient uniquement l’affichage, sans changer RailGraph, équipements, lignes, MA ou occupation. Actualiser une ancienne page avec Ctrl+F5.
