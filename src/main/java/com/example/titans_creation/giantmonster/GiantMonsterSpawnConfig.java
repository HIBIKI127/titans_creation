package com.example.titans_creation.giantmonster;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * 巨型怪物的生成机制配置。
 *
 * <p>共 10 类条件，每一类都可以<b>独立开关与配置</b>（未启用的条件不参与筛选）：
 * <ol>
 *   <li><b>群系 biomes</b>：白名单 / 黑名单，支持具体群系 id 与群系标签（{@code #minecraft:is_forest}）</li>
 *   <li><b>维度 dimensions</b>：白名单 / 黑名单（自动包含其他模组新增的维度）</li>
 *   <li><b>时间 time</b>：任意 / 白天 / 夜晚 / 自定义时间段</li>
 *   <li><b>高度 height</b>：Y 区间、要求地表 / 地下 / 可见天空</li>
 *   <li><b>亮度 light</b>：方块亮度区间、天空亮度区间</li>
 *   <li><b>天气 weather</b>：任意 / 晴朗 / 下雨 / 雷暴</li>
 *   <li><b>月相 moon_phase</b>：允许的月相集合</li>
 *   <li><b>玩家状态 player_state</b>：战斗模式、玩家人数、玩家血量比例、玩家等级、村庄附近</li>
 *   <li><b>地形 terrain</b>：所需净空体积（巨体需要足够空间）</li>
 *   <li><b>频率上限 frequency</b>：检查间隔、全世界上限、每玩家上限、冷却、与玩家距离窗口、彼此最小间距</li>
 * </ol>
 *
 * <p>白名单列表为空时表示"不限制"（即该类条件不生效），这是新怪物的默认状态。
 */
public final class GiantMonsterSpawnConfig {

    /** 列表匹配模式。 */
    public enum ListMode {
        WHITELIST,
        BLACKLIST;

        public static ListMode parse(String name, ListMode fallback) {
            if (name == null) {
                return fallback;
            }
            for (ListMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return mode;
                }
            }
            return fallback;
        }

        public ListMode next() {
            return this == WHITELIST ? BLACKLIST : WHITELIST;
        }
    }

    /** 时间模式。 */
    public enum TimeMode {
        ANY,
        DAY,
        NIGHT,
        CUSTOM;

        public static TimeMode parse(String name, TimeMode fallback) {
            if (name == null) {
                return fallback;
            }
            for (TimeMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return mode;
                }
            }
            return fallback;
        }

        public TimeMode next() {
            TimeMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String id() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 天气模式。 */
    public enum WeatherMode {
        ANY,
        CLEAR,
        RAIN,
        THUNDER;

        public static WeatherMode parse(String name, WeatherMode fallback) {
            if (name == null) {
                return fallback;
            }
            for (WeatherMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return mode;
                }
            }
            return fallback;
        }

        public WeatherMode next() {
            WeatherMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String id() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 月相数量（原版 0~7）。 */
    public static final int MOON_PHASE_COUNT = 8;

    // ==================== 总开关 ====================
    private boolean enabled = true;

    // ==================== ① 群系 / ② 维度 ====================
    private ListMode biomeMode = ListMode.WHITELIST;
    private final List<String> biomeList = new ArrayList<>();
    private ListMode dimensionMode = ListMode.WHITELIST;
    private final List<String> dimensionList = new ArrayList<>();

    // ==================== ③ 时间 ====================
    private TimeMode timeMode = TimeMode.ANY;
    private int timeMin = 13000;
    private int timeMax = 23000;

    // ==================== ④ 高度 ====================
    private boolean heightEnabled = false;
    private int heightMin = 60;
    private int heightMax = 140;
    private boolean onSurface = false;
    private boolean underGround = false;
    private boolean canSeeSky = false;

    // ==================== ⑤ 亮度 ====================
    private boolean lightEnabled = false;
    private int blockLightMin = 0;
    private int blockLightMax = 15;
    private int skyLightMin = 0;
    private int skyLightMax = 15;

    // ==================== ⑥ 天气 ====================
    private WeatherMode weatherMode = WeatherMode.ANY;

    // ==================== ⑦ 月相 ====================
    private boolean moonPhaseEnabled = false;
    private final boolean[] moonPhases = new boolean[MOON_PHASE_COUNT];

    // ==================== ⑧ 玩家状态 ====================
    private boolean requireCombatMode = false;
    private int minPlayers = 1;
    private int maxPlayers = 4;
    private double maxHealthRatio = 1.0D;
    private int minLevel = 0;
    private boolean notInVillage = false;

    // ==================== ⑨ 地形 ====================
    private boolean terrainEnabled = true;
    private int clearanceX = 8;
    private int clearanceY = 12;
    private int clearanceZ = 8;

    // ==================== ⑩ 频率上限 ====================
    private int checkInterval = 300;
    private int maxPerWorld = 1;
    private int maxPerPlayer = 1;
    private int cooldownTicks = 12000;
    private int minDistanceToPlayer = 48;
    private int maxDistanceToPlayer = 128;
    private int minDistanceBetween = 256;

    public GiantMonsterSpawnConfig() {
        for (int i = 0; i < moonPhases.length; i++) {
            moonPhases[i] = true;
        }
    }

    // ==================== 读取 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public ListMode getBiomeMode() {
        return biomeMode;
    }

    public List<String> getBiomeList() {
        return biomeList;
    }

    public ListMode getDimensionMode() {
        return dimensionMode;
    }

    public List<String> getDimensionList() {
        return dimensionList;
    }

    public TimeMode getTimeMode() {
        return timeMode;
    }

    public int getTimeMin() {
        return timeMin;
    }

    public int getTimeMax() {
        return timeMax;
    }

    public boolean isHeightEnabled() {
        return heightEnabled;
    }

    public int getHeightMin() {
        return heightMin;
    }

    public int getHeightMax() {
        return heightMax;
    }

    public boolean isOnSurface() {
        return onSurface;
    }

    public boolean isUnderGround() {
        return underGround;
    }

    public boolean isCanSeeSky() {
        return canSeeSky;
    }

    public boolean isLightEnabled() {
        return lightEnabled;
    }

    public int getBlockLightMin() {
        return blockLightMin;
    }

    public int getBlockLightMax() {
        return blockLightMax;
    }

    public int getSkyLightMin() {
        return skyLightMin;
    }

    public int getSkyLightMax() {
        return skyLightMax;
    }

    public WeatherMode getWeatherMode() {
        return weatherMode;
    }

    public boolean isMoonPhaseEnabled() {
        return moonPhaseEnabled;
    }

    public boolean isMoonPhaseAllowed(int phase) {
        return phase >= 0 && phase < moonPhases.length && moonPhases[phase];
    }

    public boolean[] getMoonPhases() {
        return moonPhases;
    }

    public boolean isRequireCombatMode() {
        return requireCombatMode;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public double getMaxHealthRatio() {
        return maxHealthRatio;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public boolean isNotInVillage() {
        return notInVillage;
    }

    public boolean isTerrainEnabled() {
        return terrainEnabled;
    }

    public int getClearanceX() {
        return clearanceX;
    }

    public int getClearanceY() {
        return clearanceY;
    }

    public int getClearanceZ() {
        return clearanceZ;
    }

    public int getCheckInterval() {
        return checkInterval;
    }

    public int getMaxPerWorld() {
        return maxPerWorld;
    }

    public int getMaxPerPlayer() {
        return maxPerPlayer;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getMinDistanceToPlayer() {
        return minDistanceToPlayer;
    }

    public int getMaxDistanceToPlayer() {
        return maxDistanceToPlayer;
    }

    public int getMinDistanceBetween() {
        return minDistanceBetween;
    }

    // ==================== 修改 ====================

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public void setBiomeMode(ListMode value) {
        this.biomeMode = value;
    }

    public void setDimensionMode(ListMode value) {
        this.dimensionMode = value;
    }

    public void setTimeMode(TimeMode value) {
        this.timeMode = value;
    }

    public void setTimeMin(int value) {
        this.timeMin = value;
    }

    public void setTimeMax(int value) {
        this.timeMax = value;
    }

    public void setHeightEnabled(boolean value) {
        this.heightEnabled = value;
    }

    public void setHeightMin(int value) {
        this.heightMin = value;
    }

    public void setHeightMax(int value) {
        this.heightMax = value;
    }

    public void setOnSurface(boolean value) {
        this.onSurface = value;
    }

    public void setUnderGround(boolean value) {
        this.underGround = value;
    }

    public void setCanSeeSky(boolean value) {
        this.canSeeSky = value;
    }

    public void setLightEnabled(boolean value) {
        this.lightEnabled = value;
    }

    public void setBlockLightMin(int value) {
        this.blockLightMin = value;
    }

    public void setBlockLightMax(int value) {
        this.blockLightMax = value;
    }

    public void setSkyLightMin(int value) {
        this.skyLightMin = value;
    }

    public void setSkyLightMax(int value) {
        this.skyLightMax = value;
    }

    public void setWeatherMode(WeatherMode value) {
        this.weatherMode = value;
    }

    public void setMoonPhaseEnabled(boolean value) {
        this.moonPhaseEnabled = value;
    }

    public void setMoonPhase(int phase, boolean allowed) {
        if (phase >= 0 && phase < moonPhases.length) {
            moonPhases[phase] = allowed;
        }
    }

    public void setAllMoonPhases(boolean allowed) {
        for (int i = 0; i < moonPhases.length; i++) {
            moonPhases[i] = allowed;
        }
    }

    public void setRequireCombatMode(boolean value) {
        this.requireCombatMode = value;
    }

    public void setMinPlayers(int value) {
        this.minPlayers = value;
    }

    public void setMaxPlayers(int value) {
        this.maxPlayers = value;
    }

    public void setMaxHealthRatio(double value) {
        this.maxHealthRatio = value;
    }

    public void setMinLevel(int value) {
        this.minLevel = value;
    }

    public void setNotInVillage(boolean value) {
        this.notInVillage = value;
    }

    public void setTerrainEnabled(boolean value) {
        this.terrainEnabled = value;
    }

    public void setClearanceX(int value) {
        this.clearanceX = value;
    }

    public void setClearanceY(int value) {
        this.clearanceY = value;
    }

    public void setClearanceZ(int value) {
        this.clearanceZ = value;
    }

    public void setCheckInterval(int value) {
        this.checkInterval = value;
    }

    public void setMaxPerWorld(int value) {
        this.maxPerWorld = value;
    }

    public void setMaxPerPlayer(int value) {
        this.maxPerPlayer = value;
    }

    public void setCooldownTicks(int value) {
        this.cooldownTicks = value;
    }

    public void setMinDistanceToPlayer(int value) {
        this.minDistanceToPlayer = value;
    }

    public void setMaxDistanceToPlayer(int value) {
        this.maxDistanceToPlayer = value;
    }

    public void setMinDistanceBetween(int value) {
        this.minDistanceBetween = value;
    }

    /** 把数值限制在合理区间，避免配置失误导致异常行为。 */
    public GiantMonsterSpawnConfig sanitize() {
        this.timeMin = Mth.clamp(this.timeMin, 0, 23999);
        this.timeMax = Mth.clamp(this.timeMax, 0, 23999);
        this.heightMin = Mth.clamp(this.heightMin, -256, 512);
        this.heightMax = Mth.clamp(this.heightMax, -256, 512);
        if (this.heightMax < this.heightMin) {
            int swap = this.heightMin;
            this.heightMin = this.heightMax;
            this.heightMax = swap;
        }
        this.blockLightMin = Mth.clamp(this.blockLightMin, 0, 15);
        this.blockLightMax = Mth.clamp(this.blockLightMax, 0, 15);
        if (this.blockLightMax < this.blockLightMin) {
            int swap = this.blockLightMin;
            this.blockLightMin = this.blockLightMax;
            this.blockLightMax = swap;
        }
        this.skyLightMin = Mth.clamp(this.skyLightMin, 0, 15);
        this.skyLightMax = Mth.clamp(this.skyLightMax, 0, 15);
        if (this.skyLightMax < this.skyLightMin) {
            int swap = this.skyLightMin;
            this.skyLightMin = this.skyLightMax;
            this.skyLightMax = swap;
        }
        this.minPlayers = Mth.clamp(this.minPlayers, 1, 64);
        this.maxPlayers = Mth.clamp(this.maxPlayers, 1, 64);
        if (this.maxPlayers < this.minPlayers) {
            int swap = this.minPlayers;
            this.minPlayers = this.maxPlayers;
            this.maxPlayers = swap;
        }
        this.maxHealthRatio = Mth.clamp(this.maxHealthRatio, 0.05D, 1.0D);
        this.minLevel = Mth.clamp(this.minLevel, 0, 1000);
        this.clearanceX = Mth.clamp(this.clearanceX, 1, 32);
        this.clearanceY = Mth.clamp(this.clearanceY, 1, 64);
        this.clearanceZ = Mth.clamp(this.clearanceZ, 1, 32);
        this.checkInterval = Mth.clamp(this.checkInterval, 20, 24000);
        this.maxPerWorld = Mth.clamp(this.maxPerWorld, 0, 64);
        this.maxPerPlayer = Mth.clamp(this.maxPerPlayer, 0, 64);
        this.cooldownTicks = Mth.clamp(this.cooldownTicks, 0, 2_400_000);
        this.minDistanceToPlayer = Mth.clamp(this.minDistanceToPlayer, 8, 512);
        this.maxDistanceToPlayer = Mth.clamp(this.maxDistanceToPlayer, 8, 512);
        if (this.maxDistanceToPlayer < this.minDistanceToPlayer) {
            int swap = this.minDistanceToPlayer;
            this.minDistanceToPlayer = this.maxDistanceToPlayer;
            this.maxDistanceToPlayer = swap;
        }
        this.minDistanceBetween = Mth.clamp(this.minDistanceBetween, 0, 4096);
        return this;
    }

    public GiantMonsterSpawnConfig copy() {
        GiantMonsterSpawnConfig copy = new GiantMonsterSpawnConfig();
        copy.enabled = enabled;
        copy.biomeMode = biomeMode;
        copy.biomeList.clear();
        copy.biomeList.addAll(biomeList);
        copy.dimensionMode = dimensionMode;
        copy.dimensionList.clear();
        copy.dimensionList.addAll(dimensionList);
        copy.timeMode = timeMode;
        copy.timeMin = timeMin;
        copy.timeMax = timeMax;
        copy.heightEnabled = heightEnabled;
        copy.heightMin = heightMin;
        copy.heightMax = heightMax;
        copy.onSurface = onSurface;
        copy.underGround = underGround;
        copy.canSeeSky = canSeeSky;
        copy.lightEnabled = lightEnabled;
        copy.blockLightMin = blockLightMin;
        copy.blockLightMax = blockLightMax;
        copy.skyLightMin = skyLightMin;
        copy.skyLightMax = skyLightMax;
        copy.weatherMode = weatherMode;
        copy.moonPhaseEnabled = moonPhaseEnabled;
        System.arraycopy(moonPhases, 0, copy.moonPhases, 0, moonPhases.length);
        copy.requireCombatMode = requireCombatMode;
        copy.minPlayers = minPlayers;
        copy.maxPlayers = maxPlayers;
        copy.maxHealthRatio = maxHealthRatio;
        copy.minLevel = minLevel;
        copy.notInVillage = notInVillage;
        copy.terrainEnabled = terrainEnabled;
        copy.clearanceX = clearanceX;
        copy.clearanceY = clearanceY;
        copy.clearanceZ = clearanceZ;
        copy.checkInterval = checkInterval;
        copy.maxPerWorld = maxPerWorld;
        copy.maxPerPlayer = maxPerPlayer;
        copy.cooldownTicks = cooldownTicks;
        copy.minDistanceToPlayer = minDistanceToPlayer;
        copy.maxDistanceToPlayer = maxDistanceToPlayer;
        copy.minDistanceBetween = minDistanceBetween;
        return copy;
    }

    // ==================== 序列化 ====================

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);

        JsonObject biomes = new JsonObject();
        biomes.addProperty("mode", biomeMode.name().toLowerCase(java.util.Locale.ROOT));
        biomes.add("list", stringListToJson(biomeList));
        json.add("biomes", biomes);

        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("mode", dimensionMode.name().toLowerCase(java.util.Locale.ROOT));
        dimensions.add("list", stringListToJson(dimensionList));
        json.add("dimensions", dimensions);

        JsonObject time = new JsonObject();
        time.addProperty("mode", timeMode.name().toLowerCase(java.util.Locale.ROOT));
        time.addProperty("min", timeMin);
        time.addProperty("max", timeMax);
        json.add("time", time);

        JsonObject height = new JsonObject();
        height.addProperty("enabled", heightEnabled);
        height.addProperty("min", heightMin);
        height.addProperty("max", heightMax);
        height.addProperty("on_surface", onSurface);
        height.addProperty("under_ground", underGround);
        height.addProperty("can_see_sky", canSeeSky);
        json.add("height", height);

        JsonObject light = new JsonObject();
        light.addProperty("enabled", lightEnabled);
        light.addProperty("block_min", blockLightMin);
        light.addProperty("block_max", blockLightMax);
        light.addProperty("sky_min", skyLightMin);
        light.addProperty("sky_max", skyLightMax);
        json.add("light", light);

        json.addProperty("weather", weatherMode.name().toLowerCase(java.util.Locale.ROOT));

        JsonObject moon = new JsonObject();
        moon.addProperty("enabled", moonPhaseEnabled);
        JsonArray phases = new JsonArray();
        for (int i = 0; i < moonPhases.length; i++) {
            if (moonPhases[i]) {
                phases.add(i);
            }
        }
        moon.add("phases", phases);
        json.add("moon_phase", moon);

        JsonObject playerState = new JsonObject();
        playerState.addProperty("require_combat_mode", requireCombatMode);
        playerState.addProperty("min_players", minPlayers);
        playerState.addProperty("max_players", maxPlayers);
        playerState.addProperty("max_health_ratio", maxHealthRatio);
        playerState.addProperty("min_level", minLevel);
        playerState.addProperty("not_in_village", notInVillage);
        json.add("player_state", playerState);

        JsonObject terrain = new JsonObject();
        terrain.addProperty("enabled", terrainEnabled);
        terrain.addProperty("clearance_x", clearanceX);
        terrain.addProperty("clearance_y", clearanceY);
        terrain.addProperty("clearance_z", clearanceZ);
        json.add("terrain", terrain);

        JsonObject frequency = new JsonObject();
        frequency.addProperty("check_interval", checkInterval);
        frequency.addProperty("max_per_world", maxPerWorld);
        frequency.addProperty("max_per_player", maxPerPlayer);
        frequency.addProperty("cooldown_ticks", cooldownTicks);
        frequency.addProperty("min_distance_to_player", minDistanceToPlayer);
        frequency.addProperty("max_distance_to_player", maxDistanceToPlayer);
        frequency.addProperty("min_distance_between", minDistanceBetween);
        json.add("frequency", frequency);
        return json;
    }

    private static JsonArray stringListToJson(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    public static GiantMonsterSpawnConfig fromJson(JsonObject json) {
        GiantMonsterSpawnConfig config = new GiantMonsterSpawnConfig();
        if (json == null) {
            return config;
        }
        config.enabled = GsonHelper.getAsBoolean(json, "enabled", config.enabled);

        JsonObject biomes = getObject(json, "biomes");
        if (biomes != null) {
            config.biomeMode = ListMode.parse(GsonHelper.getAsString(biomes, "mode", null), config.biomeMode);
            readStringList(biomes, "list", config.biomeList);
        }
        JsonObject dimensions = getObject(json, "dimensions");
        if (dimensions != null) {
            config.dimensionMode = ListMode.parse(GsonHelper.getAsString(dimensions, "mode", null),
                    config.dimensionMode);
            readStringList(dimensions, "list", config.dimensionList);
        }
        JsonObject time = getObject(json, "time");
        if (time != null) {
            config.timeMode = TimeMode.parse(GsonHelper.getAsString(time, "mode", null), config.timeMode);
            config.timeMin = GsonHelper.getAsInt(time, "min", config.timeMin);
            config.timeMax = GsonHelper.getAsInt(time, "max", config.timeMax);
        }
        JsonObject height = getObject(json, "height");
        if (height != null) {
            config.heightEnabled = GsonHelper.getAsBoolean(height, "enabled", config.heightEnabled);
            config.heightMin = GsonHelper.getAsInt(height, "min", config.heightMin);
            config.heightMax = GsonHelper.getAsInt(height, "max", config.heightMax);
            config.onSurface = GsonHelper.getAsBoolean(height, "on_surface", config.onSurface);
            config.underGround = GsonHelper.getAsBoolean(height, "under_ground", config.underGround);
            config.canSeeSky = GsonHelper.getAsBoolean(height, "can_see_sky", config.canSeeSky);
        }
        JsonObject light = getObject(json, "light");
        if (light != null) {
            config.lightEnabled = GsonHelper.getAsBoolean(light, "enabled", config.lightEnabled);
            config.blockLightMin = GsonHelper.getAsInt(light, "block_min", config.blockLightMin);
            config.blockLightMax = GsonHelper.getAsInt(light, "block_max", config.blockLightMax);
            config.skyLightMin = GsonHelper.getAsInt(light, "sky_min", config.skyLightMin);
            config.skyLightMax = GsonHelper.getAsInt(light, "sky_max", config.skyLightMax);
        }
        config.weatherMode = WeatherMode.parse(GsonHelper.getAsString(json, "weather", null), config.weatherMode);

        JsonObject moon = getObject(json, "moon_phase");
        if (moon != null) {
            config.moonPhaseEnabled = GsonHelper.getAsBoolean(moon, "enabled", config.moonPhaseEnabled);
            if (moon.has("phases") && moon.get("phases").isJsonArray()) {
                config.setAllMoonPhases(false);
                for (JsonElement element : moon.getAsJsonArray("phases")) {
                    if (element != null && element.isJsonPrimitive()) {
                        config.setMoonPhase(element.getAsInt(), true);
                    }
                }
            }
        }
        JsonObject playerState = getObject(json, "player_state");
        if (playerState != null) {
            config.requireCombatMode = GsonHelper.getAsBoolean(playerState, "require_combat_mode",
                    config.requireCombatMode);
            config.minPlayers = GsonHelper.getAsInt(playerState, "min_players", config.minPlayers);
            config.maxPlayers = GsonHelper.getAsInt(playerState, "max_players", config.maxPlayers);
            config.maxHealthRatio = GsonHelper.getAsDouble(playerState, "max_health_ratio", config.maxHealthRatio);
            config.minLevel = GsonHelper.getAsInt(playerState, "min_level", config.minLevel);
            config.notInVillage = GsonHelper.getAsBoolean(playerState, "not_in_village", config.notInVillage);
        }
        JsonObject terrain = getObject(json, "terrain");
        if (terrain != null) {
            config.terrainEnabled = GsonHelper.getAsBoolean(terrain, "enabled", config.terrainEnabled);
            config.clearanceX = GsonHelper.getAsInt(terrain, "clearance_x", config.clearanceX);
            config.clearanceY = GsonHelper.getAsInt(terrain, "clearance_y", config.clearanceY);
            config.clearanceZ = GsonHelper.getAsInt(terrain, "clearance_z", config.clearanceZ);
        }
        JsonObject frequency = getObject(json, "frequency");
        if (frequency != null) {
            config.checkInterval = GsonHelper.getAsInt(frequency, "check_interval", config.checkInterval);
            config.maxPerWorld = GsonHelper.getAsInt(frequency, "max_per_world", config.maxPerWorld);
            config.maxPerPlayer = GsonHelper.getAsInt(frequency, "max_per_player", config.maxPerPlayer);
            config.cooldownTicks = GsonHelper.getAsInt(frequency, "cooldown_ticks", config.cooldownTicks);
            config.minDistanceToPlayer = GsonHelper.getAsInt(frequency, "min_distance_to_player",
                    config.minDistanceToPlayer);
            config.maxDistanceToPlayer = GsonHelper.getAsInt(frequency, "max_distance_to_player",
                    config.maxDistanceToPlayer);
            config.minDistanceBetween = GsonHelper.getAsInt(frequency, "min_distance_between",
                    config.minDistanceBetween);
        }
        return config.sanitize();
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static void readStringList(JsonObject parent, String key, List<String> target) {
        target.clear();
        if (!parent.has(key) || !parent.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement element : parent.getAsJsonArray(key)) {
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    target.add(value);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "GiantMonsterSpawnConfig[enabled=" + enabled
                + " biomes=" + biomeMode + "/" + biomeList.size()
                + " dimensions=" + dimensionMode + "/" + dimensionList.size()
                + " time=" + timeMode
                + " weather=" + weatherMode
                + " interval=" + checkInterval
                + " maxPerWorld=" + maxPerWorld + "]";
    }
}
