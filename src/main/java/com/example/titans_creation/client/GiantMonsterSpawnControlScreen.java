package com.example.titans_creation.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.GiantMonsterSpawnConfig;
import com.example.titans_creation.giantmonster.GiantMonsterSpawnManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 二级菜单：生成控制。
 *
 * <p>10 类生成条件各自独立配置，通过顶部分类按钮切换：
 * 群系 / 维度 / 时间 / 高度 / 亮度 / 天气 / 月相 / 玩家状态 / 地形 / 频率上限。
 *
 * <p>群系与维度使用专门的勾选界面（{@link GiantMonsterRegistrySelectionScreen}），
 * 其中的候选项由注册表扫描得到，因此<b>自动包含其他模组新增的内容</b>。
 */
public class GiantMonsterSpawnControlScreen extends GiantMonsterScreen {

    private static final String[] CATEGORIES = {
            "biomes", "dimensions", "time", "height", "light",
            "weather", "moon_phase", "player_state", "terrain", "frequency"
    };

    /** 数值字段键名（与 {@link #applyNumber} 内的分支对应）。 */
    private static final String K_TIME_MIN = "time_min";
    private static final String K_TIME_MAX = "time_max";
    private static final String K_HEIGHT_MIN = "height_min";
    private static final String K_HEIGHT_MAX = "height_max";
    private static final String K_BLOCK_MIN = "block_min";
    private static final String K_BLOCK_MAX = "block_max";
    private static final String K_SKY_MIN = "sky_min";
    private static final String K_SKY_MAX = "sky_max";
    private static final String K_MOON_PHASES = "moon_phases";
    private static final String K_MIN_PLAYERS = "min_players";
    private static final String K_MAX_PLAYERS = "max_players";
    private static final String K_HEALTH_RATIO = "max_health_ratio";
    private static final String K_MIN_LEVEL = "min_level";
    private static final String K_CLEAR_X = "clearance_x";
    private static final String K_CLEAR_Y = "clearance_y";
    private static final String K_CLEAR_Z = "clearance_z";
    private static final String K_INTERVAL = "check_interval";
    private static final String K_MAX_WORLD = "max_per_world";
    private static final String K_MAX_PLAYER = "max_per_player";
    private static final String K_COOLDOWN = "cooldown_ticks";
    private static final String K_MIN_DIST = "min_distance_to_player";
    private static final String K_MAX_DIST = "max_distance_to_player";
    private static final String K_MIN_BETWEEN = "min_distance_between";

    private GiantMonsterSpawnConfig draft;
    private int category;
    /** 滚动内容中说明文字的起始 Y（布局坐标）。 */
    private int hintsTop;

    private final Map<String, EditBox> numberBoxes = new LinkedHashMap<>();
    private final List<LabeledWidget> labels = new ArrayList<>();

    /** 字段标签（数值输入框左侧显示）。 */
    private record LabeledWidget(String labelKey, AbstractWidget widget) {
    }

    public GiantMonsterSpawnControlScreen(String monsterId) {
        super(Component.translatable("titans_creation.giantmonster.menu.spawn"), monsterId);
        GiantMonsterDefinition definition = this.definition();
        this.draft = definition == null
                ? new GiantMonsterSpawnConfig()
                : definition.getSpawn().copy();
    }

    // ==================== 布局 ====================

    @Override
    protected void init() {
        this.numberBoxes.clear();
        this.labels.clear();

        final int centerX = this.centerX();

        // 生成机制总开关（固定在顶部，不随内容滚动）
        this.addToggle(40, fieldKey("spawn_enabled"), this.draft::isEnabled, this.draft::setEnabled, false);

        // 10 类条件分类按钮（2 行 × 5 个）
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int index = i;
            int column = i % 5;
            int row = i / 5;
            Button button = Button.builder(Component.translatable(categoryKey(CATEGORIES[i])), pressed -> {
                        this.captureFields(false);
                        this.category = index;
                        this.rebuildWidgets();
                    })
                    .bounds(centerX - 238 + column * 96, 64 + row * 20, 92, 18)
                    .build();
            button.active = index != this.category;
            this.addRenderableWidget(button);
        }

