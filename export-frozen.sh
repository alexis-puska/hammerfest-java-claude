#!/usr/bin/env bash
# Réexporte les images d'une bibliothèque de sprites depuis son XML figé (voir FreezeNested).
#
# Usage, depuis la racine du projet (après ./gradlew tools:freezeNested) :
#   ./export-frozen.sh anims                       -> toutes les images de anims
#   ./export-frozen.sh anims 1755 1758 1792        -> seulement ces sprites (ici : la fraise), bien plus rapide
#   ./export-frozen.sh ending
# Puis : ./gradlew tools:packSprites   (ou -Plibs=<lib> pour n'assembler que cette bibliothèque)
# Pour gfx, utiliser ./export-gfx.sh (qui exporte aussi les polices).
set -e
cd "$(dirname "$0")"
FFDEC="java -jar .tools/ffdec/ffdec-cli.jar"
LIB="$1"
if [ -z "$LIB" ]; then
  echo "Usage : ./export-frozen.sh <anims|ending> [identifiants de sprites…]" >&2
  exit 2
fi
shift
OUT=build/swf-export
XML="$OUT/$LIB-frozen.xml"
SWF="$OUT/_$LIB-frozen.swf"
if [ ! -f "$XML" ]; then
  echo "$XML absent : lancer d'abord ./gradlew tools:freezeNested" >&2
  exit 1
fi

echo "=== $XML -> $SWF ==="
$FFDEC -xml2swf "$XML" "$SWF"

if [ $# -eq 0 ]; then
  echo "=== export de toutes les images -> $OUT/$LIB ==="
  $FFDEC -format sprite:png -export sprite "$OUT/$LIB" "$SWF" > /dev/null
else
  IDS=$(IFS=,; echo "$*")
  TMP="$OUT/$LIB-partiel"
  echo "=== export des sprites $IDS -> $OUT/$LIB ==="
  rm -rf "$TMP"
  $FFDEC -selectid "$IDS" -format sprite:png -export sprite "$TMP" "$SWF" > /dev/null
  # remplace les dossiers de ces sprites dans l'export complet (anciennes images supprimées)
  for d in "$TMP"/DefineSprite_*; do
    [ -d "$d" ] || continue
    rm -rf "$OUT/$LIB/$(basename "$d")"
    cp -r "$d" "$OUT/$LIB/"
    echo "  $(basename "$d") : $(ls "$d" | wc -l) images"
  done
  rm -rf "$TMP"
fi
echo "Terminé : lancer ./gradlew tools:packSprites -Plibs=$LIB"
