# Audit SkySignals — 2.0.2

## Cause du journal fourni

Le serveur Folia 1.21.11 charge **ValorSky-SkySignals 1.0.1**, refusé avant `onEnable` car son descripteur ne déclare pas Folia. Le dépôt était déjà en 2.0.1 avec `folia-supported: true` : le fichier installé sur le serveur ne correspond donc pas au code examiné. Le nouveau JAR est en **2.0.2** et son descripteur embarqué a été vérifié.

La déclaration seule ne suffit pas : les tâches de régions et d’entités doivent utiliser leurs ordonnanceurs respectifs. Référence : https://docs.papermc.io/paper/dev/folia-support/

## Corrections effectuées

| Zone | Défaut constaté et correction |
| --- | --- |
| Folia | Détection de classes inexistantes, signatures de réflexion incorrectes, repli sur Bukkit/global. Remplacement par les API Paper/Folia typées, gestion des délais nuls, annulation et suivi des entités. |
| Cycle des événements | Aucune progression automatique après ANNOUNCING. Ajout WARNING, ACTIVE, COMPLETING ; expiration de COMPLETING ; retrait des événements qui s’annulent pendant leur préparation ; état terminal cohérent. |
| Démarrage asynchrone | Le futur annonçait un succès avant l’exécution réelle. Il attend maintenant le démarrage et transmet les erreurs aux commandes. |
| Planification | Sans Redis, aucun événement automatique. Mode local opérationnel ; annulation des anciennes vérifications au reload ; respect de la limite d’événements actifs. |
| Emplacements | Lectures de blocs depuis le thread global et génération involontaire de chunks. Recherche asynchrone sur les régions concernées, limitée aux chunks chargés ; vérification du sol et de la bordure. Sans joueur ou position valide, l’événement s’annule. |
| Météorite | `Map.of` recevait une direction nulle dès la création ; distances entre mondes ; suppression de terrain malgré l’option désactivée. Correction de ces chemins ; suppression du cratère qui remplaçait arbitrairement les blocs par de la pierre. L’impact reste visuel, sans excavation. |
| Caisse | PDC non sauvegardé, accès aux blocs depuis le global, plusieurs gagnants possibles. Marquage sauvegardé, tâches régionales, réservation atomique et retrait uniquement après résultat positif de récompense. |
| Invasion | Récompense sur chaque premier coup plutôt qu’à la mort ; mauvais cast de plugin ; délai des vagues doublé ; dernière vague sans vérification. Corrections, suivi des tâches et suppression sur les ordonnanceurs d’entités. |
| Pluie de minerais | Tâche de traînée jamais annulée et compteur décrémenté à répétition. Suivi des objets et tâches, retrait unique ; objets de test non récupérables si les récompenses de test sont désactivées. |
| Croissance | Lecture des cultures depuis le thread global ; modification de bloc écrasée par le résultat de BlockGrowEvent. Lecture dans la région possédée et modification de l’état proposé par l’événement, respect des événements annulés. |
| Animation | Un délai créait une tâche répétée sans fin. Remplacement par une tâche unique ; opérations de blocs et d’entités routées ; téléportation asynchrone. |
| Récompenses | Colonnes SQL obligatoires absentes, courses entre réclamations, succès annoncé avant livraison, quantité d’argent recalculée, perte du surplus d’inventaire. Réservation SQL atomique avec métadonnées, déduplication locale, retour après livraison, tirage unique, dépôt du surplus. |
| Mode test | La commande promettait sans récompenses sans l’appliquer. Les événements de test sont désormais identifiés dans le service de récompenses. |
| Configuration | Clés avec tirets alors que le YAML utilise des underscores ; placeholders d’environnement non résolus employés comme valeurs littérales ; intervalles inversés. Corrections et bornes sur les périodes. |
| Rechargement | La commande ne rechargeait que le gestionnaire d’événements. Elle appelle désormais le rechargement du plugin et de la configuration. |
| MySQL | Pilote absent du JAR et repository figé avec une datasource encore nulle. Pilote embarqué, datasource consultée à l’utilisation ; connexion publiée après migrations et fermée si arrêt concurrent. |
| Réseau | Consommateur RabbitMQ démarré avant sa connexion asynchrone ; publication concurrente sur un canal ; fermeture de connexions Redis oubliée lors des tentatives. Callbacks de connexion, sérialisation des publications, fermeture des connexions. Verrous Redis libérés/prolongés par comparaison atomique. |
| Distribution | JAR simple et JAR complet écrasaient le même fichier. Le simple porte désormais `-plain` ; la version du descripteur est générée depuis Gradle ; fichiers de configuration dans le bon dossier du ZIP. |
| Arrêt | Une exception de nettoyage empêchait la fermeture des autres services. Les étapes de fermeture sont isolées et les erreurs journalisées. |

