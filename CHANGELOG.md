# Changelog

## 2.0.1 (Minecraft 26.3) - 2026-10-28

### Added

- Added Pickaxe Ability cooldown and duration HUDs, with countdown and ready popups. The cooldown estimate uses a fixed 0.9 multiplier instead of detecting the active pet.
- Added Will-o-Wisp placement timing, reminders, and configurable radius rendering, plus Littlefoot detection and alerts in Mineshafts.
- Added server-hosted, per-player Hypixel join images. The bundled image appears immediately and is replaced when the remote PNG loads; new player images can be added without rebuilding the mod.

### Changed

- Updated the Minecraft and Fabric API dependencies for 26.3 while retaining the 26.2 release on its existing branch.
- Adapted SDL keyboard and mouse input, world-text rendering, particle packet accessors, and rendering Mixins to the 26.3 APIs.
- Split Mineshaft Mob ESP into separate Glacite Bowman, Caver, Mage, Littlefoot, and Mutt controls with individual colors.
- Extended Ping Offset Miner to stained glass and panes, added outline-width control, and moved its options and Mineshaft ESP options into their own feature pages.
- Restored Pearl Waypoints' Show All option. The settings sidebar now lists categories only; right-click a feature to open its options page.

### Release notes

- Requires Minecraft 26.3 and Java 25. The presence server supports UUID-specific PNGs and a default image; newly registered players require a server restart, while image changes do not.
- The offline build and presence-server tests pass. New gameplay and image-rendering paths have not been verified in Minecraft.

## 2.0.0 (Minecraft 26.2) - 2026-10-26

### Added

- Added a Slug Fish switch to AutoFish. Normal bite catches wait until ten seconds after the hook enters water or lava before reeling in.
- Added Kuudra reward-chest profit estimates and a movable session/lifetime Profit Tracker HUD, using market prices where available.
- Added the Hypixel join banner and update notifications backed by the RatAddons online services.
- Added Mineshaft ESP, No Gemstone Desync, and Ping Offset Miner under the renamed Mining category.

### Changed

- Reworked Kuudra Pearl Waypoints with dynamic flat, sky, and double-pearl solutions; KIC-style red/green block or circle aim markers, throw timers, sound, and per-style controls. Existing triggerbot and aim assist use the new personal aim point, and the previous early offset is migrated to Pearl Delay.
- Show only Pearl Waypoints for the current area.
- Made Auto Sneak in Build toggle when the bound Sneak key is pressed.
- Reworked Ping Offset Miner around the 26.1 reference's mining-tick clock, tool and Tab-stat detection, supported-area and block rules, precision particles, Mining Speed Boost, break-progress display, and RTT sampling. Custom sound and efficiency tracking are omitted.

### Removed

- Removed Auto Leap, tentacle highlights and spawn boxes, Rat Keybind, Auto Cannon Close, empty armor-stand culling, and Conditional Boxes.

### Release notes

- Requires Minecraft 26.2 and Java 25. The mod package now uses GPL-3.0; the previous CC0 text for RatAddons-origin code is retained separately.
- Gameplay behavior still requires verification in Minecraft.

## 1.5.2 (Minecraft 26.2) - 2026-10-24

### Added

- Added IQ-style Fresh Countdown with a movable personal HUD and ten-second timers above fresh players.
- Added centralized Kuudra Notifications for build, supplies, Ichor, Cannonball, no-pre, SOS, and phase changes, with individual toggles and an optional sound.
- Added global search to the settings screen; results open the matching category and option.

### Changed

- Replaced the separate Phase Display with Kuudra Notifications while retaining its existing settings and HUD position.
- Changed Rend Damage to use boss health changes, IQ's damage thresholds and colors, boss-phase timing, and a final check when Kuudra dies.
- Start Kuudra supply times at Elle's actual run-start message and avoid showing fabricated times if that message was missed.
- Check `NO PRE` when supply carriers appear, allowing up to 250 ms for a partial spawn instead of always waiting two seconds.
- Redesigned the settings screen with a fixed cyan theme, nested category navigation, and responsive option cards and sliders. The interface has no theme-color switch; feature-specific color settings remain available.

### Fixed

- Hide the matching vanilla Kuudra damage title during HUD rendering so it does not reappear after packet handling.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- The offline Gradle build and 384 Auto Leap regression checks pass. The development client loaded RatAddons and entered a singleplayer world; Kuudra gameplay behavior has not been verified in game.

## 1.5.0 (Minecraft 26.2) - 2026-10-21

### Added

- Added dropped-item scaling by SkyBlock rarity under Render → Dropped Items, with separate scale sliders for 11 rarities.

### Changed

