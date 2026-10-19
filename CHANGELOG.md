# Changelog

## 1.7.0 — Endless Mode, Achievements & Statistics

> ♾ **The horde never stops.** Play any horde without limit in the new **Endless mode**, unlock **24 achievements**, and track your career in the new **Statistics** screen.

---

### 🏘️ Village invasions
- 🔔 **Invasions of real villages**: the bell rings, villagers hide, a short preparation, then **waves** with pauses between them. The number of waves follows the world difficulty and the **Bad Omen** level, like the vanilla raid — and the default invasion reuses the vanilla wave composition — which you can customize in the editor (HP and damage multipliers, stats and skills per invader type, extra monsters, special waves and a chief).
- 🎲 **Bad Omen roulette**: walk into a village with the effect and the game picks one of the mod's invasions (Pillagers, Sand Raid, Snow Pack…). The classic vanilla raid is **off by default** (it can be given a weight in `invasions.json`, and then gets extra rewards and pillagers that open and break doors).
- 🏘 The top bar shows the **villagers left** and the **wave**. Lose too many villagers (half by default, configurable) or all of them and the village falls.
- 🚪 Invaders **open and break doors** and hunt the villagers. Nothing else is destroyed.
- 🏅 Victory: **Hero of the Village** (level from the omen), **raiders' loot**, Renaissance points, statistics and 3 new achievements.
- ⚙ Build your own in the editor: the mode button now has a **third state, Invasion**, which reshapes the tabs. Operators can use `/ws invasion start [name]` in the village they stand in, and `/ws invasion skip` to skip the current wave (skipping the last one wins the invasion) — same mechanic as `/ws skip` for hordes.
- 🏜 The **Sand Raid** now also fields **Djinns** (from wave 3) and a **Sandstone Colossus** (final wave), with their custom models.

### 🏜️ Burning Sands — new horde
- 🏜 A new **desert horde**: mummies, scarabs, scorpions, jackals, desert riders, pharaoh guards, sandstone golems and djinns, with special waves and **three bosses** — **Anubis**, **Sekhmet** and **Ra-Atum**, each with 7 to 9 skills.
- 🔓 Unlocks after the **Necropolis** — bind it at the altar with **chiseled sandstone and red sand**.
- 🗿 **Sand Desecrators** ignore players and rush the **Monolith** — protect it!
- ☀ **Ra-Atum's skills now all throw sand** (sand pit, geysers, sun ray, dust devil, sand barrier — no more purple or white effects), and the **Mirage** (illusions) is gone from his kit. The Sun Ray warning is now visible until the strike.
- 🎛 **Particles are selectable in the skill editor**: Sand Pit / Gravity Well, Eruption, Sun Ray, Dust Devil, Blink, Force Field, Venom… each has a **Pick** button that opens a searchable list (sand blocks first).
- 🦂 **Sekhmet is twice as big** (the Scorpion supports a body size setting, hitbox included), and her tail strike and burrow now throw **sand** instead of white puffs. Skills accept `block:<block id>` as a particle setting, and Blink has a new particle setting.
- 🪲 **Every desert creature has a custom 3D model** (not recolors): the mummified **Royal Guard** (also Anubis), the sacred **Scarab**, the articulated-tail **Scorpion** (also Sekhmet), the red-eyed pack-hunting **Cursed Jackal** (also the Alpha), the hunched **Sandstone Colossus** with its sun heart and orbiting rocks (also Ra-Atum), plus spawn eggs for all of them.
- 🧞 The **Djinn** is slower (it floats, it no longer rushes at you), and has a custom 3D model (turban, belted robe, smoke tail instead of legs) and summons **Djinn Spirits** instead of vanilla Vexes.
- 🧣 New **Desert Veil** relic (Elements family, now 4 pieces): immune to sandstorms and quicksand, **faster** (even more on sand). It drops from the bosses.

### 🌪️ Sandstorms & 🌋 Earthquakes — new chaos events
- 🌪 The **Storm** event now has a **sand look**, and a new **shelter rule**: get **under cover** (a roof, or in water) instead of near a heat source. An **immunity relic** can be set per storm. All configurable in the editor.
- 🌋 New **Earthquake** event: the ground rumbles, then **shakes** — cracks run across the floor, rubble falls from the sky and the camera trembles. No shelter helps: dodge them. Duration, damage, jolts, cracks, rubble and shake are all configurable.

