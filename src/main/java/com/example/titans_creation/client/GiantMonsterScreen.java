package com.example.titans_creation.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.example.titans_creation.giantmonster.GiantMonster;
import com.example.titans_creation.giantmonster.GiantMonsterConfigManager;
import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.GiantMonsterEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 巨型怪物配置界面的公共基类。
 *
 * <p>菜单层级（见 {@code 泰坦造物-项目文档.md} 的更新历史）：
 * <pre>
 * 主菜单
 *  └── 怪物配置（一级：怪物列表）
 *       ├── 基础属性（二级）
 *       │    ├── 弱点配置（三级）
 *       │    └── 抗性配置（三级）
 *       └── 生成控制（二级）
 * </pre>
 *
 * <p>本类统一提供：当前怪物上下文、状态提示、保存并应用、翻译键拼装等公共能力。
 */
public abstract class GiantMonsterScreen extends Screen {

    protected static final int COLOR_TITLE = 0xFFFFFF;
    protected static final int COLOR_LABEL = 0xFFE0E0E0;
    protected static final int COLOR_HINT = 0xFFA0A0A0;
    protected static final int COLOR_OK = 0xFF55FF55;
    protected static final int COLOR_ERROR = 0xFFFF5555;

    protected static final int ROW_HEIGHT = 18;
    protected static final int BOX_HEIGHT = 16;

    /** 从模组列表的 Config 按钮进入时记录的上级界面，供配置主菜单「返回」使用。 */
    @Nullable
    private static Screen configReturnScreen;

    /** 鼠标滚轮每格的滚动像素。 */
    private static final int SCROLL_STEP = 20;

    /** 可滚动控件条目：控件 + 其布局 Y（不随滚动改变）。 */
    private record ScrollEntry(AbstractWidget widget, int baseY, int height) {
    }

    private final List<ScrollEntry> scrollEntries = new ArrayList<>();
    private final Set<AbstractWidget> scrollableWidgets =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** 滚动视口（内容区）上下边界。 */
    protected int scrollViewportTop;
    protected int scrollViewportBottom;
    private int scrollOffset;
    private int maxScroll;
    /** 滚动内容的实际底部（用于滚动条比例）。 */
    private int scrollContentBottom;

    /** 设置配置界面的返回目标（由 {@link GiantMonsterClientSetup#registerConfigScreen()} 调用）。 */
    public static void setConfigReturnScreen(@Nullable Screen screen) {
        configReturnScreen = screen;
    }

    /** 取出并清除配置界面的返回目标。 */
    @Nullable
    public static Screen takeConfigReturnScreen() {
        Screen screen = configReturnScreen;
        configReturnScreen = null;
        return screen;
    }

    // ==================== 滚动支持 ====================

    /**
     * 注册一个「可滚动」控件：其 Y 坐标会随滚动偏移移动，渲染时被裁剪在滚动视口内。
     * 标题、分类按钮、底部操作按钮请使用普通的 {@code addRenderableWidget}（固定在屏幕上）。
     */
    protected <T extends AbstractWidget> T addScrollable(T widget) {
        this.scrollEntries.add(new ScrollEntry(widget, widget.getY(), widget.getHeight()));
        this.scrollableWidgets.add(widget);
        return this.addRenderableWidget(widget);
    }

    /**
     * 完成滚动布局：计算内容总高度与最大滚动量。
     *
     * @param viewportTop    内容区顶部（标题/分类按钮下方）
     * @param viewportBottom 内容区底部（固定按钮区上方）
     */
    protected void finishScrollLayout(int viewportTop, int viewportBottom) {
        this.scrollViewportTop = viewportTop;
        this.scrollViewportBottom = viewportBottom;
        int bottom = viewportTop;
        for (ScrollEntry entry : this.scrollEntries) {
            bottom = Math.max(bottom, entry.baseY() + entry.height());
        }
        this.scrollContentBottom = bottom;
        this.maxScroll = Math.max(0, bottom - viewportBottom);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
        this.applyScroll();
    }

    /** 当前滚动偏移（供子类绘制随滚动移动的标签）。 */
    protected int scrollOffset() {
        return this.scrollOffset;
    }

