#!/usr/bin/env bash
# Réexporte les images de gfx depuis gfx-frozen.xml (clips figés, champs texte vidés : voir FreezeNested).
# Usage, depuis la racine du projet : ./gradlew tools:freezeNested  puis  ./export-gfx.sh  puis  ./gradlew tools:packSprites
set -e
cd "$(dirname "$0")"
FFDEC="java -jar .tools/ffdec/ffdec-cli.jar"
$FFDEC -xml2swf build/swf-export/gfx-frozen.xml build/swf-export/_gfx-frozen.swf
$FFDEC -format sprite:png -export sprite build/swf-export/gfx build/swf-export/_gfx-frozen.swf > /dev/null
# Police du jeu (Franklin Gothic Heavy, id 24 de gfx) -> assets/fonts/hammerfest.ttf
rm -rf build/swf-export/fonts
$FFDEC -selectid 24 -format font:ttf -export font build/swf-export/fonts original_sources/hammerfest-master/swf/_gfx.swf > /dev/null
mkdir -p assets/fonts
cp "$(find build/swf-export/fonts -name "*.ttf" | head -1)" assets/fonts/hammerfest.ttf
# En-tête du parchemin du tutoriel (police Satans, id 275) -> assets/fonts/satans.ttf
rm -rf build/swf-export/fonts275
$FFDEC -selectid 275 -format font:ttf -export font build/swf-export/fonts275 original_sources/hammerfest-master/swf/_gfx.swf > /dev/null
cp "$(find build/swf-export/fonts275 -name "*.ttf" | head -1)" assets/fonts/satans.ttf
echo "Terminé : lancer ./gradlew tools:tileSkins tools:packSprites"