- Reworked Auto Leap around Quoi's leap flow: packet-backed Spirit Leap menus, queued requests, target suggestions, phase and stage triggers, input blocking, Fast mode, slot restoration, and presets.
- Limited Storm's automatic armor swap on bow release to Death Bow, restricted its area check to Y 164–170, and added a switch for closing the loadout after selection.
- Check for a missing own pre two seconds after the first Kuudra supply giant appears, so `NO PRE` can show earlier.
- Applied Baity's bot predicate to Click Through Players and Focus Player so matching NPCs remain visible and targetable.

### Fixed

- Fixed Focus Player so visible players also show their name tags.

### Removed

- Removed Storm's automatic swap-back armor step and its settings.
- Removed the Action Bar settings category, parser, and its HUD widgets while retaining other HUD positions.
- Removed the player-login banner, its token and server settings, and the associated presence server.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- `clean build --offline` passes, including 384 Auto Leap regression checks. Gameplay behavior has not been verified in game.

## 1.4.0 (Minecraft 26.2) - 2026-10-15

### Added

- Added a Slayer settings page with boss and miniboss highlights, spawn alerts, the custom warning sound, and optional spider, wolf, and blaze sound muting.
- Added a movable Diana mob health and hit-count HUD with an optional death summary in chat.
- Added a movable Arrow Tracker HUD that reads quiver count and arrow type, with an option to show it only while holding a bow.
- Added Kuudra phase, on-body health, and chest-count displays. Kuudra health can hide the matching vanilla HP title; the chest counter tracks earned and opened reward chests up to 60.

### Changed

- Colored selected Kuudra and command feedback messages so names, values, and warnings are easier to distinguish.
- Restored Kitty-style supply marker colors and made the supply ring follow the selected beacon color.
- Show `NO PRE` briefly once when your own pre is confirmed empty.
- Stopped Shaft Announce from automatically sending corpse coordinates; `/rataddon shaft corpses` remains available for manual sharing.

### Fixed

- Prevented Slayer Boss Spawn from repeating when quest information briefly disappears or the boss entity is matched again. The alert now resets with a new quest lifecycle.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- The offline Gradle build passes. Gameplay behavior has not been verified in game.

## 1.3.0 (Minecraft 26.2) - 2026-10-12

### Added

- Added a movable Supply Times HUD that shows teammates' supply pickup counts and times from Kuudra chat messages.

### Changed

- Gave every feature button in the `/rataddon` settings screen a consistent layout.
- Updated Kuudra supply recognition and ESP to follow IQ's supply detection and cyan coloring. Pearl landing markers are cyan and aim markers are blue; safe-spot markers now obey depth testing.
- Reduced the automatic Shaft Announce delay from 120 to 40 ticks after entering a mineshaft.

### Fixed

- Fixed stale or incorrect Kuudra supply beacons and Crate Priority waypoints, and restored the supply pickup HUD.
- Fixed Supply Swap so it can trigger repeatedly for the same supply. The pearl stays selected until the server switches back to the supply slot.
- Reset Kuudra phase state on world changes and handled Kuudra chat messages as packets arrive.
- Stopped Mineshaft Corpse ESP from continuing to outline corpses after they are looted.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- `clean build --offline` passes. Kuudra and Mineshaft gameplay behavior has not been verified in game.

## 1.2.2 (Minecraft 26.2) - 2026-10-10

### Added

- Restored Mineshaft Shaft Announce. It reads the shaft ID from visible scoreboard lines, announces the shaft and corpse summary after 120 ticks, and sends discovered corpse coordinates to party chat one second apart. Use `/rataddon shaft share` or `/rataddon shaft corpses` to send either report manually.
- Added separate Shaft Announce and Corpse ESP switches to the Mineshaft settings page.

### Changed

- Replaced the Mineshaft corpse text markers with XJA-style entity outlines. Corpse recognition uses helmet display names for Lapis, Tungsten, Umber, and Vanguard, with a named-corpse fallback.
- Updated Mineshaft detection to read Tab's `Area:` or `Dungeon:` text and visible scoreboard team prefixes and suffixes, matching XJA's source of area and shaft information.
- Aligned Kuudra box fill, outline, supply markers, safe spots, and crate previews more closely with Kitty's rendering and update logic.

### Fixed

- Reduced Kuudra safe-spot drawing to two geometry submissions per frame and removed repeated crate-preview texture lookups and line-rendering allocations.
- Cleared corpse outline targets when entities are removed or the world changes.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- `clean build --offline` passes. Mineshaft and Kuudra gameplay behavior has not been verified in game.

## 1.2.1 (Minecraft 26.2) - 2026-10-09

### Added

- Added Dungeon Auto Leap with Quoi-style name or class targets, manual leap shortcuts, phase and position triggers, input blocking, Fast mode, swap back, and saved presets. Open its settings under **Dungeon → Auto Leap**, or use `/rataddon leap <player or class>`.
- Added a location manager that identifies the current SkyBlock area from Tab information.
- Added **Swap back armor** to the Storm Death Bow sequence, with a separate loadout slot and click delay.

