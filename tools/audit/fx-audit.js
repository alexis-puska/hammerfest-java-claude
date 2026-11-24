// Audit des effets visuels : chaque appel fxMan.* du code d'origine (classe, fonction) contre les effets émis par la
// méthode de même nom de la classe de même nom dans le portage.
// Usage : node tools/audit/fx-audit.js <original_sources/hammerfest-master/class/hammer> <core/src/main/java/hammerfest/sim> <sortie.md>
const fs = require('fs');
const path = require('path');
const [origDir, portDir, outPath] = process.argv.slice(2);

function walk(dir, ext, out = []) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) walk(p, ext, out);
    else if (f.endsWith(ext)) out.push(p);
  }
  return out;
}

// ---------------------------------------------------------------- original
const orig = [];
for (const file of walk(origDir, '.mt')) {
  const cls = path.basename(file, '.mt');
  if (cls === 'FxManager') continue;
  const lines = fs.readFileSync(file, 'latin1').split('\n');
  let fn = '?';
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    const m = /^\s*(?:static\s+)?function\s+(\w+)/.exec(l);
    if (m) fn = m[1];
    if (/^\s*\/\//.test(l)) continue;
    const c = /fxMan\.(attachFx|attachShine|attachExplodeZone|attachExplosion|inGameParticlesDir|inGameParticles|dust|attachBg|attachScorePop|attachAlert|attachHurryUp|attachWarning|attachExit|detachExit|attachEnter|attachLevelPop|keyRequired|keyUsed|delayFx)\s*\(/.exec(l);
    if (!c) continue;
    // appel complet (parenthèses équilibrées, éventuellement sur plusieurs lignes)
    let text = l.slice(l.indexOf('fxMan.'));
    let depth = 0, j = i, done = false, call = '';
    for (; j < lines.length && !done; j++) {
      const s = j === i ? text : lines[j];
      for (const ch of s) {
        call += ch;
        if (ch === '(') depth++;
        if (ch === ')') { depth--; if (depth === 0) { done = true; break; } }
      }
      call += ' ';
    }
    call = call.replace(/\s+/g, ' ').trim();
    // retouches de l'animation renvoyée (échelle, rotation, alpha, image)
    const v = /(?:var\s+)?(\w+)\s*(?::\s*\w+\s*)?=\s*(?:downcast\()?game\.fxMan/.exec(l);
    const tweaks = [];
    if (v) {
      for (let k = i + 1; k < Math.min(lines.length, i + 8); k++) {
        const t = lines[k];
        if (new RegExp('\\b' + v[1] + '\\.(mc\\.)?(_xscale|_yscale|_rotation|_alpha|gotoAndStop|blendMode|fl_loop|_width|_height)').test(t)) {
          tweaks.push(t.trim().replace(/\s+/g, ' '));
        }
      }
    }
    const link = /attachFx\([^"]*"([^"]+)"/.exec(call);
    orig.push({ cls, fn, kind: c[1], link: link ? link[1] : null, call, tweaks, file: path.relative(origDir, file).replace(/\\/g, '/'), line: i + 1 });
  }
}

// ---------------------------------------------------------------- portage
const port = {}; // "Classe.méthode" -> [appels]
const portFiles = {};
for (const file of walk(portDir, '.java')) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  const classStack = []; // {name, depth}
  let depth = 0, method = null, methodDepth = -1;
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    const cm = /\b(?:class|enum)\s+(\w+)/.exec(l);
    if (cm && !/^\s*(\/\/|\*)/.test(l)) classStack.push({ name: cm[1], depth });
    const mm = /^\s*(?:public|protected|private|static|final|\s)*[\w<>\[\],.]+\s+(\w+)\s*\([^;]*\)\s*(?:throws [\w, .]+)?\s*\{\s*$/.exec(l);
    if (mm && !/\b(if|for|while|switch|catch|return|new)\b/.test(l.split('(')[0])) {
      method = mm[1];
      methodDepth = depth;
    }
    const calls = l.match(/game\.(fx|particles|particlesDir|explosion|explodeZone|alert|attachBg|scorePop|dust|shine|keyMessage|attachExit|detachExit|sound)\s*\([^;]*/g) ||
      l.match(/\b(fx|particles|particlesDir|explosion|explodeZone|alert|attachBg|shine|dust|keyMessage|attachExit|detachExit)\s*\("?[^;]*/g) ||
      l.match(/\bevents\.add\(new Event\("levelpop[^;]*/g);
    if (calls && method && classStack.length) {
      const key = classStack[classStack.length - 1].name + '.' + method;
      (port[key] = port[key] || []).push(...calls.map(c => c.trim()));
      portFiles[key] = path.relative(portDir, file).replace(/\\/g, '/') + ':' + (i + 1);
    }
    for (const ch of l) {
      if (ch === '{') depth++;
      if (ch === '}') {
        depth--;
        if (method && depth === methodDepth) { method = null; methodDepth = -1; }
        while (classStack.length && depth === classStack[classStack.length - 1].depth) classStack.pop();
      }
    }
  }
}

// ---------------------------------------------------------------- comparaison
const expect = {
  attachFx: o => c => c.includes('"' + o.link + '"') || c.includes('"' + o.link.replace(/^hammer_fx_/, '') + '"'),
  attachShine: () => c => /\bshine\(/.test(c), // clip « shine » (≠ attachFx "hammer_fx_shine")
  attachExit: () => c => /attachExit/.test(c),
  detachExit: () => c => /detachExit/.test(c),
  keyRequired: () => c => /keyMessage\(false/.test(c),
  keyUsed: () => c => /keyMessage\(true/.test(c),
  attachLevelPop: () => c => /levelpop/.test(c),
  attachEnter: () => c => /hammer_fx_enter/.test(c),
  attachExplodeZone: () => c => /explodeZone\(/.test(c),
  attachExplosion: () => c => /explosion\(/.test(c),
  inGameParticles: o => c => /particles/.test(c) && particleOf(o.call) && c.includes(particleOf(o.call)),
  inGameParticlesDir: o => c => /particlesDir/.test(c) && particleOf(o.call) && c.includes(particleOf(o.call)),
  dust: () => c => /dust/.test(c),
  attachBg: () => c => /attachBg/.test(c),
  attachScorePop: () => c => /score|pop/i.test(c),
  attachAlert: () => c => /alert/.test(c),
  attachHurryUp: () => c => /hurry/i.test(c),
  attachWarning: () => c => /alert|warning/i.test(c),
};
function particleOf(call) {
  const m = /PARTICLE_(\w+)/.exec(call);
  return m ? 'PARTICLE_' + m[1] : null;
}
// classes d'origine regroupées dans le portage : les modes de jeu sont dans Game
const CLASS_MAP = { GameMode: 'Game', Adventure: 'Game', MultiCoop: 'Game', Soccer: 'Game', TimeAttack: 'Game',
  TimeAttackMulti: 'Game', BossRush: 'Game', Tutorial: 'Game', GameMechanics: 'Game', SetManager: 'World', ViewManager: 'Game', Classic: 'ClassicBomb', RepelBomb: 'Repel', PlayerArrow: 'Arrow',
  PlayerFireBall: 'FireBall', PlayerPearl: 'Pearl', BossFireBall: 'FireBall', WalkingBomb: 'Bombe', MineFrozen: 'Mine',
  PoireBombFrozen: 'PoireBomb' };
// même nom de classe dans plusieurs paquets de l'original
const PATH_MAP = { 'entity/shoot/FireBall.mt': 'EnemyFireBall' };
// effets dessinés par l'écran (GameView, ParticleSystem) ou fonctions non portées : écart justifié
const NOTES = {
  'Physics.endUpdate': 'rendu : GameView.aquaBubbles (bulles des niveaux aquatiques)',
  'Player.shield': 'rendu : GameView.drawShields (bouclier en boucle, transmis dans RenderFrame.shields)',
  'GameMechanics.update': 'rendu : GameView.portalStars (étoiles des champs portail)',
  'GameMode.main': 'rendu : GameView.portalStars (étoiles des vortex flottants)',
  'BossRush.displayTime': 'rendu : GameView.drawTimeAttack (hammer_interf_time)',
  'Particle.onHitGround': 'rendu : ParticleSystem.onRainDrop → GameView (goutte hammer_fx_water_drop)',
  'WalkingBomb.onPortal': 'code mort : option « contrôle des bombes » (fl_bombControl), débloquée par aucune quête',
  'WalkingBomb.onPortalRefusal': 'code mort : option « contrôle des bombes » (fl_bombControl), débloquée par aucune quête',
  'MineFrozen.onExplode': 'code mort : getFrozen commenté dans Mine.mt et PoireBomb.mt',
  'PoireBombFrozen.onExplode': 'code mort : getFrozen commenté dans Mine.mt et PoireBomb.mt',
  'Fjv.onLevelReady': 'non porté : mode Fjv (événement promotionnel)',
  'Shareware.onLevelReady': 'non porté : mode Shareware (version de démonstration)',
};
const byClass = {};
for (const k in port) {
  const c = k.split('.')[0];
  (byClass[c] = byClass[c] || []).push(...port[k].map(x => ({ call: x, at: portFiles[k], method: k })));
}
const rows = orig.map(o => {
  const cls = PATH_MAP[o.file] || CLASS_MAP[o.cls] || o.cls;
  let key = cls + '.' + o.fn;
  if (NOTES[o.cls + '.' + o.fn]) return { ...o, status: 'justifié', portCalls: NOTES[o.cls + '.' + o.fn], portAt: '' };
  let calls = port[key] || [];
  let renamed = false;
  if (!port[key] && byClass[cls]) { // méthode renommée ou découpée : toute la classe
    calls = byClass[cls].map(x => x.call);
    renamed = true;
  }
  let status;
  if (!port[key] && !renamed) status = 'méthode non trouvée';
  else if (expect[o.kind] && calls.some(expect[o.kind](o))) status = renamed ? 'présent ailleurs dans la classe' : o.tweaks.length ? 'présent (retouches à vérifier)' : 'présent';
  else if (expect[o.kind]) status = renamed ? 'ABSENT de la classe' : 'ABSENT';
  else status = 'à vérifier';
  return { ...o, status, portCalls: calls.filter(c => !/sound\(/.test(c)).join(' ; '), portAt: portFiles[key] || '' };
});

const order = ['ABSENT', 'ABSENT de la classe', 'méthode non trouvée', 'à vérifier', 'présent (retouches à vérifier)', 'présent ailleurs dans la classe', 'justifié', 'présent'];
const md = ['# Effets visuels : jeu d\'origine contre portage', '',
  'Chaque appel `fxMan.*` du code d\'origine, comparé aux effets émis par la méthode de même nom de la même classe dans',
  'le portage (`game.fx`, `particles`, `explosion`, `explodeZone`, `alert`…). « Retouches » : l\'original modifie',
  'l\'animation après coup (échelle, rotation, alpha, image).', ''];
for (const s of order) {
  const r = rows.filter(x => x.status === s);
  if (!r.length) continue;
  md.push('## ' + s + ' (' + r.length + ')', '', '| Original | Appel | Retouches | Portage |', '|---|---|---|---|');
  for (const x of r) {
    md.push(`| ${x.file}:${x.line} ${x.cls}.${x.fn} | \`${x.call.replace(/\|/g, '/')}\` | ${x.tweaks.join('<br>').replace(/\|/g, '/') || '—'} | ${x.portAt ? x.portAt + ' : ' : ''}${x.portCalls.replace(/\|/g, '/') || '—'} |`);
  }
  md.push('');
}
fs.writeFileSync(outPath, md.join('\n'));
for (const s of order) console.log(s, rows.filter(x => x.status === s).length);
