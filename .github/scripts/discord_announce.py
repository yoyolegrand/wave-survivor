#!/usr/bin/env python3
"""Annonce une version sur Discord (salon #changelogs) via un webhook.

Lancé par le workflow de publication (dernière étape) et par le workflow manuel « Discord test ».
Bibliothèque standard uniquement (urllib).

Entrées (variables d'environnement) :
  DISCORD_WEBHOOK_URL    URL du webhook (secret DISCORD_WEBHOOK_CHANGELOGS) ; jamais affichée
  RELEASE_VERSION        version, ex. 2.1.0
  RELEASE_SUBTITLE       sous-titre (texte après « ## 2.1.0 — » dans CHANGELOG.md), optionnel
  RELEASE_URL            page de la release GitHub
  CHANGELOG_FILE         section du changelog (défaut : release_changelog.md)
  DISCORD_PING_ROLE_ID   rôle à mentionner dans le premier message, optionnel
  GITHUB_REPOSITORY      propriétaire/dépôt (fourni par GitHub Actions)

Options :
  --version X.Y.Z   lit directement la section et le sous-titre dans CHANGELOG.md (workflow de test, essai local)
  --dry-run         affiche les payloads JSON sans rien envoyer

Limites Discord respectées : 4096 caractères par description d'embed, 256 par titre, 6000 au total
et 10 embeds par message. Un embed par sous-section « ### » ; une sous-section trop longue est coupée
aux lignes de liste ; plusieurs messages envoyés dans l'ordre si besoin.
"""
import argparse, json, os, re, sys, time, urllib.error, urllib.request

COLOR = 0x8B5CF6
DESC_MAX = 4000        # limite Discord 4096 : marge pour les emojis comptés double
TITLE_MAX = 256
MSG_TOTAL_MAX = 5900   # limite Discord 6000
EMBEDS_MAX = 10
CURSEFORGE_URL = 'https://www.curseforge.com/minecraft/mc-mods/wave-survivor'
USER_AGENT = 'DiscordBot (https://github.com/yoyolegrand/wave-survivor, 1.0) wave-survivor-release'


