package com.example.titans_creation.client;

import com.example.titans_creation.CombatNetwork;
import com.example.titans_creation.CombatToolType;
import com.example.titans_creation.CombatToolTypeRegistry;
import com.example.titans_creation.TitansCreationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端战斗模式事件处理。
 *
 * 职责： 1. 战斗模式切换（Shift + 玩家绑定的键）。 2. 视角拖动 -> 挥动方向指针（移动视角时显示指针，停止移动时自动回中）。 3.
 * 长按攻击键 + 视角移动弧度 > 5° -> 自动挥舞（发送 {@link CombatNetwork.CombatSwingStartPacket}）。
 * 光标处于回中状态时，长按攻击键不会发动攻击。 4. 每个客户端 tick：推进挥舞状态、根据视角拖动累积额外范围、采样拖影点。 5. 隐藏原版十字准星（由
 * {@link CombatCursorOverlay} 绘制自定义光标）。
 *
 * 工具类型通过 {@link CombatToolTypeRegistry} 查询，支持原版工具和自定义武器。
 */
    @Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, value = Dist.CLIENT)
public class ClientCombatEvents {

    /**
     * 视角拖动幅度阈值（度），超过视为"正在移动视角"。
     */
    private static final double DRAG_THRESHOLD = 1.5;
    /**
     * 长按攻击键时，视角移动弧度超过此值（度）则自动触发挥舞。
     */
    private static final float SWING_TRIGGER_ARC_DEG = 5.0F;
    /**
     * 斧类工具伐树触发：瞬时（每 tick）视角移动角度超过此值（度）时触发伐树。
     */
    private static final float TREE_FELL_INSTANT_ARC_DEG = 10.0F;

    /**
     * 弧形拖影几何参数。
     */
    private static final double ARC_DIST = 2.0;   // 弧心距眼睛距离
    private static final double ARC_RADIUS = 1.6; // 弧半径
    /**
     * 每 tick 采样的拖影子点数（让弧线更平滑）。
     */
    private static final int TRAIL_SUB_SAMPLES = 3;

    // 视角跟踪：用于根据视角拖动方向更新挥动方向指针
    private static float prevYaw;
    private static float prevPitch;
    private static boolean prevInitialized = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 切换战斗模式：Shift + 玩家在控制设置中绑定的键（默认无绑定，需玩家自行绑定）
        if (mc.screen == null
                && CombatKeyMappings.TOGGLE_COMBAT_MODE.consumeClick()
                && Screen.hasShiftDown()) {
            CombatClientState.toggleCombatMode();
            prevInitialized = false; // 重置视角跟踪
            boolean on = CombatClientState.isCombatMode();
            mc.player.displayClientMessage(
                    Component.translatable(on
                        ? "msg.titans_creation.combat_mode.on"
                        : "msg.titans_creation.combat_mode.off"),
                    true);
        }

        if (!CombatClientState.isCombatMode() || mc.screen != null) {
            prevInitialized = false;
            return;
        }

        LocalPlayer player = mc.player;
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        // === 视角移动追踪（所有状态下都执行） ===
        // 移动视角时显示指针方向；停止移动时自动回中
        if (prevInitialized) {
            double dyaw = Mth.wrapDegrees(yaw - prevYaw);
            double dpitch = pitch - prevPitch;
            double mag = Math.abs(dyaw) + Math.abs(dpitch);
            if (mag > DRAG_THRESHOLD) {
                // 正在移动视角：更新指针方向
                float angleDeg = (float) Math.toDegrees(Math.atan2(dyaw, -dpitch));
                CombatClientState.setDirection(angleDeg);

                // 长按攻击键时累积弧度
                if (CombatClientState.isAttackHeld()) {
                    float arc = (float) Math.sqrt(dyaw * dyaw + dpitch * dpitch);
                    CombatClientState.accumulateArc(arc);

                    // 斧类工具伐树检测：瞬时角度 > 30° 且工具支持伐树且当前不忙碌
                    if (arc >= TREE_FELL_INSTANT_ARC_DEG && !CombatClientState.isBusy()) {
                        CombatToolType axeType = CombatToolTypeRegistry.getForStack(player.getMainHandItem());
                        if (axeType != null && axeType.canFellTrees()) {
                            triggerTreeFell(mc, player, axeType);
                            CombatClientState.resetAccumulatedArc();
                        }
                    }
                }
            } else {
                // 视角未移动：自动回中
                CombatClientState.clearDirection();
            }
        }
        prevYaw = yaw;
        prevPitch = pitch;
        prevInitialized = true;

