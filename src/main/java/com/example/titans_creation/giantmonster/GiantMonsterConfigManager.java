package com.example.titans_creation.giantmonster;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 巨型怪物配置文件管理器。
 *
 * <p>每只巨型怪物对应一份 JSON 配置，存放在
 * {@code config/titans_creation/monsters/<怪物id>.json}。
 * 新增怪物时（在 {@link GiantMonsterRegistry} 中注册）无需手动创建文件：
 * 首次启动会自动写出该怪物的默认配置文件，之后即可在游戏内菜单或直接编辑文件修改。
 *
 * <p>配置的生效方式：
 * <ul>
 *   <li>新建/新生成的怪物在构造时自动套用当前配置；</li>
 *   <li>已生成的怪物可用服务端指令 {@code /giantmonster reload} 立即套用（单人游戏下菜单保存后会自动同步）。</li>
 * </ul>
 */
public final class GiantMonsterConfigManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 配置文件所在目录（相对于游戏 config 目录）。 */
    public static final String CONFIG_SUBDIRECTORY = "titans_creation/monsters";

    /** 注册时登记的默认定义（由 {@link GiantMonsterRegistry} 填充）。 */
    private static final Map<String, GiantMonsterDefinition> DEFAULTS = new LinkedHashMap<>();
    /** 当前生效的定义。 */
    private static final Map<String, GiantMonsterDefinition> DEFINITIONS = new LinkedHashMap<>();

    private static boolean loaded = false;

    private GiantMonsterConfigManager() {
    }

    /** 配置文件目录。 */
    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_SUBDIRECTORY);
    }

    // ==================== 注册与加载 ====================

    /**
     * 登记一只巨型怪物的默认定义。由 {@link GiantMonsterRegistry#registerMonster} 自动调用，
     * 无需在新增怪物时手工调用。
     */
    public static void registerDefaults(String id, GiantMonsterDefinition definition) {
        GiantMonsterDefinition copy = definition.copy().sanitize();
        DEFAULTS.put(id, copy);
        DEFINITIONS.putIfAbsent(id, copy.copy());
    }

    /**
     * 读取全部怪物配置；不存在的配置文件会被自动生成（这是「新增怪物自动获得配置文件」的实现点）。
     */
    public static void loadAll() {
        Path directory = directory();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOGGER.error("[titans_creation] 无法创建巨型怪物配置目录: {}", directory, e);
            return;
        }

        for (Map.Entry<String, GiantMonsterDefinition> entry : DEFAULTS.entrySet()) {
            String id = entry.getKey();
            Path file = directory.resolve(id + ".json");
            if (!Files.exists(file)) {
                // 新增怪物：自动生成默认配置文件
                GiantMonsterDefinition definition = entry.getValue().copy();
                DEFINITIONS.put(id, definition);
                writeToDisk(definition);
                LOGGER.info("[titans_creation] 已生成巨型怪物默认配置: {}", file);
                continue;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                GiantMonsterDefinition definition = json == null
                        ? entry.getValue().copy()
                        : GiantMonsterDefinition.fromJson(id, json);
                definition = migrate(entry.getValue(), definition);
                DEFINITIONS.put(id, definition.sanitize());
            } catch (Exception e) {
                LOGGER.error("[titans_creation] 读取巨型怪物配置失败，本次使用默认值: {}", file, e);
                DEFINITIONS.put(id, entry.getValue().copy());
            }
        }

        loaded = true;
        LOGGER.info("[titans_creation] 已加载 {} 份巨型怪物配置（目录: {}）", DEFINITIONS.size(), directory);
    }

    /** 重新从磁盘读取全部配置（{@code /giantmonster reload} 使用）。 */
    public static void reload() {
        loadAll();
    }

    /**
     * 配置结构迁移：旧版本文件中的结构字段（部位模块几何、攀爬参数）会被新的默认结构覆盖并回写文件，
     * 因此升级模组后无需手动点「重置默认」。
     *
     * <p>数值类字段（血量、攻击、生成条件、仇恨设置等）保持玩家已保存的值不变。
     */
    private static GiantMonsterDefinition migrate(GiantMonsterDefinition defaults, GiantMonsterDefinition loaded) {
        if (loaded.getConfigVersion() >= GiantMonsterDefinition.CONFIG_VERSION) {
            return loaded;
        }
        LOGGER.info("[titans_creation] 配置结构升级: {} (v{} -> v{})，已套用新的部位模块与攀爬参数",
                loaded.getId(), loaded.getConfigVersion(), GiantMonsterDefinition.CONFIG_VERSION);
        loaded.setParts(defaults.getParts());
        loaded.setClimb(defaults.getClimb());
        loaded.setConfigVersion(GiantMonsterDefinition.CONFIG_VERSION);
        writeToDisk(loaded);
        return loaded;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // ==================== 读取与写入 ====================

    /** 所有已注册的怪物 id。 */
    public static List<String> ids() {
        return new ArrayList<>(DEFINITIONS.keySet());
    }

    /** 按 id 获取当前生效的定义（不存在返回 null）。 */
    public static GiantMonsterDefinition get(String id) {
        GiantMonsterDefinition definition = DEFINITIONS.get(id);
        return definition == null ? null : definition;
    }

    /** 按实体类型获取当前生效的定义（不是巨型怪物时返回 null）。 */
    public static GiantMonsterDefinition get(EntityType<?> type) {
        if (type == null) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key == null ? null : get(key.getPath());
    }

    /** 获取某怪物的出厂默认定义（副本）。 */
    public static GiantMonsterDefinition getDefault(String id) {
        GiantMonsterDefinition definition = DEFAULTS.get(id);
        return definition == null ? null : definition.copy();
    }

    /** 恢复出厂默认值并保存到磁盘。 */
    public static GiantMonsterDefinition resetToDefault(String id) {
        GiantMonsterDefinition definition = getDefault(id);
        if (definition == null) {
            return null;
        }
        save(definition);
        return definition;
    }

    /** 保存定义到磁盘并立即生效（新建怪物会生成新文件）。 */
    public static boolean save(GiantMonsterDefinition definition) {
        if (definition == null) {
            return false;
        }
        definition.sanitize();
        if (!writeToDisk(definition)) {
            return false;
        }
        DEFINITIONS.put(definition.getId(), definition.copy());
        return true;
    }

    private static boolean writeToDisk(GiantMonsterDefinition definition) {
        Path directory = directory();
        Path file = directory.resolve(definition.getId() + ".json");
        try {
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(definition.toJson(), writer);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("[titans_creation] 保存巨型怪物配置失败: {}", file, e);
            return false;
        }
    }

    // ==================== 应用到实体 ====================

    /**
     * 把配置套用到一只巨型怪物身上。
     *
     * @param resetHealth true 表示同时把血量补满（用于实体刚构造完成时）；
     *                    false 表示保留当前血量，仅在超过新上限时收敛
     */
    public static void applyTo(GiantMonster monster, boolean resetHealth) {
        // 配置可能已被替换，先让实体丢弃旧的定义缓存
        monster.invalidateDefinitionCache();
        GiantMonsterDefinition definition = get(monster.getType());
        if (definition == null) {
            return;
        }

        setBaseValue(monster, Attributes.MAX_HEALTH, definition.getMaxHealth());
        setBaseValue(monster, Attributes.ATTACK_DAMAGE, definition.getAttackDamage());
        setBaseValue(monster, Attributes.MOVEMENT_SPEED, definition.getMovementSpeed());
        // 基础韧性 -> 原版盔甲韧性；如需改为「抗击退」只需把下面一行换成 Attributes.KNOCKBACK_RESISTANCE
        setBaseValue(monster, Attributes.ARMOR_TOUGHNESS, definition.getToughness());
        setBaseValue(monster, Attributes.ARMOR, definition.getArmor());
        setBaseValue(monster, Attributes.FOLLOW_RANGE, definition.getFollowRange());

        monster.setMaxStamina(definition.getStamina());

        if (resetHealth) {
            monster.setHealth(monster.getMaxHealth());
        } else if (monster.getHealth() > monster.getMaxHealth()) {
            monster.setHealth(monster.getMaxHealth());
        }
    }

    /**
     * 把配置套用到世界中所有已生成的巨型怪物。
     *
     * @return 被更新的怪物数量
     */
    public static int applyToAllLoaded(MinecraftServer server) {
        if (server == null || !loaded) {
            return 0;
        }
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof GiantMonster monster) {
                    applyTo(monster, false);
                    count++;
                }
            }
        }
        return count;
    }

    private static void setBaseValue(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