## Vérifications exécutées

- `gradlew.bat test build dist --console=plain` : succès.
- **44 tests**, zéro échec, zéro erreur, zéro ignoré ; **14 nouveaux tests de régression**.
- Tests nouveaux : routage Folia global/région/entité, délai zéro, conversion ticks/millisecondes, propagation des erreurs, cycle réel d’AbstractSkyEvent, auto-annulation, expiration, sérialisation initiale météorite, clés YAML, datasource tardive, récompenses de test, planification sans Redis et annulation au reload.
- Inspection du JAR : version 2.0.2, `folia-supported: true`, absence de `paper-plugin.yml` concurrent, présence du pilote MySQL.
- Inspection du ZIP : JAR identique octet pour octet et configurations sous `plugins/ValorSky-SkySignals/`.
- `git diff --check` : aucune erreur de whitespace.

## Limites et points encore ouverts

La revue du code et les tests unitaires ne constituent pas une validation en jeu. Aucun serveur Folia avec joueurs, MySQL, Redis ou RabbitMQ réel n’a été lancé pour cet audit.

- **Îles/protections** : `DefaultIslandProvider` retourne toujours vide et `DefaultProtectionProvider` autorise tout. Aucun branchement à un plugin SkyBlock/claims n’existe. La recherche utilise donc des positions près des joueurs ; elle ne garantit pas l’appartenance d’une île. L’intégration dépend du plugin réellement installé.
- **PlaceholderAPI** : la classe fournie n’hérite pas de son API et n’est pas enregistrée. Les placeholders annoncés ne constituent pas une intégration fonctionnelle.
- **Récompenses par défaut** : le YAML fourni n’a pas de section `rewards`. Il faut configurer objets, commandes ou argent ; `eco give` dépend d’une commande d’économie compatible avec Folia.
- **Réseau** : les états distants sont mis en cache sans lancer leurs effets SERVER sur le serveur local. La reprise exacte d’un événement visuel après redémarrage n’est pas implémentée : la préparation repart de l’annonce. Si Redis est absent au démarrage puis revient, le service de verrous créé au démarrage reste désactivé jusqu’au redémarrage du plugin.
- **SQL** : plusieurs appels du repository restent synchrones dans le cycle d’événements et l’historique ; une base lente peut bloquer le thread appelant. Les connexions réelles, migrations et erreurs réseau doivent être testées séparément.
- **Livraison de récompenses** : réservation en base et effets Minecraft ne forment pas une transaction distribuée. Une panne après réservation ou une livraison partielle peut demander une intervention manuelle ; sans base, la déduplication est locale, temporaire et perdue au redémarrage.
- **Folia en charge et arrêt** : déplacements entre régions, déconnexions, effets à cheval sur plusieurs régions et nettoyage à l’arrêt nécessitent une validation réelle. Les ordonnanceurs peuvent refuser de nouvelles tâches durant `onDisable` ; un coffre persistant peut alors nécessiter un nettoyage. Les erreurs de fermeture sont maintenant visibles sans empêcher les autres fermetures.
- Certaines options/fonctions demeurent simplifiées : audiences par portée, intégration d’îles, excavation de météorite, pagination d’historique, hooks d’événements API et certaines formes de particules. Elles ne sont pas couvertes par une promesse de fonctionnement complet.

## Installation et recette

1. Arrêter le serveur et remplacer les anciens JAR SkySignals par `build/libs/ValorSky-SkySignals-2.0.2.jar`. Ne pas installer le fichier `-plain.jar`, ni conserver plusieurs versions.
2. Conserver les fichiers de configuration existants. Pour un serveur autonome, désactiver Redis/RabbitMQ si inutilisés. Les connexions externes et les adaptations d’îles/protections nécessitent leur propre configuration.
3. Redémarrer complètement Folia ; vérifier que le journal affiche **2.0.2** et `SkySignals enabled successfully`.
4. Avec un joueur connecté et du terrain chargé, tester les six types via `/skysignals test <type>` puis `/skysignals active` et `/skysignals stop` ; vérifier phases, expiration, absence d’objets/mobs persistants et absence d’erreurs de threads.
5. Vérifier `/skysignals reload`, puis les récompenses configurées sur une copie du serveur, y compris inventaire plein, déconnexion et double clic sur une caisse.
6. Tester ensuite les services externes et les situations d’arrêt/reconnexion avant mise en production.

Artefacts : `build/libs/ValorSky-SkySignals-2.0.2.jar` et `build/distributions/ValorSky-SkySignals-2.0.2.zip`.
SHA-256 du JAR : `882d3cfe1e2961cdfb7ac38ecee17129dee211a6a33f68b4e57856871f761d94`.