### 👑 New boss skills
- ✨ Five more skill types, configurable in the editor: **Sandstorm**, **Quicksand**, **Dust Devil**, **Star Fracture** and **Earthquake**.

### ♾️ Endless mode
- ♾ New button at the altar: after the horde's **last wave**, it **keeps going** — more monsters, more HP and more damage every wave — until your team falls.
- 👑 A **boss from the horde returns every few waves**, stronger each time.
- ❤ **Shared lives** for the team. At zero lives (or when the Monolith falls), the run ends.
- 🏆 Your score is the number of **waves cleared**. Top scores are ranked per horde — check them with `/ws endless`.
- 💎 **Renaissance Points**: normal waves pay as usual, plus the victory bonus once the last normal wave is cleared — then **+1 PR every 2 waves**.
- 🔒 Unlocked by completing the horde once. Classic hordes only; no save & resume.
- ⚙ Fully configurable per horde in the editor (**Boss tab › Endless**): lives, growth per wave, monster cap, boss return, unlock, leaderboard size.

### 🏅 Achievements
- ✨ **24 achievements** (real Minecraft advancements — press **L**, tab *Wave Survivor*): first pact and victories, waves cleared, monsters and bosses slain, Boss Rush, Daily Challenge streaks, Endless waves and more.
- ⏳ They unlock on their own when you reach the thresholds. Counters start with 1.7: nothing is retroactive.

### 📊 Statistics
- 📈 New **Statistics** screen (altar button or `/ws stats`): hordes started and completed, waves cleared, monsters and bosses killed, deaths, time in hordes, Boss Rush wins, Daily Challenges, best Endless wave — and your achievements with their progress.

### 🏰 Kingdom
- 🔥 **Offering Brazier** rebalanced: it levels up as the kingdom feeds it (**4,000** and **12,000** items in total, was 1,000 and 3,000), and each player can offer up to **750 items per Calm**, **+250 per Brazier level** (was a shared 1,500 per Calm).
- 🏘 **Barracks** are now more compact: **4×3 blocks, 4 high** (was 6×4, 5 high).

### ✨ Quality of life
- 🎁 **Mystery chests**: right-clicking with a **stack of keys** now uses **all of them at once** — one 3-second roulette, then every reward is given together with a single summary.
- 🧰 **Built-in roulette chests** (Mystery, Boss, Nether…) can now be **edited in-game** like custom chests, and restored to their original version.
- 🙏 **Blessing choice**: in **solo** the game now **pauses** while you choose; in **multiplayer** you are **ignored by monsters and invulnerable** until you pick (30 s at most).

### ⚖️ Balance
- 🙏 **Blessings scale with the difficulty**: ×1 on Easy and Normal, **×1.25 on Hard**, **×1.5 on Nightmare** — in every mode (hordes, Kingdom, Boss Rush, Daily Challenge, Endless). The choice screen and the catalogue show the real values.

### 🐛 Fixes
- 🧟 **Horde monsters no longer transform**: a horde zombie that falls in water stays a zombie instead of turning into a Drowned (which left the wave stuck forever), and the same goes for husks, skeletons, piglins and hoglins. Killing every unit now always ends the wave.
- 🧹 **Visual effects no longer stay behind** when a horde ends or is lost: earthquake rocks and cracks, ice spikes, quicksand discs and telegraphs are all removed.
- 🐪 **Dune Riders' war camels never sit down** anymore (at night the vanilla camel lay down and left its rider stuck).
- 🌋 **Earthquake and Tremor cracks look like real cracks**: a jagged path that narrows towards the end, with branches splitting off, instead of a straight bar.
- 🧪 **Witches no longer heal themselves** (healing potion and regeneration), which made them far too tough on high difficulties.

### ⚙️ Configuration layers
- 🧱 The mod's own content (hordes, monsters, skills, altars, chests, Renaissance shop) is now **read from the mod itself at every launch**. Updates fix the built-in hordes for everyone — **no more deleting your config** to get new balance changes.
- 🧍 Your own work lives in `config/wavesurvivor/` and only stores **what you changed**, field by field: your own hordes and creations, your edits to a built-in horde (a changed boss attack, a spawn point…), and what you deleted. Everything you did not touch keeps following the mod's updates.
- 🛟 First launch on 1.7 **migrates your existing config automatically**: the whole folder is copied to `config/wavesurvivor_backup_avant_couches` first, unmodified built-in content is recognised and left to the mod, and anything you made or edited is kept.
- 🛠️ The editors show **[modified · N]** on built-in content you changed, with a **↺** button to restore the mod's version, and **↺ Deleted mod hordes** brings back built-in hordes you deleted.
- 📍 `/horde setspawn` now saves the spawn point in your own layer. The old `config/wavesurvivor/config.json` is no longer read.

