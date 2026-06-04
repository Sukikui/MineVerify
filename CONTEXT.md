# Cahier des charges - Plugin de liaison compte Minecraft

## Objectif

Développer un plugin serveur Minecraft permettant de lier de manière fiable un joueur Minecraft connecté en jeu à un compte externe.

Dans le cas de PMC Map, le compte externe est un compte Discord connecté à l'application web. Le plugin doit toutefois rester générique afin de pouvoir être utilisé avec une autre application, un autre backend, un autre serveur ou un autre système d'identité.

Le principe central est que le serveur Minecraft est la source de vérité pour l'identité Minecraft du joueur. Le plugin doit donc lire l'UUID et le pseudo depuis le joueur réellement connecté en jeu, puis exposer cette validation à l'application externe autorisée qui a initié la demande.

## Architecture cible

L'option retenue est un flux où le plugin gère lui-même les codes de liaison.

L'application externe ne génère pas les codes. Elle demande au plugin de créer un code temporaire, l'affiche à son utilisateur, puis interroge le plugin jusqu'à ce que le joueur valide ce code en jeu ou que la demande expire.

Cette approche garde la logique critique de validation côté plugin :

- génération des codes ;
- durée de validité ;
- usage unique ;
- association d'un code à une application cliente ;
- validation du code par un joueur connecté ;
- exposition du résultat à l'application cliente autorisée.

Le plugin devient donc un service générique de preuve d'identité Minecraft, indépendant de Discord, de PMC Map ou de tout autre produit spécifique.

## Responsabilités du plugin

Le plugin doit fournir deux surfaces fonctionnelles.

La première surface est une API destinée aux applications externes autorisées. Cette API permet de demander un nouveau code de liaison, de consulter l'état d'une demande existante, et éventuellement d'annuler ou nettoyer une demande.

La seconde surface est une commande en jeu destinée aux joueurs connectés. Cette commande permet au joueur de saisir le code reçu depuis l'application externe. Le plugin vérifie alors que le code existe, qu'il est encore valide, qu'il n'a pas déjà été utilisé, puis associe ce code à l'UUID et au pseudo réels du joueur connecté.

Le plugin doit gérer les états d'une demande de liaison de manière explicite : créée, en attente de validation, validée, expirée, annulée ou refusée. Les transitions entre ces états doivent être sûres, atomiques et prévisibles.

## Responsabilités de l'application externe

L'application externe reste responsable de son propre utilisateur.

Dans le cas de PMC Map, cela signifie que l'application sait déjà quel compte Discord est connecté. Elle demande ensuite un code au plugin, conserve localement le lien entre cette demande et son utilisateur externe, affiche le code au joueur, puis récupère le résultat auprès du plugin.

Une fois la demande validée, l'application externe persiste dans sa propre base la liaison entre son utilisateur et l'identité Minecraft confirmée par le plugin.

Le plugin ne doit pas connaître la notion de compte Discord, de compte PMC Map ou de rôle applicatif. Il expose uniquement une validation d'identité Minecraft à une application cliente authentifiée.

## Flux de liaison

Le flux attendu est le suivant :

1. Un utilisateur connecté à une application externe demande à lier son compte Minecraft.
2. L'application externe authentifiée demande au plugin de créer une demande de liaison.
3. Le plugin vérifie que l'application externe est autorisée à utiliser son API.
4. Le plugin génère un code temporaire, à usage unique, lié à cette application cliente.
5. L'application externe affiche ce code à son utilisateur.
6. Le joueur rejoint le serveur Minecraft avec son compte.
7. Le joueur saisit le code via la commande de liaison en jeu.
8. Le plugin lit l'UUID et le pseudo réels du joueur depuis le serveur Minecraft.
9. Le plugin valide la demande si le code est correct, encore valide et non utilisé.
10. L'application externe consulte l'état de la demande auprès du plugin.
11. L'application externe récupère le résultat validé et enregistre la liaison dans son propre système.

Le polling côté application externe est suffisant pour ce besoin. Le plugin n'a pas besoin de connaître l'URL de callback de chaque application, ce qui réduit le couplage et rend l'intégration plus générique.

## API du plugin

Le plugin doit exposer une API technique stable et documentée, destinée uniquement aux applications clientes autorisées.

Cette API doit permettre au minimum :

