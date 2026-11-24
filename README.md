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

## Régénérer les données (développeurs)

Les données du jeu (`assets/`) sont déjà générées et versionnées ; ces commandes ne servent que pour modifier la
conversion. Elles lisent les sources d'origine de `original_sources/`.

```bash
./gradlew tools:convertData   # niveaux, objets, quêtes, textes : XML d'origine -> assets/data/**.json
./gradlew tools:copyAssets    # images et sons d'origine -> assets/
./gradlew tools:packSprites   # atlas de sprites depuis l'export FFDec (build/swf-export/)
```

La chaîne complète des sprites (export des SWF avec JPEXS FFDec, variantes du joueur) est détaillée dans
[PLAN.md](PLAN.md), section « Chaîne des sprites ».

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
original_sources/  sources d'origine (lecture seule) : jeu Motion Twin, compilateur MotionTypes
```

## Licence

Le jeu d'origine et ses ressources sont de **Motion Twin**, publiés sous licence
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) : ce portage en reprend la licence. Usage
non commercial uniquement, attribution à Motion Twin, partage dans les mêmes conditions.
