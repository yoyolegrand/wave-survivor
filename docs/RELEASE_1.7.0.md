# Wave Survivor 1.7.0 — what changed since 1.6.0

Complete list of the differences between **1.6.0** (published 2026-10-15) and **1.7.0**, written for the CurseForge / Modrinth
description, the changelog visuals and the gallery. Source of truth for the player-facing wording: `CHANGELOG.md` (section 1.7.0).
Nothing here has been published yet.

**Subtitle:** *Endless Mode, Burning Sands, Village Invasions, Achievements & Statistics*
**Pitch:** The horde never stops. Play any horde without limit, defend real villages from raids, cross a new desert campaign with
three bosses and seven custom-modelled creatures, unlock 24 achievements — and the mod now updates its own content without ever
touching your config.

## At a glance

| | |
|---|---|
| New horde | **Burning Sands** (desert, 3 bosses) |
| New game modes | **Endless mode**, **Village invasions** |
| New screens | **Statistics**, achievements (24, real advancements) |
| New chaos events | **Sandstorm look + shelter rule** (extends the Storm), **Earthquake** |
| New creatures (custom 3D models) | Djinn, Djinn Spirit, Djinn Fangs, Scarab, Scorpion, Cursed Jackal, Sandstone Colossus, Royal Guard (+ 8 spawn eggs) |
| New relic | **Desert Veil** (Elements family, now 4 pieces) |
| New boss skill types | Sandstorm, Quicksand, Dust Devil, Tremor (*Star Fracture*), Earthquake |
| New commands | `/ws endless`, `/ws stats`, `/ws invasion start / stop / skip`, `/ws skill <name> [me] / list` |
| Config system | **Layers**: the mod's content updates itself, your changes are kept field by field |

## ♾️ Endless mode
- New button at the altar: after the horde's **last wave** it keeps going — more monsters, more HP, more damage every wave — until the team falls.
- A **boss from the horde returns every few waves**, stronger each time.
- **Shared lives** for the team; at zero lives (or when the Monolith falls) the run ends.
- Score = **waves cleared**; top scores per horde (`/ws endless`).
- **Renaissance Points**: normal waves pay as usual, plus the victory bonus once the last normal wave is cleared, then **+1 PR every 2 waves**.
- Unlocked by completing the horde once. Classic hordes only; no save & resume.
- Fully configurable per horde in the editor (Boss tab › Endless): lives, growth per wave, monster cap, boss return, unlock, leaderboard size.

## 🏜️ Burning Sands — new horde
- Mummies, scarabs, scorpions, jackals, desert riders, royal guards, sandstone colossi and djinns, with special waves
  (Scarab Rush, Legion of Mummies, March of the Pharaohs, Ruins Raiders) and **three bosses**: **Anubis** (wave 4), **Sekhmet** (wave 7,
  twice the size of a normal scorpion) and **Ra-Atum** (wave 10) — 7 to 9 skills each.
- Unlocks after the **Necropolis**; bound at the altar with **chiseled sandstone and red sand**. 4-star difficulty.
- **Sand Desecrators** ignore players and rush the **Monolith**.
- **Desert Veil** relic: immune to sandstorms and quicksand, faster (more on sand); boss loot.
- Every creature is a **custom 3D model** with animations, glowing eyes and spawn eggs (not recolors):
  - 🧞 **Djinn** — turban, belted robe, smoke tail instead of legs, casts sand orbs and vanishes; summons **Djinn Spirits** and **Djinn Fangs**
  - 🪲 **Scarab** — sacred scarab in lapis and gold, never burrows
  - 🦂 **Scorpion** — articulated tail and stinger, claws, climbs, poisons (also Sekhmet)
  - 🐺 **Cursed Jackal** — red eyes, hunts in packs (also the Alpha)
  - 🗿 **Sandstone Colossus** — hunched guardian, sun heart, three orbiting rocks, hostile (also Ra-Atum)
  - 💀 **Royal Guard** — mummified guard with a Wither strike (also Anubis and the pharaoh guards)
- Skills throw **sand** (no stray purple or white effects); Ra-Atum's Mirage was removed from his kit.

## 🌪️ Sandstorms & 🌋 Earthquakes (chaos events, reusable in any horde)
- **Storm** event: new sand look, new **shelter rule** (get *under cover* — a roof or water — instead of near a heat source), per-storm **immunity relic**.
- **Earthquake** event: the ground rumbles then shakes — **jagged, branching cracks** run across the floor, rubble falls, the camera trembles. No shelter helps: dodge.
  Duration, damage, jolts, cracks, rubble and shake are configurable.

## 🏘️ Village invasions
- Walk into a village with **Bad Omen**: a roulette picks one of the mod's invasions (Pillagers, Sand Raid, Snow Pack…). The classic vanilla raid is off by default (weight in `invasions.json`).
- The bell rings, villagers hide, short preparation, then **waves** with pauses (count follows world difficulty and Bad Omen level, like the vanilla raid).
- Top bar: **villagers left** and **wave**. Lose too many villagers (half by default) and the village falls.
- Invaders **open and break doors** and hunt the villagers; nothing else is destroyed.
- Victory: **Hero of the Village**, raiders' loot, Renaissance points, statistics, 3 achievements.
- Default invasion reuses the **vanilla wave composition**, customizable in the editor (HP / damage multipliers, per-type stats and skills, extra monsters, special waves, a chief).
- Third mode in the editor (Horde / Kingdom / **Invasion**). Commands: `/ws invasion start [name]`, `stop`, **`skip`** (skips the current wave; the last one wins).

