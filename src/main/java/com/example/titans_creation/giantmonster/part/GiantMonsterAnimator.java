package com.example.titans_creation.giantmonster.part;

import java.util.HashMap;
import java.util.Map;

import com.example.titans_creation.giantmonster.GiantMonster;
import com.example.titans_creation.giantmonster.GiantMonsterState;

import net.minecraft.util.Mth;

/**
 * 动画来源：根据实体状态计算骨骼姿态。
 *
 * <p><b>本实现逐帧对齐原版 {@code HumanoidModel.setupAnim}</b>（原版巨人使用的模型），
 * 因此部位碰撞箱会与原版模型的边缘保持一致：
 * <ul>
 *   <li>四肢摆动幅度、相位与符号与原版完全一致
 *       （手臂 {@code cos(limbSwing * 0.6662 + π) * limbSwingAmount}，
 *        腿部 {@code cos(limbSwing * 0.6662) * 1.4 * limbSwingAmount}，单位弧度）；</li>
 *   <li>模型没有"呼吸起伏"，这里也不添加任何额外位移；</li>
 *   <li>头部只做旋转（跟随视线），不做位移；</li>
 *   <li>攻击时手臂按原版攻击动画向前挥出；</li>
 *   <li>死亡时按原版规则（{@code sqrt(死亡进度 * 1.6)} 倍的 90° 侧倾）整体倒地。</li>
 * </ul>
 *
 * <p>骨骼旋转围绕部位定义中的 {@code pivot}（关节）进行，手臂绕肩、腿绕髋，
 * 因此碰撞箱会随动画真实移动。接入外部模型时，只需把本方法改为读取模型骨骼矩阵。
 */
public final class GiantMonsterAnimator {

    public static final String BONE_ROOT = "root";
    public static final String BONE_BODY = "body";
    public static final String BONE_HEAD = "head";
    public static final String BONE_ARM_LEFT = "arm_left";
    public static final String BONE_ARM_RIGHT = "arm_right";
    public static final String BONE_LEG_LEFT = "leg_left";
    public static final String BONE_LEG_RIGHT = "leg_right";
    public static final String BONE_TAIL = "tail";

    /** 原版 {@code HumanoidModel} 的行走摆动系数。 */
    private static final float WALK_FREQUENCY = 0.6662F;
    /** 腿部摆动幅度（原版为 1.4 弧度）。 */
    private static final float LEG_AMPLITUDE = 1.4F;
    /** 死亡侧倾角度（原版 {@code LivingEntityRenderer#getFlipDegrees} 默认 90°）。 */
    private static final double DEATH_FLIP_DEGREES = 90.0D;

    private GiantMonsterAnimator() {
    }

    /** 计算当前时刻的姿态。 */
    public static GiantMonsterPose compute(GiantMonster monster, float partialTick) {
        Map<String, GiantMonsterPartTransform> bones = new HashMap<>(8);

        if (monster.getState() == GiantMonsterState.DEATH) {
            // 原版死亡表现：sqrt(((deathTime + partial - 1) / 20) * 1.6) 倍的 90° 侧倾
            float progress = ((monster.deathTime + partialTick - 1.0F) / 20.0F) * 1.6F;
            progress = Mth.sqrt(Math.max(0.0F, progress));
            progress = Math.min(1.0F, progress);
            bones.put(BONE_ROOT, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, progress * DEATH_FLIP_DEGREES));
            bones.put(BONE_HEAD, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
            bones.put(BONE_ARM_LEFT, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
            bones.put(BONE_ARM_RIGHT, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
            bones.put(BONE_LEG_LEFT, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
            bones.put(BONE_LEG_RIGHT, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
            bones.put(BONE_BODY, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
            return GiantMonsterPose.of(bones);
        }

        // ===== 与原版 HumanoidModel.setupAnim 完全一致的四肢摆动 =====
        float limbSwing = monster.walkAnimation.position(partialTick);
        float limbAmount = monster.walkAnimation.speed(partialTick);
        double armLeftRot = Mth.cos(limbSwing * WALK_FREQUENCY) * limbAmount;
        double armRightRot = Mth.cos(limbSwing * WALK_FREQUENCY + (float) Math.PI) * limbAmount;
        double legLeftRot = Mth.cos(limbSwing * WALK_FREQUENCY + (float) Math.PI) * LEG_AMPLITUDE * limbAmount;
        double legRightRot = Mth.cos(limbSwing * WALK_FREQUENCY) * LEG_AMPLITUDE * limbAmount;

        // 攻击动画（近似原版 HumanoidModel 的攻击挥臂）：手臂向前挥出
        float attack = monster.getAttackAnim(partialTick);
        if (attack > 0.0F) {
            float swing = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
            double attackRot = -swing * 1.2D;
            armLeftRot += attackRot;
            armRightRot += attackRot;
        }

        bones.put(BONE_ARM_LEFT, transform(0.0D, 0.0D, 0.0D, 0.0D, Math.toDegrees(armLeftRot), 0.0D));
        bones.put(BONE_ARM_RIGHT, transform(0.0D, 0.0D, 0.0D, 0.0D, Math.toDegrees(armRightRot), 0.0D));
        bones.put(BONE_LEG_LEFT, transform(0.0D, 0.0D, 0.0D, 0.0D, Math.toDegrees(legLeftRot), 0.0D));
        bones.put(BONE_LEG_RIGHT, transform(0.0D, 0.0D, 0.0D, 0.0D, Math.toDegrees(legRightRot), 0.0D));

        // 头部：与原版一致，只旋转不位移
        double headYaw = Mth.wrapDegrees(monster.getYHeadRot() - monster.yBodyRot);
        double headPitch = Mth.clamp(monster.getXRot(), -90.0F, 90.0F);
        bones.put(BONE_HEAD, transform(0.0D, 0.0D, 0.0D, headYaw, headPitch, 0.0D));

        // 身体：原版没有额外位移/旋转
        bones.put(BONE_BODY, transform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
        return GiantMonsterPose.of(bones);
    }

    private static GiantMonsterPartTransform transform(double offsetX, double offsetY, double offsetZ,
                                                       double yaw, double pitch, double roll) {
        return new GiantMonsterPartTransform(offsetX, offsetY, offsetZ, yaw, pitch, roll, 1.0D);
    }
}
