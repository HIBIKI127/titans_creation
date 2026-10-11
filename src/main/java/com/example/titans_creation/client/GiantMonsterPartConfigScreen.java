package com.example.titans_creation.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.part.GiantMonsterClimbConfig;
import com.example.titans_creation.giantmonster.part.GiantMonsterPartDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 三级菜单：部位与攀爬配置。
 *
 * <p>上方编辑部位模块列表（JSON 数组），每个部位可配置：
 * 模块类型（无碰撞不可交互 / 无碰撞可交互 / 有碰撞可交互）、绑定的动画骨骼、尺寸、偏移、旋转、
 * 是否可攀附等。碰撞箱会随骨骼动画实时改变位置与朝向。
 *
 * <p>下方配置攀爬与站立参数，其中 <b>最大可站立倾角</b> 决定多陡的面还能站住，
 * 超过该倾角就只能靠攀爬。
 */
public class GiantMonsterPartConfigScreen extends GiantMonsterScreen {

    private static final String K_MAX_STAND_ANGLE = "max_stand_angle";
    private static final String K_CONTACT_DISTANCE = "contact_distance";
    private static final String K_GROUND_PROBE = "ground_probe_distance";
    private static final String K_PUSH_OUT = "push_out_margin";
    private static final String K_CLIMB_SPEED = "climb_speed";
    private static final String K_CLIMB_OFFSET = "climb_offset";

    private GiantMonsterClimbConfig draftClimb;
    private EditBox partsBox;
    /** 滚动内容中说明文字的起始 Y（布局坐标）。 */
    private int hintsTop;

    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final List<LabeledWidget> labels = new ArrayList<>();

    private record LabeledWidget(String labelKey, AbstractWidget widget) {
    }

    public GiantMonsterPartConfigScreen(String monsterId) {
        super(Component.translatable("titans_creation.giantmonster.menu.parts"), monsterId);
        GiantMonsterDefinition definition = this.definition();
        this.draftClimb = definition == null
                ? new GiantMonsterClimbConfig()
                : definition.getClimb().copy();
    }