def extract_section(changelog, version):
    """Section « ## version » de CHANGELOG.md -> (sous-titre, corps)."""
    sub, body, found = '', [], False
    with open(changelog, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\r\n')
            if not found:
                m = re.match(r'^## ' + re.escape(version) + r'(?:\s+(?:—|-)\s*(.*))?\s*$', line)
                if m:
                    found, sub = True, (m.group(1) or '').strip()
                continue
            if line.startswith('## '):
                break
            body.append(line)
    if not found:
        sys.exit('Aucune section « ## %s » dans %s' % (version, changelog))
    return sub, '\n'.join(body)


def parse(text):
    """Corps du changelog -> (intro, [(titre ###, [lignes])])."""
    intro, sections, cur = [], [], None
    for line in text.splitlines():
        s = line.rstrip()
        if s.startswith('### '):
            cur = (s[4:].strip(), [])
            sections.append(cur)
            continue
        if re.fullmatch(r'\s*(-{3,}|\*{3,}|_{3,})\s*', s):   # séparateurs « --- » : inutiles dans Discord
            continue
        (cur[1] if cur else intro).append(s)
    strip = lambda ls: '\n'.join(ls).strip('\n')
    return strip(intro), [(t, strip(ls)) for t, ls in sections if strip(ls) or t]


def chunks(text, limit=DESC_MAX):
    """Coupe un texte en morceaux <= limit, de préférence entre deux lignes de liste."""
    if len(text) <= limit:
        return [text]
    # blocs = une ligne de liste + ses lignes de continuation
    blocks, cur = [], []
    for line in text.split('\n'):
        if re.match(r'^\s*([-*+]|\d+\.)\s', line) and cur:
            blocks.append('\n'.join(cur))
            cur = []
        cur.append(line)
    if cur:
        blocks.append('\n'.join(cur))
    out, buf = [], ''
    for b in blocks:
        while len(b) > limit:   # bloc seul trop long : coupe au dernier espace possible
            cut = b.rfind(' ', 0, limit)
            cut = cut if cut > limit // 2 else limit
            if buf:
                out.append(buf)
                buf = ''
            out.append(b[:cut].rstrip())
            b = b[cut:].lstrip()
        cand = b if not buf else buf + '\n' + b
        if len(cand) <= limit:
            buf = cand
        else:
            out.append(buf)
            buf = b
    if buf:
        out.append(buf)
    return out


def clip(s, n):
    return s if len(s) <= n else s[:n - 1] + '…'


def embed_size(e):
    return len(e.get('title', '')) + len(e.get('description', '')) + len(e.get('footer', {}).get('text', ''))


def build_embeds(version, subtitle, url, body, repo):
    intro, sections = parse(body)
    title = 'Wave Survivor %s — %s' % (version, subtitle) if subtitle else 'Wave Survivor %s' % version
    embeds = []
    head = {'title': clip(title, TITLE_MAX), 'url': url, 'color': COLOR}
    parts = chunks(intro) if intro else []
    if parts:
        head['description'] = parts[0]
    embeds.append(head)
    for p in parts[1:]:
        embeds.append({'description': p, 'color': COLOR})
    for sec_title, text in sections:
        for k, p in enumerate(chunks(text) if text else ['​']):
            e = {'title': clip(sec_title if k == 0 else sec_title + ' (cont.)', TITLE_MAX), 'color': COLOR}
            e['description'] = p
            embeds.append(e)
    # ligne finale : liens CurseForge et GitHub
    links = '📥 [CurseForge](%s) • [GitHub](%s)' % (CURSEFORGE_URL, url or 'https://github.com/%s' % repo)
    last = embeds[-1]
    joined = (last.get('description', '') + '\n\n' + links).lstrip('\n')
    if len(joined) <= DESC_MAX:
        last['description'] = joined
    else:
        embeds.append({'description': links, 'color': COLOR})
    return embeds


def pack(embeds):
    """Regroupe les embeds en messages (<= 10 embeds et <= 6000 caractères chacun), dans l'ordre."""
    msgs, cur, size = [], [], 0
    for e in embeds:
        n = embed_size(e)
        if cur and (len(cur) >= EMBEDS_MAX or size + n > MSG_TOTAL_MAX):
            msgs.append(cur)
            cur, size = [], 0
        cur.append(e)
        size += n
    if cur:
        msgs.append(cur)
    return msgs


def payloads(msgs, role):
    out = []
    for k, m in enumerate(msgs):
        p = {'embeds': m, 'allowed_mentions': {'parse': []}}   # jamais @everyone / @here
        if k == 0 and role:
            p['content'] = '<@&%s>' % role
            p['allowed_mentions'] = {'parse': [], 'roles': [role]}
        out.append(p)
    return out


def post(webhook, payload):
    data = json.dumps(payload).encode('utf-8')
    sep = '&' if '?' in webhook else '?'
    for attempt in range(5):
        req = urllib.request.Request(webhook + sep + 'wait=true', data=data, method='POST',
                                     headers={'Content-Type': 'application/json', 'User-Agent': USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status
        except urllib.error.HTTPError as e:
            detail = e.read().decode('utf-8', 'replace')[:500]
            if e.code == 429:   # limite de débit : on attend le délai indiqué
                try:
                    wait = float(json.loads(detail).get('retry_after', 2))
                except ValueError:
                    wait = 2
                time.sleep(min(wait, 30) + 0.5)
                continue
            raise RuntimeError('Discord a répondu HTTP %d : %s' % (e.code, detail)) from None
        except urllib.error.URLError as e:
            raise RuntimeError('Webhook injoignable (%s)' % type(e.reason).__name__) from None
    raise RuntimeError('Trop de limites de débit (429), abandon')


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--dry-run', action='store_true', help='affiche les payloads sans rien envoyer')
    ap.add_argument('--version', help='lit la section et le sous-titre dans CHANGELOG.md')
    ap.add_argument('--changelog', default='CHANGELOG.md', help='CHANGELOG.md utilisé avec --version')
    a = ap.parse_args()

    repo = os.environ.get('GITHUB_REPOSITORY') or 'yoyolegrand/wave-survivor'
    if a.version:
        version = a.version.lstrip('v')
        subtitle, body = extract_section(a.changelog, version.split('-')[0])
    else:
        version = os.environ.get('RELEASE_VERSION', '').strip().lstrip('v')
        if not version:
            sys.exit('RELEASE_VERSION manquant (ou utiliser --version)')
        subtitle = os.environ.get('RELEASE_SUBTITLE', '').strip()
        with open(os.environ.get('CHANGELOG_FILE') or 'release_changelog.md', encoding='utf-8') as f:
            body = f.read()
    url = os.environ.get('RELEASE_URL', '').strip() or 'https://github.com/%s/releases/tag/v%s' % (repo, version)
    role = os.environ.get('DISCORD_PING_ROLE_ID', '').strip()
    if role and not role.isdigit():
        print('::warning::DISCORD_PING_ROLE_ID invalide (chiffres attendus) : pas de mention')
        role = ''

    msgs = pack(build_embeds(version, subtitle, url, body, repo))
    pls = payloads(msgs, role)

    print('Annonce Discord %s : %d message(s), %d embed(s)' % (version, len(pls), sum(len(m) for m in msgs)))
    for k, m in enumerate(msgs, 1):
        print('  message %d : %d embed(s), %d caractères' % (k, len(m), sum(embed_size(e) for e in m)))
        for e in m:
            print('    - %-48s %5d car.' % (clip(e.get('title', '(suite)'), 48), embed_size(e)))

    if a.dry_run:
        for k, p in enumerate(pls, 1):
            print('\n----- payload %d -----' % k)
            print(json.dumps(p, ensure_ascii=False, indent=2))
        return

    webhook = os.environ.get('DISCORD_WEBHOOK_URL', '').strip()
    if not webhook:
        sys.exit('DISCORD_WEBHOOK_URL manquant')
    for k, p in enumerate(pls, 1):
        post(webhook, p)
        print('Message %d/%d envoyé' % (k, len(pls)))
        time.sleep(1)


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as e:
        print('::error::%s' % e)
        sys.exit(1)
