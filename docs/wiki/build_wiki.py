"""Usage : python docs/wiki/build_wiki.py depuis la racine du dépôt.
Génère le wiki moddedmc.wiki de Wave Survivor à partir du Codex en jeu (wslang en/fr) + pages écrites à la main."""
import json, re, os, shutil
IN_REPO = os.path.isdir('src/main/resources/assets/wavesurvivor/wslang')        # lancé depuis la racine du dépôt
U = 'src/main/resources/assets/wavesurvivor/wslang/' if IN_REPO else '/mnt/user-data/uploads/wave-survivor-mod-1.7/src/main/resources/assets/wavesurvivor/wslang/'
HAND = 'docs/wiki/hand/' if IN_REPO else '/home/claude/wiki/hand/'
EN, FR = json.load(open(U + 'en.json')), json.load(open(U + 'fr.json'))
R = 'wiki/' if IN_REPO else '/home/claude/wiki/wiki/'

# chemin de page -> clé du Codex ; dossiers -> (nom EN, nom FR, icône)
PAGES = {
    'getting_started.mdx': 'start', 'interface.mdx': 'interface',
    'hordes/classic.mdx': 'classic', 'hordes/difficulty.mdx': 'difficulty', 'hordes/bosses.mdx': 'boss', 'hordes/chaos.mdx': 'breaches',
    'hordes/arenas.mdx': 'arenas', 'hordes/frozen_peaks.mdx': 'frost', 'hordes/burning_sands.mdx': 'desert',
    'modes/kingdom/overview.mdx': 'kingdom', 'modes/kingdom/treasury.mdx': 'treasury', 'modes/kingdom/monolith.mdx': 'monolith',
    'modes/kingdom/defenses.mdx': 'defenses', 'modes/kingdom/workshop.mdx': 'workshop', 'modes/kingdom/roles.mdx': 'roles',
    'modes/kingdom/vocations.mdx': 'vocations', 'modes/kingdom/alchemy.mdx': 'alchemy', 'modes/kingdom/omens.mdx': 'omens',
    'modes/kingdom/calm_objectives.mdx': 'objectives', 'modes/kingdom/map_and_tools.mdx': 'tools',
    'modes/invasions.mdx': 'invasion', 'modes/boss_rush.mdx': 'bossrush', 'modes/endless.mdx': 'endless', 'modes/daily_challenge.mdx': 'daily',
    'modes/rift/overview.mdx': 'rift', 'modes/rift/creatures.mdx': 'rift_creatures', 'modes/rift/islets.mdx': 'rift_islets',
    'progression/blessings.mdx': 'blessings', 'progression/renaissance.mdx': 'renaissance', 'progression/relics.mdx': 'relics',
    'progression/statistics.mdx': 'stats',
    'world/seraph_shrine.mdx': 'shrine', 'world/crafting.mdx': 'crafting', 'world/ruins.mdx': 'ruins',
    'world/vestige_compass.mdx': 'vestige', 'world/key_pouch.mdx': 'pouch',
    'customization/editor.mdx': 'editor',
}
FOLDERS = {
    '': [('getting_started.mdx', 'Rocket'), ('hordes', 'Skull'), ('modes', 'Swords'), ('progression', 'TrendingUp'), ('world', 'Map'),
         ('customization', 'Wrench'), ('interface.mdx', 'Terminal'), ('compatibility.mdx', 'Puzzle')],
    'hordes': None, 'modes': None, 'modes/kingdom': None, 'modes/rift': None, 'progression': None, 'world': None, 'customization': None,
}
NAMES = {'hordes': ('Hordes', 'Hordes'), 'modes': ('Game modes', 'Modes de jeu'), 'modes/kingdom': ('Kingdom', 'Kingdom'),
         'modes/rift': ('The Rift', 'La Faille'), 'progression': ('Progression', 'Progression'), 'world': ('World & items', 'Monde et objets'),
         'customization': ('Customization', 'Personnalisation'), 'compatibility.mdx': ('Compatibility', 'Compatibilité')}
EMPH = set('fec6adb354')

def esc(t):
    return t.replace('\\', '\\\\').replace('{', '\\{').replace('}', '\\}').replace('<', '&lt;').replace('>', '&gt;').replace('$', '\\$').replace('*', '\\*').replace('_', '\\_')

def inline(line):
    segs, style = [], '7'
    for part in re.split(r'(§.)', line):
        if part.startswith('§') and len(part) == 2:
            c = part[1]
            if c != 'l': style = c
            continue
        if part: segs.append((style, part))
    out = []
    for st, tx in segs:
        if st not in EMPH or not tx.strip(): out.append(esc(tx)); continue
        lead, core, trail = re.match(r'^(\s*)(.*?)(\s*)$', tx, re.S).groups()
        if st == 'e' and core.startswith('/'): out.append(lead + '`' + core + '`' + trail)
        else: out.append(lead + '**' + esc(core) + '**' + trail)
    s = ''.join(out)
    return re.sub(r'\*\*\s*\*\*', '', s)

def to_md(body):
    lines, out = body.split('\n'), []
    for ln in lines:
        raw = re.sub(r'§.', '', ln).strip()
        if not raw: out.append(''); continue
        if ln.startswith('§6§l') and len(raw) < 60:
            # titre éventuellement suivi d'un commentaire gris (« Forge (Renaissance Altar) »)
            out.append('## ' + esc(raw)); continue
        t = inline(ln).strip()
        t = re.sub(r'^•\s*', '- ', t)
        out.append(t)
    md = '\n'.join(out)
    md = re.sub(r'\n(## )', r'\n\n\1', md); md = re.sub(r'(## [^\n]+)\n(?!\n)', r'\1\n\n', md)
    return re.sub(r'\n{3,}', '\n\n', md).strip() + '\n'

