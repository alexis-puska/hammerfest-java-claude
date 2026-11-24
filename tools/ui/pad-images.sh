#!/usr/bin/env bash
# Images de l'écran Commandes (ImageMagick) : manette Xbox / DualSense vue de face, gâchettes au-dessus, chaque
# bouton réglable marqué de la même icône que sa ligne de réglage ; icônes des 11 boutons réglables.
# Tout est dessiné à l'échelle 2 (affiché à moitié par l'écran, net sur grand écran).
# Usage, depuis la racine du projet : ./tools/ui/pad-images.sh   -> assets/ui/pad-*.png, assets/ui/btn-*.png
set -e
cd "$(dirname "$0")/../.."
OUT=assets/ui
mkdir -p "$OUT"
F="C:/Windows/Fonts/verdanab.ttf"
S=2                      # échelle
W=$((210*S)); H=$((160*S))

# glyph type bouton x y r : ajoute à G le dessin de l'icône du bouton centrée en x,y (rayon r) ; CW/CH = taille de
# l'image en cours (centre des textes, -gravity center).
txt() { # texte centré en $1,$2, taille $3, couleur $4, texte $5
  local dx=$(( $1 - CW/2 )) dy=$(( $2 - CH/2 ))
  G+=(-gravity center -font "$F" -pointsize "$3" -fill "$4" -stroke none -annotate "$(printf '%+d%+d' $dx $dy)" "$5")
}
glyph() {
  local t=$1 b=$2 x=$3 y=$4 r=$5 s=$(( $5 * 6 / 11 )) dark light
  if [ "$t" = xbox ]; then dark="#2b2f3a"; light="#ffffff"; else dark="#2b2f3a"; light="#ffffff"; fi
  case $b in
    top|right|left|bottom)
      if [ "$t" = xbox ]; then
        case $b in top) c="#e0b52c"; l=Y;; right) c="#d9463b"; l=B;; left) c="#3b7bd9"; l=X;; bottom) c="#3fae4a"; l=A;; esac
        G+=(-fill "$c" -stroke none -draw "circle $x,$y $((x+r)),$y")
        txt $x $((y+1)) $((r*5/4)) "#ffffff" "$l"
      else
        G+=(-fill "$dark" -stroke none -draw "circle $x,$y $((x+r)),$y" -fill none -strokewidth $((r/6+1)))
        case $b in
          top) G+=(-stroke "#3fd1a0" -draw "polygon $x,$((y-s)) $((x-s)),$((y+s*4/5)) $((x+s)),$((y+s*4/5))");;
          right) G+=(-stroke "#ff5c6a" -draw "circle $x,$y $((x+s)),$y");;
          left) G+=(-stroke "#ff8fd0" -draw "rectangle $((x-s*4/5)),$((y-s*4/5)) $((x+s*4/5)),$((y+s*4/5))");;
          bottom) G+=(-stroke "#8fb4ff" -draw "line $((x-s*4/5)),$((y-s*4/5)) $((x+s*4/5)),$((y+s*4/5))" -draw "line $((x-s*4/5)),$((y+s*4/5)) $((x+s*4/5)),$((y-s*4/5))");;
        esac
        G+=(-stroke none)
      fi;;
    l1|r1) # gâchette haute : bandeau arrondi
      if [ "$t" = xbox ]; then [ $b = l1 ] && l=LB || l=RB; else [ $b = l1 ] && l=L1 || l=R1; fi
      G+=(-fill "$dark" -stroke "#8a92a6" -strokewidth $((r/8+1)) -draw "roundrectangle $((x-r)),$((y-r*3/5)) $((x+r)),$((y+r*3/5)) $((r/2)),$((r/2))" -stroke none)
      txt $x $((y+1)) $((r*4/5)) "$light" "$l";;
    l2|r2) # gâchette basse : plus haute, arrondie en haut
      if [ "$t" = xbox ]; then [ $b = l2 ] && l=LT || l=RT; else [ $b = l2 ] && l=L2 || l=R2; fi
      G+=(-fill "$dark" -stroke "#8a92a6" -strokewidth $((r/8+1)) -draw "roundrectangle $((x-r*4/5)),$((y-r)) $((x+r*4/5)),$((y+r)) $((r*3/4)),$((r*3/4))" -stroke none)
      txt $x $((y+1)) $((r*4/5)) "$light" "$l";;
    l3|r3) # clic du stick
      if [ "$t" = xbox ]; then [ $b = l3 ] && l=LS || l=RS; else [ $b = l3 ] && l=L3 || l=R3; fi
      G+=(-fill "$dark" -stroke "#8a92a6" -strokewidth $((r/8+1)) -draw "circle $x,$y $((x+r)),$y" -stroke none)
      txt $x $((y+1)) $((r*4/5)) "$light" "$l";;
    select) # View (deux fenêtres) / Create (pastille à traits)
      G+=(-fill "#4a5163" -stroke none -draw "circle $x,$y $((x+r)),$y" -fill none -stroke "$light" -strokewidth $((r/7+1)))
      if [ "$t" = xbox ]; then
        G+=(-fill "#4a5163" -draw "rectangle $((x-s*4/5)),$((y-s*3/5)) $((x+s/4)),$((y+s/6))"
            -draw "rectangle $((x-s/4)),$((y-s/6)) $((x+s*4/5)),$((y+s*3/5))" -fill none)
      else
        G+=(-draw "roundrectangle $((x-s*3/5)),$((y-s)) $((x+s*3/5)),$((y+s)) $((s/3)),$((s/3))"
            -draw "line $((x-s/4)),$((y-s/2)) $((x-s/4)),$((y-s/6))" -draw "line $((x+s/4)),$((y-s/2)) $((x+s/4)),$((y-s/6))")
      fi
      G+=(-stroke none);;
    start) # Menu / Options : trois traits (pause, non réglable)
      G+=(-fill "#4a5163" -stroke none -draw "circle $x,$y $((x+r)),$y" -fill none -stroke "$light" -strokewidth $((r/7+1))
          -draw "line $((x-s)),$((y-s*2/3)) $((x+s)),$((y-s*2/3))" -draw "line $((x-s)),$y $((x+s)),$y"
          -draw "line $((x-s)),$((y+s*2/3)) $((x+s)),$((y+s*2/3))" -stroke none);;
  esac
}

