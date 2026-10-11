package com.example.titans_creation.client.grapple;

import com.example.titans_creation.TitansCreationMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 抓钩的绳索与钩头渲染。
 *
 * <ul>
 *   <li><b>绳索</b>：使用原版拴绳的渲染类型 {@link RenderType#leash()}（与拴绳完全同款：三角带 + 拴绳着色器），
 *       沿绳索分为 24 段并以面向相机的带状绘制，因此比原来的 1 像素线条更粗、外观与原版拴绳一致。
 *       飞行阶段带一点下垂（绳子松着飞出去），拉动阶段绷直。</li>
 *   <li><b>钩头</b>：在钩爪末端渲染一支<b>真实的原版箭实体</b>（通过实体渲染调度器渲染，外观与射出的箭完全相同），
 *       朝向为玩家指向钩爪的方向，也就是"扎进目标"的方向。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, value = Dist.CLIENT)
public final class GrappleRopeRenderer {

    /** 绳索分段数（与原版拴绳同量级）。 */
    private static final int SEGMENTS = 24;
    /** 绳索半宽（格）：比原来的线条粗得多。 */
    private static final float ROPE_HALF_WIDTH = 0.055F;
    /** 飞行阶段的下垂幅度（格）。 */
    private static final double FLIGHT_SAG = 0.22D;
    /** 绳索颜色（原版拴绳的黄褐色）。 */
    private static final int ROPE_RED = 0xC8;
    private static final int ROPE_GREEN = 0xAC;
    private static final int ROPE_BLUE = 0x82;

    private GrappleRopeRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!ClientGrappleData.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        Vec3 start = player.getPosition(partialTick).add(0.0D, player.getBbHeight() * 0.65D, 0.0D);
        Vec3 end = ClientGrappleData.getRenderPosition(partialTick);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        renderRope(minecraft, buffers, matrix, start, end, camera);
        renderArrowHead(minecraft, buffers, pose, start, end, camera, partialTick);
        buffers.endBatch();

        pose.popPose();
    }

    // ==================== 绳索（原版拴绳同款渲染） ====================

    private static void renderRope(Minecraft minecraft, MultiBufferSource buffers, Matrix4f matrix,
                                   Vec3 start, Vec3 end, Vec3 camera) {
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 1.0E-4D) {
            return;
        }
        Vec3 direction = axis.scale(1.0D / length);
        // 飞行阶段绳子松弛下垂，拉动阶段绷直
        double sag = ClientGrappleData.isPulling() ? 0.0D : FLIGHT_SAG * Math.min(1.0D, length / 8.0D);
        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(end));

        VertexConsumer consumer = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / (float) SEGMENTS;
            Vec3 point = start.add(axis.scale(t));
            if (sag > 0.0D) {
                point = point.subtract(0.0D, sag * Math.sin(Math.PI * t), 0.0D);
            }

            // 面向相机的带状：取"绳索方向 × 视线方向"作为宽度方向
            Vec3 toCamera = camera.subtract(point);
            if (toCamera.lengthSqr() < 1.0E-6D) {
                toCamera = new Vec3(0.0D, 1.0D, 0.0D);
            }
            Vec3 side = direction.cross(toCamera.normalize());
            if (side.lengthSqr() < 1.0E-6D) {
                side = new Vec3(1.0D, 0.0D, 0.0D);
            }
            side = side.normalize().scale(ROPE_HALF_WIDTH);

            ropeVertex(consumer, matrix, point.add(side), light);
            ropeVertex(consumer, matrix, point.subtract(side), light);
        }
    }

    private static void ropeVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 vertex, int light) {
        consumer.vertex(matrix, (float) vertex.x, (float) vertex.y, (float) vertex.z)
                .color(ROPE_RED, ROPE_GREEN, ROPE_BLUE, 0xFF)
                .uv2(light)
                .endVertex();
    }

    // ==================== 钩头（原版箭实体模型） ====================

    private static void renderArrowHead(Minecraft minecraft, MultiBufferSource buffers, PoseStack pose,
                                        Vec3 start, Vec3 end, Vec3 camera, float partialTick) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        direction = direction.normalize();

        // 用一支真实的原版箭实体来渲染钩头，外观与射出的箭完全一致
        Arrow arrow = new Arrow(minecraft.level, end.x, end.y, end.z);
        arrow.setPos(end.x, end.y, end.z);

        float yaw = (float) (Mth.atan2(direction.x, direction.z) * (180.0F / (float) Math.PI));
        float pitch = (float) (-Mth.atan2(direction.y,
                Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0F / (float) Math.PI));
        arrow.setYRot(yaw);
        arrow.setXRot(pitch);
        arrow.yRotO = yaw;
        arrow.xRotO = pitch;

        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(end));
        minecraft.getEntityRenderDispatcher().render(arrow,
                end.x - camera.x, end.y - camera.y, end.z - camera.z,
                yaw, partialTick, pose, buffers, light);
    }
}
