package net.minecraft.client.renderer.rendertype;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class RaTAddonRenderTypes {
	private static final RenderPipeline ESP_LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("rataddon", "pipeline/esp_lines_no_depth"))
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(Optional.empty())
		.build());
	private static final RenderPipeline ESP_FILLED_NO_DEPTH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("rataddon", "pipeline/esp_filled_no_depth"))
		.withDepthStencilState(Optional.empty())
		.build());

	private static final RenderType ESP_LINES_NO_DEPTH = RenderType.create(
		"rataddon_esp_lines_no_depth",
		RenderSetup.builder(ESP_LINES_NO_DEPTH_PIPELINE)
			.createRenderSetup()
	);
	private static final RenderType ESP_FILLED_NO_DEPTH = RenderType.create(
		"rataddon_esp_filled_no_depth",
		RenderSetup.builder(ESP_FILLED_NO_DEPTH_PIPELINE)
			.sortOnUpload()
			.createRenderSetup()
	);

	private RaTAddonRenderTypes() {
	}

	public static RenderType espLinesNoDepth() {
		return ESP_LINES_NO_DEPTH;
	}

	public static RenderType espFilledNoDepth() {
		return ESP_FILLED_NO_DEPTH;
	}
}