BUTTONS="top right left bottom l1 r1 l2 r2 l3 r3 select"
for t in xbox ps; do
  if [ "$t" = xbox ]; then BODY="#2b2f3a"; EDGE="#5b6275"; else BODY="#e9ecf2"; EDGE="#9aa3b5"; fi
  CW=$W; CH=$H
  G=(-size ${W}x${H} xc:none)
  # corps et poignées
  G+=(-fill "$BODY" -stroke "$EDGE" -strokewidth $((2*S))
      -draw "roundrectangle $((14*S)),$((50*S)) $((196*S)),$((120*S)) $((34*S)),$((34*S))"
      -draw "ellipse $((44*S)),$((118*S)) $((28*S)),$((36*S)) 0,360" -draw "ellipse $((166*S)),$((118*S)) $((28*S)),$((36*S)) 0,360"
      -stroke none -draw "rectangle $((30*S)),$((70*S)) $((180*S)),$((118*S))")
  if [ "$t" = ps ]; then # pavé tactile et barre lumineuse
    G+=(-fill "#cfd4de" -stroke "$EDGE" -strokewidth $S -draw "roundrectangle $((76*S)),$((52*S)) $((134*S)),$((80*S)) $((6*S)),$((6*S))" -stroke none
        -fill "#4aa3ff" -draw "rectangle $((86*S)),$((53*S)) $((124*S)),$((54*S))")
  fi
  # croix
  G+=(-fill "#11141b" -draw "rectangle $((32*S)),$((76*S)) $((60*S)),$((86*S))" -draw "rectangle $((41*S)),$((67*S)) $((51*S)),$((95*S))")
  # gâchettes (au-dessus), sticks, face, View/Create et Menu/Options
  glyph $t l2 $((40*S)) $((16*S)) $((13*S)); glyph $t r2 $((170*S)) $((16*S)) $((13*S))
  glyph $t l1 $((74*S)) $((30*S)) $((15*S)); glyph $t r1 $((136*S)) $((30*S)) $((15*S))
  glyph $t l3 $((76*S)) $((104*S)) $((12*S)); glyph $t r3 $((134*S)) $((104*S)) $((12*S))
  FX=$((166*S)); FY=$((81*S)); D=$((15*S)); R=$((9*S))
  glyph $t top $FX $((FY-D)) $R; glyph $t right $((FX+D)) $FY $R; glyph $t left $((FX-D)) $FY $R; glyph $t bottom $FX $((FY+D)) $R
  if [ "$t" = xbox ]; then SX=$((88*S)); MX=$((122*S)); SY=$((66*S)); else SX=$((66*S)); MX=$((144*S)); SY=$((60*S)); fi
  glyph $t select $SX $SY $((7*S)); glyph $t start $MX $SY $((7*S))
  magick "${G[@]}" "$OUT/pad-$t.png"
  for b in $BUTTONS; do
    CW=$((28*S)); CH=$((28*S))
    G=(-size ${CW}x${CH} xc:none); glyph $t $b $((14*S)) $((14*S)) $((12*S))
    magick "${G[@]}" "$OUT/btn-$t-$b.png"
  done
done
echo "Images écrites dans $OUT"
