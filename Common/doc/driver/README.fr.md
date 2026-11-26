# SkyRail — Premier train manuel

**[AJOUT / CORRECTION · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** Les invitations de transfert sont réessayées et les codes expirés renouvelés. Sortie/prise en charge et retour partagent un itinéraire segmenté avec rebroussements, libération MA par STCS et inversion via STA. Le chargement des chunks conserve le cran manuel sans autoriser la traction. [Details](../SAL-MANEUVERS-AND-HANDOVER.en.md)

**[CORRECTION · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** Les retours simultanés conservent itinéraire et place ; STCS gère le trafic temporaire par la MA. L’alignement automatique ne retire plus la MA d’un autre train. Dégagement de la place, obstacles stationnés et contrôles de défaut conservés. [Details](../SAL-RETURN-TRAFFIC.en.md)

**[MODIFIÉ · STF 5.1.3 · 2026-11-26]** La conduite automatique remplace P2/B4 fixes par un départ P4, une traction réduite à proximité de la vitesse cible, le freinage B6 et le maintien B7. Crans configurables ; contraintes MA, ATP, TIMS et véhicule conservées. [Details](../AUTOMATIC-DRIVING-HANDLES.en.md)

**[CORRECTION · SAL0.7.6 · 2026-11-26]** Les attentes de retour renouvellent l’itinéraire déjà accepté pour éviter l’expiration1500ms. Vérification2s par défaut sans perdre la cible ; une géométrie corporelle inconnue n’est plus dite changement topologique. Incertitude persistante : FAULT ; vrai changement/espace insuffisant : nouvelle place après arrêt. Motifs détaillés et historique séparé.

**[AJOUT / CORRECTION · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** Coordonnées et direction proviennent du même relevé STF. Incertitude : freinage immédiat et vérification2s ; tolérances bornées configurables de0,75bloc aux limites et1,5bloc pour la projection. Deux relevés indépendants d’une autre branche déclenchent la reprise plus tôt. Aucune nouvelle MA. La cause initiale persiste dans /sal status et les événements PCC.

**[AJOUT / CORRECTION · 2026-11-26]** STA2.9.2 / STF5.1.2 / SAL0.7.6 : /sal override <nom ou UUID> est réservé aux administrateurs : freinage, confirmation du retrait MA, sortie SAL puis /st drive. Occupation, TIMS et TR/PT restent conservés. En pente, la localisation utilise railXYZ observé par STF via STA, sans arrondir les coordonnées de l’entité.

**[AJOUT · SAL0.7.6 · 2026-11-26]** Les appels expliquent l’éligibilité de chaque train, les deux sens, le plafond de temps, les autres trains aux observations périmées/corps inconnus/hors graphe, les voies obstruées et les résultats périmés. Les protections restent inchangées ; le seul maintien TIMS d’un autre train ne bloque pas tout le réseau. Diagnostic borné, affiché par PCC existant.

**[MODIFICATION · SAL0.7.6 / PCC2.6.4 · 2026-11-26]** Les visites utilisent le plus court chemin ferroviaire orienté autorisé ; un long détour ne provoque plus de rejet pour ambiguïté. Les équipements/arêtes imposés restent contraignants. Sens, défauts du graphe, fermeture et MA restent vérifiés. PCC suit les rails réels, même pour le préfixe confirmé d’un brouillon inachevé ; seuls les liens non confirmés sont estimés.

**[AJOUT / MODIFICATION · 2026-11-26]** PCC2.6.4 / SAL0.7.6 / STA2.9.2 : la validation désigne le premier segment en défaut par indices, noms et ID. Les deux visites sont marquées ; cliquer le diagnostic pour y accéder. Corriger puis vérifier à nouveau. Réordonner par la poignée ≡ à droite, souris ou tactile ; un seul envoi au relâchement, aucun en cas d’annulation. Les flèches clavier fonctionnent sur la poignée. UUID, arrêt et rebroussement restent liés à la visite. Enregistrer→Valider→Publier reste nécessaire.

**[CORRECTION · PCC2.6.4 · 2026-11-26]** Le rafraîchissement conserve les cibles SAL et la liste inchangées. Sur écran étroit, l’éditeur reste visible sans train sélectionné. Les clics SAL ne déclenchent plus le déplacement de carte ; l’aperçu ne masque plus les arrêts. Une panne temporaire conserve le travail et la saisie ; les données périmées désactivent l’écriture. Les vrais conflits exigent toujours une résolution manuelle. Après remplacement de PCC : Ctrl+F5.

**[2026-11-26] Arrêts publics et affectation partagée.** Les trains SAL déjà en attente ne demandent pas de nouveau consentement. Un train manuel arrêté sur depot obtient un code à six chiffres après3s, ou immédiatement avec /sal handover. Confirmer /sal handover <code>, puis saisir /st release. Une simple libération ne vaut pas consentement. /sal release puis /st drive rend la conduite manuelle après retour.

La ligne4 de station devient un nom d’arrêt unique ; poser le panneau ne complète plus un itinéraire. awake utilise la ligne3 pour l’itinéraire et la ligne4 pour l’arrêt de départ. Les anciens awake restent désactivés jusqu’à réécriture. Les anciens station reçoivent des noms uniques : vérifier /sal station list. Les panneaux depot et les rattachements existants restent conservés.

[Procédure de recette en chinois](../../../SkyATO-Lite/TEST.zh.md) · [Contrat partagé (anglais)](../SAL-SHARED-DISPATCH.en.md)

**[CORRECTION · 2026-11-25]** STF5.1.2 / SAL0.7.6 : la demande FS/MA attend le choix avant/arrière. Un train arrêté recouvrant le marqueur depot reçoit un code aléatoire à six chiffres après3s ; `/sal handover` le demande immédiatement, `/sal handover <code>` confirme, puis `/st release` transfère. Le retour automatique reste centré.


**[MODIFICATION · 2026-11-21]** STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.6 / PCC2.6.4

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


**[RUPTURE · 2026-11-25]** Le garage aligne désormais le milieu du train. Le centre prévu est ajusté uniquement dans les limites de sa place ; un train trop long doit utiliser une autre place. Les marqueurs voisins partagent la distance suivant la voie avec un intervalle libre. Voie à un accès : places profondes prioritaires ; deux accès : évaluation des deux approches autorisées. Les trains qui obstruent le parcours sont pris en compte. PCC affiche capacité, accès, limites et décalage. Repositionner manuellement les anciens trains arrêtés par leur tête ; station ne change pas.

[Centre parking contract](../SAL-CENTRE-STABLING.en.md) · [中心停车契约](../SAL-CENTRE-STABLING.md)

## Complément d’utilisation · SAL 0.8.0 / STF 5.1.4

Arrêtez toute partie du train sur depot pendant3secondes, ou demandez un code par /sal handover. Confirmez sous30secondes avec /sal handover CODE, puis explicitement /st release. Une libération seule ne vaut pas consentement. Les notifications non reçues sont réessayées et les codes expirés renouvelés. Prise en charge/retour : jusqu’à4 inversions avec dégagement de toute la rame ; voies courtes/occupées et protections restent bloquantes. Après redémarrage, confirmation administrateur requise. Le chargement des chunks conserve le cran sans traction effective ; une nouvelle commande neutre/frein reste prioritaire.
