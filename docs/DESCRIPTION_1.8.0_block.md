## What's new in 1.8.0 — Blessings Reborn, Seraph Shrine & Arenas
> 🙏 **Blessings, rebuilt from the ground up.** Rarer cards, rerolls and bans, special blessings, curses and synergies — all editable in game. Bend the new **Seraph Shrine** towards grace or corruption to upgrade your gear, explore **eight ready-made arenas** across the world (including a giant Kingdom battlefield), and upgrade your **legendary relics**.


### 🙏 Blessings — full rework
- 🧱 **Blessings are now data, not code**: grouped into **categories** (Life, Armor, Force…) that hold their rarity levels (**Common, Rare, Epic, Legendary**), read from the mod at every launch like the hordes — your own changes live in `config/wavesurvivor/custom_blessings.json`.
- 🛠 New **Blessings editor** (button in the horde editor): categories on the left, and for each one the **◀ ▶ arrows** go from Common to Legendary. Every level has its own **Available** switch, heal and effects — any attribute of the game or of your mods (Apothic Attributes, Iron's Spells…) through a searchable picker. Changes to the mod's blessings are kept field by field (**[modified · N]**, **↺** restores); you can also create, duplicate and delete categories. Special effects (Execution, Bastion, Taunt…) are editable too, each with a description. Operators only, not during a horde.
- 🎲 **Rarity now matters in the draw** (it used to be a flat draw): each of the 3 cards is a **different category** whose rarity is rolled with weights (60 / 28 / 10 / 2 by default) that **shift towards rarer levels as the waves go on**.
- 🍀 **Luck matters**: the Luck attribute (blessings, potions, gear) raises the odds of rarer levels, with diminishing returns. It also improves the **Offering Brazier** rewards, the **roulette chests** and gives monsters a chance to drop **an extra loot batch** (never more than one extra per monster). Each use can be switched off.
- ↻ **Rerolls and bans**: start with **2 rerolls and 1 ban**, win **+1 of each every 5 waves**. *Reroll* redraws the cards — and you can **pick a family** (Attack, Defense, Mobility, Utility, Team, Pact, Statistics); *Ban* removes a category for the whole run. 📌 **Keep a card** locks a card while you reroll the others.
- ✨ **8 special blessings** (4 rarities each, stackable): **Execution** (bonus damage on weakened monsters), **Dawn** (absorption shield refilled every wave), **Magnet** (pulls items and XP), **Banner** (nearby allies deal more damage), **Blood Link** (shares part of your healing) and three **Pacts** that trade a drawback for power: **Shattered Glass** (much more damage, less max HP), **Berserker** (stronger and faster the more hurt you are, less armor) and **Burden** (a full tank kit: armor, toughness, knockback immunity, **Bastion**, **Taunt**, reduced projectile damage — at the price of speed). Pacts are capped at level 3.
- ☠️ **Curses**: rare red cards (about 1 menu in 10, never more than one per menu) with a big bonus and a real drawback — Curse of Blood, of the Mad Run, of Frenzy.
- 🔗 **Family synergies**: collect 3 / 5 / 7 blessings of the same family for a bonus tier, shown on the cards ("Attack synergy: 2/3").
- 👁️ **Previews on cards** ("current total → total after this pick"), a **My bonuses** screen (`/ws benediction mine`, or a button in the choice screen) and a **settings screen** (cards offered, rarity weights, change per wave, Luck, rerolls and bans, curses — with a "Mod defaults" reset).
- 📦 **Blessing packs**: export your blessings, changes and settings to a file and share them; import someone else's (`/ws benediction export|import <name>` or the "Packs" button).
- 📊 **Statistics** per world: how often each blessing is offered, picked and won with (`/ws benediction stats`, and on each blessing in the catalogue).
- 🛡 You can now only pick a blessing that was actually offered.

### 🕊 Seraph Shrine — new structure
- 🗿 An unbreakable statue with three pairs of wings, found in rare ruins of the temperate and desert biomes. One shared **faith** gauge (-600 to +600) takes it from **Pure** to **Corrupted** — and the statue changes with it (model, textures, wings, halo).
- 🎁 **Offerings**: flowers, honey, gold, golden apples… raise the gauge; rotten flesh, bones, sculk, skulls… lower it. Horde monsters killed nearby help, **killing a villager costs 50**. Every value is in `shrine_offerings.json`.
- 💎 **Shards of grace and taint** are produced by the statue according to its state: 6 new items in 3 levels (**Shard → Fragment → Heart**, fused 9 → 1; Hearts can also drop from bosses).
- ⚒ **The Forge**: weapons and armor get up to **3 upgrades** (cost 1, 2, 3 fragments), always **Consecrated** (positive only) or **Profaned** (strong bonus, real drawback) — never both. **12 consecrations and 12 corruptions** (Holy Might, Smite, Blessed Blade, Aegis, Vitality, Survival Pact, Bloodthirst, Executioner, Mark of the Beast…), bonuses capped at +30 %, all in `shrine_upgrades.json`.
- 🌈 Upgraded gear gets a **colored name**, a full tooltip and an **animated frame** on its slot.
- 💀 **The price of corruption**: a tainted statue calls **Rootlings**, a corrupted one more of them and sometimes a **Stone Brute**.

### 🏟 Ready-made arenas
- ⚰ **Necropolis of the Fallen Kings** and 🔥 **The Infernal Breach** (it **replaces about 1 ruined portal in 4** on the surface — nothing else is lost).
- 🟣 **Void Islet** (floating islands, End horde), 🌊 **Sunken Reef** (shallow seas), ☀ **Temple of Ra** (Burning Sands), ❄ **Frost Bastion** (Frozen Peaks — its shelters protect you from the storms) and ✨ **Arcane Circle** (requires *Iron's Spells 'n Spellbooks*).
- 👑 **Crown Vale**: a **181 × 181 Kingdom arena** for the Pillagers, with four fortified camps (a Gate at the center of each), a rocky hill, a quarry, a lake, a shallow river with fords, forests built with real trees and ominous banners. It places the Kingdom Gates itself **without touching your horde settings**.
- 🌍 **Natural generation** for every arena (rare, random rotation, biomes, terrain blending), each with its horde's **locked, unbreakable altar** already placed.
- ⚔ During the game, monsters appear at **the arena's own spots** (crypt, graves, rifts, portal…) and the boss at its own place; each arena has its own effects (the crypt gate opens, braziers fade, the portal blazes…) and a **reward chest** on the first victory.
- ⚙ Settings in `arenas.json`; `/ws arena list` and `/ws arena place <arena> [rotation]` for operators. The plan format is reusable for more arenas.

### 💍 Relics
- ⭐ **Legendary relics can now be upgraded** in the Forge by combining two identical legendaries (15 then 30 RP, same as other relics).
  - **Level I**: the two original relics act at level II (as before).
  - **Levels II and III**: they act at level III.
  - **Unique effects scale** (×1 / ×1.5 / ×2): Banner aura radius, Hunter's Trophy bonus, Chalice healing and absorption, Gates key damage, Watcher's invulnerability duration and reveal radius, Merchant King refund chance, Skeleton Key drop chance, Seasons burn and freeze duration.
- 🔢 Tooltips now show the **real numbers** for the relic's level (e.g. +25 / +37.5 / +50 %) instead of "×1.5".
- ⇧ Hold **Shift** on a legendary to see the real effects of its two original relics.

### 📢 Event feed
- 📜 Horde, Kingdom, Boss Rush, Endless and Invasion announcements (wave start, wave cleared, bonuses, boss arrival, Monolith at 50 % / 25 %, blessing chosen…) now appear in the **event feed on the left side** of the screen instead of the chat. Major events use a larger **important** card. The "blessings dissipated" message is gone.

### 🪲 New creatures
- 🌱 **Rootling**, 🗿 **Stone Brute** and 🦗 **Voidmantis**, with high-definition models and spawn eggs.

### ⚡ Performance & tools
- 📈 New `/ws perf` (operators): what a wave costs — **server tick time**, entities, horde units, visual effects, items on the ground and pending actions — measured every second during a horde or invasion, with the worst of each wave written to the log (and to the operators' chat with `/ws perf verbose`).
- ✨ Lighter particles on idle roulette chests and altars (only shown near a player), and faster Curios relic checks.

### 📖 Codex
- New pages: *Seraph Shrine* and *Ready-made arenas*.

### 📝 Notes
- Your config is never overwritten: your own changes are kept, and the built-in content updates on its own.


**Thanks for playing!** ⚔ Bugs & ideas: [GitHub Issues](https://github.com/yoyolegrand/wave-survivor/issues) · Support the development on [Patreon](https://www.patreon.com/cw/yoyolegrand34)
