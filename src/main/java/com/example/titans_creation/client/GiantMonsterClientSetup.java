package com.example.titans_creation.client;

import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.giantmonster.GiantMonsterRegistry;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 巨型怪物的客户端注册（渲染器、配置界面扩展点）。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GiantMonsterClientSetup {

    private GiantMonsterClientSetup() {
    }

    /**
     * 注册「配置界面扩展点」。
     *
     * <p>Forge 的模组列表（主界面「模组」/ 游戏内 ESC →「模组」）会用
     * {@code ConfigScreenHandler.getScreenFactoryFor(modInfo)} 判断该模组的 <b>Config</b> 按钮是否可点击。
     * 模组必须通过本扩展点提供配置界面，否则按钮一直是灰色的。
     *
     * <p>本方法必须在<b>模组构造阶段</b>调用（见 {@code TitansCreationMod} 构造器），
     * 并且要使用构造器传入的 {@link FMLJavaModLoadingContext}——{@code ModLoadingContext.get()}
     * 在此 Forge 版本已标记为待删除。
     */
    public static void registerConfigScreen(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> {
                    // 记住上级界面，使配置主菜单的「完成 / ESC」能回到模组列表
                    GiantMonsterScreen.setConfigReturnScreen(parent);
                    return new GiantMonsterMainMenuScreen();
                }));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GiantMonsterRegistry.GIANT_MONSTER.get(), GiantMonsterRenderer::new);
    }
}
