package com.example.titans_creation.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 配置主菜单（第 0 级）。
 *
 * <p>目前只有一个一级菜单「怪物配置」，后续新增的一级菜单在此追加按钮即可。
 */
public class GiantMonsterMainMenuScreen extends GiantMonsterScreen {

    public GiantMonsterMainMenuScreen() {
        super(Component.translatable("titans_creation.giantmonster.menu.main"), null);
    }

    /**
     * 关闭时：若本界面是从模组列表的 Config 按钮进入的，则回到模组列表；
     * 否则按原版行为关闭（回到游戏或标题界面）。
     */
    @Override
    public void onClose() {
        Screen back = takeConfigReturnScreen();
        if (back != null) {
            this.openScreen(back);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        final int centerX = this.centerX();
        int y = this.height / 4;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.menu.monsters"),
                        button -> this.openScreen(new GiantMonsterListScreen()))
                .bounds(centerX - 120, y, 240, 20)
                .build());

        y += 26;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(centerX - 120, y, 240, 20)
                .build());

        this.contentBottom = y + 20;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.renderTitle(guiGraphics, null);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderHints(guiGraphics, List.of(
                Component.translatable("titans_creation.giantmonster.menu.main.hint")));
        this.renderStatus(guiGraphics);
    }
}