    private void applyScroll() {
        for (ScrollEntry entry : this.scrollEntries) {
            entry.widget().setY(entry.baseY() - this.scrollOffset);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScroll > 0) {
            this.scrollOffset = Mth.clamp(
                    this.scrollOffset - (int) Math.signum(delta) * SCROLL_STEP, 0, this.maxScroll);
            this.applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * 落在滚动视口之外的点击只交给固定控件处理，
     * 避免点到已被裁剪（看不见）的滚动控件。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean insideViewport = this.scrollEntries.isEmpty()
                || (mouseY >= this.scrollViewportTop && mouseY <= this.scrollViewportBottom);
        if (insideViewport) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget && !this.scrollableWidgets.contains(widget)
                    && widget.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(widget);
                if (button == 0) {
                    this.setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 渲染全部控件：固定控件（标题/分类/底部按钮）始终绘制；
     * 可滚动控件连同子类绘制的标签一起被裁剪到滚动视口内。
     */
    protected void renderWidgets(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget && this.scrollableWidgets.contains(widget)) {
                continue;
            }
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.scrollEntries.isEmpty()) {
            return;
        }
        guiGraphics.enableScissor(0, this.scrollViewportTop, this.width, this.scrollViewportBottom);
        this.renderScrollContent(guiGraphics);
        for (ScrollEntry entry : this.scrollEntries) {
            entry.widget().render(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.disableScissor();
        this.renderScrollbar(guiGraphics);
    }

    /** 子类在此绘制随滚动移动的标签/说明（会被裁剪在滚动视口内）。 */
    protected void renderScrollContent(GuiGraphics guiGraphics) {
    }

    /** 滚动条（内容超出视口时显示）。 */
    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (this.maxScroll <= 0) {
            return;
        }
        int trackHeight = this.scrollViewportBottom - this.scrollViewportTop;
        int contentHeight = this.scrollContentBottom - this.scrollViewportTop;
        int barHeight = Math.max(14, trackHeight * trackHeight / Math.max(1, contentHeight));
        barHeight = Math.min(barHeight, trackHeight);
        int barY = this.scrollViewportTop
                + (trackHeight - barHeight) * this.scrollOffset / Math.max(1, this.maxScroll);
        int x = this.width - 6;
        guiGraphics.fill(x, this.scrollViewportTop, x + 4, this.scrollViewportBottom, 0x60000000);
        guiGraphics.fill(x, barY, x + 4, barY + barHeight, 0xFFAAAAAA);
    }

    /** 当前操作的怪物 id（主菜单与怪物列表为 null）。 */
    @Nullable
    protected final String monsterId;
    /** 状态提示文本。 */
    protected Component status = Component.empty();
    protected int statusColor = COLOR_OK;
    /** 内容区底部 Y 坐标，用于在窗口较矮时避免提示文字与内容重叠。 */
    protected int contentBottom;

    protected GiantMonsterScreen(Component title, @Nullable String monsterId) {
        super(title);
        this.monsterId = monsterId;
    }

    // ==================== 数据 ====================

    /** 当前怪物的配置定义（不存在时回退到出厂默认值）。 */
    @Nullable
    protected GiantMonsterDefinition definition() {
        if (this.monsterId == null) {
            return null;
        }
        GiantMonsterDefinition definition = GiantMonsterConfigManager.get(this.monsterId);
        return definition != null ? definition : GiantMonsterConfigManager.getDefault(this.monsterId);
    }

    /** 保存定义，并让世界中已生成的怪物立即套用。 */
    protected boolean saveDefinition(GiantMonsterDefinition definition) {
        if (!GiantMonsterConfigManager.save(definition)) {
            return false;
        }
        this.applyToLoadedEntities();
        return true;
    }

    /**
     * 让世界中已生成的巨型怪物套用新配置。
     * 单人游戏下同时把改动同步到集成服务端，保证服务端实体也立即生效。
     */
    protected void applyToLoadedEntities() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != null) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof GiantMonster monster) {
                    GiantMonsterConfigManager.applyTo(monster, false);
                }
            }
        }
        if (minecraft.hasSingleplayerServer()) {
            MinecraftServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> GiantMonsterConfigManager.applyToAllLoaded(server));
            }
        }
    }

    // ==================== 渲染辅助 ====================

    protected int centerX() {
        return this.width / 2;
    }

    protected void setStatus(Component message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    /** 绘制状态提示（自动避开内容区）。 */
    protected void renderStatus(GuiGraphics guiGraphics) {
        if (this.status.getString().isEmpty()) {
            return;
        }
        int y = Math.min(this.height - 12, this.contentBottom + 6);
        guiGraphics.drawCenteredString(this.font, this.status, this.centerX(), y, this.statusColor);
    }

    /** 绘制标题与可选副标题。 */
    protected void renderTitle(GuiGraphics guiGraphics, @Nullable Component subtitle) {
        guiGraphics.drawCenteredString(this.font, this.title, this.centerX(), 12, COLOR_TITLE);
        if (subtitle != null) {
            guiGraphics.drawCenteredString(this.font, subtitle, this.centerX(), 24, COLOR_HINT);
        }
    }

    /** 绘制底部提示行（仅在空间足够时绘制）。 */
    protected void renderHints(GuiGraphics guiGraphics, List<Component> hints) {
        int total = hints.size() * 11;
        int startY = this.height - 8 - total;
        if (startY <= this.contentBottom + 4) {
            return;
        }
        for (int i = 0; i < hints.size(); i++) {
            guiGraphics.drawCenteredString(this.font, hints.get(i), this.centerX(), startY + i * 11, COLOR_HINT);
        }
    }

    // ==================== 工具 ====================

    protected static String fieldKey(String key) {
        return "titans_creation.giantmonster.field." + key;
    }

    protected static String categoryKey(String key) {
        return "titans_creation.giantmonster.category." + key;
    }

    protected void openScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /** 解析弱点/抗性配置的 JSON 数组文本。 */
    protected static List<GiantMonsterEntry> parseEntries(String raw, String fieldName) {
        List<GiantMonsterEntry> result = new java.util.ArrayList<>();
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return result;
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " JSON 格式错误");
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(fieldName + " 需要 JSON 数组");
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                throw new IllegalArgumentException(fieldName + " 的元素需要是 JSON 对象");
            }
            result.add(GiantMonsterEntry.fromJson(item.getAsJsonObject()));
        }
        return result;
    }

    /** 把词条列表序列化为便于编辑的 JSON 文本。 */
    protected static String entriesToJson(List<GiantMonsterEntry> entries) {
        JsonArray array = new JsonArray();
        for (GiantMonsterEntry entry : entries) {
            array.add(entry.toJson());
        }
        return array.toString();
    }

    /** 数字格式化：整数不带小数点，便于阅读与编辑。 */
    protected static String formatNumber(double value) {
        if (!Double.isInfinite(value) && value == Math.floor(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
