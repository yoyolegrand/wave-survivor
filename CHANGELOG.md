# Changelog

## 1.7.0 — Endless Mode, Achievements & Statistics

> ♾ **The horde never stops.** Play any horde without limit in the new **Endless mode**, unlock **21 achievements**, and track your career in the new **Statistics** screen.

---

### 🏜️ Burning Sands — new horde
- 🏜 A new **desert horde**: mummies, scarabs, scorpions, jackals, desert riders, pharaoh guards, sandstone golems and djinns, with special waves and **three bosses** — **Anubis**, **Sekhmet** and **Ra-Atum**, each with 7 to 9 skills.
- 🔓 Unlocks after the **Necropolis** — bind it at the altar with **chiseled sandstone and red sand**.
- 🗿 **Sand Desecrators** ignore players and rush the **Monolith** — protect it!
- 🧣 New **Desert Veil** relic (Elements family, now 4 pieces): immune to sandstorms and quicksand, **faster** (even more on sand). It drops from the bosses and a nomad merchant sells it.

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
- ✨ **21 achievements** (real Minecraft advancements — press **L**, tab *Wave Survivor*): first pact and victories, waves cleared, monsters and bosses slain, Boss Rush, Daily Challenge streaks, Endless waves and more.
- ⏳ They unlock on their own when you reach the thresholds. Counters start with 1.7: nothing is retroactive.

### 📊 Statistics
- 📈 New **Statistics** screen (altar button or `/ws stats`): hordes started and completed, waves cleared, monsters and bosses killed, deaths, time in hordes, Boss Rush wins, Daily Challenges, best Endless wave — and your achievements with their progress.

### 🏰 Kingdom
- 🔥 **Offering Brazier** rebalanced: it levels up as the kingdom feeds it (**4,000** and **12,000** items in total, was 1,000 and 3,000), and each player can offer up to **750 items per Calm**, **+250 per Brazier level** (was a shared 1,500 per Calm).
- 🏘 **Barracks** are now more compact: **4×3 blocks, 4 high** (was 6×4, 5 high).

### ✨ Quality of life
- 🎁 **Mystery chests**: right-clicking with a **stack of keys** now uses **all of them at once** — one 3-second roulette, then every reward is given together with a single summary.
- 🧰 **Built-in roulette chests** (Mystery, Boss, Nether…) can now be **edited in-game** like custom chests, and restored to their original version.

### 🛠️ Editor & Codex
- 📝 New **Endless** settings view in the editor.
- 📖 The **Codex** has new *Endless mode* and *Statistics* pages.

### 📝 Notes
- Existing config files are never overwritten on update. Hordes get the Endless defaults automatically.

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
