package me.rattokens.rataddon.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RaTAddonRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class WorldRender {
	public record BoxOverlay(AABB bounds, int color, float width, int fillAlpha) {
		public BoxOverlay(AABB bounds, int color, float width) {
			this(bounds, color, width, 128);
		}
	}

	private WorldRender() {
	}

	public static void box(LevelRenderContext context, AABB box, int color, float width, boolean throughWalls) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack,
			throughWalls ? RaTAddonRenderTypes.espLinesNoDepth() : RenderTypes.LINES,
			(pose, consumer) -> renderBox(pose, consumer, box, color, width));
		poseStack.popPose();
	}

	public static void boxOverlay(LevelRenderContext context, AABB box, int color, float width) {
		boxOverlay(context, box, color, width, 128);
	}

	public static void boxOverlay(LevelRenderContext context, AABB box, int color, float width, int fillAlpha) {
		if (fillAlpha > 0) {
			filledBox(context, box, (Math.clamp(fillAlpha, 0, 255) << 24) | (color & 0xFFFFFF), true);
		}
		box(context, box, color, width, true);
	}

	/** Submit all through-wall fills and outlines in two batches, as Kitty does. */
	public static void boxOverlays(LevelRenderContext context, List<BoxOverlay> boxes) {
		boxOverlays(context, boxes, true);
	}

	public static void boxOverlays(LevelRenderContext context, List<BoxOverlay> boxes, boolean throughWalls) {
		if (boxes.isEmpty()) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		if (boxes.stream().anyMatch(box -> box.fillAlpha() > 0)) {
			context.submitNodeCollector().submitCustomGeometry(poseStack,
				throughWalls ? RaTAddonRenderTypes.espFilledNoDepth() : RenderTypes.debugFilledBox(),
				(pose, consumer) -> {
					for (BoxOverlay box : boxes) if (box.fillAlpha() > 0)
						renderFilledBox(pose, consumer, box.bounds(),
							(Math.clamp(box.fillAlpha(), 0, 255) << 24) | (box.color() & 0xFFFFFF));
				});
		}
		context.submitNodeCollector().submitCustomGeometry(poseStack,
			throughWalls ? RaTAddonRenderTypes.espLinesNoDepth() : RenderTypes.LINES,
			(pose, consumer) -> {
				for (BoxOverlay box : boxes)
					renderBox(pose, consumer, box.bounds(), box.color(), box.width());
			});
		poseStack.popPose();
	}

	public static void line(LevelRenderContext context, Vec3 start, Vec3 end, int color, float width,
							 boolean throughWalls) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack,
			throughWalls ? RaTAddonRenderTypes.espLinesNoDepth() : RenderTypes.LINES,
			(pose, consumer) -> line(pose, consumer,
				(float) start.x, (float) start.y, (float) start.z,
				(float) end.x, (float) end.y, (float) end.z, color, width));
		poseStack.popPose();
	}

	public static void beacon(LevelRenderContext context, Vec3 pos, int color) {
		beacon(context, pos, color, BeaconRenderer.MAX_RENDER_Y);
	}

	public static void beacon(LevelRenderContext context, Vec3 pos, int color, int height) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack stack = context.poseStack();
		stack.pushPose();
		stack.translate(pos.x - camera.x - 0.5, pos.y - camera.y, pos.z - camera.z - 0.5);
		BeaconRenderer.submitBeaconBeam(stack, context.submitNodeCollector(),
			BeaconRenderer.BEAM_LOCATION, 1.0F,
			client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(true),
			0, height, color,
			BeaconRenderer.SOLID_BEAM_RADIUS, BeaconRenderer.BEAM_GLOW_RADIUS);
		stack.popPose();
	}

	public static void filledBox(LevelRenderContext context, AABB box, int color, boolean throughWalls) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack,
			throughWalls ? RaTAddonRenderTypes.espFilledNoDepth() : RenderTypes.debugFilledBox(),
			(pose, consumer) -> renderFilledBox(pose, consumer, box, color));
		poseStack.popPose();
	}

	public static void ring(LevelRenderContext context, Vec3 center, double radius, double thickness,
		int segments, int color, boolean throughWalls) {
		if (radius <= 0 || thickness <= 0 || segments < 3) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack,
			throughWalls ? RaTAddonRenderTypes.espFilledNoDepth() : RenderTypes.debugFilledBox(),
			(pose, consumer) -> {
				double inner = radius - thickness / 2.0;
				double outer = radius + thickness / 2.0;
				float y = (float) center.y;
				for (int i = 0; i < segments; i++) {
					double a = Math.PI * 2.0 * i / segments;
					double b = Math.PI * 2.0 * (i + 1) / segments;
					float ix1 = (float) (center.x + Math.cos(a) * inner);
					float iz1 = (float) (center.z + Math.sin(a) * inner);
					float ox1 = (float) (center.x + Math.cos(a) * outer);
					float oz1 = (float) (center.z + Math.sin(a) * outer);
					float ix2 = (float) (center.x + Math.cos(b) * inner);
					float iz2 = (float) (center.z + Math.sin(b) * inner);
					float ox2 = (float) (center.x + Math.cos(b) * outer);
					float oz2 = (float) (center.z + Math.sin(b) * outer);
					quad(pose, consumer, color, ix1, y, iz1, ox1, y, oz1, ox2, y, oz2, ix2, y, iz2);
					quad(pose, consumer, color, ix2, y, iz2, ox2, y, oz2, ox1, y, oz1, ix1, y, iz1);
				}
			});
		poseStack.popPose();
	}

	/** Filled horizontal radius preview, matching the deployable highlight footprint. */
	public static void disc(LevelRenderContext context, Vec3 center, double radius, int segments,
		int color, boolean throughWalls) {
		if (radius <= 0 || segments < 3) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack stack = context.poseStack();
		stack.pushPose();
		stack.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(stack,
			throughWalls ? RaTAddonRenderTypes.espFilledNoDepth() : RenderTypes.debugFilledBox(),
			(pose, consumer) -> {
				float cx = (float) center.x, cy = (float) center.y, cz = (float) center.z;
				for (int i = 0; i < segments; i++) {
					double a = Math.PI * 2.0 * i / segments;
					double b = Math.PI * 2.0 * (i + 1) / segments;
					float ax = (float) (center.x + Math.cos(a) * radius);
					float az = (float) (center.z + Math.sin(a) * radius);
					float bx = (float) (center.x + Math.cos(b) * radius);
					float bz = (float) (center.z + Math.sin(b) * radius);
					quad(pose, consumer, color, cx,cy,cz, ax,cy,az, bx,cy,bz, cx,cy,cz);
					quad(pose, consumer, color, cx,cy,cz, bx,cy,bz, ax,cy,az, cx,cy,cz);
				}
			});
		stack.popPose();
	}

	/** Translucent deployable range volume. */
	public static void sphere(LevelRenderContext context, Vec3 center, double radius, int color,
		boolean throughWalls) {
		if (radius <= 0) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack stack = context.poseStack();
		stack.pushPose();
		stack.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(stack,
			throughWalls ? RaTAddonRenderTypes.espFilledNoDepth() : RenderTypes.debugFilledBox(),
			(pose, consumer) -> {
				for (int lat = 0; lat < 20; lat++) {
					double t1 = -Math.PI / 2 + Math.PI * lat / 20;
					double t2 = -Math.PI / 2 + Math.PI * (lat + 1) / 20;
					for (int lon = 0; lon < 36; lon++) {
						double a1 = Math.PI * 2 * lon / 36;
						double a2 = Math.PI * 2 * (lon + 1) / 36;
						float x11 = (float) (center.x + radius * Math.cos(t1) * Math.cos(a1));
						float z11 = (float) (center.z + radius * Math.cos(t1) * Math.sin(a1));
						float x12 = (float) (center.x + radius * Math.cos(t1) * Math.cos(a2));
						float z12 = (float) (center.z + radius * Math.cos(t1) * Math.sin(a2));
						float x21 = (float) (center.x + radius * Math.cos(t2) * Math.cos(a1));
						float z21 = (float) (center.z + radius * Math.cos(t2) * Math.sin(a1));
						float x22 = (float) (center.x + radius * Math.cos(t2) * Math.cos(a2));
						float z22 = (float) (center.z + radius * Math.cos(t2) * Math.sin(a2));
						float y1 = (float) (center.y + radius * Math.sin(t1));
						float y2 = (float) (center.y + radius * Math.sin(t2));
						quad(pose, consumer, color, x11,y1,z11, x21,y2,z21, x22,y2,z22, x12,y1,z12);
						quad(pose, consumer, color, x12,y1,z12, x22,y2,z22, x21,y2,z21, x11,y1,z11);
					}
				}
			});
		stack.popPose();
	}

	/** Filled circle that faces the camera, for aim waypoints. */
	public static void billboardCircle(LevelRenderContext context, Vec3 center, double radius,
		int segments, int color) {
		if (radius <= 0 || segments < 3) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack stack = context.poseStack();
		stack.pushPose();
		stack.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
		stack.rotate(context.levelState().cameraRenderState.orientation);
		context.submitNodeCollector().submitCustomGeometry(stack, RaTAddonRenderTypes.espFilledNoDepth(),
			(pose, consumer) -> {
				for (int i = 0; i < segments; i++) {
					double a = Math.PI * 2.0 * i / segments;
					double b = Math.PI * 2.0 * (i + 1) / segments;
					float ax = (float) (Math.cos(a) * radius), ay = (float) (Math.sin(a) * radius);
					float bx = (float) (Math.cos(b) * radius), by = (float) (Math.sin(b) * radius);
					quad(pose, consumer, color, 0, 0, 0, ax, ay, 0, bx, by, 0, bx, by, 0);
				}
			});
		stack.popPose();
	}

	private static void renderFilledBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int color) {
		float x1 = (float) (box.minX - 0.002), y1 = (float) (box.minY - 0.002),
			z1 = (float) (box.minZ - 0.002);
		float x2 = (float) (box.maxX + 0.002), y2 = (float) (box.maxY + 0.002),
			z2 = (float) (box.maxZ + 0.002);
		quad(pose, consumer, color, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1);
		quad(pose, consumer, color, x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2);
		quad(pose, consumer, color, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1);
		quad(pose, consumer, color, x2,y1,z2, x2,y2,z2, x1,y2,z2, x1,y1,z2);
		quad(pose, consumer, color, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2);
		quad(pose, consumer, color, x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int color,
		float ax,float ay,float az, float bx,float by,float bz, float cx,float cy,float cz, float dx,float dy,float dz) {
		consumer.addVertex(pose.pose(),ax,ay,az).setColor(color);
		consumer.addVertex(pose.pose(),bx,by,bz).setColor(color);
		consumer.addVertex(pose.pose(),cx,cy,cz).setColor(color);
		consumer.addVertex(pose.pose(),dx,dy,dz).setColor(color);
	}

	public static void text(LevelRenderContext context, Vec3 position, Component text, float scale,
							boolean throughWalls, int color) {
		text(context, position, text, scale, throughWalls, color, 0);
	}

	public static void text(LevelRenderContext context, Vec3 position, Component text, float scale,
							boolean throughWalls, int color, float yOffset) {
		Minecraft client = Minecraft.getInstance();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
		poseStack.rotate(context.levelState().cameraRenderState.orientation);
		poseStack.scale(scale, -scale, scale);
		context.submitNodeCollector().submitText(poseStack, -client.font.width(text) / 2.0F, yOffset,
			text.getVisualOrderText(), true,
			throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
			0xF000F0, color, 0, 0);
		poseStack.popPose();
	}

	public static void kittyText(LevelRenderContext context, Vec3 position, Component text,
			float scale, int color) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float distanceScale = Math.clamp((float) position.distanceTo(camera) * 0.02F, 0.5F, 25.0F);
		text(context, position, text, scale * distanceScale * 0.025F, true, color);
	}

	private static void renderBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int color, float width) {
		float x1 = (float) (box.minX - 0.003), y1 = (float) (box.minY - 0.003),
			z1 = (float) (box.minZ - 0.003);
		float x2 = (float) (box.maxX + 0.003), y2 = (float) (box.maxY + 0.003),
			z2 = (float) (box.maxZ + 0.003);
		line(pose, consumer, x1,y1,z1, x2,y1,z1, color, width);
		line(pose, consumer, x2,y1,z1, x2,y1,z2, color, width);
		line(pose, consumer, x2,y1,z2, x1,y1,z2, color, width);
		line(pose, consumer, x1,y1,z2, x1,y1,z1, color, width);
		line(pose, consumer, x1,y2,z1, x2,y2,z1, color, width);
		line(pose, consumer, x2,y2,z1, x2,y2,z2, color, width);
		line(pose, consumer, x2,y2,z2, x1,y2,z2, color, width);
		line(pose, consumer, x1,y2,z2, x1,y2,z1, color, width);
		line(pose, consumer, x1,y1,z1, x1,y2,z1, color, width);
		line(pose, consumer, x2,y1,z1, x2,y2,z1, color, width);
		line(pose, consumer, x2,y1,z2, x2,y2,z2, color, width);
		line(pose, consumer, x1,y1,z2, x1,y2,z2, color, width);
	}

	private static void line(PoseStack.Pose pose, VertexConsumer consumer,
		float ax, float ay, float az, float bx, float by, float bz, int color, float width) {
		float nx = bx - ax;
		float ny = by - ay;
		float nz = bz - az;
		consumer.addVertex(pose, ax, ay, az).setColor(color)
			.setNormal(pose, nx, ny, nz).setLineWidth(width);
		consumer.addVertex(pose, bx, by, bz).setColor(color)
			.setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}
