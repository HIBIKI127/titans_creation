package com.example.titans_creation.client;

import java.util.List;

import com.example.titans_creation.giantmonster.GiantMonsterConfigManager;
import com.example.titans_creation.giantmonster.GiantMonsterDefinition;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 一级菜单：怪物配置。
 *
 * <p>列出全部已注册的巨型怪物，点击其中一只进入二级菜单（基础属性）。
 * 怪物较多时自动分页。
 */
public class GiantMonsterListScreen extends GiantMonsterScreen {

    private static final int PAGE_SIZE = 8;

    private int page;

    public GiantMonsterListScreen() {
        super(Component.translatable("titans_creation.giantmonster.menu.monsters"), null);
    }

    @Override
    protected void init() {
        final int centerX = this.centerX();
        final List<String> ids = GiantMonsterConfigManager.ids();
        final int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Mth.clamp(this.page, 0, pages - 1);

        int y = 46;
        if (ids.isEmpty()) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.empty"), COLOR_ERROR);
        }

        final int from = this.page * PAGE_SIZE;
        final int to = Math.min(ids.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            final String id = ids.get(i);
            GiantMonsterDefinition definition = GiantMonsterConfigManager.get(id);
            String name = definition == null ? id : definition.getDisplayName();
            this.addRenderableWidget(Button.builder(
                            Component.literal(name + "   (" + id + ")"),
                            button -> this.openScreen(new GiantMonsterBaseAttributesScreen(id)))
                    .bounds(centerX - 150, y, 300, 18)
                    .build());
            y += 20;
        }

        y += 4;
        if (pages > 1) {
            final int currentPage = this.page;
            this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                        this.page = Math.max(0, currentPage - 1);
                        this.rebuildWidgets();
                    })
                    .bounds(centerX - 150, y, 40, 18)
                    .build());
            this.addRenderableWidget(Button.builder(
                            Component.literal((currentPage + 1) + " / " + pages), button -> {
                            })
                    .bounds(centerX - 106, y, 100, 18)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                        this.page = Math.min(pages - 1, currentPage + 1);
                        this.rebuildWidgets();
                    })
                    .bounds(centerX + 110, y, 40, 18)
                    .build());
            y += 22;
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.back"),
                        button -> this.openScreen(new GiantMonsterMainMenuScreen()))
                .bounds(centerX - 150, y, 145, 18)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(centerX + 5, y, 145, 18)
                .build());
        this.contentBottom = y + 18;
    }

    @Override
    public void onClose() {
        this.openScreen(new GiantMonsterMainMenuScreen());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.renderTitle(guiGraphics, null);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderHints(guiGraphics, List.of(
                Component.translatable("titans_creation.giantmonster.menu.monsters.hint")));
        this.renderStatus(guiGraphics);
    }
}
