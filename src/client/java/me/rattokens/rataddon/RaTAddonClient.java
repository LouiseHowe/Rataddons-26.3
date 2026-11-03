package me.rattokens.rataddon;

import me.rattokens.rataddon.config.RaTAddonConfig;
import me.rattokens.rataddon.config.RaTAddonConfigScreen;
import me.rattokens.rataddon.feature.Animation;
import me.rattokens.rataddon.feature.AutoClicker;
import me.rattokens.rataddon.feature.ChatMacros;
import me.rattokens.rataddon.feature.ChatMacrosScreen;
import me.rattokens.rataddon.feature.AutoExperiment;
import me.rattokens.rataddon.feature.CorpseEsp;
import me.rattokens.rataddon.feature.MineshaftEsp;
import me.rattokens.rataddon.feature.LittlefootDetection;
import me.rattokens.rataddon.feature.PickaxeAbility;
import me.rattokens.rataddon.feature.WillOWisp;
import me.rattokens.rataddon.feature.PingOffsetMiner;
import me.rattokens.rataddon.feature.DianaMobHits;
import me.rattokens.rataddon.feature.ShaftAnnounce;
import me.rattokens.rataddon.feature.IceSprayTracker;
import me.rattokens.rataddon.feature.HudAlert;
import me.rattokens.rataddon.feature.HypixelJoinAlerts;
import me.rattokens.rataddon.feature.HypixelJoinBanner;
import me.rattokens.rataddon.feature.UpdateChecker;
import me.rattokens.rataddon.feature.HudEditorScreen;
import me.rattokens.rataddon.feature.KuudraExtras;
import me.rattokens.rataddon.feature.KittyRelics;
import me.rattokens.rataddon.feature.AutoFish;
import me.rattokens.rataddon.feature.IceUtils;
import me.rattokens.rataddon.feature.PestEsp;
import me.rattokens.rataddon.feature.RendMacro;
import me.rattokens.rataddon.feature.Storm;
import me.rattokens.rataddon.feature.KuudraAutomation;
import me.rattokens.rataddon.util.BoneTracker;
import me.rattokens.rataddon.util.KuudraState;
import me.rattokens.rataddon.util.LocationManager;
import me.rattokens.rataddon.util.TickScheduler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RaTAddonClient implements ClientModInitializer {
	public static final String MOD_ID = "rataddon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static RaTAddonConfig CONFIG;

	@Override
	public void onInitializeClient() {
		CONFIG = RaTAddonConfig.load();
		ChatMacros.init();
		TickScheduler.init();
		LocationManager.init();
		KuudraState.init();
		HudAlert.init();
		HypixelJoinBanner.init();
		HypixelJoinAlerts.init();
		UpdateChecker.init();
		KuudraExtras.init();
		BoneTracker.init();
		AutoFish.init();
		AutoExperiment.init();
		IceUtils.init();
		PestEsp.init();
		IceSprayTracker.init();
		Storm.init();
		KittyRelics.init();
		RendMacro.init();
		KuudraAutomation.init();
		Animation.init();
		AutoClicker.init();
		CorpseEsp.init();
		MineshaftEsp.init();
		LittlefootDetection.init();
		PickaxeAbility.init();
		WillOWisp.init();
		PingOffsetMiner.init();
		DianaMobHits.init();
		ShaftAnnounce.init();
		registerCommands();
	}

	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal("rataddon")
				.executes(context -> {
					var client = net.minecraft.client.Minecraft.getInstance();
					client.execute(() -> client.gui.setScreen(new RaTAddonConfigScreen(client.gui.screen())));
					return 1;
				})
				.then(ClientCommands.literal("hud").executes(context -> {
					var client = net.minecraft.client.Minecraft.getInstance();
					client.execute(() -> client.gui.setScreen(new HudEditorScreen(client.gui.screen())));
					return 1;
				}))
				.then(ClientCommands.literal("macros").executes(context -> {
					var client = net.minecraft.client.Minecraft.getInstance();
					client.execute(() -> client.gui.setScreen(new ChatMacrosScreen(client.gui.screen())));
					return 1;
				}))
				.then(ClientCommands.literal("shaft")
					.then(ClientCommands.literal("share").executes(context -> {
						ShaftAnnounce.shareShaft();
						return 1;
					}))
					.then(ClientCommands.literal("corpses").executes(context -> {
						ShaftAnnounce.shareAllCorpses();
						return 1;
					})))
				.then(ClientCommands.literal("autoclicker")
					.then(ClientCommands.literal("add")
						.then(ClientCommands.literal("left").executes(ctx -> AutoClicker.editList(true, "add")))
						.then(ClientCommands.literal("right").executes(ctx -> AutoClicker.editList(false, "add"))))
					.then(ClientCommands.literal("remove")
						.then(ClientCommands.literal("left").executes(ctx -> AutoClicker.editList(true, "remove")))
						.then(ClientCommands.literal("right").executes(ctx -> AutoClicker.editList(false, "remove"))))
					.then(ClientCommands.literal("clear")
						.then(ClientCommands.literal("left").executes(ctx -> AutoClicker.editList(true, "clear")))
						.then(ClientCommands.literal("right").executes(ctx -> AutoClicker.editList(false, "clear")))))
			)
		);
	}
}
