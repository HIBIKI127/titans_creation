package com.example.titans_creation.client;

import java.util.List;

import org.joml.Matrix4f;

import com.example.titans_creation.TitansCreationMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 战斗模式自定义高亮拖影渲染。
 *
 * 采用双层渲染策略：
 * 1. 外层发光层：使用 Additive Blending，创建高亮光晕效果，宽度较大
 * 2. 内层实心层：使用普通混合，绘制白热核心光带，宽度较小
 *
 * 每层都使用 ribbon（带状）几何，宽度沿路径动态变化（起点宽、尾端尖），
 * 颜色按挥舞进度从冷色（青蓝光晕 + 白热核心）渐变到暖色（橙红光晕），并按年龄淡出。
 * 该效果完全替代原版挥舞弧线粒子，作为攻击模式下武器挥舞的唯一视觉反馈。
 */
    @Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, value = Dist.CLIENT)
public final class CombatTrailRenderer {

    private CombatTrailRenderer() {
    }

    /** 内层光带半宽（方块）。 */
    private static final float INNER_HALF_WIDTH = 0.14F;
    /** 外层发光半宽（方块），是内层的 2.5 倍。 */
    private static final float OUTER_HALF_WIDTH = 0.36F;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        List<CombatClientState.TrailPoint> points = CombatClientState.getTrailPoints();
        if (points.size() < 2) {
            return;
        }
        renderTrail(mc, mc.gameRenderer.getMainCamera(), mc.level.getGameTime(), event.getPartialTick());
    }

    private static void renderTrail(Minecraft mc, Camera camera, long currentTick, float partialTick) {
        List<CombatClientState.TrailPoint> points = CombatClientState.getTrailPoints();
        int n = points.size();
        if (n < 2) {
            return;
        }

        Vec3 camPos = camera.getPosition();

        PoseStack pose = new PoseStack();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = pose.last().pose();

        // === 第一层：外层发光（Additive Blending） ===
        renderGlowLayer(points, matrix, camPos, currentTick, partialTick);

        // === 第二层：内层实心光带 ===
        renderCoreLayer(points, matrix, camPos, currentTick, partialTick);
    }

    /**
     * 外层发光层：使用 Additive Blending 创建高亮光晕效果。
     * 宽度更大，颜色更亮，alpha 较高形成强烈的发光边缘。
     */
    private static void renderGlowLayer(List<CombatClientState.TrailPoint> points, Matrix4f matrix,
                                        Vec3 camPos, long currentTick, float partialTick) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        boolean hasVertices = false;

        for (int i = 0; i < points.size() - 1; i++) {
            CombatClientState.TrailPoint p0 = points.get(i);
            CombatClientState.TrailPoint p1 = points.get(i + 1);

            float age0 = (currentTick - p0.birthTick + partialTick) / (float) CombatClientState.TRAIL_LIFETIME_TICKS;
            float age1 = (currentTick - p1.birthTick + partialTick) / (float) CombatClientState.TRAIL_LIFETIME_TICKS;
            float alpha0 = clamp01(1.0F - age0);
            float alpha1 = clamp01(1.0F - age1);
            if (alpha0 <= 0.0F && alpha1 <= 0.0F) {
                continue;
            }

            // 发光层颜色：高亮青蓝 -> 橙红
            int r0 = toByte(lerp(0.40F, 1.00F, p0.progress));
            int g0 = toByte(lerp(0.85F, 0.45F, p0.progress));
            int b0 = toByte(lerp(1.00F, 0.10F, p0.progress));
            int r1 = toByte(lerp(0.40F, 1.00F, p1.progress));
            int g1 = toByte(lerp(0.85F, 0.45F, p1.progress));
            int b1 = toByte(lerp(1.00F, 0.10F, p1.progress));

            // 发光层 alpha：头部最高 0.9，形成高亮光晕
            int a0 = toByte(alpha0 * 0.90F);
            int a1 = toByte(alpha1 * 0.90F);

            Vec3 seg = p1.position.subtract(p0.position);
            Vec3 toCam = camPos.subtract(p0.position);
            Vec3 perp = seg.cross(toCam);
            if (perp.lengthSqr() < 1.0E-12) {
                continue;
            }
            perp = perp.normalize();

            // 外层宽度：起点更宽，尾端收窄
            float w0 = OUTER_HALF_WIDTH * (0.25F + 0.75F * (1.0F - age0) * (1.0F - age0));
            float w1 = OUTER_HALF_WIDTH * (0.25F + 0.75F * (1.0F - age1) * (1.0F - age1));

            Vec3 a = p0.position.add(perp.scale(w0));
            Vec3 b = p0.position.subtract(perp.scale(w0));
            Vec3 c = p1.position.add(perp.scale(w1));
            Vec3 d = p1.position.subtract(perp.scale(w1));

            buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z)
                    .color(r0, g0, b0, a0).endVertex();
            buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z)
                    .color(r0, g0, b0, a0).endVertex();
            buffer.vertex(matrix, (float) d.x, (float) d.y, (float) d.z)
                    .color(r1, g1, b1, a1).endVertex();
            buffer.vertex(matrix, (float) c.x, (float) c.y, (float) c.z)
                    .color(r1, g1, b1, a1).endVertex();
            hasVertices = true;
        }

        if (!hasVertices) {
            buffer.end();
            return;
        }

        // Additive Blending 创建发光效果
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        BufferUploader.drawWithShader(buffer.end());

        // 恢复状态
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 内层实心光带：使用普通 Alpha Blending，绘制清晰的核心光带。
     * 宽度较小，颜色更鲜明。
     */
    private static void renderCoreLayer(List<CombatClientState.TrailPoint> points, Matrix4f matrix,
                                         Vec3 camPos, long currentTick, float partialTick) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        boolean hasVertices = false;

        for (int i = 0; i < points.size() - 1; i++) {
            CombatClientState.TrailPoint p0 = points.get(i);
            CombatClientState.TrailPoint p1 = points.get(i + 1);

            float age0 = (currentTick - p0.birthTick + partialTick) / (float) CombatClientState.TRAIL_LIFETIME_TICKS;
            float age1 = (currentTick - p1.birthTick + partialTick) / (float) CombatClientState.TRAIL_LIFETIME_TICKS;
            float alpha0 = clamp01(1.0F - age0);
            float alpha1 = clamp01(1.0F - age1);
            if (alpha0 <= 0.0F && alpha1 <= 0.0F) {
                continue;
            }

            // 核心层颜色：白热核心，随进度轻微转暖（白 -> 暖橙）
            int r0 = toByte(1.00F);
            int g0 = toByte(lerp(1.00F, 0.75F, p0.progress));
            int b0 = toByte(lerp(1.00F, 0.40F, p0.progress));
            int r1 = toByte(1.00F);
            int g1 = toByte(lerp(1.00F, 0.75F, p1.progress));
            int b1 = toByte(lerp(1.00F, 0.40F, p1.progress));
            int a0 = toByte(alpha0);
            int a1 = toByte(alpha1);

            Vec3 seg = p1.position.subtract(p0.position);
            Vec3 toCam = camPos.subtract(p0.position);
            Vec3 perp = seg.cross(toCam);
            if (perp.lengthSqr() < 1.0E-12) {
                continue;
            }
            perp = perp.normalize();

            // 核心层宽度：起点宽，尾端尖
            float w0 = INNER_HALF_WIDTH * (0.2F + 0.8F * (1.0F - age0));
            float w1 = INNER_HALF_WIDTH * (0.2F + 0.8F * (1.0F - age1));

            Vec3 a = p0.position.add(perp.scale(w0));
            Vec3 b = p0.position.subtract(perp.scale(w0));
            Vec3 c = p1.position.add(perp.scale(w1));
            Vec3 d = p1.position.subtract(perp.scale(w1));

            buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z)
                    .color(r0, g0, b0, a0).endVertex();
            buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z)
                    .color(r0, g0, b0, a0).endVertex();
            buffer.vertex(matrix, (float) d.x, (float) d.y, (float) d.z)
                    .color(r1, g1, b1, a1).endVertex();
            buffer.vertex(matrix, (float) c.x, (float) c.y, (float) c.z)
                    .color(r1, g1, b1, a1).endVertex();
            hasVertices = true;
        }

        if (!hasVertices) {
            buffer.end();
            return;
        }

        // 普通 Alpha Blending
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int toByte(float f) {
        int i = (int) (clamp01(f) * 255.0F);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
