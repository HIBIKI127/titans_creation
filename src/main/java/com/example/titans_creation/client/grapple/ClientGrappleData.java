package com.example.titans_creation.client.grapple;

import com.example.titans_creation.grapple.GrappleManager;

import net.minecraft.world.phys.Vec3;

/**
 * 客户端抓钩状态：由服务端的 {@code GrappleSyncPacket} 填充，
 * 用于拉动阶段的本地预测与绳索渲染（飞行阶段只渲染绳索）。
 */
public final class ClientGrappleData {

    private static int phase = GrappleManager.PHASE_NONE;
    private static Vec3 position = Vec3.ZERO;
    private static Vec3 previousPosition = Vec3.ZERO;
    private static double pullSpeed;

    private ClientGrappleData() {
    }

    public static void set(int phase, double x, double y, double z, double pullSpeed) {
        ClientGrappleData.previousPosition = ClientGrappleData.position;
        ClientGrappleData.phase = phase;
        ClientGrappleData.position = new Vec3(x, y, z);
        ClientGrappleData.pullSpeed = pullSpeed;
    }

    public static void clear() {
        phase = GrappleManager.PHASE_NONE;
        position = Vec3.ZERO;
        previousPosition = Vec3.ZERO;
        pullSpeed = 0.0D;
    }

    /** 是否有钩爪（飞行中或拉动中）。 */
    public static boolean isActive() {
        return phase != GrappleManager.PHASE_NONE;
    }

    /** 是否处于拉动阶段（此时客户端做本地预测）。 */
    public static boolean isPulling() {
        return phase == GrappleManager.PHASE_PULLING;
    }

    /** 钩爪当前位置（飞行中）或锚点位置（拉动中）。 */
    public static Vec3 getPosition() {
        return position;
    }

    /** 渲染用位置：在上一 tick 与当前 tick 之间插值，避免快速飞行的钩爪看起来一顿一顿。 */
    public static Vec3 getRenderPosition(float partialTick) {
        if (previousPosition.equals(Vec3.ZERO) || !isActive()) {
            return position;
        }
        return previousPosition.lerp(position, partialTick);
    }

    public static double getPullSpeed() {
        return pullSpeed;
    }
}
