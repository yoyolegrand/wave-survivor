# 🌊 Wave Survivor

**A wave-defense mod for Minecraft Forge 1.20.1.** Bind a Runic Altar to a horde, defend the Monolith against waves of monsters, defeat bosses with unique mechanics, and walk away with relics, keys and loot.

> *Français : voir [README_FR.md](README_FR.md).*

---

## ✨ Features

- **9 ready-to-play hordes**: a tutorial and 4 themed campaigns (Undead, Nether, End, Depths), most of them in a **Vanilla** and a **Modded** variant, plus an Iron's Spells horde.
- **Boss fights with real mechanics**: phases, shields, protective totems, crystals, meteor showers…
- **Horde mechanics**: Monolith Defense, Infernal Breaches, gravitational Anomalies, chaos events (deposits to mine, totems, chaos merchants, lightning…).
- **Kingdom mode**: build a kingdom, survive **Assaults** and recover during **Calms** — towers, a Barracks with orders, Repair Workshops, a kingdom map and a Scout.
- **Loot & progression**: Reliquary chests opened with keys, **relics in 6 families with set bonuses** (Curios-compatible, tiers I–III, upgradable and fusable into legendaries), merchants between waves, end-of-horde leaderboard rewards.
- **Renaissance** (prestige): a Heritage tree (3 branches × 5 nodes), ranks I–V, Renaissance Points earned by playing, and a shop.
- **Full in-game editor** (`/ws editor`): create and tune hordes, monsters, bosses, skills and loot without touching a file.
- **English & French**, switchable in game (`/ws lang en` / `/ws lang fr`).

---

## 📦 Requirements

| | |
|---|---|
| **Minecraft** | 1.20.1 |
| **Forge** | 47.x or newer |
| **Required mods** | none — the Vanilla hordes work on their own |

**Optional mods** (detected automatically):

| Mod | What it unlocks |
|---|---|
| **Curios API** | relics can be equipped in Curios slots (ring, necklace, charm, belt…) |
| **Iron's Spells 'n Spellbooks** | *The Arcane Conclave* horde + the "Iron's Spell" skill for custom monsters |
| **L_Ender's Cataclysm**, **Dungeons & Combat**, **Alex's Caves**, **Travel Optics**, **Variants & Ventures**… | the **Modded** variants of the hordes |

If a variant needs a mod you don't have, its **Start** button is greyed out and the missing mods are listed.

---

## 🚀 Getting started

1. Get a **Runic Altar** (creative tab *Wave Survivor*, or `/ws altar give`) and place it.
2. **Right-click it** with the binding ingredients of a horde (shown on the binding screen):

| Horde | Binding ingredients |
|---|---|
| Vanilla Tutorial | 1 iron ingot + 1 bone |
| The Necropolis | 1 skeleton skull + 4 bone blocks |
| The Scorched Lands | 1 blaze rod + 4 magma blocks |
| The Eternal Void | 1 ender pearl + 4 end stone |
| Horde of the Depths | 1 heart of the sea |
| The Arcane Conclave | 1 enchanted book + 4 amethyst blocks |

3. An **arena** forms around the altar (themed floor, roulette chests).
4. **Right-click the altar** again → choose the **Vanilla** or **Modded** version → **Start**.

Between waves, a short pause lets you heal, repair the Monolith and visit the merchants.

---

## 🗺 The hordes

| Horde | Waves | Mechanics | Bosses |
|---|---|---|---|
| **Vanilla Tutorial** | 5 | simple waves, chaos events | Mighty Zombie |
| **The Necropolis** (Vanilla / Modded) | 10 | Monolith Defense, resurrections, curses | The Gravedigger (w5) · **Morvhal, the Lich** (w10) |
| **The Scorched Lands** (Vanilla / Modded) | 10 | Monolith Defense, **Infernal Breaches** | The Bastion Lord (w5) · **The Ash Rider** (w10) |
| **The Eternal Void** (Vanilla / Modded) | 10 | Monolith Defense, Breaches, **gravitational Anomalies** | The Shulker Guardian / The End Golem (w5) · **Xâl'Tor, the Eye of the Void** (w10) |
| **Horde of the Depths** | 10 | aquatic monsters (modded) | The Monster of the Depths |
| **The Arcane Conclave** | 10 | Iron's Spells mages, Arcane Breaches | The Supreme Archevoker (w5) · **The Dead King** (w10) |

**Special waves** sometimes replace a normal wave (Hoglin Stampede, Treasure waves full of keys, Pyromancer Circle…).
**Leaderboard**: at the end of a horde, the top 3 players and the boss killer receive rewards.

---

## ⚔ Horde mechanics

### 🗿 Monolith Defense
The altar becomes the **Monolith**, with an integrity bar. If it falls, **the horde is lost**.
- **Desecrators / Breakers / Devourers** ignore players and go straight for it — kill them first.
- **Repair** it by right-clicking with the horde's repair item (e.g. gold ingot, ender pearl, lapis).
- **Monolith shop** (emeralds): Reinforcement, Regeneration, Plating.

### 🔥 Breaches
Rifts open around the Monolith and spawn **reinforcements** until destroyed. Destroying one gives a reward; a Breach still open at the end of a wave **makes the horde stronger**. Each horde can have several **Breach types** (monsters, rewards, looks).

### ✦ Anomalies
Glowing circles appear under players, turn **red** right before activating, then apply their effects (levitation, slowness, poison, pull…). **Step out!** Counter-play: slow falling, the *Void Anchor Ring*…

### ⚡ Chaos events
Random events during a horde: monster spawns, lightning, **totems** with auras (Frenzy, Regeneration, Curse, Summoning), **ore deposits** to mine with a pickaxe (Fortune works!), and **chaos merchants** selling rare and legendary items — including the horde's relic.

