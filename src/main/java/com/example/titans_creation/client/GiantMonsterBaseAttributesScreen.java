package com.example.titans_creation.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.titans_creation.giantmonster.GiantMonsterConfigManager;
import com.example.titans_creation.giantmonster.GiantMonsterDefinition;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 二级菜单：基础属性。
 *
 * <p>编辑总血量、基础攻击力、基础移动速度、基础韧性、基础防御力、基础侦测范围、基础耐力，
 * 并提供进入三级菜单（弱点配置 / 抗性配置）与二级菜单（生成控制）的入口。
 */
public class GiantMonsterBaseAttributesScreen extends GiantMonsterScreen {

    private static final List<String> NUMERIC_KEYS = List.of(
            GiantMonsterDefinition.KEY_MAX_HEALTH,
            GiantMonsterDefinition.KEY_ATTACK_DAMAGE,
            GiantMonsterDefinition.KEY_MOVEMENT_SPEED,
            GiantMonsterDefinition.KEY_TOUGHNESS,
            GiantMonsterDefinition.KEY_ARMOR,
            GiantMonsterDefinition.KEY_FOLLOW_RANGE,
            GiantMonsterDefinition.KEY_STAMINA);

    private final Map<String, EditBox> boxes = new LinkedHashMap<>();

    public GiantMonsterBaseAttributesScreen(String monsterId) {
        super(Component.translatable("titans_creation.giantmonster.menu.base"), monsterId);
    }

    @Override
    protected void init() {
        this.boxes.clear();
        final int centerX = this.centerX();
        int y = 34;

        for (String key : NUMERIC_KEYS) {
            EditBox box = new EditBox(this.font, centerX + 10, y, 150, BOX_HEIGHT,
                    Component.translatable(fieldKey(key)));
            box.setMaxLength(24);
            this.boxes.put(key, this.addScrollable(box));
            y += ROW_HEIGHT;
        }

        y += 6;
        final String id = this.monsterId;
        this.addScrollable(Button.builder(
                        Component.translatable("titans_creation.giantmonster.menu.weak_points"),
                        button -> this.openScreen(new GiantMonsterWeakPointScreen(id)))
                .bounds(centerX - 150, y, 300, 18)
                .build());
        y += 22;
        this.addScrollable(Button.builder(
                        Component.translatable("titans_creation.giantmonster.menu.resistances"),
                        button -> this.openScreen(new GiantMonsterResistanceScreen(id)))
                .bounds(centerX - 150, y, 300, 18)
                .build());
        y += 22;
        this.addScrollable(Button.builder(
                        Component.translatable("titans_creation.giantmonster.menu.combat"),
                        button -> this.openScreen(new GiantMonsterCombatControlScreen(id)))
                .bounds(centerX - 150, y, 300, 18)
                .build());
        y += 22;
        this.addScrollable(Button.builder(
                        Component.translatable("titans_creation.giantmonster.menu.spawn"),
                        button -> this.openScreen(new GiantMonsterSpawnControlScreen(id)))
                .bounds(centerX - 150, y, 300, 18)
                .build());
        y += 22;
        this.addScrollable(Button.builder(
                        Component.translatable("titans_creation.giantmonster.menu.parts"),
                        button -> this.openScreen(new GiantMonsterPartConfigScreen(id)))
                .bounds(centerX - 150, y, 300, 18)
                .build());

        // ===== 固定在底部的操作按钮（不随内容滚动）=====
        final int buttonY = this.height - 64;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.save"), button -> this.save())
                .bounds(centerX - 150, buttonY, 145, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.reset"), button -> this.resetToDefault())
                .bounds(centerX + 5, buttonY, 145, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.back"),
                        button -> this.openScreen(new GiantMonsterListScreen()))
                .bounds(centerX - 150, buttonY + 22, 300, 18)
                .build());

        this.contentBottom = this.height - 94;
        this.finishScrollLayout(30, this.height - 94);
        this.loadFields();
    }

    private void loadFields() {
        GiantMonsterDefinition definition = this.definition();
        if (definition == null) {
            return;
        }
        setBox(GiantMonsterDefinition.KEY_MAX_HEALTH, definition.getMaxHealth());
        setBox(GiantMonsterDefinition.KEY_ATTACK_DAMAGE, definition.getAttackDamage());
        setBox(GiantMonsterDefinition.KEY_MOVEMENT_SPEED, definition.getMovementSpeed());
        setBox(GiantMonsterDefinition.KEY_TOUGHNESS, definition.getToughness());
        setBox(GiantMonsterDefinition.KEY_ARMOR, definition.getArmor());
        setBox(GiantMonsterDefinition.KEY_FOLLOW_RANGE, definition.getFollowRange());
        setBox(GiantMonsterDefinition.KEY_STAMINA, definition.getStamina());
    }

    private void setBox(String key, double value) {
        EditBox box = this.boxes.get(key);
        if (box != null) {
            box.setValue(formatNumber(value));
        }
    }

    private double parse(String key) {
        EditBox box = this.boxes.get(key);
        String raw = box == null ? "" : box.getValue().trim();
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " = \"" + raw + "\"");
        }
    }

    private void save() {
        GiantMonsterDefinition current = this.definition();
        if (current == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.empty"), COLOR_ERROR);
            return;
        }
        GiantMonsterDefinition definition = current.copy();
        try {
            definition.setMaxHealth(parse(GiantMonsterDefinition.KEY_MAX_HEALTH));
            definition.setAttackDamage(parse(GiantMonsterDefinition.KEY_ATTACK_DAMAGE));
            definition.setMovementSpeed(parse(GiantMonsterDefinition.KEY_MOVEMENT_SPEED));
            definition.setToughness(parse(GiantMonsterDefinition.KEY_TOUGHNESS));
            definition.setArmor(parse(GiantMonsterDefinition.KEY_ARMOR));
            definition.setFollowRange(parse(GiantMonsterDefinition.KEY_FOLLOW_RANGE));
            definition.setStamina(parse(GiantMonsterDefinition.KEY_STAMINA));
        } catch (RuntimeException e) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.invalid",
                    e.getMessage() == null ? "?" : e.getMessage()), COLOR_ERROR);
            return;
        }
        if (!this.saveDefinition(definition)) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.loadFields();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.saved"), COLOR_OK);
    }

    private void resetToDefault() {
        if (this.monsterId == null) {
            return;
        }
        GiantMonsterDefinition definition = GiantMonsterConfigManager.resetToDefault(this.monsterId);
        if (definition == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.loadFields();
        this.applyToLoadedEntities();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.reset"), COLOR_OK);
    }

    @Override
    public void onClose() {
        this.openScreen(new GiantMonsterListScreen());
    }

    /** 随内容一起滚动的字段标签。 */
    @Override
    protected void renderScrollContent(GuiGraphics guiGraphics) {
        for (String key : NUMERIC_KEYS) {
            EditBox box = this.boxes.get(key);
            if (box != null) {
                guiGraphics.drawString(this.font, Component.translatable(fieldKey(key)),
                        this.centerX() - 150, box.getY() + 4, COLOR_LABEL, false);
            }
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
