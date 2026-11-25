# Hammerfest – portage Java/libGDX

Portage hors ligne du jeu Flash « Les cavernes de Hammerfest » (Motion Twin, 2006) en Java/libGDX.

**Le plan complet et les décisions sont dans [PLAN.md](PLAN.md) : le lire avant toute tâche.**

## Décisions clés (détails dans PLAN.md)
- Build : Gradle Wrapper, projet généré avec gdx-liftoff (modules `core/`, `lwjgl3/`, `tools/`), Java 17.
- Données : JSON uniquement au runtime ; les XML d'origine ne servent que de source de conversion (`tools/`).
- Au lancement : menus (Jouer + options, Frigo, Quêtes, Statistiques), jamais directement en partie.
- Sauvegarde locale dans `save.json` (frigo, quêtes, familles débloquées, stats).
- Simulation dans `core` sans dépendance libGDX, entrées via `InputFrame` (prépare le multi).
- Contrôles : clavier et manette de jeu (`gdx-controllers`, classe `hammerfest.input.Gamepads`), convertis tous deux en `InputFrame`. Écran Commandes (menu principal) : manette d'Igor / de Sandy, action des 11 boutons réglables (façade, gâchettes, clics des sticks, View), vibrations, zone morte (`save.ControlSettings` dans `save.json`).
- Multi (phase ultérieure) : hôte autoritaire, KryoNet, ouverture de port UPnP (weupnp).
- Nom de joueur saisi depuis le menu principal (Profil, stocké dans `save.json` avec un `playerId`), obligatoire pour le multi : affiché dans l'annonce des parties, envoyé à l'hôte qui accepte/refuse la connexion, et nom de l'hôte affiché au joueur qui rejoint.

## Commandes
- `./gradlew lwjgl3:run` : lancer le jeu (dossier de travail `assets/`, `save.json` y est écrit).
- `./gradlew build` : compiler et lancer les tests.
- `./gradlew lwjgl3:dist` : jar exécutable unique `dist/Hammerfest.jar` (double-clic ; Java 17+ requis ; `save.json` et `hammerfest-erreur.log` écrits à côté du jar).
- `./gradlew lwjgl3:packageWindows` : application Windows autonome sans Java installé (jpackage + jlink) : `dist/Hammerfest/Hammerfest.exe` et `dist/Hammerfest-windows.zip` ; sauvegarde dans `dist/Hammerfest/app/`.
- `./gradlew tools:convertData` : régénérer `assets/data/**.json` depuis les XML ; `./gradlew tools:copyAssets` : copier images et sons.
- `./gradlew tools:packSprites` : atlas de sprites depuis l'export FFDec de `build/swf-export/` (voir PLAN.md, « Commandes sprites »).
- JSON : classes de données POJO lues/écrites par Jackson (`hammerfest.json.Jsons`), jamais de JsonValue manuel.
- Textes du portage : `Tr.t("texte français")` (`hammerfest.ui.Tr`), traductions en/es dans `assets/data/lang/ui.json` (vérifié par `TrTest`).

## Contournements temporaires (à supprimer en fin de portage)
- Touche **1** en partie : saut au niveau suivant (`Game.debugSkipLevel`, `GameScreen`), pour les tests manuels.
- Touche **2** en partie : une vie de plus pour chaque joueur (`Game.debugAddLife`, `GameScreen`), pour les tests manuels.

## Sources de référence
Dans `original_sources/` (sources d'origine, lecture seule) :
- `hammerfest-master/` : code MotionTypes d'origine (`class/hammer/`), XML, assets, swf/fla.
- `mtypes-master/` : compilateur MotionTypes.
- `gs-rest-service-master/` : guide Spring « Building a RESTful Web Service » (licence Apache 2.0), non utilisé par le portage.
- Licence : CC BY-NC-SA 4.0 (non commercial, attribution Motion Twin).
