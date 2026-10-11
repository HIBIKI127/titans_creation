package com.example.titans_creation.client;

import com.example.titans_creation.giantmonster.GiantMonster;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 巨型怪物渲染器：直接复用<b>原版巨人（Giant）</b>的模型层、动画与贴图。
 *
 * <p>与原版巨人的渲染方式完全一致（见原版 {@code GiantMobRenderer}）：
 * <ul>
 *   <li>模型：{@link HumanoidModel} + 原版巨人模型层 {@link ModelLayers#GIANT}
 *       —— 使用原版人形骨骼（head / body / arm / leg），其骨骼名与
 *       {@code GiantMonsterAnimator} 使用的 bone 一一对应；</li>
 *   <li>动画：原版 {@code HumanoidModel.setupAnim} 播放行走摆臂、头部跟随等原版动作；</li>
 *   <li>贴图：原版巨人使用的僵尸贴图 {@code minecraft:textures/entity/zombie/zombie.png}；</li>
 *   <li>缩放：与原版巨人一致放大 6 倍（约 12 格高），配合 3.6×10.8 的实体碰撞箱。</li>
 * </ul>
 */
public class GiantMonsterRenderer extends MobRenderer<GiantMonster, HumanoidModel<GiantMonster>> {

    /** 原版巨人使用的贴图（僵尸贴图）。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");

    /** 原版巨人的模型放大倍数。 */
    private static final float MODEL_SCALE = 6.0F;

    public GiantMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.GIANT)), 3.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(GiantMonster entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(GiantMonster entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}
