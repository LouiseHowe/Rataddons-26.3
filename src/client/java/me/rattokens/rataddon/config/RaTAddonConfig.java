package me.rattokens.rataddon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.feature.SkyblockItemRarity;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public final class RaTAddonConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("rataddon.json");

	public boolean slayerMinibossAlert = false;
	public boolean slayerBossSpawnAlert = false;
	public boolean slayerBossSpawnSound = true;
	public boolean autoFish = false;
	public boolean slugFish = false;
	public boolean fbWalk = false;
	public int fbWalkMinSeconds = 30;
	public int fbWalkMaxSeconds = 60;
	public int fishMaxWaitTime = 60;
	public int fishMinWaitTime = 0;
	public boolean entityCheck = false;
	public boolean goldenFish = false;
	public boolean autoBreath = false;
	public boolean puddleJumper = false;
	public boolean autoKill = false;
	public int fishingRodHotbarSlot = 1;
	public int autoKillHotbarSlot = 2;
	public int autoKillSwitchTicks = 1;
	public int autoKillReturnTicks = 4;
	public boolean hypeWipe = false;
	public int hypeHitCount = 1;
	public boolean hideIceSprayParticles = false;
	public boolean iceHelper = false;
	public boolean shortDamageNumbers = false;
	public boolean compactDamageStyle = false;
	public int shortDamageNumbersColor = 5;
	public boolean gardenPestEsp = false;
	public boolean gardenPestLine = false;
	public int gardenPestEspColor = 0;
	public boolean dianaFeet = false;
	public boolean dianaMobHits = false;
	public boolean dianaMobHitsChatSummary = true;
	public boolean corpseEsp = false;
	public boolean mineshaftEsp = false;
	public boolean mineshaftCorpseWaypoints = true;
	public boolean mineshaftHideLootedCorpses = true;
	public boolean mineshaftFossilEsp = true;
	public boolean mineshaftMobEsp = true;
	public Map<String, Boolean> mineshaftMobs = new LinkedHashMap<>();
	public Map<String, Integer> mineshaftMobColors = new LinkedHashMap<>();
	public boolean littlefootDetection = false;
	public boolean littlefootPopup = true;
	public boolean littlefootMessage = true;
	public int littlefootPopupFadeIn = 10;
	public int littlefootPopupStay = 60;
	public int littlefootPopupFadeOut = 10;
	public String littlefootPopupText = "Littlefoot!";
	public int littlefootPopupColor = 5;
	public boolean pickaxeAbility = false;
	public boolean pickaxeAbilityDuration = false;
	public boolean pickaxeAbilityNoPopup = false;
	public boolean pickaxePopupSound = true;
	public String pickaxePopupCountdownText = "{seconds}";
	public String pickaxePopupReadyText = "Ability Ready!";
	public int pickaxePopupCountdownColor = 5;
	public int pickaxePopupReadyColor = 4;
	public int pickaxePopupStayTicks = 30;
	public boolean willOWisp = false;
	public boolean wispHud = true;
	public boolean wispPopup = true;
	public boolean wispPopupSound = true;
	public int wispRadiusMode = 0;
	public int wispRenderMode = 3;
	public int wispMaxMarkers = 64;
	public String wispPopupCountdownText = "WISP {seconds}";
	public String wispPopupFinalText = "Place!";
	public int wispPopupCountdownColor = 5;
	public int wispPopupFinalColor = 4;
	public int wispPopupStayTicks = 30;
	public boolean noGemstoneDesync = false;
	public boolean pingOffsetMiner = false;
	public int pingOffsetTickMargin = 0;
	public boolean pingOffsetOutline = true;
	public int pingOffsetOutlineWidth = 1;
	public boolean pingOffsetHighlight = true;
	public boolean pingOffsetProgress = true;
	public boolean pingOffsetPrecisionMining = true;
	public boolean pingOffsetTickHud = true;
	public boolean pingOffsetProfilerHud = false;
	public boolean pingOffsetCustomStats = false;
	public int pingOffsetCustomSpeed = 6000;
	public int pingOffsetCustomPing = 100;
	public int pingOffsetCustomTps = 20;
	public boolean pingOffsetMsbOnly = false;
	public int pingOffsetExtraGemstoneSpeed = 855;
	public int pingOffsetExtraMetalSpeed = 0;
	public int pingOffsetExtraOreSpeed = 0;
	public Map<String, Boolean> pingOffsetBlocks = new LinkedHashMap<>();
	public Map<String, Boolean> pingOffsetIslands = new LinkedHashMap<>();
	public boolean shaftAnnounce = false;
	public String shaftAnnounceMessage = "/pc Entered a {type} mineshaft ({id}). Corpses: {corpses}.";
	public String shaftAnnounceTrigger = "";
	public boolean autoExperiment = true;
	public boolean focusPlayer = false;
	public boolean droppedItemScale = false;
	public boolean droppedItemRarityScale = true;
	public Map<String, Double> droppedItemRarityScales = new LinkedHashMap<>();
	public boolean signEnterKey = false;
	public boolean chatMacros = false;
	public volatile String hypixelJoinServerUrl = "https://presence.rattokens.asia";
	public volatile String hypixelJoinToken = "";
	public boolean clickThroughPlayers = false;
	public boolean clickThroughDungeonKuudraOnly = true;
	public boolean clickThroughPlayerAlpha = false;
	public boolean iceSprayChat = false;
	public boolean iceAnnounceDungeonOnly = true;
	public boolean iceAnnouncePartyChat = false;
	@SerializedName(value = "hudSettings", alternate = {"actionbarHuds"})
	public Map<String, HudSetting> hudSettings = new LinkedHashMap<>();

	public boolean stormEnabled = false;
	public boolean kittyRelics = false;
	public boolean kittyRelicTriggerbot = false;
	public boolean kittyCauldronTriggerbot = false;
	public boolean kittyRelicSpawnBoxes = false;
	public boolean stormBowTint = false;
	public boolean stormAutoSwapCritItem = false;
	public int stormSwapDelay = 0;
	public int stormSwapSlot = 1;
	public boolean stormAutoSwapArmor = false;
	public int stormClickDelay = 1;
	public int stormLoadoutSlot = 1;
	public boolean stormCloseLoadout = true;
	public boolean stormAutoReleaseLB = false;
	public double stormReleaseTime = 32.0;
	public double stormWaypointOffset = 0.0;
	public boolean stormAutoSwapTerm = false;
	public boolean kuudraHitbox = false;
	public int kuudraHitboxColor = 1;
	public int kuudraHitboxWidth = 3;

	public boolean rendMacro = false;
	public boolean rendAutoSneak = false;
	public boolean rendAutoRotate = false;
	public double rendYawOffsetMin = 0.0;
	public double rendYawOffsetMax = 0.0;
	public double rendPitchOffsetMin = 0.0;
	public double rendPitchOffsetMax = 0.0;
	public double rendFrontMin = 85.0;
	public double rendFrontMax = 145.0;
	public double rendRightMin = 235.0;
	public double rendRightMax = 295.0;
	public double rendLeftMin = 235.0;
	public double rendLeftMax = 295.0;
	public double rendBackMin = 285.0;
	public double rendBackMax = 345.0;
	public int rendDelay = 5;
	public boolean rendAutoWalk = false;
	public boolean rendAutoJump = false;
	public boolean rendAutoHollowWand = false;
	public int rendFirstClickDelay = 1;
	public int rendSecondClickDelay = 1;
	public boolean rendRagingWind = false;
	public boolean rendAutoRod = false;
	public double rendOffsetRight = 0.0;
	public double rendOffsetLeft = 0.0;
	public double rendOffsetBack = 0.0;
	public double rendOffsetFront = 0.0;
	public boolean rendRenderArea = false;
	public boolean rendAutoBone = false;
	public int rendBoneDelay = 6;
	public boolean rendAutoHalberd = false;
	public boolean rendAutoLoadout = false;
	public int rendLoadoutSlot = 1;
	public int rendClickDelay = 1;
	public boolean rendUseIceSpray = false;
	public boolean rendAutoPull = false;
	public int rendPullItemSlot = 1;
	public int rendPullDelay = 1;
	public boolean rendDebug = false;

	// Additional Kuudra helpers.
	public double boneMinDistance = 12.0;
	public double boneMaxDistance = 14.0;
	public boolean kuudraDistance = false;
	public boolean autoSneakBuild = false;
	@SerializedName(value = "supplySwap", alternate = {"supplyPearl"})
	public boolean supplySwap = false;
	public boolean autoCroesus = false;
	public int croesusClickDelay = 7;
	public boolean kittyKuudraFixes = false;
	public boolean kittyHollowFix = false;
	public boolean kittyCancelConduitPlacement = false;
	public boolean kittyCancelLavaWarp = false;
	public boolean kittyNoSkyblockMenu = false;
	public boolean kittyClickThroughGiants = false;
	public boolean kittyCancelPearlInteract = false;
	public boolean animationEnabled = false;
	public boolean autoClicker = false;
	public boolean autoClickBreakBlocks = false;
	public boolean autoClickStopOnSwap = false;
	public boolean autoClickWhitelistOnly = false;
	public boolean autoClickBlockDungeonBreaker = true;
	public boolean autoClickLeft = false;
	public int autoClickLeftMinCps = 10;
	public int autoClickLeftMaxCps = 12;
	public boolean autoClickRight = false;
	public int autoClickRightMinCps = 10;
	public int autoClickRightMaxCps = 12;
	public List<String> autoClickLeftItems = new ArrayList<>();
	public List<String> autoClickRightItems = new ArrayList<>();
	public boolean animDisableSwapMain = true;
	public boolean animDisableSwapOff = true;
	public boolean animBlockingAnimation = true;
	public int animBlockingMode = 1;
	public int animSwingMode = 0;
	public boolean animOnlyWeapon = true;
	public boolean animModifySwingDuration = false;
	public int animSwingDuration = 6;
	public boolean animSwingWhileUsing = true;
	public boolean animOnlyOnBlock = true;

	public HudSetting hud(String key) {
		if (hudSettings == null) {
			hudSettings = new LinkedHashMap<>();
		}
		return hudSettings.computeIfAbsent(key, ignored -> new HudSetting());
	}

	public double rarityScale(SkyblockItemRarity rarity) {
		if (rarity == SkyblockItemRarity.UNKNOWN) return 1.0;
		if (droppedItemRarityScales == null) droppedItemRarityScales = new LinkedHashMap<>();
		Double value = droppedItemRarityScales.get(rarity.name());
		return value == null || !Double.isFinite(value) ? rarity.defaultScale : Math.clamp(value, 1.0, 3.5);
	}

	public void setRarityScale(SkyblockItemRarity rarity, double value) {
		if (rarity == SkyblockItemRarity.UNKNOWN || !Double.isFinite(value)) return;
		if (droppedItemRarityScales == null) droppedItemRarityScales = new LinkedHashMap<>();
		droppedItemRarityScales.put(rarity.name(), Math.clamp(value, 1.0, 3.5));
	}

	public static final class HudSetting {
		public boolean hide = false;
		public float x = -1.0F;
		public float y = -1.0F;
		public float scale = 1.0F;
	}

	public static RaTAddonConfig load() {
		if (Files.notExists(PATH)) {
			RaTAddonConfig config = new RaTAddonConfig();
			config.save();
			return config;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			JsonObject saved = JsonParser.parseReader(reader).getAsJsonObject();
			RaTAddonConfig config = GSON.fromJson(saved, RaTAddonConfig.class);
			if (config == null) {
				config = new RaTAddonConfig();
			}
			config.sanitize();
			return config;
		} catch (Exception exception) {
			RaTAddonClient.LOGGER.error("Failed to load {}", PATH, exception);
			return new RaTAddonConfig();
		}
	}

	public void save() {
		sanitize();
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			RaTAddonClient.LOGGER.error("Failed to save {}", PATH, exception);
		}
	}

	private void sanitize() {
		if (mineshaftMobs == null) mineshaftMobs = new LinkedHashMap<>();
		mineshaftMobs.replaceAll((key, enabled) -> enabled == null || enabled);
		if (mineshaftMobColors == null) mineshaftMobColors = new LinkedHashMap<>();
		mineshaftMobColors.replaceAll((key, color) -> Math.clamp(color == null ? 5 : color, 0, 8));
		littlefootPopupFadeIn = Math.clamp(littlefootPopupFadeIn, 0, 100);
		littlefootPopupStay = Math.clamp(littlefootPopupStay, 0, 200);
		littlefootPopupFadeOut = Math.clamp(littlefootPopupFadeOut, 0, 100);
		littlefootPopupColor = Math.clamp(littlefootPopupColor, 0, 8);
		if (littlefootPopupText == null) littlefootPopupText = "Littlefoot!";
		if (pickaxePopupCountdownText == null) pickaxePopupCountdownText = "{seconds}";
		if (pickaxePopupReadyText == null) pickaxePopupReadyText = "Ability Ready!";
		if (wispPopupCountdownText == null) wispPopupCountdownText = "WISP {seconds}";
		if (wispPopupFinalText == null) wispPopupFinalText = "Place!";
		pickaxePopupCountdownColor = Math.clamp(pickaxePopupCountdownColor, 0, 8);
		pickaxePopupReadyColor = Math.clamp(pickaxePopupReadyColor, 0, 8);
		pickaxePopupStayTicks = Math.clamp(pickaxePopupStayTicks, 1, 200);
		wispPopupCountdownColor = Math.clamp(wispPopupCountdownColor, 0, 8);
		wispPopupFinalColor = Math.clamp(wispPopupFinalColor, 0, 8);
		wispPopupStayTicks = Math.clamp(wispPopupStayTicks, 1, 200);
		wispRadiusMode = Math.clamp(wispRadiusMode, 0, 2);
		wispRenderMode = Math.clamp(wispRenderMode, 0, 3);
		wispMaxMarkers = Math.clamp(wispMaxMarkers, 32, 512);
		if (pingOffsetBlocks == null) pingOffsetBlocks = new LinkedHashMap<>();
		if (pingOffsetIslands == null) pingOffsetIslands = new LinkedHashMap<>();
		pingOffsetTickMargin = Math.clamp(pingOffsetTickMargin, -10, 10);
		pingOffsetOutlineWidth = Math.clamp(pingOffsetOutlineWidth, 1, 10);
		pingOffsetCustomSpeed = Math.clamp(pingOffsetCustomSpeed, 1, 20000);
		pingOffsetCustomPing = Math.clamp(pingOffsetCustomPing, 0, 1000);
		pingOffsetCustomTps = Math.clamp(pingOffsetCustomTps, 1, 20);
		pingOffsetExtraGemstoneSpeed = Math.clamp(pingOffsetExtraGemstoneSpeed, 0, 5000);
		pingOffsetExtraMetalSpeed = Math.clamp(pingOffsetExtraMetalSpeed, 0, 5000);
		pingOffsetExtraOreSpeed = Math.clamp(pingOffsetExtraOreSpeed, 0, 5000);
		if (droppedItemRarityScales == null) droppedItemRarityScales = new LinkedHashMap<>();
		for (SkyblockItemRarity rarity : SkyblockItemRarity.values()) {
			if (rarity != SkyblockItemRarity.UNKNOWN && droppedItemRarityScales.containsKey(rarity.name()))
				setRarityScale(rarity, rarityScale(rarity));
		}
		fishMaxWaitTime = Math.clamp(fishMaxWaitTime, 30, 120);
		fbWalkMinSeconds = Math.clamp(fbWalkMinSeconds, 1, 300);
		fbWalkMaxSeconds = Math.clamp(fbWalkMaxSeconds, fbWalkMinSeconds, 300);
		fishMinWaitTime = Math.clamp(fishMinWaitTime, 0, 20);
		fishingRodHotbarSlot = Math.clamp(fishingRodHotbarSlot, 1, 9);
		autoKillHotbarSlot = Math.clamp(autoKillHotbarSlot, 1, 9);
		autoKillSwitchTicks = Math.clamp(autoKillSwitchTicks, 0, 10);
		autoKillReturnTicks = Math.clamp(autoKillReturnTicks, 1, 12);
		hypeHitCount = Math.clamp(hypeHitCount, 1, 10);
		shortDamageNumbersColor = Math.clamp(shortDamageNumbersColor, 0, 8);
		gardenPestEspColor = Math.clamp(gardenPestEspColor, 0, 8);
		if (hudSettings == null) {
			hudSettings = new LinkedHashMap<>();
		}
		// Drop retired Action Bar and Kuudra widget positions.
		for (String key : List.of("HEALTH", "VITALITY", "DEFENSE", "MANA", "MANA_USE", "TRUE_DEFENSE",
			"SKILL_XP", "SECRETS", "TERM_LASER", "DRILL_FUEL", "ARMOR_STACKS", "RIFT_TIME", "GECKO_COMBO",
			"ESSENCE_GAINED", "RAG_AXE_TIMER", "AURORA_STAFF_RUNE", "SOUL_ESOWARD", "BITS_GAINED", "KUUDRA_DIRECTION", "ANKY_TICK", "kitty_pickup", "kitty_supply_times",
			"kitty_supply_alert", "kitty_giant_alert", "kitty_build", "kitty_build_secondary",
			"kitty_fresh_countdown", "kitty_stun", "kitty_backbone", "kitty_kuudra_alert",
			"kitty_kuudra_hp", "kitty_phase_display", "kitty_chest_count",
			"kitty_crate_priority", "kitty_crate_preview")) hudSettings.remove(key);
		hudSettings.forEach((key, hud) -> hud.scale = Math.clamp(hud.scale, 0.5F,
			"kitty_littlefoot_popup".equals(key) ? 5.0F : 4.0F));
		if (fishMinWaitTime >= fishMaxWaitTime) {
			fishMinWaitTime = Math.max(0, fishMaxWaitTime - 1);
		}
		stormSwapDelay = Math.clamp(stormSwapDelay, 0, 10);
		stormSwapSlot = Math.clamp(stormSwapSlot, 1, 8);
		stormClickDelay = Math.clamp(stormClickDelay, 0, 10);
		stormLoadoutSlot = Math.clamp(stormLoadoutSlot, 1, 12);
		stormReleaseTime = Math.clamp(stormReleaseTime, 31.0, 33.0);
		stormWaypointOffset = Math.clamp(stormWaypointOffset, -2.0, 2.0);
		kuudraHitboxColor = Math.clamp(kuudraHitboxColor, 0, 8);
		kuudraHitboxWidth = Math.clamp(kuudraHitboxWidth, 1, 10);
		rendYawOffsetMin = Math.clamp(rendYawOffsetMin, -10.0, 10.0);
		rendYawOffsetMax = Math.clamp(rendYawOffsetMax, -10.0, 10.0);
		rendPitchOffsetMin = Math.clamp(rendPitchOffsetMin, -10.0, 10.0);
		rendPitchOffsetMax = Math.clamp(rendPitchOffsetMax, -10.0, 10.0);
		rendFrontMin = Math.clamp(rendFrontMin, 50.0, 500.0);
		rendFrontMax = Math.clamp(rendFrontMax, 50.0, 500.0);
		rendRightMin = Math.clamp(rendRightMin, 50.0, 500.0);
		rendRightMax = Math.clamp(rendRightMax, 50.0, 500.0);
		rendLeftMin = Math.clamp(rendLeftMin, 50.0, 500.0);
		rendLeftMax = Math.clamp(rendLeftMax, 50.0, 500.0);
		rendBackMin = Math.clamp(rendBackMin, 50.0, 500.0);
		rendBackMax = Math.clamp(rendBackMax, 50.0, 500.0);
		rendDelay = Math.clamp(rendDelay, 1, 20);
		rendFirstClickDelay = Math.clamp(rendFirstClickDelay, 1, 10);
		rendSecondClickDelay = Math.clamp(rendSecondClickDelay, 1, 10);
		rendOffsetRight = Math.clamp(rendOffsetRight, -2.5, 2.5);
		rendOffsetLeft = Math.clamp(rendOffsetLeft, -2.5, 2.5);
		rendOffsetBack = Math.clamp(rendOffsetBack, -2.5, 2.5);
		rendOffsetFront = Math.clamp(rendOffsetFront, -2.5, 2.5);
		rendBoneDelay = Math.clamp(rendBoneDelay, 1, 20);
		rendLoadoutSlot = Math.clamp(rendLoadoutSlot, 1, 12);
		rendClickDelay = Math.clamp(rendClickDelay, 1, 10);
		rendPullItemSlot = Math.clamp(rendPullItemSlot, 1, 8);
		rendPullDelay = Math.clamp(rendPullDelay, 1, 20);
		boneMinDistance = Math.clamp(boneMinDistance, 0.0, 30.0);
		boneMaxDistance = Math.clamp(boneMaxDistance, 0.0, 30.0);
		croesusClickDelay = Math.clamp(croesusClickDelay, 1, 20);
		animBlockingMode = Math.clamp(animBlockingMode, 0, 5);
		animSwingMode = Math.clamp(animSwingMode, 0, 1);
		animSwingDuration = Math.clamp(animSwingDuration, 0, 20);
		autoClickLeftMinCps = Math.clamp(autoClickLeftMinCps, 1, 20);
		autoClickLeftMaxCps = Math.clamp(autoClickLeftMaxCps, autoClickLeftMinCps, 20);
		autoClickRightMinCps = Math.clamp(autoClickRightMinCps, 1, 20);
		autoClickRightMaxCps = Math.clamp(autoClickRightMaxCps, autoClickRightMinCps, 20);
		if (autoClickLeftItems == null) autoClickLeftItems = new ArrayList<>();
		if (autoClickRightItems == null) autoClickRightItems = new ArrayList<>();
	}
}