### Changed

- Reworked the `/rataddon` settings layout with evenly sized columns, a single-column layout on narrow windows, and scrolling for short screens.
- Split imported Kuudra settings into separate feature entries matching Kitty's names and parameter groups.
- Simplified Storm settings by removing automatic tracking and walking, automatic Storm hits, automatic sneak at yellow, and the related click and swap controls.
- Set the Storm Last Breath release-time range to 31–33 seconds.
- Updated Kuudra Pearl and Etherwarp aim checks and waypoint handling, and adjusted Fireball helper timing and rotation.
- Replaced Mineshaft Corpse ESP with SkyblockWidget-style helmet-ID detection, name and distance markers, and looted-corpse tracking.

### Removed

- Removed Mineshaft Shaft Announce and its settings.

### Fixed

- Matched Kitty's Kuudra waypoint and ESP rendering: corrected world-text color and lighting, registered through-wall line and fill pipelines, and restored filled markers and beacon waypoints.
- Fixed a Minecraft 26.2 block-interaction Mixin signature mismatch.
- Fixed Auto Leap settings initialization when loading older configuration files.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- The offline Gradle build passes. Dungeon and Kuudra gameplay behavior has not been verified in game.

## 1.2.0 (Minecraft 26.2) - 2026-10-08

### Added

- Added Chat Macros to General. Match server chat with regular expressions, manage and test macros in the editor, and open it with `/rataddon macros`.
- Added Click Through Players to General, with an optional Dungeon/Kuudra restriction and distance-based player transparency.
- Added M7 Relics pickup and cauldron triggerbots, plus relic spawn boxes.
- Added Kuudra phase alerts, Auto GFS, boss display, backbone alerts, safe spots, and interaction fixes.
- Added Kuudra supply markers and alerts, build helpers, crate priority and previews, pearl and Etherwarp waypoints, stun helpers, and Fireball helpers.
- Added movable HUD positions for the new Kuudra overlays.
- Added Auto Clicker and Tweaks settings for Minecraft 26.2, including the skull and block-related item animations.
- Added Kuudra tentacle detection, spawn boxes, and configurable conditional boxes.
- Added Supply Swap to switch to an Ender Pearl after using Elle's Supplies.

### Changed

- Improved Auto Clicker state handling after item changes while the attack key is held.
- Renamed the imported feature group to **Kuudra Features** and kept individual feature names in the settings UI.
- Replaced the nonworking Supply Pearl behavior with Supply Swap and migrated its saved setting.

### Removed

- Removed the Diana mob hit counter.
- Removed Kuudra Performance, Real Tracking, Supply Pull Helper, and the Throw Bone alert from Kuudra Distance.
- Removed the standalone Auto Warp feature.

### Release notes

- Requires Minecraft 26.2 and Java 25.
- The Gradle build passes. The development client starts; in-game behavior remains unverified.

## 1.1.0 (Minecraft 26.2) - 2026-09-29

### Changed

- Updated Minecraft support from 26.1.2 to 26.2.
- Updated Fabric API to 0.158.0+26.2 and Fabric Loom to 1.18.
- Migrated screen access, entity and block constants, and world overlays to the Minecraft 26.2 APIs.
- Updated hand animation, scoreboard HUD, and held-item tint Mixins for renamed 26.2 render targets.

## 1.1.0 - 2026-09-28

### Added

- Expanded Rend Macro with direction-aware rotation, movement, Hollow Wand, Raging Wind, rod, bone, Halberd, loadout, Ice Spray, and supply-pull automation.
- Added Real Tracking for thrown Bonemerangs, including the Bone Broke alert.
- Added Kuudra Distance rendering with configurable green range and Throw Bone alert.
- Added Supply Pull Helper, Supply Pearl, Auto Fire Veil, Auto Sneak in Build, and Auto Cannon Close.
- Added Auto Croesus with configurable click delay, automatic run selection, chest opening, and missing-key alerts.
- Added Rend Damage attribution and timing output.
- Added screenshot clipboard copying, visible rendering for invisible players, and Enter-to-confirm sign editing.
- Added `/rataddon hud` for opening the HUD editor directly.

### Changed

- Title-style alerts now use the movable and scalable HUD system.
- Matched Rend Macro direction rotation and Kuudra helper behavior to the reference implementation.
- Moved all imported Kuudra helpers into the main `feature` package and removed their temporary testing names.
- Improved Kuudra phase detection, Real Tracking, and supply helper behavior.
- Updated the production JAR selection used by GitHub Actions.

### Fixed

- Fixed Auto Sneak in Build continuing to hold the sneak key after the feature was disabled or stopped controlling sneak.

### Upgrade notes

- Settings for Kuudra helpers from development builds that used the old temporary configuration keys must be configured again.
- This release targets Minecraft 26.1.2, Fabric Loader 0.19.5 or newer, and Java 25.