## 🏅 Achievements & 📊 Statistics
- **24 achievements** as real Minecraft advancements (**L** › *Wave Survivor*): first pact and victories, waves cleared, kills, bosses, Boss Rush, Daily Challenge streaks, Endless waves, village invasions…
  Counters start with 1.7 — nothing is retroactive.
- **Statistics screen** (altar button or `/ws stats`): hordes started / completed, waves, kills, bosses, deaths, time in hordes, Boss Rush wins, Daily Challenges, best Endless wave, invasions won, and achievement progress.

## 👑 Skills & editor
- 5 new skill types (Sandstorm, Quicksand, Dust Devil, Tremor, Earthquake), all configurable.
- **Particle picker** in the skill editor: every *Particle* field has a **Pick** button (searchable list, sand blocks first); `block:<id>` accepted everywhere.
- **Test any skill yourself**: `/ws skill <name>` (a harmless test monster casts it on you), `/ws skill <name> me`, `/ws skill list` — switch to survival, most skills ignore creative players.
- **Horde list sorted into Hordes / Kingdom / Invasions tabs**, with counts.
- New **Endless** settings view; Codex pages: *Endless mode*, *Statistics*, *Burning Sands*, *Village invasions*.

## ⚙️ Configuration layers (the big under-the-hood change)
- Hordes, monsters, skills, altars, chests and the Renaissance shop are now **read from the mod at every launch**: updates fix built-in content for everyone, **no more deleting your config**.
- Your folder keeps only **what you changed**, field by field (your own hordes, your edits to built-in ones, what you deleted).
- First launch on 1.7 **migrates automatically** and keeps a full copy in `config/wavesurvivor_backup_avant_couches`.
- Editors show **[modified · N]** with a **↺** restore button; **↺ Deleted mod hordes** brings deleted built-in hordes back.
- `/horde setspawn` saves in your own layer; the old `config/wavesurvivor/config.json` is no longer read.

## 🏰 Kingdom
- **Offering Brazier** rebalanced: levels at **4,000** and **12,000** items total (was 1,000 / 3,000); each player can offer **750 per Calm, +250 per Brazier level** (was a shared 1,500).
- **Barracks** more compact: **4×3, 4 high** (was 6×4, 5 high).

## ✨ Quality of life
- **Mystery chests**: right-click with a **stack of keys** opens them **all at once** — one roulette, one summary.
- **Built-in roulette chests** are editable in-game like custom ones, and can be restored.
- **Blessing choice**: the game **pauses in solo**; in multiplayer you are **ignored by monsters and invulnerable** until you pick (30 s max).

## ⚖️ Balance
- **Blessings scale with difficulty**: ×1 Easy / Normal, **×1.25 Hard**, **×1.5 Nightmare**, in every mode; the screens show the real values.
- **Tuto Vanilla** wave-5 boss attack 15 → 10; **Dash** skill bonus ×2 → ×1.2.

## 🐛 Fixes
- Horde monsters **no longer transform** (zombie → drowned in water, husk, skeleton, piglin, hoglin): a wave can no longer get stuck.
- **Witches no longer heal themselves.**
- **Dash** no longer one-shots players (damage only on contact, once).
- **Anomalies** no longer appear between waves.
- **Visual effects** (rocks, cracks, ice spikes, quicksand discs, telegraphs) no longer stay behind when a horde ends or is lost.
- **Dune Riders' war camels** never sit down anymore.

## Upgrade notes (for the description's "Notes" box)
- Safe update from 1.6: your config is migrated and backed up automatically; hordes get the Endless defaults on their own.
- Minecraft **1.20.1**, **Forge 47+**, required on client and server; Curios API optional (relic slot).
- Existing worlds keep working, including saved games in progress.
- Achievement and statistic counters start at 1.7.

## Gallery ideas (screenshots / short clips)
1. Endless mode: wave counter high, boss returning, leaderboard (`/ws endless`).
2. Burning Sands arena at night: sandstorm particles, Djinn with its smoke tail, Sandstone Colossus with orbiting rocks.
3. Sekhmet (giant scorpion) and Ra-Atum fight, sand pit / geysers / sun-ray warning.
4. Earthquake: branching cracks, rubble, shaken camera (a clip works best).
5. Village invasion: bell, boss bar with villagers left, invaders at doors, Hero of the Village.
6. Statistics screen and the advancements tab (L).
7. Editor: Hordes / Kingdom / Invasions tabs, `[modified · N]` + ↺, particle picker.
8. Desert Veil relic icon and a Royal Guard / Jackal / Scarab / Scorpion lineup (spawn eggs in creative).

## Release checklist (not done yet)
- [ ] Merge `dev-1.7` into `main`; `mod_version=1.7.0` is already set.
- [ ] Descriptions (CurseForge + Modrinth) from this file and `CHANGELOG.md`; gallery images.
- [ ] Tag `v1.7.0` — only with Yoann's explicit go (triggers GitHub + CurseForge + Modrinth upload).
- [ ] Still untested before release: multiplayer statistics / shared lives, the "Marathon" achievement, the Djinn's new speed (see `docs/TESTS_1.7.md`).
