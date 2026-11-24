// Audit des animations d'objets : original (SWF) contre portage (anims.json).
// Usage : node items-audit.js <anims.xml> <anims.json> <fr.json> <scoreItems.json> <specialItems.json> <sortie.md>
const fs = require('fs');
const [xmlPath, libPath, langPath, scorePath, specialPath, outPath] = process.argv.slice(2);
const xml = fs.readFileSync(xmlPath, 'utf8').split('\n');
const lib = JSON.parse(fs.readFileSync(libPath, 'utf8'));
const lang = JSON.parse(fs.readFileSync(langPath, 'utf8'));

// --- lecture des sprites : images (liste d'affichage) et stop() en image 1
const sprites = {};
let cur = null, dl = null;
for (const l of xml) {
  if (/<item type="DefineSpriteTag"/.test(l)) {
    cur = { id: +/spriteId="(\d+)"/.exec(l)[1], frames: [], stop1: false };
    dl = {};
    continue;
  }
  if (!cur) continue;
  if (/^\s*<\/subTags>/.test(l)) { sprites[cur.id] = cur; cur = null; continue; }
  if (l.includes('type="PlaceObject')) {
    const d = +/depth="(\d+)"/.exec(l)[1];
    const c = /characterId="(\d+)"/.exec(l), n = /\bname="([^"]*)"/.exec(l);
    if (c) dl[d] = { id: +c[1], name: n ? n[1] : null };
    else if (dl[d] && n) dl[d].name = n[1];
  } else if (l.includes('type="RemoveObject')) {
    delete dl[+/depth="(\d+)"/.exec(l)[1]];
  } else if (l.includes('type="DoActionTag"') && cur.frames.length === 0) {
    const hex = (/actionBytes="([^"]*)"/.exec(l) || [])[1] || '';
    if (hex.startsWith('07') || hex.includes('9f010000')) cur.stop1 = true;
  } else if (l.includes('type="ShowFrameTag"')) {
    cur.frames.push(JSON.parse(JSON.stringify(dl)));
  }
}
const playsAlone = id => sprites[id] && sprites[id].frames.length > 1 && !sprites[id].stop1;

/** Clips qui jouent seuls dans une liste d'affichage (récursif dans les clips fixes, sous-clip figé à l'image subFrame). */
function animatedIn(frame, depth, subFrame) {
  const out = [];
  for (const k in frame) {
    const ch = frame[k], s = sprites[ch.id];
    if (!s) continue;
    if (ch.name === 'sub') { // figé par le script sur l'image subFrame : ses enfants jouent quand même
      const f = s.frames[Math.min(subFrame, s.frames.length - 1)] || {};
      out.push(...animatedIn(f, depth + 1, 0));
    } else if (playsAlone(ch.id)) {
      out.push({ id: ch.id, n: s.frames.length });
    } else if (depth < 5 && s.frames.length >= 1) {
      out.push(...animatedIn(s.frames[0], depth + 1, 0));
    }
  }
  return out;
}

/** Ce que le portage anime pour l'image f du clip : boucles (placement en boucle, boucle emboîtée), couches. */
function portAnimated(clip, f) {
  const out = [];
  const p = clip.subs[f];
  if (p && p.loop) {
    out.push(p.id);
    const inner = lib.clips[p.id];
    if (inner && inner.subs) for (const q of inner.subs) if (q && q.loop) out.push(q.id);
  } else if (p) {
    const img = lib.clips[p.id];
    if (img && img.subs && img.subs[0] && img.subs[0].loop) out.push(img.subs[0].id);
    if (img && img.layers && img.layers[0]) for (const q of img.layers[0]) out.push(q.id);
  }
  if (clip.layers && clip.layers[f]) for (const q of clip.layers[f]) out.push(q.id);
  return out;
}

function audit(symbol, families, idToFrame) {
  const sid = lib.symbols[symbol], clip = lib.clips[sid], sp = sprites[sid];
  const rows = [];
  for (const fam of families) {
    for (const it of fam.items) {
      const f = idToFrame(it.id);
      if (f < 0 || f >= sp.frames.length) continue;
      const orig = animatedIn(sp.frames[f], 0, 0);
      const port = portAnimated(clip, f);
      const origIds = [...new Set(orig.map(o => o.id))];
      const missing = origIds.filter(id => !port.includes(id));
      let status;
      if (origIds.length === 0) status = 'fixe';
      else if (missing.length === 0) status = 'animé (OK)';
      else if (missing.length < origIds.length) status = 'PARTIEL';
      else status = 'NON ANIMÉ';
      rows.push({ id: it.id, name: (lang['item.' + it.id] || '?').replace(/\|/g, '/'), frame: f + 1, status,
        detail: orig.map(o => 'clip ' + o.id + ' (' + o.n + ' im.)').join(', ') });
    }
  }
  return rows;
}

const score = audit('hammer_item_score', JSON.parse(fs.readFileSync(scorePath, 'utf8')), id => id - 1000);
const special = audit('hammer_item_special', JSON.parse(fs.readFileSync(specialPath, 'utf8')), id => id);

function section(title, rows) {
  const by = s => rows.filter(r => r.status === s);
  const lines = ['## ' + title, '',
    `${rows.length} objets : ${by('animé (OK)').length} animés comme l'original, ${by('NON ANIMÉ').length} non animés, ` +
    `${by('PARTIEL').length} partiellement animés, ${by('fixe').length} fixes dans l'original aussi.`, ''];
  for (const s of ['NON ANIMÉ', 'PARTIEL', 'animé (OK)', 'fixe']) {
    const r = by(s);
    if (!r.length) continue;
    lines.push('### ' + s + ' (' + r.length + ')', '', '| Id | Objet | Image | Animation d\'origine |', '|---|---|---|---|');
    for (const x of r) lines.push(`| ${x.id} | ${x.name} | ${x.frame} | ${x.detail || '—'} |`);
    lines.push('');
  }
  return lines.join('\n');
}
const md = ['# Animations des objets : jeu d\'origine contre portage', '',
  'Comparaison automatique du SWF d\'origine (`_anims.swf`) et des données du portage (`assets/sprites/anims.json`).',
  'Un objet est animé dans l\'original quand son image contient un clip qui joue tout seul (plusieurs images, sans',
  'stop()). « Animé (OK) » : le portage anime ces mêmes clips ; « Non animé » : le portage les affiche figés ;',
  '« Partiel » : une partie seulement est animée ; « Fixe » : pas d\'animation dans l\'original non plus.', '',
  section('Objets à score', score), section('Objets spéciaux', special)].join('\n');
fs.writeFileSync(outPath, md);
const count = (rows, s) => rows.filter(r => r.status === s).length;
console.log('score : OK', count(score, 'animé (OK)'), 'NON', count(score, 'NON ANIMÉ'), 'PARTIEL', count(score, 'PARTIEL'), 'fixe', count(score, 'fixe'));
console.log('spéciaux : OK', count(special, 'animé (OK)'), 'NON', count(special, 'NON ANIMÉ'), 'PARTIEL', count(special, 'PARTIEL'), 'fixe', count(special, 'fixe'));
