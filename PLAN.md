# Hammerfest → Java/libGDX : plan et état du portage

Version hors ligne fidèle des « Cavernes de Hammerfest » (Motion Twin, 2006) en Java 17 / libGDX, avec frigo,
quêtes et statistiques gérés localement, multi coopératif sur le même poste et en ligne.
Licence des sources d'origine : CC BY-NC-SA 4.0 (non commercial, attribution Motion Twin, même licence).

## Sources d'origine (`original_sources/`)

Sources de référence, lues seulement (jamais modifiées ni chargées par le jeu) :
- `hammerfest-master/` : jeu d'origine de Motion Twin. Code MotionTypes (`class/hammer/`, référence de tout le
  portage), XML des niveaux, objets, quêtes et textes (`xml/`, convertis par `./gradlew tools:convertData`), SWF/FLA
  des graphismes et des sons (`swf/`, exportés par FFDec, voir « Chaîne des sprites »).
- `mtypes-master/` : compilateur MotionTypes ; `lib/PersistCodec.mt` est la référence du décodeur des niveaux
  (`tools/PersistCodec`).
- `gs-rest-service-master/` : guide Spring « Building a RESTful Web Service » (Apache 2.0), présent à l'origine,
  non utilisé.

Les chemins sont centralisés dans `tools/build.gradle` (`sourceDir`), `export-gfx.sh` et les tests de `tools/`.

## Décisions

- **Build** : Gradle Wrapper 8.14.3, projet de type gdx-liftoff, modules `core` (jeu), `lwjgl3` (lanceur desktop),
  `tools` (conversion des données et des sprites). libGDX 1.13.5, Java 17.
- **Données** : JSON uniquement au runtime (`assets/data/**`), lus et écrits par Jackson (`hammerfest.json.Jsons`) dans
  des classes POJO (`LevelData`, `QuestDef`, `ItemFamily`, `PortalData`, `SaveData`…). Les XML d'origine ne servent
  qu'à la conversion (`./gradlew tools:convertData`).
- **Simulation** : `core/.../sim`, sans dépendance libGDX, à pas fixe de **32 cycles/s** (`Data.SECOND = 32`, `tmod = 1`),
  déterministe (RNG à graine). Toutes les entrées passent par `InputFrame` (1 octet de bits), en solo comme en multi.
- **Rendu** : `GameView` dessine uniquement à partir d'un `RenderFrame` (liste de dessin générique), en solo comme en
  ligne. Les sprites viennent de l'export FFDec des SWF d'origine, rejoués avec les matrices Flash.
- **Au lancement** : écran titre puis menus, jamais directement en partie.
- **Sauvegarde** : `save.json` (frigo, quêtes, familles/options/modes débloqués, stats, nom et identifiant du joueur,
  réglages des manettes). À côté du jar ou de l'exe.
- **Multi en ligne** : hôte qui fait foi, KryoNet, ouverture de port par UPnP (weupnp). Pas de serveur d'annonce ni
  de relais : par Internet, les joueurs se passent l'adresse IP de l'hôte.

## Commandes

- `./gradlew lwjgl3:run` : lancer le jeu (dossier de travail `assets/`).
- `./gradlew build` : compiler et lancer les tests.
- `./gradlew lwjgl3:dist` : jar unique `dist/Hammerfest.jar` (Java 17 requis).
- `./gradlew lwjgl3:packageWindows` : application autonome sans Java installé (jpackage + jlink) :
  `dist/Hammerfest/Hammerfest.exe` et `dist/Hammerfest-windows.zip` (zip sans sauvegarde ; la sauvegarde de l'exe,
  `dist/Hammerfest/app/save.json`, est conservée d'une reconstruction à l'autre).
- `./gradlew tools:convertData` : JSON depuis les XML ; `./gradlew tools:copyAssets` : images (hors dalles) et sons.

## Chaîne des sprites (FFDec, sur le poste)

