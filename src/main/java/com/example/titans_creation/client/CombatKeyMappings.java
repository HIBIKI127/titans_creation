package com.example.titans_creation.client;

import com.example.titans_creation.TitansCreationMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

    @Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CombatKeyMappings
{
    public static final String CATEGORY = "key.categories." + TitansCreationMod.MODID;

    // 切换战斗模式的按键绑定，默认无绑定（InputConstants.UNKNOWN），由玩家在控制设置中自行绑定。
    // 由于 Minecraft 按键绑定不原生支持修饰键组合，“Shift + 绑定键” 通过在按下该绑定键时
    // 额外检测 Shift 是否按下来实现（见 ClientCombatEvents#onClientTick）。
    public static final KeyMapping TOGGLE_COMBAT_MODE = new KeyMapping(
                "key." + TitansCreationMod.MODID + ".toggle_combat",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    // 攀爬已改为「由装备自动启用」：装备攀爬手套 / 利爪之靴后自动生效，穿脱装备即启用或失效，
    // 因此不再提供攀爬开关热键（原先的 R 键已移除）。

    // 手套功能键：分别触发左手套槽 / 右手套槽里主动装备的功能
    // （攀爬手套是被动效果，不需要触发；抓钩手弩是主动效果，需要按键）。
    public static final KeyMapping LEFT_GLOVE = new KeyMapping(
            "key." + TitansCreationMod.MODID + ".left_glove",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static final KeyMapping RIGHT_GLOVE = new KeyMapping(
            "key." + TitansCreationMod.MODID + ".right_glove",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(TOGGLE_COMBAT_MODE);
        event.register(LEFT_GLOVE);
        event.register(RIGHT_GLOVE);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event)
    {
        // 注册自定义光标叠加层，绘制在所有 HUD 之上
        // id 只需传路径字符串，Forge 会自动加上当前 mod 的命名空间
        event.registerAboveAll("combat_cursor", CombatCursorOverlay.INSTANCE);
        event.registerAboveAll("combat_cooldown", CombatCooldownOverlay.INSTANCE);
    }
}
