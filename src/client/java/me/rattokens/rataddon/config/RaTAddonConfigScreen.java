package me.rattokens.rataddon.config;

import me.rattokens.rataddon.RaTAddonClient;
import me.rattokens.rataddon.feature.Animation;
import me.rattokens.rataddon.feature.AutoFish;
import me.rattokens.rataddon.feature.HudEditorScreen;
import me.rattokens.rataddon.feature.PingOffsetMiner;
import me.rattokens.rataddon.feature.MineshaftEsp;
import me.rattokens.rataddon.feature.WillOWisp;
import me.rattokens.rataddon.feature.ChatMacrosScreen;
import me.rattokens.rataddon.feature.SkyblockItemRarity;
import me.rattokens.rataddon.util.ColorOptions;
import me.rattokens.rataddon.util.LocationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class RaTAddonConfigScreen extends Screen {
	private static final int SIDEBAR_WIDTH = 108;
	private static final int HEADER_HEIGHT = 34;
	private static final int FOOTER_HEIGHT = 28;
	private static final int ROW = 25;
	private static final int SIDEBAR_ROW = 24;
	private static final int ACCENT = 0xFF38D8E7;
	private static final String CHEVRON = "▸";
	private static final Map<Page, Integer> SCROLL = new EnumMap<>(Page.class);
	private static Page lastPage = Page.GENERAL;
	private static int sidebarScroll;

	private final Screen parent;
	private Page page;
	private final List<Placed> content = new ArrayList<>();
	private final List<Label> labels = new ArrayList<>();
	private final List<FeatureRow> features = new ArrayList<>();
	private final List<SearchResultRow> searchResults = new ArrayList<>();
	private final List<SearchEntry> searchIndex = new ArrayList<>();
	private final Map<Page, List<NavSection>> navigation = new EnumMap<>(Page.class);
	private final List<NavEntry> sidebarEntries = new ArrayList<>();
	private EditBox searchBox;
	private String searchQuery = "";
	private String pendingSearchFocus;
	private boolean collectingIndex;
	private boolean inlineBuild;
	private boolean searchRebuildPending;
	private boolean revealSidebarSelection = true;
	private Section section = Section.LIST;
	private int sectionScroll;
	private int contentHeight;
	private int sidebarWidth;
	private int panelX, panelY, panelWidth, panelHeight, contentTop, contentBottom;
	private int left;
	private int right;
	private int columnWidth;
	private int contentWidth;
	private final NavigableMap<Integer, Integer> wrappedRowShifts = new TreeMap<>();

	public RaTAddonConfigScreen(Screen parent) {
		this(parent, lastPage);
	}

	private RaTAddonConfigScreen(Screen parent, Page page) {
		super(Component.literal("RatAddons Settings"));
		this.parent = parent;
		this.page = page;
		lastPage = page;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(400, Math.max(280, width - 24));
		panelHeight = Math.min(280, Math.max(210, height - 24));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		sidebarWidth = SIDEBAR_WIDTH;
		left = panelX + sidebarWidth + 12;
		contentWidth = Math.max(1, panelWidth - sidebarWidth - 24);
		columnWidth = Math.max(1, (contentWidth - 8) / 2);
		right = left + columnWidth + 8;
		contentTop = panelY + 56;
		contentBottom = panelY + panelHeight - 9;
		if (searchIndex.isEmpty()) buildSearchIndex();

		content.clear();
		labels.clear();
		features.clear();
		searchResults.clear();
		contentHeight = 0;
		int searchWidth = Math.min(144, Math.max(80, panelWidth - sidebarWidth - 60));
		searchBox = new EditBox(font, panelX + panelWidth - searchWidth - 2, panelY + 12,
			searchWidth - 10, 9, Component.literal("Search settings"));
		searchBox.setMaxLength(96);
		searchBox.setHint(Component.empty());
		searchBox.setBordered(false);
		searchBox.setTextColor(0xFFE8F9FB);
		searchBox.setValue(searchQuery);
		searchBox.setResponder(value -> {
			searchQuery = value;
			sectionScroll = 0;
			searchRebuildPending = true;
		});
		addRenderableWidget(searchBox);
		buildSidebar();
		int footerButtonWidth = (sidebarWidth - 18) / 2;
		addRenderableWidget(ThemedButton.builder(Component.literal("HUD"), button ->
			minecraft.gui.setScreen(new HudEditorScreen(this)))
			.bounds(panelX + 6, panelY + panelHeight - 23, footerButtonWidth, 18).build());
		addRenderableWidget(ThemedButton.builder(Component.literal("Done"), button -> onClose())
			.bounds(panelX + 12 + footerButtonWidth, panelY + panelHeight - 23, footerButtonWidth, 18).build());

		if (searchQuery.isBlank()) buildVisiblePage();
		else buildSearchResults();
		layout();
		if (pendingSearchFocus != null) {
			focusOption(pendingSearchFocus);
			pendingSearchFocus = null;
		}
	}

	private void buildVisiblePage() {
		if (section == Section.LIST) {
			buildPage(page);
			List<Placed> roots = List.copyOf(content);
			content.clear();
			for (int i = 0; i < roots.size(); i++) {
				Placed root = roots.get(i);
				content.add(new Placed(root.widget, i * ROW, root.x, root.width));
			}
			return;
		}
		Section expanded = section;
		section = Section.LIST;
		buildPage(page);
		section = expanded;
		List<Placed> originalRoots = List.copyOf(content);
		List<Placed> roots = new ArrayList<>();
		for (int i = 0; i < originalRoots.size(); i++) {
			Placed root = originalRoots.get(i);
			roots.add(new Placed(root.widget, i * ROW, root.x, root.width));
		}
		List<FeatureRow> rootFeatures = List.copyOf(features);
		for (FeatureRow feature : rootFeatures) ((ThemedButton) feature.widget).selected(feature.section == expanded);
		int insertionY = -1;
		for (FeatureRow feature : rootFeatures) {
			if (feature.section != expanded) continue;
			for (Placed root : roots) {
				if (root.widget == feature.widget) insertionY = root.y + ROW;
			}
		}
		if (insertionY < 0) {
			section = Section.LIST;
			return;
		}
		content.clear();
		labels.clear();
		features.clear();
		inlineBuild = true;
		try {
			buildPage(page);
		} finally {
			inlineBuild = false;
		}
		List<Placed> children = List.copyOf(content);
		List<Label> childLabels = List.copyOf(labels);
		int childHeight = children.stream().mapToInt(child -> child.y + child.widget.getHeight()).max().orElse(0);
		int shift = Math.max(ROW, ((childHeight + ROW - 1) / ROW) * ROW + 7);
		content.clear();
		boolean inserted = false;
		for (Placed root : roots) {
			if (!inserted && root.y >= insertionY) {
				for (Placed child : children) content.add(new Placed(child.widget, insertionY + child.y + 5, child.x, child.width, true));
				inserted = true;
			}
			content.add(new Placed(root.widget, root.y + (root.y >= insertionY ? shift : 0), root.x, root.width, false));
		}
		if (!inserted) {
			for (Placed child : children) content.add(new Placed(child.widget, insertionY + child.y + 5, child.x, child.width, true));
		}
		for (Label label : childLabels) labels.add(new Label(label.text, label.x, insertionY + label.y + 5, label.color));
		features.addAll(rootFeatures);
	}

	private void buildPage(Page target) {
		switch (target) {
			case GENERAL -> buildGeneral();
			case DUNGEON -> buildDungeon();
			case FISHING -> buildFishing();
			case DIANA -> buildDiana();
			case SLAYER -> buildSlayer();
			case RENDER -> buildRender();
			case KUUDRA -> buildKuudra();
			case MINING -> buildMining();
		}
	}

	private void buildSearchIndex() {
		Section previous = section;
		collectingIndex = true;
		try {
			for (Page candidate : Page.values()) {
				clearCollected();
				section = Section.LIST;
				buildPage(candidate);
				List<FeatureRow> rootFeatures = List.copyOf(features);
				List<NavSection> sections = new ArrayList<>();
				sections.add(new NavSection(Section.LIST, "Overview"));
				for (Placed placed : content) {
					if (rootFeatures.stream().anyMatch(row -> row.widget == placed.widget)) continue;
					addSearchEntry(candidate, Section.LIST, optionLabel(placed.widget));
				}
				for (FeatureRow row : rootFeatures) {
					String name = optionLabel(row.widget);
					sections.add(new NavSection(row.section, name));
					addSearchEntry(candidate, row.section, name);
				}
				for (NavSection nav : sections) {
					if (nav.section == Section.LIST) continue;
					clearCollected();
					section = nav.section;
					buildPage(candidate);
					for (Placed placed : content) {
						String name = optionLabel(placed.widget);
						if (!name.startsWith("← ")) addSearchEntry(candidate, nav.section, name);
					}
				}
				navigation.put(candidate, List.copyOf(sections));
			}
		} finally {
			collectingIndex = false;
			section = previous;
			clearCollected();
		}
	}

	private void clearCollected() {
		content.clear();
		labels.clear();
		features.clear();
		contentHeight = 0;
	}

	private void addSearchEntry(Page targetPage, Section targetSection, String label) {
		if (label.isBlank() || searchIndex.stream().anyMatch(entry ->
			entry.page == targetPage && entry.section == targetSection && entry.label.equals(label))) return;
		searchIndex.add(new SearchEntry(targetPage, targetSection, label));
	}

	private static String optionLabel(AbstractWidget widget) {
		String text = net.minecraft.ChatFormatting.stripFormatting(widget.getMessage().getString());
		if (text == null) return "";
		int suffix = text.indexOf(": ");
		return (suffix < 0 ? text : text.substring(0, suffix)).trim();
	}

	private void buildSidebar() {
		sidebarEntries.clear();
		for (Page candidate : Page.values()) {
			if (!searchQuery.isBlank() && searchIndex.stream().noneMatch(entry ->
				entry.page == candidate && matchesSearch(entry.label, searchQuery))
				&& !matchesSearch(candidate.label, searchQuery)) continue;
			sidebarEntries.add(new NavEntry(candidate, candidate.label));
		}
		int visible = visibleSidebarTabs();
		sidebarScroll = Math.clamp(sidebarScroll, 0, Math.max(0, sidebarEntries.size() - visible));
		if (revealSidebarSelection) {
			for (int index = 0; index < sidebarEntries.size(); index++) {
				NavEntry entry = sidebarEntries.get(index);
				if (entry.page != page) continue;
				if (index < sidebarScroll) sidebarScroll = index;
				else if (index >= sidebarScroll + visible) sidebarScroll = index - visible + 1;
				break;
			}
			revealSidebarSelection = false;
		}
		for (int index = sidebarScroll; index < Math.min(sidebarEntries.size(), sidebarScroll + visible); index++) {
			NavEntry entry = sidebarEntries.get(index);
			int x = panelX + 1;
			int w = sidebarWidth - 2;
			ThemedButton button = ThemedButton.builder(Component.literal(entry.label), ignored -> navigate(entry.page, Section.LIST, null))
				.bounds(x, panelY + HEADER_HEIGHT + 6 + (index - sidebarScroll) * SIDEBAR_ROW, w, 22).build();
			button.sidebar(true);
			button.selected(entry.page == page);
			addRenderableWidget(button);
		}
	}

	private void buildSearchResults() {
		int y = 0;
		for (SearchEntry entry : searchIndex) {
			if (!matchesSearch(entry.label, searchQuery) && !matchesSearch(entry.page.label, searchQuery)) continue;
			String sectionName = navigation.getOrDefault(entry.page, List.of()).stream()
				.filter(nav -> nav.section == entry.section).map(NavSection::label).findFirst().orElse("Overview");
			String path = "Right-click  ·  " + entry.label + "   ·   " + entry.page.label + " / " + sectionName;
			ThemedButton result = ThemedButton.builder(Component.literal(path), ignored -> {})
				.bounds(left, 0, featureWidth(), 23).build();
			place(result, y);
			searchResults.add(new SearchResultRow(result, entry));
			y += ROW;
		}
		if (content.isEmpty()) {
			ThemedButton empty = ThemedButton.builder(Component.literal("No matching settings"), ignored -> {})
				.bounds(left, 0, featureWidth(), 23).build();
			empty.active = false;
			place(empty, 0);
		}
	}

	private static boolean matchesSearch(String text, String query) {
		return text.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
	}

	private void navigate(Page targetPage, Section targetSection, String option) {
		page = targetPage;
		lastPage = targetPage;
		section = targetSection;
		sectionScroll = 0;
		searchQuery = "";
		pendingSearchFocus = option;
		revealSidebarSelection = true;
		cfg().save();
		rebuildWidgets();
	}

	private void focusOption(String label) {
		for (Placed placed : content) {
			if (!optionLabel(placed.widget).equals(label)) continue;
			setCurrentScroll(Math.max(0, placed.visualY - ROW));
			layout();
			break;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!searchRebuildPending) return;
		int cursor = searchBox == null ? searchQuery.length() : searchBox.getCursorPosition();
		searchRebuildPending = false;
		rebuildWidgets();
		searchBox.setFocused(true);
		setFocused(searchBox);
		searchBox.setCursorPosition(Math.min(cursor, searchQuery.length()));
	}

	private void buildSlayer() {
		if (section == Section.SLAYER_BOSS_SPAWN) {
			int y = sectionHeader("Boss Spawn Alert", () -> cfg().slayerBossSpawnAlert,
				v -> cfg().slayerBossSpawnAlert = v);
			place(toggle(left, "Alert Sound", () -> cfg().slayerBossSpawnSound,
				v -> cfg().slayerBossSpawnSound = v), y);
			return;
		}
		place(toggle(left, featureWidth(), "Miniboss Alert", () -> cfg().slayerMinibossAlert,
			v -> cfg().slayerMinibossAlert = v), 0);
		place(feature("Boss Spawn Alert", () -> cfg().slayerBossSpawnAlert,
			v -> cfg().slayerBossSpawnAlert = v, Section.SLAYER_BOSS_SPAWN), ROW);
	}

	private void buildGeneral() {
		if (section == Section.AUTO_CLICKER) { buildAutoClicker(); return; }
		if (section == Section.CHAT_MACROS) { buildChatMacros(); return; }
		if (section == Section.CLICK_THROUGH_PLAYERS) { buildClickThroughPlayers(); return; }
		place(toggle(left, featureWidth(), "AutoExperiment", () -> cfg().autoExperiment,
			v -> cfg().autoExperiment = v), 0);
		place(toggle(left, "Focus Player", () -> cfg().focusPlayer, v -> cfg().focusPlayer = v), ROW);
		place(toggle(left, "Sign Enter Key", () -> cfg().signEnterKey, v -> cfg().signEnterKey = v), ROW * 2);
		place(feature("Auto Clicker", () -> cfg().autoClicker, v -> cfg().autoClicker = v, Section.AUTO_CLICKER), ROW * 3);
		place(feature("Chat Macros", () -> cfg().chatMacros, v -> cfg().chatMacros = v, Section.CHAT_MACROS), ROW * 4);
		place(feature("Click Through Players", () -> cfg().clickThroughPlayers,
			v -> cfg().clickThroughPlayers = v, Section.CLICK_THROUGH_PLAYERS), ROW * 5);
	}

	private void buildChatMacros() {
		int y = sectionHeader("Chat Macros", () -> cfg().chatMacros, v -> cfg().chatMacros = v);
		place(ThemedButton.builder(Component.literal("Open Macro Manager"), button ->
			minecraft.gui.setScreen(new ChatMacrosScreen(this))).bounds(left, 0, featureWidth(), 20).build(), y);
	}

	private void buildClickThroughPlayers() {
		int y = sectionHeader("Click Through Players", () -> cfg().clickThroughPlayers,
			v -> cfg().clickThroughPlayers = v);
		place(toggle(left, "Only Dungeons and Kuudra", () -> cfg().clickThroughDungeonKuudraOnly,
			v -> cfg().clickThroughDungeonKuudraOnly = v), y);
		place(toggle(right, "Change Player Alpha", () -> cfg().clickThroughPlayerAlpha,
			v -> cfg().clickThroughPlayerAlpha = v), y);
	}

	private void buildAutoClicker() {
		int y = sectionHeader("Auto Clicker", () -> cfg().autoClicker, v -> cfg().autoClicker = v);
		place(toggle(left, "Allow Breaking Blocks", () -> cfg().autoClickBreakBlocks, v -> cfg().autoClickBreakBlocks = v), y);
		place(toggle(right, "Stop On Item Swap", () -> cfg().autoClickStopOnSwap, v -> cfg().autoClickStopOnSwap = v), y);
		y += ROW;
		place(toggle(left, "Whitelist Only", () -> cfg().autoClickWhitelistOnly, v -> cfg().autoClickWhitelistOnly = v), y);
		place(toggle(right, "Block Dungeon Breaker", () -> cfg().autoClickBlockDungeonBreaker, v -> cfg().autoClickBlockDungeonBreaker = v), y);
		y += ROW;
		place(toggle(left, "Enable Left Click", () -> cfg().autoClickLeft, v -> cfg().autoClickLeft = v), y);
		place(toggle(right, "Enable Right Click", () -> cfg().autoClickRight, v -> cfg().autoClickRight = v), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Left CPS min", 1, 20, () -> cfg().autoClickLeftMinCps, v -> cfg().autoClickLeftMinCps = v, ""), y);
		place(new IntegerSlider(right, columnWidth, "Left CPS max", 1, 20, () -> cfg().autoClickLeftMaxCps, v -> cfg().autoClickLeftMaxCps = v, ""), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Right CPS min", 1, 20, () -> cfg().autoClickRightMinCps, v -> cfg().autoClickRightMinCps = v, ""), y);
		place(new IntegerSlider(right, columnWidth, "Right CPS max", 1, 20, () -> cfg().autoClickRightMaxCps, v -> cfg().autoClickRightMaxCps = v, ""), y);
	}

	private void buildDungeon() {
		if (section == Section.ICE_SPRAY) {
			buildIceSpray();
			return;
		}
		if (section == Section.ICE_ANNOUNCE) {
			buildIceAnnounce();
			return;
		}
		if (section == Section.STORM) {
			buildStorm();
			return;
		}
		if (section == Section.RELICS) {
			buildRelics();
			return;
		}
		int y = 0;
		place(feature("Ice Spray", Section.ICE_SPRAY), y);
		y += ROW;
		place(feature("Ice Announce", () -> cfg().iceSprayChat, v -> cfg().iceSprayChat = v,
			Section.ICE_ANNOUNCE), y);
		y += ROW;
		place(feature("Storm", () -> cfg().stormEnabled, v -> cfg().stormEnabled = v, Section.STORM), y);
		y += ROW;
		place(feature("Relics", () -> cfg().kittyRelics, v -> cfg().kittyRelics = v, Section.RELICS), y);
	}

	private void buildRelics() {
		int y = sectionHeader("Relics", () -> cfg().kittyRelics, v -> cfg().kittyRelics = v);
		place(toggle(left, "Relic triggerbot", () -> cfg().kittyRelicTriggerbot,
			v -> cfg().kittyRelicTriggerbot = v), y);
		place(toggle(right, "Cauldron triggerbot", () -> cfg().kittyCauldronTriggerbot,
			v -> cfg().kittyCauldronTriggerbot = v), y);
		y += ROW;
		place(toggle(left, "Render spawn boxes", () -> cfg().kittyRelicSpawnBoxes,
			v -> cfg().kittyRelicSpawnBoxes = v), y);
	}

	private void buildIceSpray() {
		int y = sectionHeader("Ice Spray");
		place(toggle(left, "Hide Ice Spray Particles", () -> cfg().hideIceSprayParticles,
			v -> cfg().hideIceSprayParticles = v), y);
		place(toggle(right, "ICE Helper", () -> cfg().iceHelper, v -> cfg().iceHelper = v), y);
	}

	private void buildIceAnnounce() {
		int y = sectionHeader("Ice Announce", () -> cfg().iceSprayChat, v -> cfg().iceSprayChat = v);
		place(toggle(left, "Only in The Catacombs", () -> cfg().iceAnnounceDungeonOnly,
			v -> cfg().iceAnnounceDungeonOnly = v), y);
		place(toggle(right, "Send Party Chat", () -> cfg().iceAnnouncePartyChat,
			v -> cfg().iceAnnouncePartyChat = v), y);
	}

	private void buildStorm() {
		int y = sectionHeader("Storm", () -> cfg().stormEnabled, v -> cfg().stormEnabled = v);
		place(toggle(left, "Apply tint at max pull", () -> cfg().stormBowTint, v -> cfg().stormBowTint = v), y);
		y += ROW;
		place(toggle(left, "Auto swap crit item", () -> cfg().stormAutoSwapCritItem,
			v -> cfg().stormAutoSwapCritItem = v), y);
		place(new IntegerSlider(right, columnWidth, "Swap delay", 0, 10, () -> cfg().stormSwapDelay,
			v -> cfg().stormSwapDelay = v, " t"), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Item slot", 1, 8, () -> cfg().stormSwapSlot,
			v -> cfg().stormSwapSlot = v, ""), y);
		place(toggle(right, "Auto swap armor", () -> cfg().stormAutoSwapArmor,
			v -> cfg().stormAutoSwapArmor = v), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Click delay", 0, 10, () -> cfg().stormClickDelay,
			v -> cfg().stormClickDelay = v, " t"), y);
		place(new IntegerSlider(right, columnWidth, "Loadout slot", 1, 12, () -> cfg().stormLoadoutSlot,
			v -> cfg().stormLoadoutSlot = v, ""), y);
		y += ROW;
		place(toggle(left, "Close loadout after swap", () -> cfg().stormCloseLoadout,
			v -> cfg().stormCloseLoadout = v), y);
		y += ROW;
		place(toggle(left, "Auto release Last Breath", () -> cfg().stormAutoReleaseLB,
			v -> cfg().stormAutoReleaseLB = v), y);
		place(new DoubleSlider(right, columnWidth, "Release time", 31.0, 33.0, 0.05,
			() -> cfg().stormReleaseTime, v -> cfg().stormReleaseTime = v, " s"), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Waypoint offset", -2.0, 2.0, 0.1,
			() -> cfg().stormWaypointOffset, v -> cfg().stormWaypointOffset = v, ""), y);
		y += ROW;
		place(toggle(left, "Auto swap term in Storm", () -> cfg().stormAutoSwapTerm,
			v -> cfg().stormAutoSwapTerm = v), y);
	}

	private void buildKuudra() {
		if (section == Section.REND) { buildRend(); return; }
		if (section == Section.KUUDRA_SNEAK) { buildKuudraSneak(); return; }
		if (section == Section.KUUDRA_CROESUS) { buildKuudraCroesus(); return; }
		if (section == Section.KUUDRA_SUPPLY_SWAP) { buildKuudraSupplySwap(); return; }
		if (section == Section.KITTY_FIXES) { buildKittyFixes(); return; }
		if (section == Section.KUUDRA_DISTANCE) { buildKuudraDistance(); return; }
		if (section == Section.KUUDRA_HITBOX) { buildKuudraHitbox(); return; }
		int y = 0;
		place(feature("Rend Macro", () -> cfg().rendMacro, v -> cfg().rendMacro = v, Section.REND), y);
		y += ROW;
		place(feature("Auto Sneak in Build", () -> cfg().autoSneakBuild, v -> cfg().autoSneakBuild = v,
			Section.KUUDRA_SNEAK), y);
		y += ROW;
		place(feature("Supply Swap", () -> cfg().supplySwap, v -> cfg().supplySwap = v,
			Section.KUUDRA_SUPPLY_SWAP), y);
		y += ROW;
		place(feature("Auto Croesus", () -> cfg().autoCroesus, v -> cfg().autoCroesus = v,
			Section.KUUDRA_CROESUS), y);
		y += ROW;
		place(feature("Fixes", () -> cfg().kittyKuudraFixes, v -> cfg().kittyKuudraFixes = v,
			Section.KITTY_FIXES), y);
		y += ROW;
		place(feature("Kuudra Distance", () -> cfg().kuudraDistance, v -> cfg().kuudraDistance = v,
			Section.KUUDRA_DISTANCE), y);
		y += ROW;
		place(feature("Kuudra Hitbox", () -> cfg().kuudraHitbox,
			v -> cfg().kuudraHitbox = v, Section.KUUDRA_HITBOX), y);
	}

	private void buildKittyFixes() {
		int y = sectionHeader("Fixes", () -> cfg().kittyKuudraFixes,
			v -> cfg().kittyKuudraFixes = v);
		place(toggle(left, "Hollow wand fix", () -> cfg().kittyHollowFix,
			v -> cfg().kittyHollowFix = v), y);
		y += ROW;
		place(toggle(left, "Cancel conduit place", () -> cfg().kittyCancelConduitPlacement,
			v -> cfg().kittyCancelConduitPlacement = v), y);
		place(toggle(right, "Cancel lava warp", () -> cfg().kittyCancelLavaWarp,
			v -> cfg().kittyCancelLavaWarp = v), y);
		y += ROW;
		place(toggle(left, "No SkyBlock menu", () -> cfg().kittyNoSkyblockMenu,
			v -> cfg().kittyNoSkyblockMenu = v), y);
		place(toggle(right, "Click through giants", () -> cfg().kittyClickThroughGiants,
			v -> cfg().kittyClickThroughGiants = v), y);
		y += ROW;
		place(toggle(left, "Cancel pearl interact", () -> cfg().kittyCancelPearlInteract,
			v -> cfg().kittyCancelPearlInteract = v), y);
	}

	private void addGroupLabel(String label, int y) {
		labels.add(new Label(label, left + 4, y + 4, ACCENT));
	}

	private void buildKuudraDistance() {
		int y = sectionHeader("Kuudra Distance", () -> cfg().kuudraDistance, v -> cfg().kuudraDistance = v);
		place(new DoubleSlider(left, columnWidth, "Green min distance", 0, 30, .5,
			() -> cfg().boneMinDistance, v -> cfg().boneMinDistance = v, "m"), y);
		place(new DoubleSlider(right, columnWidth, "Green max distance", 0, 30, .5,
			() -> cfg().boneMaxDistance, v -> cfg().boneMaxDistance = v, "m"), y);
	}

	private void buildKuudraSneak() {
		int y = sectionHeader("Auto Sneak in Build", () -> cfg().autoSneakBuild, v -> cfg().autoSneakBuild = v);
		addGroupLabel("Press Sneak to toggle during Build", y);
	}

	private void buildKuudraSupplySwap() {
		int y = sectionHeader("Supply Swap", () -> cfg().supplySwap, v -> cfg().supplySwap = v);
		addGroupLabel("Use a hotbar Ender Pearl with Elle's Supplies", y);
	}

	private void buildKuudraCroesus() {
		int y = sectionHeader("Auto Croesus", () -> cfg().autoCroesus, v -> cfg().autoCroesus = v);
		place(new IntegerSlider(left, columnWidth, "Click delay", 1, 20,
			() -> cfg().croesusClickDelay, v -> cfg().croesusClickDelay = v, " t"), y);
	}

	private void buildKuudraHitbox() {
		int y = sectionHeader("Kuudra Hitbox", () -> cfg().kuudraHitbox, v -> cfg().kuudraHitbox = v);
		place(colorButton(left, "Hitbox Color", () -> cfg().kuudraHitboxColor,
			v -> cfg().kuudraHitboxColor = v), y);
		place(new IntegerSlider(right, columnWidth, "Line Width", 1, 10,
			() -> cfg().kuudraHitboxWidth, v -> cfg().kuudraHitboxWidth = v, ""), y);
	}

	private void buildRend() {
		int y = sectionHeader("Rend Macro", () -> cfg().rendMacro, v -> cfg().rendMacro = v);
		place(toggle(left, "Debug", () -> cfg().rendDebug, v -> cfg().rendDebug = v), y);
		y += ROW;
		place(toggle(left, "Auto sneak", () -> cfg().rendAutoSneak, v -> cfg().rendAutoSneak = v), y);
		place(toggle(right, "Auto rotate", () -> cfg().rendAutoRotate, v -> cfg().rendAutoRotate = v), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Yaw offset min", -10.0, 10.0, 0.1,
			() -> cfg().rendYawOffsetMin, v -> cfg().rendYawOffsetMin = v, "°"), y);
		place(new DoubleSlider(right, columnWidth, "Yaw offset max", -10.0, 10.0, 0.1,
			() -> cfg().rendYawOffsetMax, v -> cfg().rendYawOffsetMax = v, "°"), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Pitch offset min", -10.0, 10.0, 0.1,
			() -> cfg().rendPitchOffsetMin, v -> cfg().rendPitchOffsetMin = v, "°"), y);
		place(new DoubleSlider(right, columnWidth, "Pitch offset max", -10.0, 10.0, 0.1,
			() -> cfg().rendPitchOffsetMax, v -> cfg().rendPitchOffsetMax = v, "°"), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Front min", 50.0, 500.0, 1.0,
			() -> cfg().rendFrontMin, v -> cfg().rendFrontMin = v, ""), y);
		place(new DoubleSlider(right, columnWidth, "Front max", 50.0, 500.0, 1.0,
			() -> cfg().rendFrontMax, v -> cfg().rendFrontMax = v, ""), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Right min", 50.0, 500.0, 1.0,
			() -> cfg().rendRightMin, v -> cfg().rendRightMin = v, ""), y);
		place(new DoubleSlider(right, columnWidth, "Right max", 50.0, 500.0, 1.0,
			() -> cfg().rendRightMax, v -> cfg().rendRightMax = v, ""), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Left min", 50.0, 500.0, 1.0,
			() -> cfg().rendLeftMin, v -> cfg().rendLeftMin = v, ""), y);
		place(new DoubleSlider(right, columnWidth, "Left max", 50.0, 500.0, 1.0,
			() -> cfg().rendLeftMax, v -> cfg().rendLeftMax = v, ""), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Back min", 50.0, 500.0, 1.0,
			() -> cfg().rendBackMin, v -> cfg().rendBackMin = v, ""), y);
		place(new DoubleSlider(right, columnWidth, "Back max", 50.0, 500.0, 1.0,
			() -> cfg().rendBackMax, v -> cfg().rendBackMax = v, ""), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Rotation delay", 1, 20, () -> cfg().rendDelay,
			v -> cfg().rendDelay = v, " t"), y);
		place(toggle(right, "Auto walk", () -> cfg().rendAutoWalk, v -> cfg().rendAutoWalk = v), y);
		y += ROW;
		place(toggle(left, "Auto jump", () -> cfg().rendAutoJump, v -> cfg().rendAutoJump = v), y);
		place(toggle(right, "Auto hollow wand", () -> cfg().rendAutoHollowWand,
			v -> cfg().rendAutoHollowWand = v), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "First hollow click", 1, 10, () -> cfg().rendFirstClickDelay,
			v -> cfg().rendFirstClickDelay = v, " t"), y);
		place(new IntegerSlider(right, columnWidth, "Second hollow click", 1, 10,
			() -> cfg().rendSecondClickDelay, v -> cfg().rendSecondClickDelay = v, " t"), y);
		y += ROW;
		place(toggle(left, "Raging wind", () -> cfg().rendRagingWind, v -> cfg().rendRagingWind = v), y);
		place(toggle(right, "Auto rod", () -> cfg().rendAutoRod, v -> cfg().rendAutoRod = v), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Offset right", -2.5, 2.5, 0.1,
			() -> cfg().rendOffsetRight, v -> cfg().rendOffsetRight = v, ""), y);
		place(new DoubleSlider(right, columnWidth, "Offset left", -2.5, 2.5, 0.1,
			() -> cfg().rendOffsetLeft, v -> cfg().rendOffsetLeft = v, ""), y);
		y += ROW;
		place(new DoubleSlider(left, columnWidth, "Offset back", -2.5, 2.5, 0.1,
			() -> cfg().rendOffsetBack, v -> cfg().rendOffsetBack = v, ""), y);
		place(new DoubleSlider(right, columnWidth, "Offset front", -2.5, 2.5, 0.1,
			() -> cfg().rendOffsetFront, v -> cfg().rendOffsetFront = v, ""), y);
		y += ROW;
		place(toggle(left, "Render area", () -> cfg().rendRenderArea, v -> cfg().rendRenderArea = v), y);
		place(toggle(right, "Auto bone", () -> cfg().rendAutoBone, v -> cfg().rendAutoBone = v), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Bone delay", 1, 20, () -> cfg().rendBoneDelay,
			v -> cfg().rendBoneDelay = v, " t"), y);
		place(toggle(right, "Auto halberd", () -> cfg().rendAutoHalberd, v -> cfg().rendAutoHalberd = v), y);
		y += ROW;
		place(toggle(left, "Auto loadout", () -> cfg().rendAutoLoadout, v -> cfg().rendAutoLoadout = v), y);
		place(new IntegerSlider(right, columnWidth, "Loadout slot", 1, 12, () -> cfg().rendLoadoutSlot,
			v -> cfg().rendLoadoutSlot = v, ""), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Click delay", 1, 10, () -> cfg().rendClickDelay,
			v -> cfg().rendClickDelay = v, " t"), y);
		place(toggle(right, "Ice spray after loadout", () -> cfg().rendUseIceSpray,
			v -> cfg().rendUseIceSpray = v), y);
		y += ROW;
		place(toggle(left, "Auto pull on backbone", () -> cfg().rendAutoPull, v -> cfg().rendAutoPull = v), y);
		place(new IntegerSlider(right, columnWidth, "Pull item slot", 1, 8, () -> cfg().rendPullItemSlot,
			v -> cfg().rendPullItemSlot = v, ""), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Pull delay", 1, 20, () -> cfg().rendPullDelay,
			v -> cfg().rendPullDelay = v, " t"), y);
	}

	private void buildRender() {
		if (section == Section.DROPPED_ITEMS) { buildDroppedItems(); return; }
		if (section == Section.SHORT_DAMAGE) {
			buildShortDamage();
			return;
		}
		if (section == Section.PEST_ESP) {
			buildPestEsp();
			return;
		}
		if (section == Section.ANIMATION) {
			buildAnimation();
			return;
		}
		int y = 0;
		place(feature("Short Damage Numbers", () -> cfg().shortDamageNumbers,
			v -> cfg().shortDamageNumbers = v, Section.SHORT_DAMAGE), y);
		y += ROW;
		place(feature("Garden Pest ESP", () -> cfg().gardenPestEsp, v -> cfg().gardenPestEsp = v,
			Section.PEST_ESP), y);
		y += ROW;
		place(feature("Animation", () -> cfg().animationEnabled, v -> cfg().animationEnabled = v,
			Section.ANIMATION), y);
		y += ROW;
		place(feature("Dropped Items", () -> cfg().droppedItemScale,
			v -> cfg().droppedItemScale = v, Section.DROPPED_ITEMS), y);
	}

	private void buildDroppedItems() {
		int y = sectionHeader("Dropped Items", () -> cfg().droppedItemScale, v -> cfg().droppedItemScale = v);
		place(toggle(left, featureWidth(), "Rarity Scale", () -> cfg().droppedItemRarityScale,
			v -> cfg().droppedItemRarityScale = v), y);
		y += ROW;
		int index = 0;
		for (SkyblockItemRarity rarity : SkyblockItemRarity.values()) {
			if (rarity == SkyblockItemRarity.UNKNOWN) continue;
			int x = index % 2 == 0 ? left : right;
			int row = y + index / 2 * ROW;
			place(new DoubleSlider(x, columnWidth, rarity.label, 1.0, 3.5, 0.05,
				() -> cfg().rarityScale(rarity), value -> cfg().setRarityScale(rarity, value), "x"), row);
			index++;
		}
	}

	private void buildAnimation() {
		int y = sectionHeader("Animation", () -> cfg().animationEnabled, v -> cfg().animationEnabled = v);
		place(toggle(left, "Disable Swap Main", () -> cfg().animDisableSwapMain,
			v -> cfg().animDisableSwapMain = v), y);
		place(toggle(right, "Disable Swap Off", () -> cfg().animDisableSwapOff,
			v -> cfg().animDisableSwapOff = v), y);
		y += ROW;
		place(toggle(left, "Blocking Animation", () -> cfg().animBlockingAnimation,
			v -> cfg().animBlockingAnimation = v), y);
		place(enumButton(right, "Blocking Mode", Animation.BlockMode.values(),
			() -> cfg().animBlockingMode, v -> cfg().animBlockingMode = v), y);
		y += ROW;
		place(enumButton(left, "Swing Mode", Animation.SwingMode.values(),
			() -> cfg().animSwingMode, v -> cfg().animSwingMode = v), y);
		place(toggle(right, "Only Weapon", () -> cfg().animOnlyWeapon, v -> cfg().animOnlyWeapon = v), y);
		y += ROW;
		place(toggle(left, "Modify Swing Duration", () -> cfg().animModifySwingDuration,
			v -> cfg().animModifySwingDuration = v), y);
		place(new IntegerSlider(right, columnWidth, "Swing Duration", 0, 20, () -> cfg().animSwingDuration,
			v -> cfg().animSwingDuration = v, " t"), y);
		y += ROW;
		place(toggle(left, "Visual Swing On Use", () -> cfg().animSwingWhileUsing,
			v -> cfg().animSwingWhileUsing = v), y);
		place(toggle(right, "Only On Block", () -> cfg().animOnlyOnBlock, v -> cfg().animOnlyOnBlock = v), y);
	}

	private void buildShortDamage() {
		ThemedButton color = colorButton(left, "Damage Color", () -> cfg().shortDamageNumbersColor,
			v -> cfg().shortDamageNumbersColor = v);
		ThemedButton compact = toggle(right, "Compact Damage Style", () -> cfg().compactDamageStyle, v -> {
			cfg().compactDamageStyle = v;
			color.active = cfg().shortDamageNumbers && !v;
		});
		int y = sectionHeader("Short Damage Numbers", () -> cfg().shortDamageNumbers, v -> {
			cfg().shortDamageNumbers = v;
			compact.active = v;
			color.active = v && !cfg().compactDamageStyle;
		});
		place(color, y);
		place(compact, y);
		compact.active = cfg().shortDamageNumbers;
		color.active = cfg().shortDamageNumbers && !cfg().compactDamageStyle;
	}

	private void buildPestEsp() {
		ThemedButton line = toggle(left, "Pest Line", () -> cfg().gardenPestLine, v -> cfg().gardenPestLine = v);
		ThemedButton color = colorButton(right, "Pest ESP Color", () -> cfg().gardenPestEspColor,
			v -> cfg().gardenPestEspColor = v);
		int y = sectionHeader("Garden Pest ESP", () -> cfg().gardenPestEsp, v -> {
			cfg().gardenPestEsp = v;
			line.active = v;
			color.active = v;
		});
		place(line, y);
		place(color, y);
		line.active = cfg().gardenPestEsp;
		color.active = cfg().gardenPestEsp;
	}

	private void buildDiana() {
		if (section == Section.DIANA_MOB_HITS) {
			int y = sectionHeader("Diana Mob Health & Hits", () -> cfg().dianaMobHits,
				v -> cfg().dianaMobHits = v);
			place(toggle(left, "Death Chat Summary", () -> cfg().dianaMobHitsChatSummary,
				v -> cfg().dianaMobHitsChatSummary = v), y);
			return;
		}
		place(toggle(left, featureWidth(), "Diana's Feet", () -> cfg().dianaFeet,
			v -> cfg().dianaFeet = v), 0);
		place(feature("Diana Mob Health & Hits", () -> cfg().dianaMobHits,
			v -> cfg().dianaMobHits = v, Section.DIANA_MOB_HITS), ROW);
	}

	private void buildMining() {
		if (section == Section.MINESHAFT_ESP) { buildMineshaftEsp(); return; }
		if (section == Section.PING_OFFSET_MINER) { buildPingOffsetMiner(); return; }
		if (section == Section.LITTLEFOOT) { buildLittlefoot(); return; }
		if (section == Section.PICKAXE_ABILITY) { buildPickaxeAbility(); return; }
		if (section == Section.WILL_O_WISP) { buildWillOWisp(); return; }
		place(toggle(left, featureWidth(), "Shaft Announce", () -> cfg().shaftAnnounce,
			v -> cfg().shaftAnnounce = v), 0);
		place(toggle(left, featureWidth(), "Corpse ESP", () -> cfg().corpseEsp,
			v -> cfg().corpseEsp = v), ROW);
		place(feature("Mineshaft ESP", () -> cfg().mineshaftEsp,
			v -> cfg().mineshaftEsp = v, Section.MINESHAFT_ESP), ROW * 2);
		place(toggle(left, featureWidth(), "No Gemstone Desync", () -> cfg().noGemstoneDesync,
			v -> cfg().noGemstoneDesync = v), ROW * 3);
		place(feature("Ping Offset Miner", () -> cfg().pingOffsetMiner,
			v -> cfg().pingOffsetMiner = v, Section.PING_OFFSET_MINER), ROW * 4);
		place(feature("Littlefoot Detection", () -> cfg().littlefootDetection,
			v -> cfg().littlefootDetection = v, Section.LITTLEFOOT), ROW * 5);
		place(feature("Pickaxe Ability", () -> cfg().pickaxeAbility,
			v -> cfg().pickaxeAbility = v, Section.PICKAXE_ABILITY), ROW * 6);
		place(feature("Will-o-Wisp", () -> cfg().willOWisp,
			v -> cfg().willOWisp = v, Section.WILL_O_WISP), ROW * 7);
	}

	private void buildPickaxeAbility() {
		int y = sectionHeader("Pickaxe Ability", () -> cfg().pickaxeAbility,
			v -> cfg().pickaxeAbility = v);
		place(toggle(left, "Show Duration", () -> cfg().pickaxeAbilityDuration,
			v -> cfg().pickaxeAbilityDuration = v), y);
		place(toggle(right, "No Popup", () -> cfg().pickaxeAbilityNoPopup,
			v -> cfg().pickaxeAbilityNoPopup = v), y);
		place(toggle(left, "Popup Sound", () -> cfg().pickaxePopupSound,
			v -> cfg().pickaxePopupSound = v), y + ROW);
		place(ThemedButton.builder(Component.literal("Move HUD"), button ->
			minecraft.gui.setScreen(new HudEditorScreen(this)))
			.bounds(right, 0, columnWidth, 20).build(), y + ROW);
		place(new IntegerSlider(right, columnWidth, "Ready Stay", 1, 200,
			() -> cfg().pickaxePopupStayTicks, v -> cfg().pickaxePopupStayTicks = v, " ticks"), y + ROW * 2);
		place(textBox(left, "Countdown Text", () -> cfg().pickaxePopupCountdownText,
			v -> cfg().pickaxePopupCountdownText = v), y + ROW * 3);
		place(textBox(right, "Ready Text", () -> cfg().pickaxePopupReadyText,
			v -> cfg().pickaxePopupReadyText = v), y + ROW * 3);
		place(colorButton(left, "Countdown Color", () -> cfg().pickaxePopupCountdownColor,
			v -> cfg().pickaxePopupCountdownColor = v), y + ROW * 4);
		place(colorButton(right, "Ready Color", () -> cfg().pickaxePopupReadyColor,
			v -> cfg().pickaxePopupReadyColor = v), y + ROW * 4);
	}

	private void buildWillOWisp() {
		int y = sectionHeader("Will-o-Wisp", () -> cfg().willOWisp,
			v -> cfg().willOWisp = v);
		place(toggle(left, "Wisp HUD", () -> cfg().wispHud,
			v -> cfg().wispHud = v), y);
		place(toggle(right, "Popup Timer", () -> cfg().wispPopup,
			v -> cfg().wispPopup = v), y);
		place(toggle(left, "Popup Sound", () -> cfg().wispPopupSound,
			v -> cfg().wispPopupSound = v), y + ROW);
		place(enumButton(left, "Radius Mode", WillOWisp.RadiusMode.values(),
			() -> cfg().wispRadiusMode, v -> cfg().wispRadiusMode = v), y + ROW * 2);
		place(enumButton(right, "Radius Render", WillOWisp.RenderMode.values(),
			() -> cfg().wispRenderMode, v -> cfg().wispRenderMode = v), y + ROW * 2);
		place(new IntegerSlider(left, columnWidth, "Scan Radius", 32, 512,
			() -> cfg().wispMaxMarkers, v -> cfg().wispMaxMarkers = v, " blocks"), y + ROW * 3);
		place(ThemedButton.builder(Component.literal("Move HUD"), button ->
			minecraft.gui.setScreen(new HudEditorScreen(this)))
			.bounds(left, 0, columnWidth, 20).build(), y + ROW * 4);
		place(new IntegerSlider(right, columnWidth, "Place Stay", 1, 200,
			() -> cfg().wispPopupStayTicks, v -> cfg().wispPopupStayTicks = v, " ticks"), y + ROW * 3);
		place(textBox(left, "Countdown Text", () -> cfg().wispPopupCountdownText,
			v -> cfg().wispPopupCountdownText = v), y + ROW * 5);
		place(textBox(right, "Final Text", () -> cfg().wispPopupFinalText,
			v -> cfg().wispPopupFinalText = v), y + ROW * 5);
		place(colorButton(left, "Countdown Color", () -> cfg().wispPopupCountdownColor,
			v -> cfg().wispPopupCountdownColor = v), y + ROW * 6);
		place(colorButton(right, "Final Color", () -> cfg().wispPopupFinalColor,
			v -> cfg().wispPopupFinalColor = v), y + ROW * 6);
	}

	private void buildLittlefoot() {
		int y = sectionHeader("Littlefoot Detection", () -> cfg().littlefootDetection,
			v -> cfg().littlefootDetection = v);
		place(toggle(left, "Popup", () -> cfg().littlefootPopup,
			v -> cfg().littlefootPopup = v), y);
		place(toggle(right, "Chat Message", () -> cfg().littlefootMessage,
			v -> cfg().littlefootMessage = v), y);
		place(new IntegerSlider(left, columnWidth, "Fade In", 0, 100,
			() -> cfg().littlefootPopupFadeIn, v -> cfg().littlefootPopupFadeIn = v, " ticks"), y + ROW);
		place(new IntegerSlider(right, columnWidth, "Stay", 0, 200,
			() -> cfg().littlefootPopupStay, v -> cfg().littlefootPopupStay = v, " ticks"), y + ROW);
		place(new IntegerSlider(left, columnWidth, "Fade Out", 0, 100,
			() -> cfg().littlefootPopupFadeOut, v -> cfg().littlefootPopupFadeOut = v, " ticks"), y + ROW * 2);
		place(colorButton(right, "Popup Color", () -> cfg().littlefootPopupColor,
			v -> cfg().littlefootPopupColor = v), y + ROW * 2);
		place(textBox(left, "Popup Text", () -> cfg().littlefootPopupText,
			v -> cfg().littlefootPopupText = v), y + ROW * 3);
		place(ThemedButton.builder(Component.literal("Move HUD"), button ->
			minecraft.gui.setScreen(new HudEditorScreen(this)))
			.bounds(right, 0, columnWidth, 20).build(), y + ROW * 3);
	}

	private void buildMineshaftEsp() {
		int y = sectionHeader("Mineshaft ESP", () -> cfg().mineshaftEsp,
			v -> cfg().mineshaftEsp = v);
		place(toggle(left, "Corpse Waypoints", () -> cfg().mineshaftCorpseWaypoints,
			v -> cfg().mineshaftCorpseWaypoints = v), y);
		place(toggle(right, "Hide Looted Corpses", () -> cfg().mineshaftHideLootedCorpses,
			v -> cfg().mineshaftHideLootedCorpses = v), y);
		y += ROW;
		place(toggle(left, "Fossil ESP", () -> cfg().mineshaftFossilEsp,
			v -> cfg().mineshaftFossilEsp = v), y);
		place(toggle(right, "Mob ESP", () -> cfg().mineshaftMobEsp,
			v -> cfg().mineshaftMobEsp = v), y);
		y += ROW * 2;
		addGroupLabel("Mineshaft Mobs", y);
		y += ROW;
		for (MineshaftEsp.Mob mob : MineshaftEsp.Mob.values()) {
			String key = mob.name();
			place(toggle(left, mob.label(), () -> cfg().mineshaftMobs.getOrDefault(key, true),
				v -> cfg().mineshaftMobs.put(key, v)), y);
			place(colorButton(right, mob.label() + " Color", () -> cfg().mineshaftMobColors.getOrDefault(key, 5),
				v -> cfg().mineshaftMobColors.put(key, v)), y);
			y += ROW;
		}
	}

	private void buildPingOffsetMiner() {
		int start = sectionHeader("Ping Offset Miner", () -> cfg().pingOffsetMiner,
			v -> cfg().pingOffsetMiner = v);
		place(new IntegerSlider(left, columnWidth, "POM Tick Margin", -10, 10,
			() -> cfg().pingOffsetTickMargin, v -> cfg().pingOffsetTickMargin = v, " ticks"), start);
		place(new IntegerSlider(right, columnWidth, "POM Outline Width", 1, 10,
			() -> cfg().pingOffsetOutlineWidth, v -> cfg().pingOffsetOutlineWidth = v, ""), start);
		place(toggle(left, "POM Outline", () -> cfg().pingOffsetOutline,
			v -> cfg().pingOffsetOutline = v), start + ROW);
		place(toggle(right, "POM Highlight", () -> cfg().pingOffsetHighlight,
			v -> cfg().pingOffsetHighlight = v), start + ROW);
		place(toggle(left, "POM Break Progress", () -> cfg().pingOffsetProgress,
			v -> cfg().pingOffsetProgress = v), start + ROW * 2);
		place(toggle(right, "POM Precision Mining", () -> cfg().pingOffsetPrecisionMining,
			v -> cfg().pingOffsetPrecisionMining = v), start + ROW * 2);
		place(toggle(left, "POM MSB Only", () -> cfg().pingOffsetMsbOnly,
			v -> cfg().pingOffsetMsbOnly = v), start + ROW * 3);
		place(new IntegerSlider(left, columnWidth, "POM Extra Gem Speed", 0, 5000,
			() -> cfg().pingOffsetExtraGemstoneSpeed, v -> cfg().pingOffsetExtraGemstoneSpeed = v, ""), start + ROW * 4);
		place(new IntegerSlider(right, columnWidth, "POM Extra Metal Speed", 0, 5000,
			() -> cfg().pingOffsetExtraMetalSpeed, v -> cfg().pingOffsetExtraMetalSpeed = v, ""), start + ROW * 4);
		place(new IntegerSlider(left, columnWidth, "POM Extra Ore Speed", 0, 5000,
			() -> cfg().pingOffsetExtraOreSpeed, v -> cfg().pingOffsetExtraOreSpeed = v, ""), start + ROW * 5);
		place(toggle(left, "POM Tick HUD", () -> cfg().pingOffsetTickHud,
			v -> cfg().pingOffsetTickHud = v), start + ROW * 6);
		place(toggle(right, "POM Ping/TPS HUD", () -> cfg().pingOffsetProfilerHud,
			v -> cfg().pingOffsetProfilerHud = v), start + ROW * 6);
		place(toggle(left, "POM Custom Stats", () -> cfg().pingOffsetCustomStats,
			v -> cfg().pingOffsetCustomStats = v), start + ROW * 7);
		place(new IntegerSlider(left, columnWidth, "POM Custom Speed", 1, 20000,
			() -> cfg().pingOffsetCustomSpeed, v -> cfg().pingOffsetCustomSpeed = v, ""), start + ROW * 8);
		place(new IntegerSlider(right, columnWidth, "POM Custom Ping", 0, 1000,
			() -> cfg().pingOffsetCustomPing, v -> cfg().pingOffsetCustomPing = v, " ms"), start + ROW * 8);
		place(new IntegerSlider(left, columnWidth, "POM Custom TPS", 1, 20,
			() -> cfg().pingOffsetCustomTps, v -> cfg().pingOffsetCustomTps = v, ""), start + ROW * 9);
		int y = start + ROW * 10;
		addGroupLabel("POM Islands", y);
		y += ROW;
		int column = 0;
		for (LocationManager.Island island : LocationManager.Island.values()) {
			if (island == LocationManager.Island.UNKNOWN || island == LocationManager.Island.SINGLE_PLAYER) continue;
			String key = island.name();
			place(toggle(column % 2 == 0 ? left : right,
				"POM " + key.replace('_', ' '),
				() -> cfg().pingOffsetIslands.getOrDefault(key, PingOffsetMiner.defaultIsland(island)),
				v -> cfg().pingOffsetIslands.put(key, v)), y);
			if (++column % 2 == 0) y += ROW;
		}
		if (column % 2 != 0) y += ROW;
		addGroupLabel("POM Blocks", y);
		y += ROW;
		column = 0;
		for (String block : PingOffsetMiner.supportedBlocks()) {
			place(toggle(column % 2 == 0 ? left : right,
				block.replace('_', ' '),
				() -> cfg().pingOffsetBlocks.getOrDefault(block, true),
				v -> cfg().pingOffsetBlocks.put(block, v)), y);
			if (++column % 2 == 0) y += ROW;
		}
	}

	private void buildFishing() {
		if (section == Section.AUTO_FISH) {
			buildAutoFish();
			return;
		}
		place(feature("AutoFish", () -> cfg().autoFish, v -> {
			cfg().autoFish = v;
			if (!v) {
				AutoFish.stop();
			}
		}, Section.AUTO_FISH), 0);
	}

	private void buildAutoFish() {
		int y = sectionHeader("AutoFish", () -> cfg().autoFish, v -> {
			cfg().autoFish = v;
			if (!v) {
				AutoFish.stop();
			}
		});
		place(toggle(left, "Auto Kill", () -> cfg().autoKill, v -> cfg().autoKill = v), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Min Bite Delay", 0, 20, () -> cfg().fishMinWaitTime,
			v -> cfg().fishMinWaitTime = v, " s"), y);
		place(new IntegerSlider(right, columnWidth, "Max Cast Time", 30, 120, () -> cfg().fishMaxWaitTime,
			v -> cfg().fishMaxWaitTime = v, " s"), y);
		y += ROW;
		place(toggle(left, "Entity Check", () -> cfg().entityCheck, v -> cfg().entityCheck = v), y);
		place(toggle(right, "Golden Fish", () -> cfg().goldenFish, v -> cfg().goldenFish = v), y);
		y += ROW;
		place(toggle(left, "Slug Fish (10s in fluid)", () -> cfg().slugFish, v -> cfg().slugFish = v), y);
		y += ROW;
		place(toggle(left, "FB Walk", () -> cfg().fbWalk, v -> {
			cfg().fbWalk = v;
			if (!v) AutoFish.stopFbWalk();
		}), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "FB Walk Min", 1, 300,
			() -> cfg().fbWalkMinSeconds, v -> {
				cfg().fbWalkMinSeconds = v;
				cfg().fbWalkMaxSeconds = Math.max(v, cfg().fbWalkMaxSeconds);
				AutoFish.stopFbWalk();
			}, " s"), y);
		place(new IntegerSlider(right, columnWidth, "FB Walk Max", 1, 300,
			() -> cfg().fbWalkMaxSeconds, v -> {
				cfg().fbWalkMaxSeconds = v;
				cfg().fbWalkMinSeconds = Math.min(v, cfg().fbWalkMinSeconds);
				AutoFish.stopFbWalk();
			}, " s"), y);
		y += ROW;
		place(toggle(left, "Auto Breath", () -> cfg().autoBreath, v -> cfg().autoBreath = v), y);
		place(toggle(right, "Puddle Jumper", () -> cfg().puddleJumper, v -> cfg().puddleJumper = v), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Rod Slot", 1, 9, () -> cfg().fishingRodHotbarSlot,
			v -> cfg().fishingRodHotbarSlot = v, ""), y);
		place(new IntegerSlider(right, columnWidth, "Kill Weapon Slot", 1, 9, () -> cfg().autoKillHotbarSlot,
			v -> cfg().autoKillHotbarSlot = v, ""), y);
		y += ROW;
		place(new IntegerSlider(left, columnWidth, "Weapon Use Delay", 0, 10, () -> cfg().autoKillSwitchTicks,
			v -> cfg().autoKillSwitchTicks = v, " tick"), y);
		place(new IntegerSlider(right, columnWidth, "Return to Rod Delay", 1, 12,
			() -> cfg().autoKillReturnTicks, v -> cfg().autoKillReturnTicks = v, " tick"), y);
		y += ROW;
		place(toggle(left, "HYPE (WIPE)", () -> cfg().hypeWipe, v -> cfg().hypeWipe = v), y);
		place(new IntegerSlider(right, columnWidth, "HYPE Hit Count", 1, 10, () -> cfg().hypeHitCount,
			v -> cfg().hypeHitCount = v, ""), y);
	}

	private int sectionHeader(String name) {
		return sectionHeader(name, null, null);
	}

	private int sectionHeader(String name, BooleanSupplier getter, BooleanConsumer setter) {
		if (inlineBuild) return 0;
		ThemedButton backButton = ThemedButton.builder(Component.literal("← " + name), button -> back())
			.bounds(left, 0, getter == null ? featureWidth() : columnWidth, 20)
			.build();
		place(backButton, 0);
		if (getter != null) {
			place(toggle(right, name, getter, setter), 0);
		}
		return ROW;
	}

	private ThemedButton feature(String name, Section target) {
		return feature(name, null, null, target);
	}

	private ThemedButton feature(String name, BooleanSupplier getter, BooleanConsumer setter, Section target) {
		ThemedButton button = ThemedButton.builder(featureText(name, getter), clicked -> {
			if (setter == null) return;
			boolean value = !getter.get();
			setter.accept(value);
			clicked.setMessage(featureText(name, getter));
		}).bounds(left, 0, featureWidth(), 24).build();
		button.childPage(true);
		button.selected(section == target);
		features.add(new FeatureRow(button, target));
		return button;
	}

	private static Component featureText(String name, BooleanSupplier getter) {
		return getter == null ? Component.literal(name) : toggleText(name, getter.get());
	}

	private void enter(Section target) {
		section = section == target ? Section.LIST : target;
		sectionScroll = 0;
		revealSidebarSelection = true;
		rebuildWidgets();
	}

	private void back() {
		section = Section.LIST;
		revealSidebarSelection = true;
		rebuildWidgets();
	}

	private void place(AbstractWidget widget, int y) {
		content.add(new Placed(widget, y, widget.getX(), widget.getWidth()));
		contentHeight = Math.max(contentHeight, y + widget.getHeight());
		if (!collectingIndex) addRenderableWidget(widget);
	}

	private void layout() {
		boolean singleColumn = contentWidth < 420;
		wrappedRowShifts.clear();
		int extraHeight = 0;
		int currentRow = Integer.MIN_VALUE;
		int rowIndex = 0;
		int lastRowCount = 0;
		int lastRowY = Integer.MIN_VALUE;
		int maxBottom = 0;
		for (Placed placed : content) {
			if (placed.y != currentRow) {
				if (singleColumn && lastRowCount > 1) {
					extraHeight += (lastRowCount - 1) * ROW;
					wrappedRowShifts.put(lastRowY, extraHeight);
				}
				currentRow = placed.y;
				rowIndex = 0;
				lastRowY = currentRow;
				lastRowCount = 0;
			}
			placed.visualY = placed.y + extraHeight + (singleColumn ? rowIndex * ROW : 0);
			if (singleColumn || !placed.child) {
				placed.widget.setX(left + (placed.child ? 10 : 0));
				placed.widget.setWidth(contentWidth - (placed.child ? 10 : 0));
			} else {
				placed.widget.setX(placed.x);
				placed.widget.setWidth(placed.width);
			}
			maxBottom = Math.max(maxBottom, placed.visualY + placed.widget.getHeight());
			rowIndex++;
			lastRowCount++;
		}
		if (singleColumn && lastRowCount > 1) {
			extraHeight += (lastRowCount - 1) * ROW;
			wrappedRowShifts.put(lastRowY, extraHeight);
		}
		for (Label label : labels) maxBottom = Math.max(maxBottom, label.y + wrappedShift(label.y) + 10);
		contentHeight = maxBottom;
		positionContentWidgets();
	}

	private void positionContentWidgets() {
		int scroll = Math.clamp(currentScroll(), 0, maxScroll());
		setCurrentScroll(scroll);
		int bottom = contentBottom;
		for (Placed placed : content) {
			int y = contentTop + placed.visualY - scroll;
			placed.widget.setY(y);
			placed.widget.visible = y >= contentTop && y + placed.widget.getHeight() <= bottom;
		}
	}

	private int wrappedShift(int rawY) {
		var entry = wrappedRowShifts.lowerEntry(rawY);
		return entry == null ? 0 : entry.getValue();
	}

	private int currentScroll() {
		return section == Section.LIST && searchQuery.isBlank() ? SCROLL.getOrDefault(page, 0) : sectionScroll;
	}

	private void setCurrentScroll(int scroll) {
		if (section == Section.LIST && searchQuery.isBlank()) {
			SCROLL.put(page, scroll);
		} else {
			sectionScroll = scroll;
		}
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - (contentBottom - contentTop));
	}

	private int featureWidth() {
		return contentWidth;
	}

	private int visibleSidebarTabs() {
		return Math.max(1, (panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 10) / SIDEBAR_ROW);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= panelX && mouseX < panelX + sidebarWidth && sidebarEntries.size() > visibleSidebarTabs()) {
			sidebarScroll = Math.clamp(sidebarScroll - (int) Math.signum(vertical), 0,
				sidebarEntries.size() - visibleSidebarTabs());
			rebuildWidgets();
			return true;
		}
		if (mouseX >= left && mouseX < left + contentWidth && maxScroll() > 0) {
			setCurrentScroll(Math.clamp(currentScroll() - (int) Math.signum(vertical) * 22, 0, maxScroll()));
			layout();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			for (FeatureRow row : features) {
				if (row.widget.visible && row.widget.isMouseOver(event.x(), event.y())) {
					if (minecraft != null) {
						AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
					}
					enter(row.section);
					return true;
				}
			}
			for (SearchResultRow row : searchResults) {
				if (row.widget.visible && row.widget.isMouseOver(event.x(), event.y())) {
					SearchEntry entry = row.entry;
					navigate(entry.page, entry.section, entry.label);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused()) {
			if (event.isEscape() && !searchQuery.isEmpty()) {
				searchBox.setValue("");
				return true;
			}
			return super.keyPressed(event);
		}
		if (event.isEscape() && !searchQuery.isEmpty()) {
			searchQuery = "";
			rebuildWidgets();
			return true;
		}
		if (section != Section.LIST) {
			if (event.isEscape()) {
				back();
				return true;
			}
			if (event.isLeft() && !(getFocused() instanceof AbstractSliderButton)) {
				back();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xBB06080C);
		UiShapes.rounded(graphics, panelX - 4, panelY - 4,
			panelX + panelWidth + 4, panelY + panelHeight + 4, 10, 0x66000000);
		UiShapes.rounded(graphics, panelX - 1, panelY - 1,
			panelX + panelWidth + 1, panelY + panelHeight + 1, 7, 0xFF35444B);
		UiShapes.rounded(graphics, panelX, panelY,
			panelX + panelWidth, panelY + panelHeight, 6, 0xFF15191F);
		graphics.enableScissor(panelX, panelY, panelX + panelWidth, panelY + panelHeight);
		UiShapes.radialGlow(graphics, panelX + sidebarWidth, panelY + HEADER_HEIGHT,
			panelWidth - sidebarWidth, panelHeight - HEADER_HEIGHT, 0x3238D8E7);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, 0xFF1B2127);
		graphics.fill(panelX, panelY + HEADER_HEIGHT - 1,
			panelX + panelWidth, panelY + HEADER_HEIGHT, ACCENT);
		graphics.fill(panelX, panelY + HEADER_HEIGHT,
			panelX + sidebarWidth, panelY + panelHeight - FOOTER_HEIGHT, 0xFF111A20);
		graphics.fill(panelX + sidebarWidth - 1, panelY + HEADER_HEIGHT,
			panelX + sidebarWidth, panelY + panelHeight, 0xFF314048);
		graphics.fill(panelX, panelY + panelHeight - FOOTER_HEIGHT,
			panelX + sidebarWidth, panelY + panelHeight, 0xFF1B2127);
		graphics.fill(panelX, panelY + panelHeight - FOOTER_HEIGHT,
			panelX + sidebarWidth, panelY + panelHeight - FOOTER_HEIGHT + 1, 0xFF314048);
		UiShapes.rounded(graphics, panelX + 7, panelY + 6, panelX + 29, panelY + 28, 5, 0xFF23434C);
		graphics.text(font, Component.literal("R"), panelX + 14, panelY + 13, ACCENT, true);
		graphics.text(font, Component.literal("RatAddons"), panelX + 36, panelY + 13, ACCENT, true);
		UiShapes.rounded(graphics, searchBox.getX() - 6, searchBox.getY() - 4,
			searchBox.getRight() + 6, searchBox.getBottom() + 4, 5, 0xFF52636B);
		UiShapes.rounded(graphics, searchBox.getX() - 5, searchBox.getY() - 3,
			searchBox.getRight() + 5, searchBox.getBottom() + 3, 4,
			searchBox.isFocused() ? 0xFF233A42 : 0xFF11191F);
		if (searchQuery.isBlank() && !searchBox.isFocused()) {
			graphics.text(font, Component.literal("Search..."), searchBox.getX(),
				searchBox.getY(), 0xFF8298A1, false);
		}
		String title = searchQuery.isBlank()
			? page.label + (section == Section.LIST ? "" : "  /  " + sectionName(page, section))
			: "Search results";
		graphics.text(font, Component.literal(ThemedButton.fit(font, title, contentWidth - 12)),
			left + 1, panelY + 42, 0xFF93ABB3, false);
		UiShapes.rounded(graphics, panelX + 5, panelY + panelHeight - 24,
			panelX + 6 + (sidebarWidth - 18) / 2, panelY + panelHeight - 4, 4, 0xFF27343B);
		UiShapes.rounded(graphics, panelX + 11 + (sidebarWidth - 18) / 2, panelY + panelHeight - 24,
			panelX + sidebarWidth - 5, panelY + panelHeight - 4, 4, 0xFF27343B);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		for (Label label : labels) {
			int y = contentTop + label.y + wrappedShift(label.y) - currentScroll();
			if (y >= contentTop && y + 9 <= contentBottom) {
				graphics.text(font, Component.literal(label.text), label.x, y, label.color, true);
			}
		}
		int max = maxScroll();
		if (max > 0) {
			int track = contentBottom - contentTop;
			int thumb = Math.max(18, track * track / (track + max));
			int thumbY = contentTop + (track - thumb) * currentScroll() / max;
			graphics.fill(left + contentWidth + 7, contentTop,
				left + contentWidth + 10, contentBottom, 0x2038D8E7);
			graphics.fill(left + contentWidth + 7, thumbY,
				left + contentWidth + 10, thumbY + thumb, ACCENT);
		}
		graphics.disableScissor();
	}
	private String sectionName(Page targetPage, Section targetSection) {
		return navigation.getOrDefault(targetPage, List.of()).stream()
			.filter(nav -> nav.section == targetSection).map(NavSection::label).findFirst().orElse("Overview");
	}

	private ThemedButton toggle(int x, String name, BooleanSupplier getter, BooleanConsumer setter) {
		return toggle(x, columnWidth, name, getter, setter);
	}

	private ThemedButton toggle(int x, int width, String name, BooleanSupplier getter, BooleanConsumer setter) {
		return ThemedButton.builder(toggleText(name, getter.get()), button -> {
			boolean value = !getter.get();
			setter.accept(value);
			button.setMessage(toggleText(name, getter.get()));
		}).bounds(x, 0, width, 24).build();
	}

	private ThemedButton colorButton(int x, String name, IntSupplier getter, IntConsumer setter) {
		return ThemedButton.builder(Component.literal(name + ": " + ColorOptions.name(getter.getAsInt())), button -> {
			int value = ColorOptions.next(getter.getAsInt());
			setter.accept(value);
			button.setMessage(Component.literal(name + ": " + ColorOptions.name(value)));
		}).bounds(x, 0, columnWidth, 20).build();
	}

	private ThemedButton enumButton(int x, String name, Enum<?>[] values, IntSupplier getter, IntConsumer setter) {
		return ThemedButton.builder(Component.literal(name + ": " + values[Math.clamp(getter.getAsInt(), 0, values.length - 1)].name()),
			button -> {
				int next = (Math.clamp(getter.getAsInt(), 0, values.length - 1) + 1) % values.length;
				setter.accept(next);
				button.setMessage(Component.literal(name + ": " + values[next].name()));
			}).bounds(x, 0, columnWidth, 20).build();
	}

	private static Component onOff(boolean enabled) {
		return Component.literal(enabled ? "§aON" : "§7OFF");
	}

	private static Component toggleText(String name, boolean enabled) {
		return Component.literal(name + ": ").append(onOff(enabled));
	}

	private EditBox textBox(int x, String name, Supplier<String> getter, Consumer<String> setter) {
		EditBox box = new EditBox(font, x, 0, columnWidth, 20, Component.literal(name)) {
			@Override
			public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
				UiShapes.rounded(graphics, getX(), getY(), getRight(), getBottom(), 5, 0xFF52646B);
				UiShapes.rounded(graphics, getX() + 1, getY() + 1,
					getRight() - 1, getBottom() - 1, 4, isFocused() ? 0xFF233B43 : 0xFF121A20);
				super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
			}
		};
		box.setMaxLength(80);
		box.setBordered(false);
		box.setTextColor(0xFFE6F6F8);
		box.setHint(Component.literal(name));
		box.setValue(getter.get());
		box.setResponder(setter);
		return box;
	}

	private ThemedButton choiceButton(int x, String name, String[] values, IntSupplier getter, IntConsumer setter) {
		return ThemedButton.builder(Component.literal(name + ": " + values[Math.clamp(getter.getAsInt(), 0, values.length - 1)]),
			button -> {
				int next = (Math.clamp(getter.getAsInt(), 0, values.length - 1) + 1) % values.length;
				setter.accept(next);
				button.setMessage(Component.literal(name + ": " + values[next]));
			}).bounds(x, 0, columnWidth, 20).build();
	}

	private static void renderSlider(AbstractSliderButton slider, double value, GuiGraphicsExtractor graphics) {
		int x = slider.getX(), y = slider.getY(), right = slider.getRight(), bottom = slider.getBottom();
		boolean highlighted = slider.active && slider.isHoveredOrFocused();
		if (highlighted) graphics.fill(x, y, right, bottom, 0x0DFFFFFF);
		graphics.fill(x, bottom - 1, right, bottom, 0x1EFFFFFF);
		UiShapes.rounded(graphics, x + 9, bottom - 6, right - 9, bottom - 3, 1, 0xFF394D54);
		int handle = x + 9 + (int) Math.round(Math.clamp(value, 0.0, 1.0) * Math.max(0, slider.getWidth() - 18));
		if (handle > x + 9) UiShapes.rounded(graphics, x + 9, bottom - 6, handle, bottom - 3, 1, ACCENT);
		UiShapes.rounded(graphics, handle - 3, bottom - 9, handle + 3, bottom, 3, slider.active ? ACCENT : 0xFF78949B);
		String label = net.minecraft.ChatFormatting.stripFormatting(slider.getMessage().getString());
		if (label != null) graphics.text(Minecraft.getInstance().font,
			Component.literal(ThemedButton.fit(Minecraft.getInstance().font, label, slider.getWidth() - 18)), x + 9, y + 3,
			slider.active ? 0xFFE6F6F8 : 0xFF7F9AA2, false);
	}

	private static RaTAddonConfig cfg() {
		return RaTAddonClient.CONFIG;
	}

	@Override
	public void onClose() {
		cfg().save();
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	private static final class Placed {
		final AbstractWidget widget;
		final int y;
		final int x;
		final int width;
		final boolean child;
		int visualY;
		Placed(AbstractWidget widget, int y, int x, int width) {
			this(widget, y, x, width, false);
		}
		Placed(AbstractWidget widget, int y, int x, int width, boolean child) {
			this.widget = widget;
			this.y = y;
			this.x = x;
			this.width = width;
			this.child = child;
		}
	}

	private record Label(String text, int x, int y, int color) {
	}

	private record FeatureRow(AbstractWidget widget, Section section) {
	}

	private record SearchResultRow(AbstractWidget widget, SearchEntry entry) {
	}

	private record SearchEntry(Page page, Section section, String label) {
	}

	private record NavSection(Section section, String label) {
	}

	private record NavEntry(Page page, String label) {
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean get();
	}

	@FunctionalInterface
	private interface BooleanConsumer {
		void accept(boolean value);
	}

	private enum Section {
		LIST,
		AUTO_CLICKER,
		CHAT_MACROS,
		CLICK_THROUGH_PLAYERS,
		MINESHAFT_ESP,
		LITTLEFOOT,
		PICKAXE_ABILITY,
		WILL_O_WISP,
		PING_OFFSET_MINER,
		DIANA_MOB_HITS,
		SLAYER_BOSS_SPAWN,
		ICE_SPRAY,
		ICE_ANNOUNCE,
		STORM,
		RELICS,
		KUUDRA_HITBOX,
		AUTO_FISH,
		SHORT_DAMAGE,
		PEST_ESP,
		ANIMATION,
		DROPPED_ITEMS,
		REND,
		KUUDRA_DISTANCE,
		KUUDRA_SNEAK,
		KUUDRA_CROESUS,
		KUUDRA_SUPPLY_SWAP,
		KITTY_FIXES
	}

	private enum Page {
		GENERAL("General"),
		DUNGEON("Dungeon"),
		FISHING("Fishing"),
		DIANA("Diana"),
		SLAYER("Slayer"),
		RENDER("Render"),
		KUUDRA("Kuudra"),
		MINING("Mining");

		private final String label;

		Page(String label) {
			this.label = label;
		}
	}

	private static final class IntegerSlider extends AbstractSliderButton {
		private final String label;
		private final String suffix;
		private final int min;
		private final int max;
		private final IntConsumer setter;

		private IntegerSlider(int x, int width, String label, int min, int max,
							  IntSupplier getter, IntConsumer setter, String suffix) {
			super(x, 0, width, 20, Component.empty(), normalize(getter.getAsInt(), min, max));
			this.label = label;
			this.suffix = suffix;
			this.min = min;
			this.max = max;
			this.setter = setter;
			updateMessage();
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			renderSlider(this, value, graphics);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + currentValue() + suffix));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}

		private int currentValue() {
			return (int) Math.round(min + value * (max - min));
		}

		private static double normalize(int value, int min, int max) {
			return (double) (value - min) / (max - min);
		}
	}

	private static final class DoubleSlider extends AbstractSliderButton {
		private final String label;
		private final String suffix;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer setter;

		private DoubleSlider(int x, int width, String label, double min, double max, double step,
							 DoubleSupplier getter, DoubleConsumer setter, String suffix) {
			super(x, 0, width, 20, Component.empty(), (getter.getAsDouble() - min) / (max - min));
			this.label = label;
			this.suffix = suffix;
			this.min = min;
			this.max = max;
			this.step = step;
			this.setter = setter;
			updateMessage();
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			renderSlider(this, value, graphics);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + format(currentValue()) + suffix));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}

		private double currentValue() {
			double raw = min + value * (max - min);
			return Math.round(raw / step) * step;
		}

		private static String format(double value) {
			if (Math.abs(value - Math.round(value)) < 0.0001) {
				return Integer.toString((int) Math.round(value));
			}
			return String.format(java.util.Locale.ROOT, "%.2f", value);
		}
	}
}
