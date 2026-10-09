# Release 1.5.0 — preparation kit

Ready-to-paste texts for the CurseForge and Modrinth 1.5.0 upload. Nothing here has been published.

## Upload fields (both sites)

| Field | Value |
|---|---|
| File | `build/libs/wavesurvivor-1.5.0.jar` |
| Version name | `Wave Survivor 1.5.0` |
| Version number | `1.5.0` |
| Release type | Release (switch to Beta if you want more testing first) |
| Game version | **1.20.1 only** |
| Loader | Forge |
| Environment | Client and server (required on both) |
| Dependencies | none required; Curios API **optional** |
| Changelog | content of `CHANGELOG.md`, section 1.5.0 |

## Short summary (Modrinth "Summary", ≤ 256 chars)

Wave defense meets kingdom building. Defend your Monolith against hordes and bosses, build towers and walls, collect relics in 6 families and climb the Renaissance prestige ranks. Everything is editable in game.

## Long description (Markdown, for both pages)

```markdown
# 🛡️ Wave Survivor

Bind a Runic Altar to a horde, defend the Monolith, survive the waves — or build a whole kingdom around it.

Wave Survivor turns Minecraft into a wave-defense game you can play solo or with friends. Hordes grow stronger every wave, bosses arrive, chaos events shake the arena, and between fights you trade, open mystery chests and choose blessings. Everything — hordes, waves, bosses, loot, merchants — can be edited in game, no file editing required.

## ⚔️ Two ways to play

### Classic Hordes
- Waves of monsters that scale with players and difficulty
- Bosses, special waves, Breaches to close, merchants between waves
- Mystery chests (roulette), blessings, and a full horde progression to unlock
- Chaos events: ore deposits and fantasy trees to harvest mid-fight

### 🏰 Kingdom Mode
- Your Monolith sits at the heart of a claim; enemy Gates open in a ring around it
- Alternate between **Calm** (gather, build, complete objectives) and **Assaults**
- Buildings with levels and specializations: Archer Tower, Mage Tower, Shrine, Barracks, Gleaner Tower
- Walls, gates, traps and siege units (Rams, Sappers, Climbers, Standard-bearers)
- **New in 1.5:** Repair Workshop (specializations: Blazing Forge, Field Armory, Mechanic's Workshop, Recycling Foundry) with a quoted "Repair all", Barracks orders, defense sheets, a Kingdom map and Compass, a Scout, post-Assault reports, rebalanced towers, smarter archers, mandatory foundations and saved Kingdom progress

## 🔮 Relics & Renaissance
- **Relics in 6 families with set bonuses**, a dedicated Curios slot, tiers I–III
- Upgrade and fuse relics at the **Forge** into **12 legendary relics**; open **Sealed Reliquaries** for a random relic
- **Renaissance** prestige: a Heritage tree (3 branches × 5 nodes), ranks I–V with title, aura and extra slots, Renaissance Points earned by playing, and a 16-item shop

## 🛠️ Fully editable in game
- Visual horde editor: waves, entities, bosses, chaos events, loot, merchants, Kingdom settings
- Starter templates, validation, wave preview, import/export (.zip)
- Collaborative editing and an in-game Codex that explains every mechanic
- Altar layout screen: place chests and merchants on a top-down grid

## 📦 Requirements
- Minecraft **1.20.1** · Forge **47+**
- Install on both client and server
- Optional: **Curios API** (relic slots)

## 🌍 Languages
English · Français

## 💬 Feedback
Bug reports and ideas are welcome on the [issue tracker](https://github.com/yoyolegrand/wave-survivor/issues). Source code: [GitHub](https://github.com/yoyolegrand/wave-survivor).
```

## Checks before clicking "Publish"

- [ ] **Rebuild the jar** (`mods.toml` was fixed to use `${mod_version}`; the jar from 15:43 still says 1.4.0 inside). `build.log` must show BUILD SUCCESSFUL, then check the version in the in-game Mods list reads 1.5.0
- [ ] Last in-game tests done (Renaissance ranks, Kingdom save/resume, Barracks orders)
- [ ] Jar tested in the test instance with Minecraft 1.20.1 / Forge 47.x
- [ ] Game version set to **1.20.1 only** on both sites
- [ ] Description updated (the old one still says "15 relics" and "Rebirth"; the in-game name is **Renaissance**)
- [ ] Gallery: add screenshots of the Forge, the Kingdom map and the Renaissance tree (new in 1.5)
- [ ] Optional: tag the release on GitHub (`git tag v1.5.0`) and attach the jar