### 👥 Spawning
Monsters spawn **progressively** (batches) and only on **safe positions**: solid floor, enough room for their size, no lava, water, cactus or walls. Admins can check an arena with `/ws spawnzone`.

---

## 🎁 Loot & progression

### Reliquaries
**Roulette chests** opened with a matching **key** (right-click with the key). **Left-click** a chest to preview its loot.

| Reliquary | Keys mostly from |
|---|---|
| Boss | bosses, leaderboard |
| Treasures | Necropolis, treasure waves |
| Nether | Scorched Lands |
| End | Eternal Void |
| Abyss | Horde of the Depths |
| Arcane | The Arcane Conclave |

### Relics
Relics come in **6 families** with **set bonuses** when you wear several pieces of the same family. They go in the dedicated **Relic** Curios slot, and **only equipped relics take effect**.
- **Tiers I–III**, upgraded at the **Forge**.
- **Fusion**: combine relics at the Forge to craft **12 legendary relics**.
- **Sealed Reliquary**: opens to give a random relic.

Examples: Void Anchor Ring (immune to Levitation), Brazier Amulet (immune to fire and lava), Sacred Bone (+damage against undead), Breach Shard (extra damage to Breaches), Last Breath Talisman (survive a fatal blow), Guardian's Seal (the Monolith takes less damage while you're near).

### Renaissance
A prestige system with an **Heritage tree** (3 branches × 5 nodes), **ranks I–V** (title, aura, extra slots) and a **shop** (16 items). **Renaissance Points (PR)** are earned by playing, and everything you spend them on counts toward your rank.

---

## 🏰 Kingdom mode

Build your own kingdom around the Monolith and hold it through alternating phases: **Assaults** (waves of enemies, including siege units) and **Calms** (rebuild and gather guaranteed resources).
- **Defenses**: rebalanced towers, archers with line-of-sight AI, mandatory foundations (unsupported structures collapse).
- **Barracks**: give orders to your troops; the Commander has exclusive options.
- **Repair Workshop**: Ember Forge, Armory and Recycling Foundry, plus a quoted **Repair all**.
- **Info**: right-click a defense for its sheet, use the kingdom map (3 zoom levels) and the Kingdom Compass, send a Scout, read the post-Assault report.
- Kingdom progress is saved and resumed.

---

## 🛠 In-game editor (`/ws editor`)

Everything is editable in game, no file editing needed:
- **Hordes**: waves, monsters, special waves, bosses (and their **minions** — stats, gear, loot), pauses, merchants, chaos events, altar (binding, arena, Monolith Defense, Breach & Anomaly types).
- **Entities & skills**: custom monsters with their own stats, gear, loot and **unlimited skills** (charge, uppercut, eruption, gravity well, illusions… 28 types), each skill **tunable per monster**.
- **Iron's Spells**: give any custom monster an **Iron's spell** (fireball, heal, summon…) picked from a searchable list.
- **Quick pickers**: choose an entity from a **searchable list** or by clicking its **spawn egg**; pick items straight from your inventory — **enchantments included**.

Remember to click **💾 Save**.

---

## ⌨ Commands

**Players**

| Command | |
|---|---|
| `/ws info` | current horde, wave and remaining monsters |
| `/renaissance` | open the Renaissance menu |

**Operators**

| Command | |
|---|---|
| `/ws editor` | open the in-game editor |
| `/ws start <horde>` · `/ws stop` · `/ws skip` | start / stop a horde, skip to next wave |
| `/ws lang <en\|fr>` | mod language (synced to all players) |
| `/ws spawnzone [radius]` | show valid spawn positions (green particles) |
| `/ws altar give` · `/ws altar bind <horde>` | Runic Altar tools |
| `/ws breach open [n] [style]` · `/ws breach close` | test Breaches |
| `/ws anomaly [n]` · `/ws totem [aura]` · `/ws gisement [size]` | test Anomalies, totems, deposits |
| `/ws chaos [list\|n]` | list / trigger chaos events |
| `/ws roulettechest …` | roulette chest builder |
| `/ws reload` | reload the configuration |
| `/renaissance points add/set <player> <n>` | manage Renaissance Points |
| `/renaissance reset <player>` | reset permanent stats and shop purchases |

---

## ⚙ Configuration

All configuration lives in `config/wavesurvivor/`.
- On **first launch**, the mod installs its full default content (hordes, entities, skills, altars, Reliquaries, Renaissance shop).
- Existing files are **never overwritten** on update — your edits are kept.
- To restore a default file, **delete it** and restart the game.
- Language: `config/wavesurvivor/settings.json` (English by default, or use `/ws lang`).

---

## 🔨 Building from source

Requires **Java 17**.

```
./gradlew build
```

The mod jar is produced in `build/libs/`. To run a dev client: `./gradlew runClient`.

---

## 🚀 Releasing (maintainer)

Releases are published automatically to GitHub, Modrinth and CurseForge by `.github/workflows/release.yml`.

1. Set `mod_version` in `gradle.properties`.
2. Add a `## <version>` section at the top of `CHANGELOG.md`.
3. Commit and push, then tag: `git tag v<version>` and `git push origin v<version>`.
   (use a `-beta` suffix, e.g. `v1.6.0-beta`, for a beta release).

Required repository secrets: `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.

---

## 📜 License & credits

- **License**: source code under [MIT](LICENSE); art assets (textures, logo) are **all rights reserved**, see [LICENSE-ASSETS](LICENSE-ASSETS)
- **Author**: yoyolegrand
- Also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/wave-survivor) and [Modrinth](https://modrinth.com/mod/wave-survivor).
