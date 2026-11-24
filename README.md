# Hammerfest – portage Java / libGDX

Portage hors ligne du jeu Flash **« Les cavernes de Hammerfest »** (Motion Twin, 2006) en Java 17 avec libGDX :
Aventure, Apprentissage, Time Attack, SoccerFest et Tubercourse ; frigo, quêtes et statistiques sauvegardés en
local ; jeu à deux sur le même poste ou en ligne ; clavier et manettes Xbox / PlayStation.

Le plan, les choix techniques et l'état du portage sont décrits dans [PLAN.md](PLAN.md).

## Prérequis

- **JDK 17** (pas seulement un JRE) : le build impose une chaîne d'outils Java 17 que Gradle doit trouver sur le
  poste (`JAVA_HOME` ou installation standard), par exemple [Eclipse Temurin 17](https://adoptium.net/).
- Rien d'autre à installer : Gradle est fourni par le wrapper (`gradlew`, `gradlew.bat`), qui télécharge la bonne
  version au premier lancement.

Sous Windows, utiliser `gradlew.bat` (invite de commandes, PowerShell) ou `./gradlew` (Git Bash). Les exemples
ci-dessous utilisent `./gradlew`.

## Compiler

```bash
./gradlew build
```

Compile les trois modules et lance les tests. Les modules :

| Module   | Rôle                                                                 |
|----------|----------------------------------------------------------------------|
| `core`   | le jeu : simulation, écrans, rendu, réseau                           |
| `lwjgl3` | lanceur desktop (fenêtre, manettes, UPnP) et empaquetage             |
| `tools`  | conversion des données et des sprites (inutile pour jouer)           |

## Lancer le jeu

### En ligne de commande

```bash
./gradlew lwjgl3:run
```

Le dossier de travail est `assets/` : la sauvegarde `assets/save.json` y est créée.

### Depuis un IDE

**IntelliJ IDEA**
1. *File > Open* et choisir le dossier du projet (le `build.gradle` racine) ; accepter l'import Gradle.
2. *File > Project Structure > Project* : SDK = JDK 17.
3. Lancer, au choix :
   - la tâche Gradle **`lwjgl3 > Tasks > application > run`** (fenêtre Gradle), qui règle déjà le dossier de travail ;
   - ou une configuration *Application* :
     - *Module* : `hammerfest.lwjgl3.main`
     - *Main class* : `hammerfest.lwjgl3.Lwjgl3Launcher`
     - *Working directory* : `<projet>/assets` (**obligatoire** : le jeu y lit ses images, sons et données).

**Eclipse** : *File > Import > Gradle > Existing Gradle Project*, puis lancer la tâche `lwjgl3 > application > run`
depuis la vue *Gradle Tasks* (ou une configuration *Java Application* sur `hammerfest.lwjgl3.Lwjgl3Launcher` avec
`assets/` comme dossier de travail, onglet *Arguments*).

**VS Code** (Extension Pack for Java + Gradle for Java) : ouvrir le dossier, puis lancer `lwjgl3 > run` depuis la vue
Gradle.

### Raccourci de test

En partie, la touche **1** passe au niveau suivant (aide aux tests manuels, retirée en fin de portage).

## Créer la version à distribuer

### Jar exécutable unique (Java 17+ requis chez le joueur)

```bash
./gradlew lwjgl3:dist
```

Produit `dist/Hammerfest.jar` (jeu et ressources dans un seul fichier) : double-clic ou
`java -jar Hammerfest.jar`. `save.json` et, en cas de plantage, `hammerfest-erreur.log` sont écrits à côté du jar.

### Application Windows autonome (sans Java installé)

```bash
./gradlew lwjgl3:packageWindows
```

Utilise `jpackage` et `jlink` du JDK 17 (aucun autre outil : pas de WiX, ce n'est pas un installateur). Produit :

- `dist/Hammerfest/Hammerfest.exe` : le jeu, avec un Java réduit embarqué dans `dist/Hammerfest/runtime/` ;
- `dist/Hammerfest-windows.zip` : le même dossier zippé, à envoyer tel quel (le joueur dézippe et lance l'exe).

La sauvegarde de l'exe est `dist/Hammerfest/app/save.json` ; elle est conservée quand on reconstruit, et le zip
n'en contient pas.

## Sauvegarde d'exemple

[`save_exemple/save-toutes-quetes.json`](save_exemple/) est une sauvegarde toute prête : les 76 quêtes
terminées (donc toutes les options et tous les modes qu'elles débloquent) et chacun des 353 objets à 99 exemplaires
dans le frigo. Pratique pour tester sans tout débloquer.

Le jeu lit sa sauvegarde dans un fichier nommé **`save.json`** ; il suffit d'y copier l'exemple, jeu fermé.
L'emplacement dépend de la façon dont le jeu est lancé :

| Lancement                                                        | Emplacement de `save.json`        |
|------------------------------------------------------------------|-----------------------------------|
| `./gradlew lwjgl3:run`, ou depuis l'IDE (dossier de travail `assets/`) | `assets/save.json`          |
| jar : `dist/Hammerfest.jar`                                      | `dist/save.json` (à côté du jar)  |
| exe : `dist/Hammerfest/Hammerfest.exe`                           | `dist/Hammerfest/app/save.json`   |

```bash
cp save_exemple/save-toutes-quetes.json assets/save.json                # Gradle / IDE
cp save_exemple/save-toutes-quetes.json dist/Hammerfest/app/save.json   # exe (après packageWindows)
```

Sous Windows sans Git Bash : `copy save_exemple\save-toutes-quetes.json assets\save.json`.

La sauvegarde existante est remplacée : la renommer d'abord pour la garder. `assets/save.json` n'est pas versionné
(`.gitignore`).

## Régénérer les données (développeurs)

Les données du jeu (`assets/`) sont déjà générées et versionnées ; ces commandes ne servent que pour modifier la
conversion. Elles lisent les sources d'origine de `original_sources/`.

```bash
./gradlew tools:convertData   # niveaux, objets, quêtes, textes : XML d'origine -> assets/data/**.json
./gradlew tools:copyAssets    # images et sons d'origine -> assets/
./gradlew tools:packSprites   # atlas de sprites depuis l'export FFDec (build/swf-export/)
```

`tools:packSprites` a besoin de l'export des SWF d'origine : voir
[Retravailler les sprites](#retravailler-les-sprites-ffdec) en fin de document.

## Contrôles

| Action          | Igor                  | Sandy (2 joueurs) | Manette (par défaut)          |
|-----------------|-----------------------|-------------------|-------------------------------|
| Se déplacer     | flèches gauche/droite | D / G             | croix ou stick gauche         |
| Sauter          | flèche haut           | R                 | A / Croix                     |
| Bombe           | Espace, Ctrl, Entrée  | A                 | X / Carré, RB / R1            |
| Bombe en haut   | flèche bas            | F                 | Y / Triangle, LB / L1         |
| Carte           | C                     | C                 | B / Rond                      |
| Pause           | P                     | P                 | Menu / Options                |

Les boutons de la manette (façade, gâchettes, clics des sticks, View / Create) se règlent dans le menu
**Commandes**, avec les vibrations et la zone morte du stick.

## Arborescence

```
assets/            ressources du jeu (données JSON, atlas, sons, polices, interface)
core/              code du jeu
lwjgl3/            lanceur desktop et empaquetage
tools/             conversion des données et des sprites, audits
save_exemple/      sauvegarde d'exemple (toutes les quêtes, un objet de chaque type)
original_sources/  sources d'origine (lecture seule) : jeu Motion Twin, compilateur MotionTypes
```

## Licence

Le jeu d'origine et ses ressources sont de **Motion Twin**, publiés sous licence
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) : ce portage en reprend la licence. Usage
non commercial uniquement, attribution à Motion Twin, partage dans les mêmes conditions.

## Retravailler les sprites (FFDec)

Inutile pour compiler, jouer ou distribuer : les atlas finis sont déjà dans `assets/sprites`. Cette chaîne ne sert
qu'à modifier la façon dont les graphismes d'origine sont convertis. Les dossiers qu'elle utilise ne sont pas
versionnés (`.gitignore`) et se recréent ainsi :

| Dossier              | Contenu                                         | Recréé par                     |
|----------------------|-------------------------------------------------|--------------------------------|
| `.gradle/`, `build/` | caches et compilation                           | Gradle, automatiquement        |
| `.tools/ffdec/`      | JPEXS FFDec (décompilateur SWF)                 | téléchargement manuel (étape 1) |
| `build/swf-export/`  | export XML et PNG des SWF d'origine (~2 Go)     | étapes 2 à 6 ci-dessous        |

Toutes les commandes se lancent depuis la racine du projet, dans **Git Bash** (les scripts `.sh` sont en bash).
Prévoir environ 2 Go d'espace disque et une petite heure au total.

### 1. Outils

- **JPEXS FFDec** : télécharger le zip de la dernière version sur
  <https://github.com/jindrapetrik/jpexs-decompiler/releases> (testé avec la 26.3.0) et le dézipper dans
  `.tools/ffdec/`, de façon à avoir `.tools/ffdec/ffdec-cli.jar`.
- **ImageMagick 7** (commande `magick`), seulement pour redessiner les images de l'écran Commandes (étape 8).
- **Node.js**, seulement pour les scripts d'audit (`tools/audit/`).

Pour alléger les commandes suivantes :

```bash
FFDEC="java -jar .tools/ffdec/ffdec-cli.jar"
mkdir -p build/swf-export
```

### 2. Export des SWF en XML

```bash
for lib in anims gfx ending; do
  $FFDEC -swf2xml original_sources/hammerfest-master/swf/_$lib.swf build/swf-export/$lib.xml
done
```

### 3. Clips figés

```bash
./gradlew tools:freezeNested
```

Écrit `build/swf-export/<lib>-frozen.xml` : clips imbriqués pilotés par script (tête d'Igor, arme…) réduits à une
image, textes par défaut des champs vidés, éléments affichés à la demande retirés.

### 4. Images des sprites

```bash
./export-frozen.sh anims
./export-frozen.sh ending
./export-gfx.sh   # même chose pour gfx, plus les polices assets/fonts/hammerfest.ttf et satans.ttf
```

Après une modification de `FreezeNested` qui ne touche que quelques sprites, inutile de tout réexporter :
`./export-frozen.sh anims 1755 1758 1792` (identifiants des sprites, ici la fraise) ne remplace que ceux-là, puis
`./gradlew tools:packSprites -Plibs=anims`.

### 5. Variantes du joueur (Sandy, chapeaux, tenues de foot, collier, sans arme)

```bash
./gradlew tools:playerVariants   # build/swf-export/anims-<variante>.xml
./export-variants.sh             # images des 27 variantes (environ 1 min chacune)
```

`./export-variants.sh igor-lock sandy-lock` n'exporte que les variantes données.

### 6. Sons

```bash
$FFDEC -format sound:mp3_wav -export sound build/swf-export/sounds original_sources/hammerfest-master/swf/_sounds.swf
./gradlew tools:copySounds       # -> assets/sounds
```

### 7. Dalles et atlas

```bash
./gradlew tools:tileSkins        # les 63 dalles et embouts, depuis l'export de gfx
./gradlew tools:packSprites      # assets/sprites/<lib>.json + .atlas + pages PNG (environ 25 min)
```

`./gradlew tools:packSprites -Plibs=ending` n'assemble que les bibliothèques données (sans les variantes du
joueur), beaucoup plus vite.

### 8. Images de l'écran Commandes (facultatif)

```bash
./tools/ui/pad-images.sh         # assets/ui/pad-*.png et btn-*.png (ImageMagick)
```

Le détail de chaque étape (ce qui est figé, recalé ou retiré, et pourquoi) est dans [PLAN.md](PLAN.md),
section « Chaîne des sprites ».