### 🛠️ Editor & Codex
- 🧪 **Test any boss skill yourself**: operators can run `/ws skill <name>` (a harmless test monster appears in front of you and casts it on you, like a real boss), `/ws skill <name> me` (you cast it), and `/ws skill list` — tab completion included. Switch to survival: most skills ignore creative players.
- 📂 The horde list is sorted into **Hordes / Kingdom / Invasions** tabs (with counts); a new horde lands in the right tab by itself.
- 📝 New **Endless** settings view in the editor.
- 📖 The **Codex** has new *Endless mode* and *Statistics* pages.

### 📝 Notes
- Your config is never overwritten: see *Configuration layers* above. Hordes get the Endless defaults automatically.

---

**Thanks for playing!** ⚔ Bugs & ideas: [GitHub Issues](https://github.com/yoyolegrand/wave-survivor/issues) · Support the development on [Patreon](https://www.patreon.com/cw/yoyolegrand34)

## 1.6.0 — Frozen Peaks, Boss Rush & Daily Challenge

> ❄ **A frozen horde, a race against the bosses, and a new challenge every day.** Brave the blizzard of the Frozen Peaks and its three bosses, fight them all back to back in **Boss Rush**, and take on the **Daily Challenge** for bonus Renaissance Points. Kingdom mode gets a new **Offering Brazier**, smarter **Gleaners** and a lot of quality-of-life.

---

### ❄️ Frozen Peaks — new horde
- 🏔 A new **horde of blizzards and frost creatures**: ten monsters, special waves, chaos events and **three bosses with their own mechanics**.
- 🔓 Unlocks after the **Necropolis** — bind it at the altar with **blue ice and snow blocks**.
- 🧊 New **Frost Golem**, **Snow Sorcerer** and **Frost Ravager** creatures, each with its own look and spawn egg.
- 🗿 **Frost Desecrators** ignore players and rush the **Monolith** — protect it!
- 🐺 A **Wolf Alpha** leads the wolf pack, the **Yeti** hurls ice boulders and buries players who stand still, and the bosses drop **frost-themed loot** (Frost Crystal, Sealed Reliquary, and a rare Heart of the Seasons from the final boss).
- 🐢 Slowness is frequent here, on purpose: it is the identity of the horde.

### 🌨️ Storms — new chaos event
- ❄ Exposed players are slowed, **frozen** (the vanilla frost bar, like powder snow) and hurt, amid dense snow and gusts of wind.
- 🔥 **A lit campfire gives shelter.** Shelter blocks, duration, damage, wind, particles and effects are all configurable.
- ♻ Reusable in **any horde** from the editor's Chaos tab.

### 👑 New boss skills
- ✨ Five new skill types, configurable in the editor: **Ice Spikes**, **Frost Nova**, **Blizzard**, **Glacier Pillars** and **Shatter** (a player trapped in ice that allies must break free).
- 🧊 Powerful attacks are now **telegraphed with ice effects** — cracks, ice spikes and falling ice boulders built from block displays: no collision, and the terrain is never modified.

### ⚔️ Boss Rush
- 🔥 New mode: play a horde with **only its bosses**, back to back — in **hordes and in Kingdom**.
- 🎯 Two modes, chosen at the altar. In Kingdom, **lives are shared** between players.
- ⚙ Configurable per horde in the editor (including the Calm between bosses, 20 s by default).
- 🏆 Records are kept — check them with `/ws bossrush`.

### 📅 Daily Challenge
- 🎲 A **daily difficulty** (Normal, Hard or Nightmare) with **three mutators** — the same for everyone on the server.
- 💎 **Bonus Renaissance Points** for the first victory of the day, growing with your streak.
- 🏰 Works in hordes and in Kingdom (higher reward in Kingdom). Check today's challenge with `/ws daily` or in the Codex.

### 🏰 Kingdom
- 🔥 **New building — Offering Brazier** (2×2, 2 high): sacrifice **any items**, only the **quantity** counts. Every full gauge rolls a reward: wood, stone, coins, iron, Breach Essence, and rarely a **Sealed Reliquary**, a **blessing choice** or Renaissance Points.
  - Levels up as you feed it (**1,000** and **3,000** items), then pick a specialization: **Abundance** (resources ×1.5), **Relics** (Reliquary and blessing chances ×2) or **War** (Repair Wave and Kingdom's Fervor: +10% tower and soldier damage for the next Assault).
  - Offerings during the **Calm** only, up to **1,500 items per Calm**. Enchanted items, mod items, keys and treasury resources are refused.
- 🧺 **Gleaners**: their storage now has **tabs** to jump between nearby Gleaners, and a **sorting filter** (Resources, Equipment, Keys, Food, Blocks, Other). Filtered Gleaners get their categories first; unfiltered ones keep the rest.
- 🧱 **Ramparts**: right-click opens a **rampart sheet** with HP, the upgrade and its **cost**, and a Reinforce button.
- 🔧 **Repair Workshop**: the **crucible** (melt gear into resources) is now available with **every specialization**. The Recycling Foundry is replaced by the **Enchanting Forge**: fully repaired gear can gain or improve an **enchantment** once per Calm.
- 🪤 **Traps** can now be placed **outside the claim**, up to 64 blocks from the Monolith.
- 🏹 **Archer towers** are limited to **4 at Town Hall level 1, +2 per level**.
- ⚖ Balance: **Fire** archers +25% damage and the target burns for 5 s; **Frost** archers freeze on the **2nd** hit for **3 s** (+60% damage taken); the **Treasury** Gleaner produces coins every **10 s** (was 20 s).

### 🛠️ Editor & Codex
- 🚫 Special waves can now **exclude chosen waves** — never on wave 1 by default.
- 📝 New editor forms for the new skill types, the Storm event and Boss Rush settings.
- 📖 The **Codex** has a new Frozen Peaks page and updated Boss and Editor pages.

### 🐛 Fixes
- Defenses, soldiers, gates and traps are now always removed at the end of a Kingdom game — including a victory won in the final boss arena.

### 📝 Notes
- Existing config files are never overwritten on update. The Frozen Peaks horde is added to your config automatically **once**; to reset a horde to its defaults, delete the file and restart the game.

---

**Thanks for playing!** ⚔ Bugs & ideas: [GitHub Issues](https://github.com/yoyolegrand/wave-survivor/issues) · Support the development on [Patreon](https://www.patreon.com/cw/yoyolegrand34)

## 1.5.0 — Relics, Renaissance & Kingdom Tools

**Minecraft 1.20.1 · Forge 47+** — the biggest update so far: relics are reworked, Renaissance becomes a full prestige system, and Kingdom mode gets repair, command and map tools.

### Relics
- Relics now belong to **6 families**, each with a **set bonus** when you wear several pieces of the same family.
- New dedicated **Relic** slot (Curios). **Only equipped relics take effect.**
- Relics have **tiers I–III**.
- New **Forge**: upgrade your relics and **fuse them into 12 legendary relics**.
- New **Sealed Reliquary**: opens to give a random relic.

### Renaissance (prestige)
- New **Heritage tree**: 3 branches × 5 nodes, rebalanced.
- New **ranks I–V**, with a title, an aura and extra slots.
- **Renaissance Points (PR) are now earned by playing.**
- Every PR spent now counts toward your Renaissance.
- Shop expanded to **16 items**.

### Kingdom mode
- **Repair Workshop** with four specializations: Blazing Forge, Field Armory, Mechanic's Workshop and Recycling Foundry.
- **Repair all** button, with a price quote before you confirm.
- **Barracks orders**, plus exclusive options for the Commander.
- **Defense sheet**: right-click any defense to see its stats.
- **Kingdom map** (3 zoom levels) and the **Kingdom Compass**. Corruption Hearths from the Purification objective show on the map for everyone and can be targeted with the Compass.
- New **Scout** unit and an **Assault report** after every Assault.
- **All towers rebalanced.**
- **Foundations are now required** — unsupported structures collapse.
- **Archer AI** reworked: line of sight and target priorities.
- **Guaranteed resources** during every Calm.
- Kingdom progress is now **saved and resumed** with the world.

### Quality of life
- New **event feed** on the left of the screen (chaos events, Breaches, Catalysts, merchants, objectives) instead of chat messages.
- **Roulette chests queue**: opening several chests in a row no longer overlaps.
- **Blessings are kept on death.**

### Notes
- Existing config files are never overwritten on update. To get the new defaults, delete the file you want to reset and restart the game.
- New relic and legendary textures are prototypes and will be refined in a later update.