        // === 挥舞状态处理 ===
        if (CombatClientState.isSwingActive()) {
            // 挥舞进行中：累积视角拖动带来的额外范围，并采样拖影点
            tickActiveSwing(mc, player);
        } else if (CombatClientState.isBusy()) {
            // 收招冷却：仅推进计数
            CombatClientState.advanceSwingTick();
        } else {
            // 空闲：检查是否满足长按+拖动触发挥舞的条件
            CombatToolType toolType = CombatToolTypeRegistry.getForStack(player.getMainHandItem());
            if (toolType != null
                    && CombatClientState.isAttackHeld()
                    && CombatClientState.hasDirection()
                    && CombatClientState.getAccumulatedArcDeg() >= SWING_TRIGGER_ARC_DEG) {
                triggerSwing(mc, player, toolType);
                CombatClientState.resetAccumulatedArc();
            }
        }

        // 拖影点过期清理
        if (mc.level != null) {
            CombatClientState.cullTrail(mc.level.getGameTime());
        }
    }

    /**
     * 挥舞活跃阶段每 tick 处理：累积额外范围、采样拖影点、推进状态。
     */
    private static void tickActiveSwing(Minecraft mc, LocalPlayer player) {
        Vec3 forward0 = CombatClientState.getForward0();
        Vec3 swingDir = CombatClientState.getSwingDir();
        if (forward0.lengthSqr() < 1.0E-8 || swingDir.lengthSqr() < 1.0E-8 || mc.level == null) {
            CombatClientState.advanceSwingTick();
            return;
        }

        // 当前视线在挥舞平面内相对 forward0 的角度（朝 swingDir 为正）
        Vec3 currentLook = player.getLookAngle().normalize();
        double a = currentLook.dot(forward0);
        double b = currentLook.dot(swingDir);
        float viewPhiDeg = (float) Math.toDegrees(Math.atan2(b, a));
        CombatClientState.accumulateExtraRange(viewPhiDeg);

        // 采样拖影子点：覆盖 [本 tick 起点进度, 本 tick 终点进度] 区间，
        // 保证子点沿弧线均匀分布（此前 prevProgress 与当前进度相同导致子点全部重合）。
        int tick = currentSwingTick();
        int swingDuration = CombatClientState.getSwingDurationTicks();
        float startProgress = (float) tick / swingDuration;
        float endProgress = (float) (tick + 1) / swingDuration;
        long gameTick = mc.level.getGameTime();
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 center = eyePos.add(forward0.scale(ARC_DIST));
        float extra = CombatClientState.getExtraRangeDeg();
        float baseHalf = CombatClientState.getBaseHalfAngleDeg();
        float endPhi = baseHalf + extra;

        for (int s = 1; s <= TRAIL_SUB_SAMPLES; s++) {
            float subT = (float) s / TRAIL_SUB_SAMPLES;
            float progress = startProgress + (endProgress - startProgress) * subT;
            // 当前 φ：从 -baseHalf 到 +(baseHalf+extra)
            float phiDeg = -baseHalf + (endPhi + baseHalf) * progress;
            double phiRad = Math.toRadians(phiDeg);
            Vec3 tip = center
                    .add(forward0.scale(ARC_RADIUS * Math.cos(phiRad)))
                    .add(swingDir.scale(ARC_RADIUS * Math.sin(phiRad)));
            CombatClientState.addTrailPoint(tip, gameTick, progress);
        }

        CombatClientState.advanceSwingTick();
    }

    /**
     * 获取当前挥舞 tick（在 advance 之前）。
     */
    private static int currentSwingTick() {
        float p = CombatClientState.getSwingProgress();
        return Math.round(p * CombatClientState.getSwingDurationTicks());
    }

    /**
     * 触发一次方向挥舞：计算挥舞几何、启动客户端状态机、发送战挥包到服务端。 调用前需确保：非忙碌、有方向、手持战斗工具。
     *
     * @param toolType 当前手持工具的类型（决定挥舞参数）
     */
    private static void triggerSwing(Minecraft mc, LocalPlayer player, CombatToolType toolType) {
        // 计算挥舞几何（forward0 / swingDir）
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 camRight = forward.cross(worldUp);
        if (camRight.lengthSqr() < 1.0E-8) {
            camRight = new Vec3(1.0, 0.0, 0.0);
        } else {
            camRight = camRight.normalize();
        }
        Vec3 camUp = camRight.cross(forward).normalize();
        float angleRad = (float) Math.toRadians(CombatClientState.getSwingAngle());
        float rightComp = (float) Math.sin(angleRad);
        float upComp = (float) Math.cos(angleRad);
        Vec3 swingDir = camRight.scale(rightComp).add(camUp.scale(upComp));
        if (swingDir.lengthSqr() < 1.0E-12) {
            swingDir = camUp;
        } else {
            swingDir = swingDir.normalize();
        }

        if (!CombatClientState.tryStartSwing(forward, swingDir, toolType)) {
            return;
        }

        // 在起点先采样一个拖影点（progress=0）
        if (mc.level != null) {
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 center = eyePos.add(forward.scale(ARC_DIST));
            float phiDeg = -CombatClientState.getBaseHalfAngleDeg();
            double phiRad = Math.toRadians(phiDeg);
            Vec3 tip = center
                    .add(forward.scale(ARC_RADIUS * Math.cos(phiRad)))
                    .add(swingDir.scale(ARC_RADIUS * Math.sin(phiRad)));
            CombatClientState.addTrailPoint(tip, mc.level.getGameTime(), 0.0F);
        }

        // 发送战挥开始包到服务端（服务端按挥舞平面做逐 tick 渐进 AoE）
        CombatNetwork.CHANNEL.sendToServer(
                new CombatNetwork.CombatSwingStartPacket(CombatClientState.getSwingAngle()));
    }

    /**
     * 触发伐树：与普通挥舞类似的视觉反馈，但发送伐树包到服务端。
     * 服务端会通过射线检测找到目标原木方块，BFS 搜索整棵树并破坏。
     *
     * @param toolType 当前手持工具类型（必须 canFellTrees()）
     */
    private static void triggerTreeFell(Minecraft mc, LocalPlayer player, CombatToolType toolType) {
        // 计算挥舞几何（forward0 / swingDir），与 triggerSwing 一致
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 camRight = forward.cross(worldUp);
        if (camRight.lengthSqr() < 1.0E-8) {
            camRight = new Vec3(1.0, 0.0, 0.0);
        } else {
            camRight = camRight.normalize();
        }
        Vec3 camUp = camRight.cross(forward).normalize();
        float angleRad = (float) Math.toRadians(CombatClientState.getSwingAngle());
        Vec3 swingDir = camRight.scale(Math.sin(angleRad)).add(camUp.scale(Math.cos(angleRad)));
        if (swingDir.lengthSqr() < 1.0E-12) {
            swingDir = camUp;
        } else {
            swingDir = swingDir.normalize();
        }

        // 启动客户端挥舞状态（用于拖影视觉效果）
        if (!CombatClientState.tryStartSwing(forward, swingDir, toolType)) {
            return;
        }

        // 在起点采样拖影点
        if (mc.level != null) {
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 center = eyePos.add(forward.scale(ARC_DIST));
            float phiDeg = -CombatClientState.getBaseHalfAngleDeg();
            double phiRad = Math.toRadians(phiDeg);
            Vec3 tip = center
                    .add(forward.scale(ARC_RADIUS * Math.cos(phiRad)))
                    .add(swingDir.scale(ARC_RADIUS * Math.sin(phiRad)));
            CombatClientState.addTrailPoint(tip, mc.level.getGameTime(), 0.0F);
        }

        // 发送伐树包到服务端
        CombatNetwork.CHANNEL.sendToServer(
                new CombatNetwork.TreeFellPacket(CombatClientState.getSwingAngle()));
    }

    /**
     * 战斗模式下，追踪鼠标左键的按下/释放状态，并取消原版攻击。 挥舞由 onClientTick 中的长按+拖动逻辑自动触发，不再由单击触发。
     */
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        if (!CombatClientState.isCombatMode()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!CombatToolTypeRegistry.isCombatTool(mc.player.getMainHandItem())) {
            return;
        }

        if (event.getAction() == 1) {
            // 左键按下：开始长按
            CombatClientState.setAttackHeld(true);
        } else if (event.getAction() == 0) {
            // 左键释放：结束长按
            CombatClientState.setAttackHeld(false);
        }

        // 战斗模式下始终取消原版攻击
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    /**
     * 战斗模式下隐藏原版十字准星，改由 CombatCursorOverlay 绘制自定义光标。
     */
    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!CombatClientState.isCombatMode()) {
            return;
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            event.setCanceled(true);
        }
    }
}
