package me.rattokens.rataddon.feature;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatMacrosScreen extends Screen {
	private final Screen parent;
	private int selected = -1;
	private int scroll;
	private int detailScroll;
	private final List<DetailWidget> detailWidgets = new ArrayList<>();
	private final Map<Integer, String> testInputs = new HashMap<>();
	private EditBox trigger;
	private EditBox command;
	private EditBox testText;

	public ChatMacrosScreen(Screen parent) {
		super(Component.literal("Chat Macro Manager"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		detailWidgets.clear();
		if (selected < 0 && !ChatMacros.list().isEmpty()) selected = 0;
		if (selected >= ChatMacros.list().size()) selected = ChatMacros.list().size() - 1;
		scroll = Math.clamp(scroll, 0, Math.max(0, ChatMacros.list().size() - visibleRows()));
		int leftWidth = Math.min(190, Math.max(110, width / 3));
		int detailX = leftWidth + 24;
		int detailWidth = Math.max(80, width - detailX - 12);
		int actionWidth = (detailWidth - 6) / 2;
		addRenderableWidget(Button.builder(Component.literal("Add"), button -> {
			selected = ChatMacros.add();
			detailScroll = 0;
			scroll = Math.max(0, selected - visibleRows() + 1);
			rebuildWidgets();
		}).bounds(12, height - 52, leftWidth - 12, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
			.bounds(12, height - 28, leftWidth - 12, 20).build());

		int rows = visibleRows();
		var macros = ChatMacros.list();
		for (int row = 0; row < rows && scroll + row < macros.size(); row++) {
			int index = scroll + row;
			var macro = macros.get(index);
			String name = macro.trigger().isBlank() ? "(new macro)" : macro.trigger();
			if (name.length() > 22) name = name.substring(0, 19) + "...";
			addRenderableWidget(Button.builder(Component.literal((macro.active() ? "● " : "○ ") + name), button -> {
				selected = index;
				detailScroll = 0;
				rebuildWidgets();
			}).bounds(12, 42 + row * 23, leftWidth - 12, 20).build());
		}
		if (selected < 0) return;
		var macro = macros.get(selected);
		addDetail(Button.builder(Component.literal(macro.active() ? "Enabled" : "Disabled"), button -> {
			saveSelected(!ChatMacros.list().get(selected).active());
			rebuildWidgets();
		}).bounds(detailX, 42, actionWidth, 20).build());
		addDetail(Button.builder(Component.literal("Delete"), button -> {
			ChatMacros.delete(selected);
			Map<Integer, String> shifted = new HashMap<>();
			for (var entry : testInputs.entrySet()) {
				if (entry.getKey() < selected) shifted.put(entry.getKey(), entry.getValue());
				else if (entry.getKey() > selected) shifted.put(entry.getKey() - 1, entry.getValue());
			}
			testInputs.clear();
			testInputs.putAll(shifted);
			selected = Math.min(selected, ChatMacros.list().size() - 1);
			rebuildWidgets();
		}).bounds(detailX + actionWidth + 6, 42, actionWidth, 20).build());
		trigger = new EditBox(font, detailX, 94, detailWidth, 20, Component.literal("Trigger Regex"));
		trigger.setMaxLength(160);
		trigger.setValue(macro.trigger());
		trigger.setResponder(value -> saveSelected(macroActive()));
		addDetail(trigger);
		command = new EditBox(font, detailX, 140, detailWidth, 20, Component.literal("Command"));
		command.setMaxLength(160);
		command.setValue(macro.command());
		command.setResponder(value -> saveSelected(macroActive()));
		addDetail(command);
		testText = new EditBox(font, detailX, 186, detailWidth, 20, Component.literal("Test Text"));
		testText.setMaxLength(160);
		testText.setValue(testInputs.getOrDefault(selected, ""));
		testText.setResponder(value -> testInputs.put(selected, value));
		addDetail(testText);
		layoutDetail();
	}

	private record DetailWidget(AbstractWidget widget, int y) {}
	private void addDetail(AbstractWidget widget) {
		detailWidgets.add(new DetailWidget(widget, widget.getY()));
		addRenderableWidget(widget);
	}
	private int detailMaxScroll() { return Math.max(0, 223 - (height - 8)); }
	private void layoutDetail() {
		detailScroll = Math.clamp(detailScroll, 0, detailMaxScroll());
		for (DetailWidget item : detailWidgets) {
			int y = item.y - detailScroll;
			item.widget.setY(y);
			item.widget.visible = y >= 42 && y + item.widget.getHeight() <= height - 8;
		}
	}
	private void detailLabel(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
		int shown = y - detailScroll;
		if (shown >= 42 && shown + 9 <= height - 8)
			graphics.text(font, Component.literal(value), x, shown, color, false);
	}

	private boolean macroActive() {
		return selected >= 0 && selected < ChatMacros.list().size() && ChatMacros.list().get(selected).active();
	}

	private void saveSelected(boolean active) {
		if (selected >= 0 && trigger != null && command != null) {
			ChatMacros.set(selected, trigger.getValue(), command.getValue(), active);
		}
	}

	private int visibleRows() {
		return Math.max(1, (height - 106) / 23);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX < Math.min(190, Math.max(110, width / 3)) + 12) {
			int max = Math.max(0, ChatMacros.list().size() - visibleRows());
			scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, max);
			rebuildWidgets();
			return true;
		}
		if (detailMaxScroll() > 0) {
			detailScroll = Math.clamp(detailScroll - (int) Math.signum(vertical) * 22, 0, detailMaxScroll());
			layoutDetail();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("Chat Macro Manager"), width / 2, 14, 0xFFFFFFFF);
		if (selected < 0 || selected >= ChatMacros.list().size()) {
			graphics.text(font, Component.literal("Add a macro to begin"), Math.min(190, Math.max(110, width / 3)) + 24, 74, 0xFFAAAAAA, false);
			return;
		}
		int x = Math.min(190, Math.max(110, width / 3)) + 24;
		detailLabel(graphics, "Trigger Regex", x, 78, 0xFFFFFFFF);
		detailLabel(graphics, "Command", x, 124, 0xFFFFFFFF);
		detailLabel(graphics, "Test Text", x, 170, 0xFFFFFFFF);
		String result;
		int color;
		if (trigger.getValue().isBlank() || testText.getValue().isBlank()) {
			result = "Enter trigger and test text";
			color = 0xFFAAAAAA;
		} else {
			try {
				boolean match = Pattern.compile(trigger.getValue()).matcher(testText.getValue()).find();
				result = match ? "Match" : "No match";
				color = match ? 0xFF88DD88 : 0xFFCCAAAA;
			} catch (PatternSyntaxException e) {
				result = "Invalid regex";
				color = 0xFFFFAA77;
			}
		}
		detailLabel(graphics, "Result: " + result, x, 214, color);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