- de créer une demande de liaison ;
- de consulter l'état d'une demande créée par la même application cliente ;
- de récupérer le résultat d'une demande validée ;
- de distinguer clairement les demandes en attente, validées, expirées, annulées ou refusées.

Le contrat d'API doit être assez générique pour qu'une application web, un bot Discord, un outil interne ou un autre service puisse l'utiliser.

Le format exact des routes, des champs et du protocole reste à définir par le développeur du plugin. En revanche, le contrat doit documenter clairement les états, les erreurs, les délais d'expiration, les règles d'authentification et les données retournées.

## Commande en jeu

Le plugin doit fournir une commande de liaison configurable permettant à un joueur connecté de saisir son code.

La commande doit refuser toute exécution qui ne vient pas d'un joueur réel, car l'identité Minecraft doit être dérivée du contexte serveur du joueur connecté.

Le plugin doit afficher au joueur un message clair dans les situations principales :

- code accepté ;
- code invalide ;
- code expiré ;
- code déjà utilisé ;
- tentative trop fréquente ;
- erreur interne ou configuration invalide.

Les messages doivent être configurables afin de pouvoir adapter le plugin à plusieurs serveurs ou langues.

## Données manipulées

Une demande de liaison doit contenir au minimum les informations nécessaires au suivi de son cycle de vie :

- le code généré ;
- l'application cliente qui a demandé ce code ;
- l'état actuel de la demande ;
- la date de création ;
- la date d'expiration ;
- la date de validation, si elle existe ;
- l'UUID Minecraft validé, si la demande aboutit ;
- le pseudo Minecraft au moment de la validation ;
- l'identifiant du serveur Minecraft, si le plugin est utilisé dans un réseau multi-serveurs.

Les données retournées à une application cliente doivent rester minimales. Une application ne doit pouvoir consulter que les demandes qu'elle a elle-même créées, sauf configuration explicitement prévue pour un usage administratif.

## Sécurité et authentification

L'API du plugin ne doit pas être considérée comme publique ou anonyme. Une application externe qui demande un code ou consulte un résultat doit être authentifiée.

CORS peut être utilisé pour contrôler les appels depuis un navigateur, mais ce n'est pas un mécanisme d'authentification. Il ne protège pas contre les appels serveur à serveur, le spam, l'abus d'API ou l'usurpation d'une application cliente.

Le plugin doit prévoir un mécanisme permettant d'identifier les applications clientes autorisées. La solution exacte reste ouverte, mais les pistes pertinentes incluent :

- une clé d'API par application cliente ;
- un secret partagé par application cliente ;
- une signature cryptographique des requêtes ;
- une authentification portée par un reverse proxy ;
- une restriction réseau ou une liste d'adresses autorisées ;
- une authentification plus structurée si le plugin est intégré dans une plateforme plus large.

Le choix final doit dépendre du contexte de déploiement, mais le cahier des charges impose que le plugin puisse refuser une application inconnue ou non autorisée.

L'authentification doit aussi servir à isoler les demandes. Une application cliente ne doit pas pouvoir consulter, valider, annuler ou récupérer le résultat d'une demande créée par une autre application cliente.

## Protection contre les abus

Le plugin doit prévoir des limites afin d'éviter qu'une application autorisée ou compromise puisse saturer le serveur.

Les protections attendues incluent :

- limitation du nombre de créations de codes par application cliente ;
- limitation du nombre de codes actifs par application cliente ;
- expiration courte des codes ;
- usage unique strict ;
- limitation des tentatives de validation en jeu ;
- nettoyage régulier des demandes expirées ;
- logs d'audit exploitables par l'administration.

Les codes doivent avoir suffisamment d'entropie pour ne pas être devinables dans leur fenêtre de validité. Leur format doit rester lisible pour un joueur, mais la sécurité ne doit pas reposer uniquement sur la discrétion du code.

Les secrets, clés et jetons ne doivent jamais apparaître dans les messages joueur, les réponses d'erreur publiques ou les logs standards.

## Source de vérité Minecraft

L'UUID et le pseudo doivent toujours être lus côté serveur Minecraft au moment où le joueur valide le code.

Le plugin ne doit pas faire confiance à une identité Minecraft fournie par l'application externe. L'application externe peut connaître un pseudo saisi par l'utilisateur, mais cette information ne doit pas être considérée comme une preuve.

