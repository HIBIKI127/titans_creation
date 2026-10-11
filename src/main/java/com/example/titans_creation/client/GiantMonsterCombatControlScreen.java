package com.example.titans_creation.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.titans_creation.giantmonster.GiantMonsterCombatConfig;
import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.GiantMonsterDisposition;
import com.example.titans_creation.giantmonster.GiantMonsterState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 二级菜单：仇恨设置。
 *
 * <p>配置内容对应配置文件的 {@code combat} 段：
 * <ul>
 *   <li><b>状态模式</b>：新生成怪物的初始状态（无状态 / 脱战）；</li>
 *   <li><b>友好程度</b>：友好 / 中立 / 敌对；</li>
 *   <li><b>创造模式仇恨</b>：创造模式玩家是否会被怪物仇恨；</li>
 *   <li><b>脱战行为</b>：动作间隔、启用哪些动作、休息回血、环境影响踩踏；</li>
 *   <li><b>战斗与死亡</b>：失去目标多久脱战、死亡后停留多久消失。</li>
 * </ul>
 */
public class GiantMonsterCombatControlScreen extends GiantMonsterScreen {

    private static final String K_INTERVAL = "action_interval";
    private static final String K_REST_HEAL = "rest_heal_per_second";
    private static final String K_LOSE_TARGET = "lose_target_ticks";
    private static final String K_DEATH_STAY = "death_stay_ticks";

    private GiantMonsterCombatConfig draft;
    /** 滚动内容中说明文字的起始 Y（布局坐标）。 */
    private int hintsTop;

    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final List<LabeledWidget> labels = new java.util.ArrayList<>();

    private record LabeledWidget(String labelKey, AbstractWidget widget) {
    }

    public GiantMonsterCombatControlScreen(String monsterId) {
        super(Component.translatable("titans_creation.giantmonster.menu.combat"), monsterId);
        GiantMonsterDefinition definition = this.definition();
        this.draft = definition == null
                ? new GiantMonsterCombatConfig()
                : definition.getCombat().copy();
    }

    @Override
    protected void init() {
        this.boxes.clear();
        this.labels.clear();

        final int centerX = this.centerX();
        int y = 44;

        // ① 状态模式（初始状态）
        y = this.addCycle(y, fieldKey("initial_state"),
                () -> modeLabel(fieldKey("initial_state"), this.draft.getInitialState().id()),
                () -> this.draft.setInitialState(this.draft.getInitialState().nextSpawnState()));

        // ② 友好程度
        y = this.addCycle(y, fieldKey("disposition"),
                () -> modeLabel(fieldKey("disposition"), this.draft.getDisposition().id()),
                () -> this.draft.setDisposition(this.draft.getDisposition().next()));

        // ③ 创造模式仇恨开关
        y = this.addToggle(y, fieldKey("aggro_in_creative"), this.draft::isAggroInCreative,
                this.draft::setAggroInCreative);

        // ④ 脱战行为
        y = this.addNumber(y, K_INTERVAL, fieldKey("action_interval"), this.draft.getActionInterval());
        y = this.addToggle(y, fieldKey("action_idle"), this.draft::isActionIdle, this.draft::setActionIdle);
        y = this.addToggle(y, fieldKey("action_move"), this.draft::isActionMove, this.draft::setActionMove);
        y = this.addToggle(y, fieldKey("action_rest"), this.draft::isActionRest, this.draft::setActionRest);
        y = this.addToggle(y, fieldKey("action_environment"), this.draft::isActionEnvironment,
                this.draft::setActionEnvironment);
        y = this.addNumber(y, K_REST_HEAL, fieldKey("rest_heal_per_second"), this.draft.getRestHealPerSecond());
        y = this.addToggle(y, fieldKey("environment_trample"), this.draft::isEnvironmentTrample,
                this.draft::setEnvironmentTrample);

        // ⑤ 战斗与死亡
        y = this.addNumber(y, K_LOSE_TARGET, fieldKey("lose_target_ticks"), this.draft.getLoseTargetTicks());
        y = this.addNumber(y, K_DEATH_STAY, fieldKey("death_stay_ticks"), this.draft.getDeathStayTicks());
        this.hintsTop = y + 2;

        y += 4;
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
        this.finishScrollLayout(30, this.height - 94);
    }

    @Override
    public void onClose() {
        this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId));
    }

    // ==================== 控件工厂 ====================

    private int addNumber(int y, String key, String labelKey, double value) {
        EditBox box = new EditBox(this.font, this.centerX() + 10, y, 150, BOX_HEIGHT,
                Component.translatable(labelKey));
        box.setMaxLength(16);
        box.setValue(formatNumber(value));
        this.boxes.put(key, this.addScrollable(box));
        this.labels.add(new LabeledWidget(labelKey, box));
        return y + ROW_HEIGHT;
    }

    private int addToggle(int y, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        Button[] holder = new Button[1];
        Button button = Button.builder(toggleLabel(labelKey, getter.getAsBoolean()), pressed -> {
                    setter.accept(!getter.getAsBoolean());
                    holder[0].setMessage(toggleLabel(labelKey, getter.getAsBoolean()));
                })
                .bounds(this.centerX() - 190, y, 380, 18)
                .build();
        holder[0] = button;
        this.addScrollable(button);
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

    private static Component toggleLabel(String labelKey, boolean value) {
        return Component.translatable(labelKey).append(": ").append(Component.translatable(value
                ? "titans_creation.giantmonster.value.on"
                : "titans_creation.giantmonster.value.off"));
    }

    private static Component modeLabel(String labelKey, String modeName) {
        return Component.translatable(labelKey).append(": ").append(Component.translatable(
                "titans_creation.giantmonster.mode." + modeName));
    }

    // ==================== 读写 ====================

    private void captureFields(boolean strict) {
        for (Map.Entry<String, EditBox> entry : this.boxes.entrySet()) {
            String key = entry.getKey();
            String raw = entry.getValue().getValue().trim();
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

    private void applyNumber(String key, double value) {
        switch (key) {
            case K_INTERVAL -> this.draft.setActionInterval((int) Math.round(value));
            case K_REST_HEAL -> this.draft.setRestHealPerSecond(value);
            case K_LOSE_TARGET -> this.draft.setLoseTargetTicks((int) Math.round(value));
            case K_DEATH_STAY -> this.draft.setDeathStayTicks((int) Math.round(value));
            default -> {
            }
        }
    }

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
        copy.setCombat(this.draft);
        if (!this.saveDefinition(copy)) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.draft = copy.getCombat().copy();
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
        this.draft = definition.getCombat().copy();
        this.rebuildWidgets();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.reset"), COLOR_OK);
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
                Component.translatable("titans_creation.giantmonster.hint.combat.state"),
                Component.translatable("titans_creation.giantmonster.hint.combat.disposition"))) {
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

    /** 便于配置界面之外的地方复用状态名称翻译。 */
    public static Component stateName(GiantMonsterState state) {
        return Component.translatable("titans_creation.giantmonster.mode." + state.id());
    }

    /** 便于配置界面之外的地方复用友好程度名称翻译。 */
    public static Component dispositionName(GiantMonsterDisposition disposition) {
        return Component.translatable("titans_creation.giantmonster.mode." + disposition.id());
    }
}