        int y = this.buildFields(114);
        this.hintsTop = y + 2;

        // ===== 固定在底部的操作按钮 =====
        final int buttonY = this.height - 64;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.save"), pressed -> this.save())
                .bounds(centerX - 190, buttonY, 180, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.reset"), pressed -> this.resetToDefault())
                .bounds(centerX + 10, buttonY, 180, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.back"),
                        pressed -> this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId)))
                .bounds(centerX - 190, buttonY + 22, 380, 18)
                .build());
        this.contentBottom = this.height - 94;
        // 分类按钮固定在 40~102，内容区从 108 开始
        this.finishScrollLayout(108, this.height - 94);
    }

    /** 按当前分类创建对应字段控件，返回新的纵坐标。 */
    private int buildFields(int startY) {
        int y = startY;
        switch (this.category) {
            case 0 -> { // 群系
                y = this.addCycle(y, groupKey("biome_mode"),
                        () -> modeLabel(groupKey("biome_mode"), this.draft.getBiomeMode().name()),
                        () -> this.draft.setBiomeMode(this.draft.getBiomeMode().next()));
                y = this.addAction(y, Component.translatable("titans_creation.giantmonster.button.edit_biomes",
                        this.draft.getBiomeList().size()), () -> {
                    this.captureFields(false);
                    this.openScreen(new GiantMonsterRegistrySelectionScreen(this.draft, true, this));
                });
            }
            case 1 -> { // 维度
                y = this.addCycle(y, groupKey("dimension_mode"),
                        () -> modeLabel(groupKey("dimension_mode"), this.draft.getDimensionMode().name()),
                        () -> this.draft.setDimensionMode(this.draft.getDimensionMode().next()));
                y = this.addAction(y, Component.translatable("titans_creation.giantmonster.button.edit_dimensions",
                        this.draft.getDimensionList().size()), () -> {
                    this.captureFields(false);
                    this.openScreen(new GiantMonsterRegistrySelectionScreen(this.draft, false, this));
                });
            }
            case 2 -> { // 时间
                y = this.addCycle(y, fieldKey("time_mode"),
                        () -> modeLabel(fieldKey("time_mode"), this.draft.getTimeMode().name()),
                        () -> this.draft.setTimeMode(this.draft.getTimeMode().next()));
                y = this.addNumber(y, K_TIME_MIN, fieldKey("time_min"), this.draft.getTimeMin());
                y = this.addNumber(y, K_TIME_MAX, fieldKey("time_max"), this.draft.getTimeMax());
            }
            case 3 -> { // 高度
                y = this.addToggle(y, fieldKey("height_enabled"), this.draft::isHeightEnabled,
                        this.draft::setHeightEnabled);
                y = this.addNumber(y, K_HEIGHT_MIN, fieldKey("height_min"), this.draft.getHeightMin());
                y = this.addNumber(y, K_HEIGHT_MAX, fieldKey("height_max"), this.draft.getHeightMax());
                y = this.addToggle(y, fieldKey("on_surface"), this.draft::isOnSurface, this.draft::setOnSurface);
                y = this.addToggle(y, fieldKey("under_ground"), this.draft::isUnderGround,
                        this.draft::setUnderGround);
                y = this.addToggle(y, fieldKey("can_see_sky"), this.draft::isCanSeeSky, this.draft::setCanSeeSky);
            }
            case 4 -> { // 亮度
                y = this.addToggle(y, fieldKey("light_enabled"), this.draft::isLightEnabled,
                        this.draft::setLightEnabled);
                y = this.addNumber(y, K_BLOCK_MIN, fieldKey("block_light_min"), this.draft.getBlockLightMin());
                y = this.addNumber(y, K_BLOCK_MAX, fieldKey("block_light_max"), this.draft.getBlockLightMax());
                y = this.addNumber(y, K_SKY_MIN, fieldKey("sky_light_min"), this.draft.getSkyLightMin());
                y = this.addNumber(y, K_SKY_MAX, fieldKey("sky_light_max"), this.draft.getSkyLightMax());
            }
            case 5 -> // 天气
                    y = this.addCycle(y, fieldKey("weather"),
                            () -> modeLabel(fieldKey("weather"), this.draft.getWeatherMode().name()),
                            () -> this.draft.setWeatherMode(this.draft.getWeatherMode().next()));
            case 6 -> { // 月相
                y = this.addToggle(y, fieldKey("moon_phase_enabled"), this.draft::isMoonPhaseEnabled,
                        this.draft::setMoonPhaseEnabled);
                y = this.addNumber(y, K_MOON_PHASES, fieldKey("moon_phases"), 0.0D);
                if (this.numberBoxes.containsKey(K_MOON_PHASES)) {
                    this.numberBoxes.get(K_MOON_PHASES).setValue(this.moonPhasesToText());
                }
            }
            case 7 -> { // 玩家状态
                y = this.addToggle(y, fieldKey("require_combat_mode"), this.draft::isRequireCombatMode,
                        this.draft::setRequireCombatMode);
                y = this.addNumber(y, K_MIN_PLAYERS, fieldKey("min_players"), this.draft.getMinPlayers());
                y = this.addNumber(y, K_MAX_PLAYERS, fieldKey("max_players"), this.draft.getMaxPlayers());
                y = this.addNumber(y, K_HEALTH_RATIO, fieldKey("max_health_ratio"),
                        this.draft.getMaxHealthRatio());
                y = this.addNumber(y, K_MIN_LEVEL, fieldKey("min_level"), this.draft.getMinLevel());
                y = this.addToggle(y, fieldKey("not_in_village"), this.draft::isNotInVillage,
                        this.draft::setNotInVillage);
            }
            case 8 -> { // 地形
                y = this.addToggle(y, fieldKey("terrain_enabled"), this.draft::isTerrainEnabled,
                        this.draft::setTerrainEnabled);
                y = this.addNumber(y, K_CLEAR_X, fieldKey("clearance_x"), this.draft.getClearanceX());
                y = this.addNumber(y, K_CLEAR_Y, fieldKey("clearance_y"), this.draft.getClearanceY());
                y = this.addNumber(y, K_CLEAR_Z, fieldKey("clearance_z"), this.draft.getClearanceZ());
            }
            default -> { // 频率上限
                y = this.addNumber(y, K_INTERVAL, fieldKey("check_interval"), this.draft.getCheckInterval());
                y = this.addNumber(y, K_MAX_WORLD, fieldKey("max_per_world"), this.draft.getMaxPerWorld());
                y = this.addNumber(y, K_MAX_PLAYER, fieldKey("max_per_player"), this.draft.getMaxPerPlayer());
                y = this.addNumber(y, K_COOLDOWN, fieldKey("cooldown_ticks"), this.draft.getCooldownTicks());
                y = this.addNumber(y, K_MIN_DIST, fieldKey("min_distance_to_player"),
                        this.draft.getMinDistanceToPlayer());
                y = this.addNumber(y, K_MAX_DIST, fieldKey("max_distance_to_player"),
                        this.draft.getMaxDistanceToPlayer());
                y = this.addNumber(y, K_MIN_BETWEEN, fieldKey("min_distance_between"),
                        this.draft.getMinDistanceBetween());
            }
        }
        return y;
    }

    private static String groupKey(String key) {
        return "titans_creation.giantmonster.group." + key;
    }

    // ==================== 控件工厂 ====================

    private int addNumber(int y, String key, String labelKey, double value) {
        EditBox box = new EditBox(this.font, this.centerX() + 10, y, 150, BOX_HEIGHT,
                Component.translatable(labelKey));
        box.setMaxLength(16);
        box.setValue(formatNumber(value));
        this.numberBoxes.put(key, this.addScrollable(box));
        this.labels.add(new LabeledWidget(labelKey, box));
        return y + ROW_HEIGHT;
    }

    private int addToggle(int y, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        return this.addToggle(y, labelKey, getter, setter, true);
    }

    private int addToggle(int y, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter,
                          boolean scrollable) {
        Button[] holder = new Button[1];
        Button button = Button.builder(toggleLabel(labelKey, getter.getAsBoolean()), pressed -> {
                    setter.accept(!getter.getAsBoolean());
                    holder[0].setMessage(toggleLabel(labelKey, getter.getAsBoolean()));
                })
                .bounds(this.centerX() - 190, y, 380, 18)
                .build();
        holder[0] = button;
        if (scrollable) {
            this.addScrollable(button);
        } else {
            this.addRenderableWidget(button);
        }
        return y + 22;
    }

    private int addCycle(int y, String labelKey, Supplier<Component> label, Runnable onCycle) {
        Button[] holder = new Button[1];
        Button button = Button.builder(label.get(), pressed -> {
                    onCycle.run();
                    holder[0].setMessage(label.get());
                })
                .bounds(this.centerX() - 190, y, 380, 18)
                .build();
        holder[0] = button;
        this.addScrollable(button);
        return y + 22;
    }

    private int addAction(int y, Component label, Runnable action) {
        this.addScrollable(Button.builder(label, pressed -> action.run())
                .bounds(this.centerX() - 190, y, 380, 18)
                .build());
        return y + 22;
    }

    private static Component toggleLabel(String labelKey, boolean value) {
        return Component.translatable(labelKey).append(": ").append(Component.translatable(value
                ? "titans_creation.giantmonster.value.on"
                : "titans_creation.giantmonster.value.off"));
    }

    private static Component modeLabel(String labelKey, String modeName) {
        return Component.translatable(labelKey).append(": ").append(Component.translatable(
                "titans_creation.giantmonster.mode." + modeName.toLowerCase(Locale.ROOT)));
    }

    // ==================== 读写 ====================

    private String moonPhasesToText() {
        StringBuilder builder = new StringBuilder();
        for (int phase = 0; phase < GiantMonsterSpawnConfig.MOON_PHASE_COUNT; phase++) {
            if (this.draft.isMoonPhaseAllowed(phase)) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(phase);
            }
        }
        return builder.toString();
    }

    /** 把输入框内容写回草稿；strict 为 true 时非法输入抛出异常。 */
    private void captureFields(boolean strict) {
        for (Map.Entry<String, EditBox> entry : this.numberBoxes.entrySet()) {
            String key = entry.getKey();
            String raw = entry.getValue().getValue().trim();
            if (K_MOON_PHASES.equals(key)) {
                this.applyMoonPhases(raw, strict);
                continue;
            }
            if (raw.isEmpty()) {
                if (strict) {
                    throw new IllegalArgumentException(key + " 不能为空");
                }
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                if (strict) {
                    throw new IllegalArgumentException(key + " = \"" + raw + "\"");
                }
                continue;
            }
            this.applyNumber(key, value);
        }
    }

    private void applyMoonPhases(String raw, boolean strict) {
        this.draft.setAllMoonPhases(false);
        if (raw.isEmpty()) {
            return;
        }
        for (String part : raw.split("[,，\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                int phase = Integer.parseInt(part.trim());
                if (phase < 0 || phase >= GiantMonsterSpawnConfig.MOON_PHASE_COUNT) {
                    throw new NumberFormatException("out of range");
                }
                this.draft.setMoonPhase(phase, true);
            } catch (NumberFormatException e) {
                if (strict) {
                    throw new IllegalArgumentException("月相需要 0~7 的数字: \"" + part + "\"");
                }
            }
        }
    }

    private void applyNumber(String key, double value) {
        switch (key) {
            case K_TIME_MIN -> this.draft.setTimeMin((int) Math.round(value));
            case K_TIME_MAX -> this.draft.setTimeMax((int) Math.round(value));
            case K_HEIGHT_MIN -> this.draft.setHeightMin((int) Math.round(value));
            case K_HEIGHT_MAX -> this.draft.setHeightMax((int) Math.round(value));
            case K_BLOCK_MIN -> this.draft.setBlockLightMin((int) Math.round(value));
            case K_BLOCK_MAX -> this.draft.setBlockLightMax((int) Math.round(value));
            case K_SKY_MIN -> this.draft.setSkyLightMin((int) Math.round(value));
            case K_SKY_MAX -> this.draft.setSkyLightMax((int) Math.round(value));
            case K_MIN_PLAYERS -> this.draft.setMinPlayers((int) Math.round(value));
            case K_MAX_PLAYERS -> this.draft.setMaxPlayers((int) Math.round(value));
            case K_HEALTH_RATIO -> this.draft.setMaxHealthRatio(value);
            case K_MIN_LEVEL -> this.draft.setMinLevel((int) Math.round(value));
            case K_CLEAR_X -> this.draft.setClearanceX((int) Math.round(value));
            case K_CLEAR_Y -> this.draft.setClearanceY((int) Math.round(value));
            case K_CLEAR_Z -> this.draft.setClearanceZ((int) Math.round(value));
            case K_INTERVAL -> this.draft.setCheckInterval((int) Math.round(value));
            case K_MAX_WORLD -> this.draft.setMaxPerWorld((int) Math.round(value));
            case K_MAX_PLAYER -> this.draft.setMaxPerPlayer((int) Math.round(value));
            case K_COOLDOWN -> this.draft.setCooldownTicks((int) Math.round(value));
            case K_MIN_DIST -> this.draft.setMinDistanceToPlayer((int) Math.round(value));
            case K_MAX_DIST -> this.draft.setMaxDistanceToPlayer((int) Math.round(value));
            case K_MIN_BETWEEN -> this.draft.setMinDistanceBetween((int) Math.round(value));
            default -> {
                // 未知键忽略
            }
        }
    }

    // ==================== 操作 ====================

    private void save() {
        GiantMonsterDefinition definition = this.definition();
        if (definition == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.empty"), COLOR_ERROR);
            return;
        }
        try {
            this.captureFields(true);
        } catch (RuntimeException e) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.invalid",
                    e.getMessage() == null ? "?" : e.getMessage()), COLOR_ERROR);
            return;
        }
        GiantMonsterDefinition copy = definition.copy();
        copy.setSpawn(this.draft);
        if (!this.saveDefinition(copy)) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        GiantMonsterSpawnManager.resetRuntimeState();
        this.draft = copy.getSpawn().copy();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.saved"), COLOR_OK);
    }

    private void resetToDefault() {
        if (this.monsterId == null) {
            return;
        }
        GiantMonsterDefinition definition =
                com.example.titans_creation.giantmonster.GiantMonsterConfigManager.resetToDefault(this.monsterId);
        if (definition == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.draft = definition.getSpawn().copy();
        GiantMonsterSpawnManager.resetRuntimeState();
        this.rebuildWidgets();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.reset"), COLOR_OK);
    }

    @Override
    public void onClose() {
        this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId));
    }

    // ==================== 渲染 ====================

    /** 随内容一起滚动的字段标签。 */
    @Override
    protected void renderScrollContent(GuiGraphics guiGraphics) {
        for (LabeledWidget labeled : this.labels) {
            guiGraphics.drawString(this.font, Component.translatable(labeled.labelKey()),
                    this.centerX() - 190, labeled.widget().getY() + 4, COLOR_LABEL, false);
        }
        int y = this.hintsTop - this.scrollOffset();
        for (Component hint : List.of(
                Component.translatable("titans_creation.giantmonster.hint.spawn"),
                Component.translatable("titans_creation.giantmonster.hint.file"))) {
            guiGraphics.drawCenteredString(this.font, hint, this.centerX(), y, COLOR_HINT);
            y += 11;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        GiantMonsterDefinition definition = this.definition();
        this.renderTitle(guiGraphics, definition == null ? null
                : Component.literal(definition.getDisplayName() + "  (" + definition.getId() + ")"));
        this.renderWidgets(guiGraphics, mouseX, mouseY, partialTick);
        this.renderStatus(guiGraphics);
    }
}
