package com.example.titans_creation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.titans_creation.giantmonster.GiantMonsterSpawnConfig;
import com.example.titans_creation.giantmonster.GiantMonsterSpawnOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 群系 / 维度选择界面。
 *
 * <p>候选项直接从注册表扫描得到，因此<b>自动包含其他模组新增的群系与维度</b>
 * （例如 Twilight Forest 的群系、其他模组的维度）；群系还额外提供群系标签
 * （显示为 {@code #minecraft:is_forest} 形式），一次勾选即可覆盖一整类群系。
 *
 * <p>列表支持搜索过滤、鼠标滚轮滚动、逐条勾选。
 */
public class GiantMonsterRegistrySelectionScreen extends GiantMonsterScreen {

    private static final int ROW = 12;
    private static final int SCROLL_STEP = 3;

    private final GiantMonsterSpawnConfig draft;
    private final boolean biomes;
    private final GiantMonsterSpawnControlScreen returnTo;

    private final List<String> allValues = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();

    private EditBox searchBox;
    private int scroll;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int visibleRows = 1;

    public GiantMonsterRegistrySelectionScreen(GiantMonsterSpawnConfig draft, boolean biomes,
                                               GiantMonsterSpawnControlScreen returnTo) {
        super(Component.translatable(biomes
                        ? "titans_creation.giantmonster.menu.biomes"
                        : "titans_creation.giantmonster.menu.dimensions"),
                returnTo.monsterId);
        this.draft = draft;
        this.biomes = biomes;
        this.returnTo = returnTo;

        RegistryAccess access = Minecraft.getInstance().level == null
                ? null
                : Minecraft.getInstance().level.registryAccess();
        if (biomes) {
            for (ResourceLocation id : GiantMonsterSpawnOptions.biomes()) {
                this.allValues.add(GiantMonsterSpawnOptions.toConfigValue(id));
            }
            for (ResourceLocation tag : GiantMonsterSpawnOptions.biomeTags(access)) {
                this.allValues.add(GiantMonsterSpawnOptions.toTagConfigValue(tag));
            }
        } else {
            for (ResourceLocation id : GiantMonsterSpawnOptions.dimensions(access)) {
                this.allValues.add(GiantMonsterSpawnOptions.toConfigValue(id));
            }
        }
        this.allValues.sort(String.CASE_INSENSITIVE_ORDER);
        this.refreshFilter();
    }

    /** 当前配置中已勾选的列表。 */
    private List<String> selectedList() {
        return this.biomes ? this.draft.getBiomeList() : this.draft.getDimensionList();
    }

    // ==================== 布局 ====================

    @Override
    protected void init() {
        final int centerX = this.centerX();
        this.searchBox = this.addRenderableWidget(new EditBox(this.font, centerX - 200, 40, 400, 18,
                Component.translatable("titans_creation.giantmonster.search")));
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(text -> {
            this.refreshFilter();
            this.scroll = 0;
        });

        this.listLeft = centerX - 200;
        this.listRight = centerX + 200;
        this.listTop = 64;
        this.listBottom = Math.max(this.listTop + ROW, this.height - 56);
        this.visibleRows = Math.max(1, (this.listBottom - this.listTop) / ROW);

        int y = this.listBottom + 4;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.select_visible"),
                        pressed -> this.selectVisible())
                .bounds(centerX - 200, y, 130, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.clear_selection"),
                        pressed -> {
                            this.selectedList().clear();
                            this.setStatus(Component.translatable("titans_creation.giantmonster.status.cleared"),
                                    COLOR_OK);
                        })
                .bounds(centerX - 65, y, 130, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.done"),
                        pressed -> this.openScreen(this.returnTo))
                .bounds(centerX + 70, y, 130, 18)
                .build());

        this.contentBottom = y + 18;
    }

    @Override
    public void onClose() {
        this.openScreen(this.returnTo);
    }

    // ==================== 数据 ====================

    private void refreshFilter() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filtered.clear();
        for (String value : this.allValues) {
            if (query.isEmpty() || displayOf(value).toLowerCase(Locale.ROOT).contains(query)) {
                this.filtered.add(value);
            }
        }
    }

    private boolean isSelected(String value) {
        for (String selected : this.selectedList()) {
            if (selected.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void toggle(String value) {
        List<String> selected = this.selectedList();
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i).equalsIgnoreCase(value)) {
                selected.remove(i);
                return;
            }
        }
        selected.add(value);
    }

    private void selectVisible() {
        List<String> selected = this.selectedList();
        for (String value : this.filtered) {
            if (!isSelected(value)) {
                selected.add(value);
            }
        }
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.selected_count",
                selected.size()), COLOR_OK);
    }

    private static String displayOf(String value) {
        boolean tag = GiantMonsterSpawnOptions.isTag(value);
        ResourceLocation id = tag
                ? GiantMonsterSpawnOptions.parseTagId(value)
                : GiantMonsterSpawnOptions.parseId(value);
        String text = id == null ? value : id.toString();
        return tag ? GiantMonsterSpawnOptions.TAG_PREFIX + text : text;
    }

    private static String namespaceOf(String value) {
        ResourceLocation id = GiantMonsterSpawnOptions.isTag(value)
                ? GiantMonsterSpawnOptions.parseTagId(value)
                : GiantMonsterSpawnOptions.parseId(value);
        return id == null ? "minecraft" : id.getNamespace();
    }

    private int maxScroll() {
        return Math.max(0, this.filtered.size() - this.visibleRows);
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseX >= this.listLeft && mouseX < this.listRight
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            int row = (int) ((mouseY - this.listTop) / ROW);
            int index = this.scroll + row;
            if (index >= 0 && index < this.filtered.size()) {
                this.toggle(this.filtered.get(index));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta) * SCROLL_STEP, 0, this.maxScroll());
        return true;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.renderTitle(guiGraphics, Component.translatable("titans_creation.giantmonster.hint.registry_scan",
                this.selectedList().size(), this.allValues.size(), this.filtered.size()));

        guiGraphics.fill(this.listLeft - 1, this.listTop - 1, this.listRight + 1, this.listBottom + 1, 0x80000000);

        for (int row = 0; row < this.visibleRows; row++) {
            int index = this.scroll + row;
            if (index >= this.filtered.size()) {
                break;
            }
            String value = this.filtered.get(index);
            int y = this.listTop + row * ROW;
            boolean selected = this.isSelected(value);
            boolean hovered = mouseX >= this.listLeft && mouseX < this.listRight
                    && mouseY >= y && mouseY < y + ROW - 1;
            if (selected) {
                guiGraphics.fill(this.listLeft, y, this.listRight, y + ROW - 1, 0x5533AA33);
            } else if (hovered) {
                guiGraphics.fill(this.listLeft, y, this.listRight, y + ROW - 1, 0x33FFFFFF);
            }
            int color;
            if (selected) {
                color = 0xFFAAFFAA;
            } else if ("minecraft".equals(namespaceOf(value))) {
                color = 0xFFD0D0D0;
            } else {
                // 其他模组的内容用浅绿色标注，便于识别
                color = 0xFF9BE29B;
            }
            guiGraphics.drawString(this.font, (selected ? "✔ " : "   ") + displayOf(value),
                    this.listLeft + 4, y + 2, color, false);
        }

        // 滚动条
        if (this.maxScroll() > 0) {
            int trackHeight = this.listBottom - this.listTop;
            int barHeight = Math.max(10, trackHeight * this.visibleRows / this.filtered.size());
            int barY = this.listTop + (trackHeight - barHeight) * this.scroll / this.maxScroll();
            guiGraphics.fill(this.listRight - 3, barY, this.listRight - 1, barY + barHeight, 0xFFAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderStatus(guiGraphics);
    }
}
