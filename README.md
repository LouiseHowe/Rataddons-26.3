# RatAddons

RatAddons is a client-side Fabric mod for Hypixel SkyBlock. Version **2.0.1** brings Kuudra, Dungeon, Mining, Fishing, Diana, rendering, and movable HUD tools into one searchable settings screen.

## Install

- Minecraft **26.3**
- Fabric Loader **0.19.5 or newer** and Fabric API for Minecraft 26.3
- Java **25 or newer**

Download the 26.3 JAR from [RatAddons Releases](https://github.com/LouiseHowe/RatAddons-Releases/releases) when 2.0.1 is published. Put it and Fabric API in your `mods` folder, remove older RatAddons JARs, and start the Fabric profile. The mod ID is `rataddon`.

## First steps

Run `/rataddon` to open settings. Pick a category in the sidebar or search all options with the search box. Features with a `▸` have a separate options page. Most automation is off by default.

Run `/rataddon hud` to move screen HUDs: drag to position, scroll to scale, and right-click to reset. Settings are saved in `config/rataddon.json`.

## Features

### Kuudra

- **Rend Macro:** configurable Rend combat actions and timing.
- **Auto Sneak in Build:** press Sneak to toggle crouching during the Build phase.
- **Supply Swap:** use a hotbar Ender Pearl while holding Elle's Supplies during Supplies.
- **Auto Croesus:** automatically click Croesus with an adjustable delay.
- **Fixes:** optional Kuudra interaction and targeting fixes.
- **Kuudra Distance:** show the configurable green distance range.
- **Kuudra Hitbox:** draw a configurable hitbox outline.

### Dungeon

- **Storm** handles aim and item timing, Death Bow armor swaps, and Last Breath/Terminator options.
- **Ice Spray and Ice Announce** provide particle and target alerts; **M7 Relics** adds pickup and cauldron helpers.

### Mining

- **Corpse ESP** highlights recognized unlooted corpses; **Shaft Announce** shares the current Mineshaft and corpse summary.
- **Mineshaft ESP** marks corpse spots, fossils, and Mineshaft mobs, with separate visibility options.
- **No Gemstone Desync** repairs local stained-glass connection updates in supported SkyBlock areas.
- **Ping Offset Miner** shows red/green outlines and highlights for supported mining blocks, adjusts the vanilla break-progress overlay, and offers movable tick and Ping/TPS HUDs. Timing follows client mining ticks, Mining Speed, latency, and server TPS; precision mining and Mining Speed Boost are supported. Show Mining Speed in the SkyBlock Tab widget and adjust Tick Margin if needed. This is a timing hint, not an automatic miner.
- **Pickaxe Ability** estimates ability cooldowns and durations with movable HUDs and alerts; **Will-o-Wisp** tracks placement time and radius; **Littlefoot Detection** alerts once per Mineshaft.

### Other categories

- **Fishing:** AutoFish with optional Slug Fish timing and FB Walk movement, Golden Fish handling, Auto Breath, Puddle Jumper, and optional Auto Kill weapon switching.
- **Diana:** shovel-targeting help and a movable mob health/hit display with an optional chat summary.
- **General:** AutoExperiment, Auto Clicker, Chat Macros, Click Through Players, Focus Player, and Sign Enter Key.
- **Render:** damage-number styling, Garden Pest ESP, dropped-item scaling by rarity, and item animation controls.

## Commands

| Command | Purpose |
| --- | --- |
| `/rataddon` | Open settings. |
| `/rataddon hud` | Edit HUD positions and scale. |
| `/rataddon macros` | Open the Chat Macros manager. |
| `/rataddon shaft share` / `corpses` | Manually share the shaft or corpse coordinates. |
| `/rataddon autoclicker add\|remove\|clear left\|right` | Manage Auto Clicker item lists. |

## Online services

RatAddons checks a release manifest when joining a server and shows a link if a newer build exists; it does not install updates automatically. The Hypixel join-banner client listens to `presence.rattokens.asia`; reporting your own join requires a separately configured token. These services are separate from the Minecraft server connection.

## Build and license

With JDK 25, run `./gradlew clean build` or `gradlew.bat clean build` on Windows. The mod JAR is `build/libs/RatAddons-2.0.1-26.3.jar`; the `-sources.jar` is for source browsing.

The combined 2.0.1 mod is distributed under [GPL-3.0](LICENSE). The earlier CC0 text for RatAddons-origin code is preserved in [LICENSE-CC0-ORIGINAL](LICENSE-CC0-ORIGINAL). Notices and original licenses for adapted [quoi](https://github.com/jcnlk/quoi) and [PingOffsetMiner 26.1](https://github.com/Revvilon/PingOffsetMiner/tree/26.1) code are packed into the JAR.

See [CHANGELOG.md](CHANGELOG.md) for release history.
