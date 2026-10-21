#!/usr/bin/env python3
"""Vérifie le titre de version que le workflow de publication enverra à Modrinth / CurseForge.

Titre = « Wave Survivor <mod_version> — <sous-titre du CHANGELOG> ». Modrinth le limite à 64 caractères : un titre trop long fait échouer
la publication (arrivé à la 1.7.0). Lancé à chaque push (avertissement) et juste avant la publication (STRICT=1 : échec).
"""
import os, re, sys

LIMIT = 64
ver = None
for line in open('gradle.properties', encoding='utf-8'):
    if line.startswith('mod_version='):
        ver = line.split('=', 1)[1].strip()
if not ver:
    print('::error::mod_version introuvable dans gradle.properties'); sys.exit(1)

sub = ''
for line in open('CHANGELOG.md', encoding='utf-8'):
    m = re.match(r'^## ' + re.escape(ver) + r'(?:\s+[—-]\s*(.*))?\s*$', line.rstrip('\r\n'))
    if m:
        sub = (m.group(1) or '').strip()
        break
else:
    print('::warning::Aucune section « ## %s » dans CHANGELOG.md (la publication la réclamera).' % ver)

title = 'Wave Survivor %s — %s' % (ver, sub) if sub else 'Wave Survivor %s' % ver
print('Titre de version : « %s » (%d caractères, limite %d)' % (title, len(title), LIMIT))
problems = []
if len(title) > LIMIT:
    problems.append('titre trop long (%d > %d) : raccourcis le sous-titre de « ## %s » dans CHANGELOG.md' % (len(title), LIMIT, ver))
if re.search(r'[*_`]|\(in progress\)|en cours', sub, re.I):
    problems.append('le sous-titre contient du Markdown ou « in progress » : à nettoyer avant de publier')
strict = os.environ.get('STRICT') == '1'
for p in problems:
    print(('::error::' if strict else '::warning::') + p)
sys.exit(1 if strict and problems else 0)