FFDec (`.tools/ffdec/ffdec-cli.jar`) ne peut être lancé que par l'utilisateur.

1. Export initial : `-swf2xml original_sources/hammerfest-master/swf/_<lib>.swf build/swf-export/<lib>.xml` et
   `-format sprite:png -export sprite build/swf-export/<lib> …` (lib = `anims`, `gfx`, `ending`).
2. `./gradlew tools:freezeNested` écrit `build/swf-export/<lib>-frozen.xml` :
   - clips imbriqués figés par script (tête `head`, costume `skin`, arme `currentWeapon`) réduits à une image ;
   - textes par défaut des champs vidés (« pouet », « blabla »… : le jeu dessine le vrai texte) ;
   - enfants affichés à la demande retirés (`HIDDEN_CHILDREN` : lettres EXTEND du bandeau, jauge et pointeur de la carte ;
     `HIDDEN_IN_SUBS`, dans les sous-clips « sub » : balle de la fraise, redessinée par `SwfLayout.toggleLayers` comme
     couche `part` que seule la fraise qui tient la balle affiche, `RenderFrame.EXTRA_BALL`).
   Puis FFDec `-xml2swf` vers `_<lib>-frozen.swf` et réexport des PNG (`./export-frozen.sh <lib> [ids…]` pour anims et
   ending, `./export-gfx.sh` pour gfx, qui exporte aussi les
   polices `hammerfest.ttf` et `satans.ttf`).
3. Variantes du joueur : `./gradlew tools:playerVariants` puis `./export-variants.sh [variantes…]` :
   `igor`, `sandy`, `soccer1`, `soccer2`, chapeaux `head2`…`head6`, avec collier `<v>-chourou`, sans arme `<v>-lock`.
4. `./gradlew tools:tileSkins` : les 63 dalles et embouts depuis l'export gfx (habillage n = image n ; les PNG fournis
   avec les sources sont mal numérotés).