    @Override
    protected void init() {
        this.boxes.clear();
        this.labels.clear();
        final int centerX = this.centerX();

        this.partsBox = this.addScrollable(new EditBox(this.font, centerX - 200, 50, 400, 18,
                Component.translatable(fieldKey(GiantMonsterDefinition.KEY_PARTS))));
        this.partsBox.setMaxLength(8192);
        GiantMonsterDefinition definition = this.definition();
        this.partsBox.setValue(definition == null ? "[]" : partsToJson(definition.getParts()));

        int y = 84;
        y = this.addToggle(y, fieldKey("climb_enabled"), this.draftClimb::isEnabled, this.draftClimb::setEnabled);
        y = this.addToggle(y, fieldKey("allow_standing"), this.draftClimb::isAllowStanding,
                this.draftClimb::setAllowStanding);
        y = this.addToggle(y, fieldKey("allow_climbing"), this.draftClimb::isAllowClimbing,
                this.draftClimb::setAllowClimbing);
        y = this.addNumber(y, K_MAX_STAND_ANGLE, fieldKey("max_stand_angle"), this.draftClimb.getMaxStandAngle());
        y = this.addNumber(y, K_CONTACT_DISTANCE, fieldKey("contact_distance"),
                this.draftClimb.getContactDistance());
        y = this.addNumber(y, K_GROUND_PROBE, fieldKey("ground_probe_distance"),
                this.draftClimb.getGroundProbeDistance());
        y = this.addNumber(y, K_PUSH_OUT, fieldKey("push_out_margin"), this.draftClimb.getPushOutMargin());
        y = this.addNumber(y, K_CLIMB_SPEED, fieldKey("climb_speed"), this.draftClimb.getClimbSpeed());
        y = this.addNumber(y, K_CLIMB_OFFSET, fieldKey("climb_offset"), this.draftClimb.getClimbOffset());
        // 说明文字也放在滚动内容里，随内容一起滚动
        this.hintsTop = y + 6;

        y += 4;
        // ===== 固定在底部的操作按钮 =====
        final int buttonY = this.height - 64;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.save"), pressed -> this.save())
                .bounds(centerX - 200, buttonY, 195, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.reset"), pressed -> this.resetToDefault())
                .bounds(centerX + 5, buttonY, 195, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.back"),
                        pressed -> this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId)))
                .bounds(centerX - 200, buttonY + 22, 400, 18)
                .build());

        this.contentBottom = this.height - 94;
        this.finishScrollLayout(34, this.height - 94);
    }

    @Override
    public void onClose() {
        this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId));
    }

    // ==================== 控件 ====================

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
                .bounds(this.centerX() - 200, y, 400, 18)
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

    // ==================== 读写 ====================

    private void captureFields(boolean strict) {
        for (Map.Entry<String, EditBox> entry : this.boxes.entrySet()) {
            String raw = entry.getValue().getValue().trim();
            if (raw.isEmpty()) {
                if (strict) {
                    throw new IllegalArgumentException(entry.getKey() + " 不能为空");
                }
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                if (strict) {
                    throw new IllegalArgumentException(entry.getKey() + " = \"" + raw + "\"");
                }
                continue;
            }
            switch (entry.getKey()) {
                case K_MAX_STAND_ANGLE -> this.draftClimb.setMaxStandAngle(value);
                case K_CONTACT_DISTANCE -> this.draftClimb.setContactDistance(value);
                case K_GROUND_PROBE -> this.draftClimb.setGroundProbeDistance(value);
                case K_PUSH_OUT -> this.draftClimb.setPushOutMargin(value);
                case K_CLIMB_SPEED -> this.draftClimb.setClimbSpeed(value);
                case K_CLIMB_OFFSET -> this.draftClimb.setClimbOffset(value);
                default -> {
                }
            }
        }
    }

    private void save() {
        GiantMonsterDefinition definition = this.definition();
        if (definition == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.empty"), COLOR_ERROR);
            return;
        }
        List<GiantMonsterPartDefinition> parts;
        try {
            this.captureFields(true);
            parts = parseParts(this.partsBox == null ? "[]" : this.partsBox.getValue());
        } catch (RuntimeException e) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.invalid",
                    e.getMessage() == null ? "?" : e.getMessage()), COLOR_ERROR);
            return;
        }
        GiantMonsterDefinition copy = definition.copy();
        copy.setParts(parts);
        copy.setClimb(this.draftClimb);
        if (!this.saveDefinition(copy)) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.draftClimb = copy.getClimb().copy();
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
        this.draftClimb = definition.getClimb().copy();
        this.rebuildWidgets();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.reset"), COLOR_OK);
    }

    private static String partsToJson(List<GiantMonsterPartDefinition> parts) {
        JsonArray array = new JsonArray();
        for (GiantMonsterPartDefinition part : parts) {
            array.add(part.toJson());
        }
        return array.toString();
    }

    private static List<GiantMonsterPartDefinition> parseParts(String raw) {
        List<GiantMonsterPartDefinition> result = new ArrayList<>();
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return result;
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("parts JSON 格式错误");
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("parts 需要 JSON 数组");
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                throw new IllegalArgumentException("parts 的元素需要是 JSON 对象");
            }
            result.add(GiantMonsterPartDefinition.fromJson(item.getAsJsonObject()));
        }
        return result;
    }

    /** 随内容一起滚动的标签。 */
    @Override
    protected void renderScrollContent(GuiGraphics guiGraphics) {
        if (this.partsBox != null) {
            guiGraphics.drawString(this.font, Component.translatable(fieldKey(GiantMonsterDefinition.KEY_PARTS)),
                    this.partsBox.getX(), this.partsBox.getY() - 11, COLOR_LABEL, false);
        }
        for (LabeledWidget labeled : this.labels) {
            guiGraphics.drawString(this.font, Component.translatable(labeled.labelKey()),
                    this.centerX() - 200, labeled.widget().getY() + 4, COLOR_LABEL, false);
        }
        int y = this.hintsTop - this.scrollOffset();
        for (Component hint : List.of(
                Component.translatable("titans_creation.giantmonster.hint.parts.types"),
                Component.translatable("titans_creation.giantmonster.hint.parts.animation"),
                Component.translatable("titans_creation.giantmonster.hint.climb"))) {
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
