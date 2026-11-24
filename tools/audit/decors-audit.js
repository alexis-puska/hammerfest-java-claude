// Audit des animations des décors de script (e_mc / e_pmc) : jeu d'origine (SWF) contre portage (sprites JSON).
// Usage : node tools/audit/decors-audit.js <dossier swf-export> <assets/sprites> <assets/data/levels> <sortie.md>
// Dans le jeu, un décor joue sa propre timeline si p > 0, sinon il est arrêté sur sa 1re image ; son « sub » est arrêté.
// Ses enfants qui jouent tout seuls (plusieurs images, sans stop() d'entrée) s'animent dans tous les cas.
const fs = require('fs');
const path = require('path');
const [exportDir, spritesDir, levelsDir, outPath] = process.argv.slice(2);

function parse(xmlPath) {
  const sprites = {}, exportsMap = {};
  let cur = null, dl = null, names = [], ids = [];
  for (const l of fs.readFileSync(xmlPath, 'utf8').split('\n')) {
    if (/<item type="DefineSpriteTag"/.test(l)) {
      cur = { id: +/spriteId="(\d+)"/.exec(l)[1], frames: [], stop1: false };
      dl = {};
      continue;
    }
    if (/type="ExportAssetsTag"/.test(l)) { names = []; ids = []; }
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
  return sprites;
}

const libs = {};
for (const lib of ['gfx', 'anims', 'ending']) {
  const xml = path.join(exportDir, lib + '.xml');
  const json = path.join(spritesDir, lib + '.json');
  if (fs.existsSync(xml) && fs.existsSync(json)) libs[lib] = { sprites: parse(xml), data: JSON.parse(fs.readFileSync(json, 'utf8')) };
}

// décors utilisés par les scripts, avec p (joue sa timeline ?)
const decors = {};
for (const f of fs.readdirSync(levelsDir)) {
  const set = JSON.parse(fs.readFileSync(path.join(levelsDir, f), 'utf8'));
  (set.levels || []).forEach((lv, i) => {
    const re = /<e_p?mc\b([^>]*)\/?>/g;
    let m;
    while ((m = re.exec(lv.script || ''))) {
      const n = /\bn="([^"]*)"/.exec(m[1]);
      if (!n) continue;
      const p = /\bp="(\d+)"/.exec(m[1]);
      const d = decors[n[1]] || (decors[n[1]] = { plays: false, where: new Set() });
      if (p && +p[1] > 0) d.plays = true;
      if (d.where.size < 4) d.where.add(f.replace('.json', '') + ' ' + i);
    }
  });
}

function animatedIn(sprites, frame, depth) {
  const out = [];
  for (const k in frame) {
    const ch = frame[k], s = sprites[ch.id];
    if (!s || ch.name === 'sub') continue;
    if (s.frames.length > 1 && !s.stop1) out.push(ch.id);
    else if (depth < 5 && s.frames[0]) out.push(...animatedIn(sprites, s.frames[0], depth + 1));
  }
  return out;
}

const rows = [];
for (const name of Object.keys(decors).sort()) {
  let lib = null;
  for (const l in libs) if (libs[l].data.symbols[name] !== undefined) lib = l;
  const d = decors[name];
  if (!lib) { rows.push({ name, status: 'ABSENT', detail: 'symbole introuvable dans les sprites', d }); continue; }
  const { sprites, data } = libs[lib];
  const sid = data.symbols[name], clip = data.clips[sid], sp = sprites[sid];
  const shown = d.plays ? sp.frames.map((_, i) => i) : [0];
  const orig = new Set(), missing = new Set();
  for (const f of shown) {
    const anim = animatedIn(sprites, sp.frames[f] || {}, 0);
    const port = new Set();
    const p = clip.subs && clip.subs[f];
    if (p && p.loop) {
      port.add(p.id);
      const inner = data.clips[p.id];
      if (inner && inner.subs) for (const q of inner.subs) if (q && q.loop) port.add(q.id);
    }
    if (clip.layers && clip.layers[f]) for (const q of clip.layers[f]) port.add(q.id);
    for (const id of anim) {
      orig.add(id);
      if (!port.has(id)) missing.add(id);
    }
  }
  let status;
  if (orig.size === 0) status = d.plays && sp.frames.length > 1 ? 'timeline (OK)' : 'fixe';
  else if (missing.size === 0) status = 'animé (OK)';
  else if (missing.size < orig.size) status = 'PARTIEL';
  else status = 'NON ANIMÉ';
  const detail = [...orig].map(id => 'clip ' + id + ' (' + sprites[id].frames.length + ' im.)' + (missing.has(id) ? ' figé' : '')).join(', ');
  rows.push({ name, status, detail, d, frames: sp.frames.length });
}

const order = ['NON ANIMÉ', 'PARTIEL', 'ABSENT', 'animé (OK)', 'timeline (OK)', 'fixe'];
const lines = ['# Animations des décors : jeu d\'origine contre portage', '',
  'Décors posés par les scripts de niveau (`e_mc`, `e_pmc`). « Timeline » : le décor joue ses propres images (p > 0),',
  'gérées par le moteur de script ; « Animé » : il contient des clips qui jouent tout seuls, animés aussi par le portage ;',
  '« Non animé » : ces clips sont figés dans le portage ; « Fixe » : aucune animation dans l\'original.', ''];
for (const s of order) {
  const r = rows.filter(x => x.status === s);
  if (!r.length) continue;
  lines.push('## ' + s + ' (' + r.length + ')', '', '| Décor | Images | Joue sa timeline | Exemples (set niveau) | Détail |', '|---|---|---|---|---|');
  for (const x of r) lines.push(`| ${x.name} | ${x.frames || '—'} | ${x.d.plays ? 'oui' : 'non'} | ${[...x.d.where].join(', ')} | ${x.detail || '—'} |`);
  lines.push('');
}
fs.writeFileSync(outPath, lines.join('\n'));
for (const s of order) console.log(s, rows.filter(x => x.status === s).length);