5. `./gradlew tools:packSprites` (lance `layoutSprites`) : `assets/sprites/<lib>.json` (`SpriteLibrary`) + atlas.
   - `SwfLayout` : cadres recalés sur les PNG, sous-clips « sub », clips qui jouent seuls en boucle (`LOOP_CLIPS`,
     sous-clip à une image dont l'enfant boucle), arrêts (stop, removeMovieClip, gotoAndPlay vers image ou étiquette :
     `loopLength`/`loopStart`), parties nommées dessinées à part (`PARTS` : boule de feu, carte, lettres du bandeau) ;
   - `VariantAlign` : ancrages des variantes recalés sur les images d'Igor ;
   - `PackSprites` : halo « overlay » de la torche retiré (`OVERLAY_GLOW`), lame de la scie retirée de son clip
     (`CUT_INNER`, dessinée à part en rotation) ;
   - couches animées des images figées des ennemis, des objets et des décors de script (`SwfLayout.enemyLayers`, `Clip.layers` ; décors lus dans les niveaux via la propriété `hammerfest.levels`) : clips qui
     jouent seuls à côté de parties fixes (étoiles des ennemis assommés, ailes du tzongre, éclats de la brioche
     dorée…), retirés de l'image à l'assemblage (`PackSprites.cutLayers`) et redessinés à leur image courante ;
     sous-clips des rayons arrêtés par script dont l'enfant boucle (scintillement des téléporteurs).
   - audits des animations (original contre portage) : `node tools/audit/items-audit.js` (objets) et `node tools/audit/decors-audit.js` (décors), arguments dans l'en-tête des scripts.
   - audit des effets visuels (chaque appel `fxMan.*` de l'original contre `game.fx`/`particles`/`explosion`/`shine`/`dust`… du portage) : `node tools/audit/fx-audit.js original_sources/hammerfest-master/class/hammer core/src/main/java/hammerfest/sim build/check/audit-fx.md` (0 absent ; écarts justifiés listés dans le script).
6. Images de l'écran Commandes : `./tools/ui/pad-images.sh` (ImageMagick) → `assets/ui`.

## Menus

Écran titre → menu principal (souris, clavier, manette) : **Jouer** (mode, options débloquées par les quêtes, terrain
de SoccerFest), **Partie multi**, **Frigo**, **Quêtes** (76, validées automatiquement d'après le frigo),
**Statistiques**, **Profil** (nom de 3 à 16 caractères, obligatoire pour le multi), **Commandes**, **Quitter**.
Récapitulatif en fin de partie (objets rangés au frigo, quêtes terminées). Polices : Franklin Gothic Heavy (jeu),
Verdana du système (menus, parchemins), Satans (en-tête du tutoriel).

## Contrôles

- Clavier : Igor flèches + Espace/Ctrl/Entrée (bombe), Sandy D/G/R/F + A ; P pause, C carte, D déguisement (solo),
  Échap quitter. Les touches ne se règlent pas.
- Manettes (`gdx-controllers`, `input/Gamepads`) converties en `InputFrame`. Fixes : croix/stick gauche = déplacement,
  Menu/Options = pause. Réglables (disposition par défaut) : A/Croix = saut, X/Carré = bombe, Y/Triangle = bombe vers
  le haut, B/Rond = carte, RB/R1 = bombe, LB/L1 = bombe vers le haut, LT/L2, RT/R2, clics des sticks et View/Create =
  rien. LT/RT sont lus comme axes (Jamepad, seuil 0,5). Menus : A valide, B/View/capture revient, Start lance.
- **Écran Commandes** (`ControlsScreen`, réglages `save.ControlSettings`) : manette d'Igor et de Sandy par menu
  déroulant (échange si l'autre l'avait, « aucune » = clavier) ; toucher l'un des deux affiche son réglage ; dessin
  de la manette (Xbox / DualSense, `tools/ui/pad-images.sh`) entouré d'un menu déroulant par bouton réglable (11 :
  4 de droite, gâchettes hautes et basses, clics des sticks, View/Create ; Sauter, Bombe, Bombe en haut, Carte, Pause
  ou Aucune ; plusieurs boutons peuvent avoir la même action, avertissement si saut ou bombe n'a aucun bouton), icône
  qui s'allume et nom du bouton à l'appui ; vibrations (activation, intensité : explosion proche, vie perdue, boss
  touché, `screen/Rumble`) et zone morte du stick. Les anciens réglages à 4 boutons sont complétés au chargement.
- En solo, toutes les manettes jouent Igor ; à deux sur le poste, chacun sa manette. En ligne, le joueur qui rejoint
  joue Sandy avec le réglage d'Igor de son poste, ce que rappelle un libellé du réglage d'Igor dans l'écran Commandes.

## Fonctionnalités portées

- **Niveaux et données** : 17 sets décodés (`tools/PersistCodec`), rendu des niveaux (fond `hammer_bg`, dalles, colonnes,
  champs/rayons, téléporteurs), scripts de niveau (`sim/ScriptEngine` : tous les déclencheurs, actions et codes 0-15),
  décors animés, obscurité et halos (joueur, bougie, torches), inversion de l'écran, miroir, niveaux visités.
- **Joueur** : déplacement, sauts, bombes et armes (classique, noire, bleue, verte, rouge, répulsive, tirs), coup de
  pied, bouclier, vies, lettres EXTEND, objets spéciaux (118 effets, `SpecialManager`) et à score, super-objets,
  déguisements (D), collier de Poils de Chourou, écharpe aux couleurs de l'arme (cadenas sans arme), Sandy.
- **Ennemis** : tous ceux de l'Aventure et des dimensions (fruits, lance, volants, framboise, wallwalkers : blob et
  scie), hurry up (colère puis boule de feu par joueur), boss Chauve-souris et Tuberculoz, fin du jeu (prison, ascenseur).
- **Portails et dimensions** : adventure, deepnight, hiko, ayame, hk ; portails flottants, vortex animés.
- **Modes** : Aventure, Apprentissage, Time Attack (seul et à deux), multi coopératif, SoccerFest, Tubercourse ; options
  (miroir, cauchemar, ninjutsu, explosifs instables, partage de vies, contrôle du ballon, tornade).
- **Interface** : bandeau d'origine (lettres ramassées, chapeaux de vie et « + », score, niveau), second bandeau
  (niveau, joueurs ; en ligne : joueur connecté et comment quitter), parchemin des messages, nom de la contrée,
  alertes (« VITE ! », boss…), carte (C), pause avec aide, musique et bruitages, particules ; effets d'origine
  (`FxPlayer` : échelle, rotation, alpha, fusion overlay ; reflet `shine`, poussière `dust`, gouttes de pluie),
  flèche « niveau suivant » (`Game.exitArrow`), flèche d'entrée, pointeur du tutoriel, messages « Clé requise » /
  « Utilise », nuage de la poupée Guu.
- **Frigo, quêtes, statistiques** : `Profile.applyResult` en fin de partie (Apprentissage exclu).

## Multijoueur en ligne

- Paquet `hammerfest.net` (KryoNet 2.22.0-RC1) : `JoinRequest`, `JoinResponse`, `StartGame`, `InputMsg`, `Leave`,
  `RenderFrame`.
- Découverte LAN (`LanDiscovery`, UDP 54556) ; par Internet, saisie de l'adresse de l'hôte. Demande de connexion avec
  nom : l'hôte accepte ou refuse ; refus automatique si la partie est pleine ou la version différente.
- Salon de l'hôte, une fois le joueur accepté : mode à deux (multi coopératif, SoccerFest, Time Attack à deux) et
  mêmes options que l'écran Jouer (`ModeOptionsTable` partagé : partage de vies, miroir, cauchemar, terrain et
  contrôle du ballon de SoccerFest), selon les déblocages de l'hôte.