FOOT = {'en': '\n<Callout type="info">This page mirrors the in-game **Codex** (`/ws codex`). Values are the defaults: most of them can be changed in the config or the in-game editor.</Callout>\n',
        'fr': '\n<Callout type="info">Cette page reprend le **Codex** du jeu (`/ws codex`). Les valeurs indiquées sont celles par défaut : la plupart se règlent dans la config ou l\'éditeur en jeu.</Callout>\n'}

def write(path, txt):
    os.makedirs(os.path.dirname(path), exist_ok=True); open(path, 'w').write(txt)

for lang, D, base in (('en', EN, R + 'docs/'), ('fr', FR, R + 'translated/fr_fr/docs/')):
    for p, k in PAGES.items():
        title = re.sub(r'§.', '', D['codex.' + k]); body = D.get('codex.%s.body' % k, '')
        write(base + p, '# ' + title + '\n\n' + to_md(body) + FOOT[lang])
    # sidebars
    for folder in FOLDERS:
        files = sorted({p[len(folder) + 1 if folder else 0:].split('/')[0] for p in PAGES if (p.startswith(folder + '/') if folder else True)})
        meta = {}
        if folder == '':
            for name, icon in FOLDERS['']:
                label = NAMES.get(name, (None, None))[0 if lang == 'en' else 1]
                if name.endswith('.mdx') and name in PAGES: label = re.sub(r'§.', '', D['codex.' + PAGES[name]])
                meta[name] = {'name': label, 'icon': icon}
        else:
            for fn in files:
                key = (folder + '/' + fn)
                meta[fn] = NAMES[key][0 if lang == 'en' else 1] if key in NAMES else re.sub(r'§.', '', D['codex.' + PAGES[key]])
        write(base + (folder + '/' if folder else '') + '_meta.json', json.dumps(meta, indent=4, ensure_ascii=False) + '\n')
    shutil.copy(HAND + '%s/_index.mdx' % lang, base + '_index.mdx')
    shutil.copy(HAND + '%s/compatibility.mdx' % lang, base + 'compatibility.mdx')
print('pages:', len(PAGES) + 2, 'x2 langues')

# ---------------------------------------------------------------- pages complétées depuis les données du jeu
def block(D, key):
    """Bloc multi-lignes « §dTitre\n§7ligne… » → ### Titre + puces."""
    v = D[key].split('\n'); title = re.sub(r'§.', '', v[0]).strip(); rest = [l for l in v[1:] if re.sub(r'§.', '', l).strip()]
    return '### ' + esc(title) + '\n\n' + '\n'.join('- ' + re.sub(r'^•\s*', '', inline(l).strip()) for l in rest) + '\n'
def name_desc(D, key):
    return '- **' + esc(re.sub(r'§.', '', D[key]).strip()) + '**: ' + inline(D[key + '.desc']).strip()
LBL = {'en': {'diffs': 'Difficulties', 'muts': 'Mutators', 'roles': 'The 9 roles', 'flasks': 'Flasks', 'omens': 'The omens'},
       'fr': {'diffs': 'Difficultés', 'muts': 'Mutateurs', 'roles': 'Les 9 rôles', 'flasks': 'Fioles', 'omens': 'Les présages'}}
EXTRA = {
  'hordes/difficulty.mdx': lambda D, L: '## ' + L['diffs'] + '\n\n' + '\n'.join(name_desc(D, 'difficulty.' + x) for x in ('easy', 'normal', 'hard', 'nightmare'))
      + '\n\n## ' + L['muts'] + '\n\n' + '\n'.join(name_desc(D, k) for k in D if re.fullmatch(r'mutator\.[a-z_]+', k) and k + '.desc' in D and not k.startswith('mutator.cat')),
  'modes/kingdom/roles.mdx': lambda D, L: '## ' + L['roles'] + '\n\n' + '\n'.join(block(D, 'kingdom.role.%s.desc' % r) for r in
      ('arcanist', 'miner', 'builder', 'commander', 'quartermaster', 'alchemist', 'bounty_hunter', 'augur', 'scavenger')),
  'modes/kingdom/alchemy.mdx': lambda D, L: '## ' + L['flasks'] + '\n\n' + '\n'.join(block(D, 'kingdom.alchemy.desc.' + f) for f in ('frost', 'fire', 'heal', 'storm', 'bastion')),
  'modes/kingdom/omens.mdx': lambda D, L: '## ' + L['omens'] + '\n\n' + '\n'.join(block(D, 'kingdom.omen.%s.desc' % o) for o in
      ('tide', 'bounty', 'slumber', 'frenzy', 'iron_rain', 'drowsy', 'royal_hunt', 'fair_winds', 'blood_moon', 'offering')),
}
for lang, D, base in (('en', EN, R + 'docs/'), ('fr', FR, R + 'translated/fr_fr/docs/')):
    for p, fn in EXTRA.items():
        txt = open(base + p).read(); cut = txt.index('\n<Callout')
        open(base + p, 'w').write(txt[:cut].rstrip() + '\n\n' + fn(D, LBL[lang]).rstrip() + '\n' + txt[cut:])
print('pages complétées :', len(EXTRA))
