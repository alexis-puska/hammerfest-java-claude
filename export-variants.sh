#!/usr/bin/env bash
# Exporte les images du joueur pour Sandy et les tenues de foot (voir PLAN.md, PlayerVariants).
# Usage, depuis la racine du projet : ./export-variants.sh   puis   ./gradlew tools:packSprites
set -e
cd "$(dirname "$0")"
FFDEC="java -jar .tools/ffdec/ffdec-cli.jar"
IDS=497,508,516,530,544,547,554,568,593,636,686,701,716,740,742,744,757,766,776,782,802,804,805
VARIANTS="igor sandy soccer1 soccer2 head2 head3 head4 head5 head6 \
    igor-chourou sandy-chourou head2-chourou head3-chourou head4-chourou head5-chourou head6-chourou \
    igor-lock sandy-lock head2-lock head3-lock head4-lock head5-lock head6-lock \
    soccer1-crown soccer1-lose soccer2-crown soccer2-lose"
# chacune aussi avec la carotte d'Igor (famille 108 ou objet 102) : <v>-carot
for v in $VARIANTS; do VARIANTS="$VARIANTS $v-carot"; done
# Variantes données en arguments (ex. ./export-variants.sh igor-lock sandy-lock), sinon toutes
[ $# -gt 0 ] && VARIANTS="$*"
for v in $VARIANTS; do
  echo "=== $v ==="
  $FFDEC -xml2swf build/swf-export/anims-$v.xml build/swf-export/_anims-$v.swf
  $FFDEC -selectid $IDS -format sprite:png -export sprite build/swf-export/anims-$v build/swf-export/_anims-$v.swf > /dev/null
done
echo "Terminé : lancer ./gradlew tools:packSprites"