- UPnP (`UpnpPortService`, weupnp) : redirection TCP + UDP avec bail d'1 h renouvelé, supprimée à la fermeture ; l'IP
  externe s'affiche chez l'hôte. Sans UPnP : réseau local, ou port ouvert à la main.
- Hôte qui fait foi : le client envoie ses touches à chaque cycle, l'hôte un instantané tous les 2 cycles (TCP).
  Le client interpole et **prédit son personnage** (`sim/MovePredictor` : simulation réduite au niveau, recalée sur
  l'état envoyé par l'hôte au dernier tick de touches reçu, touches suivantes rejouées ; glace et eau comprises).
- Fin de partie : le client range les objets ramassés dans son frigo, ses stats et quêtes sont mises à jour.
- Testé : local, deux postes du même réseau, Internet avec UPnP (légère latence avant la prédiction).

## Restes à faire

- **À tester** : prédiction en ligne avec un joueur distant (autoriser l'accès réseau de Windows sur l'hôte) ;
  écran Commandes avec de vraies manettes Xbox et DualSense (gâchettes LT/RT, L2/R2 comprises), vibrations
  comprises ; variantes « sans arme » (cadenas sur l'écharpe) et têtes de SoccerFest (couronne du buteur, tête
  basse contre son camp), exportées et assemblées le 24/11.
- **Fin de portage** : retirer la touche 1 (saut de niveau, tests manuels).

## Vérification

- Comparer avec le jeu original (vidéos de référence, Ruffle sur `swf/game.swf`) : tutoriel, premiers niveaux, objets
  et frigo.
- Menus : partie, objets ramassés, mort → frigo et stats à jour ; quête validée en complétant ses objets.
- Multi : nom de l'hôte dans la liste, accepter / refuser, redirection UPnP visible dans la box puis supprimée à la
  fermeture.