Le serveur Minecraft doit être configuré pour garantir l'authenticité des UUID. Sur un serveur public classique, cela implique un serveur en mode online. Si un proxy est utilisé, la transmission des UUID doit être correctement sécurisée entre le proxy et les serveurs backend.

## Stockage et persistance

Le plugin doit stocker les demandes de liaison pendant leur durée de vie.

Un stockage en mémoire peut suffire pour un premier usage simple, à condition que le comportement en cas de redémarrage soit clair. Une persistance peut être prévue si le plugin doit survivre aux redémarrages, fonctionner en réseau multi-serveurs ou partager les demandes entre plusieurs instances.

Le choix du stockage ne doit pas être imposé par ce document. En revanche, les règles fonctionnelles doivent rester les mêmes : expiration, usage unique, isolation par application cliente et transitions d'état sûres.

## Robustesse

Le plugin doit rester sûr pour les performances du serveur Minecraft.

L'API et la commande doivent éviter de bloquer le thread principal du serveur. Les opérations réseau, disque ou potentiellement lentes doivent être maîtrisées et ne pas dégrader le tick serveur.

Le plugin doit gérer proprement :

- les accès concurrents à une même demande ;
- les doubles validations accidentelles ;
- les demandes expirées pendant une action ;
- les erreurs de configuration ;
- les redémarrages, selon le mode de stockage choisi ;
- les réponses API cohérentes même en cas d'erreur interne.

## Configuration attendue

Le plugin doit être configurable sans modification du code.

La configuration doit permettre d'adapter :

- l'activation et l'adresse d'écoute de l'API ;
- l'identité du serveur Minecraft ;
- les applications clientes autorisées ;
- le mécanisme d'authentification retenu ;
- les durées de validité des codes ;
- les limites de débit et quotas ;
- les messages joueur ;
- les noms ou alias de commande ;
- les paramètres CORS si l'API est appelée depuis un navigateur ;
- le mode de stockage des demandes.

Le plugin doit pouvoir être utilisé dans plusieurs environnements avec des configurations différentes.

## Généricité

Le plugin doit être pensé comme un module générique de preuve d'identité Minecraft.

Il doit pouvoir être utilisé avec :

- PMC Map ;
- une autre application web ;
- un bot Discord ;
- un autre système d'authentification ;
- un autre serveur Minecraft ;
- plusieurs applications clientes sur le même serveur ;
- plusieurs environnements avec des règles de sécurité différentes.

Le plugin doit éviter les noms, dépendances ou hypothèses spécifiques à PMC Map dans sa logique interne. Les intégrations spécifiques doivent rester côté application cliente ou configuration.

## Intégration côté PMC Map

Côté PMC Map, l'application devra demander un code au plugin lorsqu'un utilisateur connecté à Discord veut lier son compte Minecraft.

PMC Map conservera localement l'association entre la demande de liaison et l'utilisateur Discord connecté. L'application affichera le code au joueur, interrogera le plugin jusqu'à validation ou expiration, puis enregistrera l'UUID Minecraft confirmé.

La base de données de PMC Map devra stocker au minimum :

- l'UUID Minecraft lié ;
- le pseudo Minecraft au moment de la liaison ;
- la date de liaison ;
- le compte utilisateur externe associé ;
- éventuellement l'identifiant du serveur ayant confirmé la liaison.

Cette partie appartient au backend PMC Map. Le plugin doit seulement fournir une preuve fiable et authentifiée que tel joueur Minecraft connecté a validé telle demande créée par telle application cliente.

## Critères d'acceptation

Le plugin sera considéré comme fonctionnel si :

- une application cliente autorisée peut demander un code de liaison ;
- une application cliente non autorisée est refusée ;
- un code est temporaire, non devinable raisonnablement et à usage unique ;
- un code est associé à l'application cliente qui l'a créé ;
- une application cliente ne peut pas consulter les demandes d'une autre application ;
- un joueur connecté peut valider un code en jeu ;
- l'UUID Minecraft réel du joueur est lu côté serveur ;
- le pseudo Minecraft actuel est capturé au moment de la validation ;
- l'application cliente peut récupérer l'état et le résultat d'une demande validée ;
- les erreurs sont compréhensibles pour le joueur et exploitables dans les logs ;
- les secrets ne sont jamais exposés dans les messages ou logs standards ;
- la configuration permet de réutiliser le plugin dans un autre contexte.
