package com.example.titans_creation.giantmonster.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * 部位模块定义（配置文件中 {@code parts} 数组的元素）。
 *
 * <p>一个部位 = 一个可交互模块，包含：
 * <ul>
 *   <li>{@code id} 部位标识；</li>
 *   <li>{@code bone} 绑定的动画骨骼名 —— 碰撞箱的位置与朝向完全由该骨骼的动画变换驱动；</li>
 *   <li>{@code type} 模块类型（无碰撞不可交互 / 无碰撞可交互 / 有碰撞可交互）；</li>
 *   <li>{@code size} 全尺寸（X/Y/Z，格）；</li>
 *   <li>{@code offset} 相对骨骼（实体局部坐标）的偏移；</li>
 *   <li>{@code rotation} 相对骨骼的本地旋转（偏航/俯仰/翻滚，度）；</li>
 *   <li>{@code climbable} 该部位表面是否允许攀附；</li>
 *   <li>{@code interaction_margin} 交互判定的外扩余量（格，便于大体积怪物被命中）；</li>
 *   <li>{@code description} 说明文本。</li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 * { "id": "torso", "bone": "body", "type": "solid_interactive",
 *   "size": [6.0, 8.0, 4.0], "offset": [0.0, 4.5, 0.0], "rotation": [0.0, 0.0, 0.0],
 *   "climbable": true, "interaction_margin": 0.15, "description": "躯干" }
 * }</pre>
 */
public final class GiantMonsterPartDefinition {

    public static final String KEY_ID = "id";
    public static final String KEY_BONE = "bone";
    public static final String KEY_TYPE = "type";
    public static final String KEY_SIZE = "size";
    public static final String KEY_OFFSET = "offset";
    public static final String KEY_PIVOT = "pivot";
    public static final String KEY_ROTATION = "rotation";
    public static final String KEY_CLIMBABLE = "climbable";
    public static final String KEY_INTERACTION_MARGIN = "interaction_margin";
    public static final String KEY_DESCRIPTION = "description";

    private final String id;
    private String bone;
    private GiantMonsterPartType type = GiantMonsterPartType.SOLID_INTERACTIVE;
    private double sizeX = 2.0D;
    private double sizeY = 2.0D;
    private double sizeZ = 2.0D;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    /** 骨骼枢轴（关节）位置；未指定时默认与 offset 相同（即绕自身中心旋转）。 */
    private boolean hasPivot;
    private double pivotX;
    private double pivotY;
    private double pivotZ;
    private double yaw;
    private double pitch;
    private double roll;
    private boolean climbable = true;
    private double interactionMargin = 0.1D;
    private String description = "";

    public GiantMonsterPartDefinition(String id) {
        this.id = id == null || id.isBlank() ? "part" : id.trim();
    }

    // ==================== 读取 ====================

    public String getId() {
        return id;
    }

    public String getBone() {
        return bone == null || bone.isBlank() ? GiantMonsterAnimator.BONE_BODY : bone;
    }

    public GiantMonsterPartType getType() {
        return type;
    }

    public double getSizeX() {
        return sizeX;
    }

    public double getSizeY() {
        return sizeY;
    }

    public double getSizeZ() {
        return sizeZ;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    /** 关节枢轴 X：骨骼旋转围绕该点进行（例如手臂绕肩、腿绕髋）。 */
    public double getPivotX() {
        return hasPivot ? pivotX : offsetX;
    }

    public double getPivotY() {
        return hasPivot ? pivotY : offsetY;
    }

    public double getPivotZ() {
        return hasPivot ? pivotZ : offsetZ;
    }

    public double getYaw() {
        return yaw;
    }

    public double getPitch() {
        return pitch;
    }

    public double getRoll() {
        return roll;
    }

    public boolean isClimbable() {
        return climbable;
    }

    public double getInteractionMargin() {
        return interactionMargin;
    }

    public String getDescription() {
        return description;
    }

    /** 是否可以参与命中/攀附判定。 */
    public boolean isInteractive() {
        return type.isInteractive();
    }

    /** 是否有碰撞体积。 */
    public boolean hasCollision() {
        return type.hasCollision();
    }

    // ==================== 修改（链式，便于注册默认部位） ====================

    public GiantMonsterPartDefinition bone(String value) {
        this.bone = value;
        return this;
    }

    public GiantMonsterPartDefinition type(GiantMonsterPartType value) {
        this.type = value == null ? GiantMonsterPartType.SOLID_INTERACTIVE : value;
        return this;
    }

    public GiantMonsterPartDefinition size(double x, double y, double z) {
        this.sizeX = x;
        this.sizeY = y;
        this.sizeZ = z;
        return this;
    }

    public GiantMonsterPartDefinition offset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    /** 设置骨骼枢轴（关节位置）；骨骼动画会带动部位绕该点旋转，从而让碰撞箱跟着动画移动。 */
    public GiantMonsterPartDefinition pivot(double x, double y, double z) {
        this.hasPivot = true;
        this.pivotX = x;
        this.pivotY = y;
        this.pivotZ = z;
        return this;
    }

    public GiantMonsterPartDefinition rotation(double yawDeg, double pitchDeg, double rollDeg) {
        this.yaw = yawDeg;
        this.pitch = pitchDeg;
        this.roll = rollDeg;
        return this;
    }

    public GiantMonsterPartDefinition climbable(boolean value) {
        this.climbable = value;
        return this;
    }

    public GiantMonsterPartDefinition interactionMargin(double value) {
        this.interactionMargin = value;
        return this;
    }

    public GiantMonsterPartDefinition description(String value) {
        this.description = value == null ? "" : value;
        return this;
    }

    /** 数值钳制。 */
    public GiantMonsterPartDefinition sanitize() {
        this.sizeX = clamp(this.sizeX, 0.05D, 256.0D);
        this.sizeY = clamp(this.sizeY, 0.05D, 256.0D);
        this.sizeZ = clamp(this.sizeZ, 0.05D, 256.0D);
        this.offsetX = clamp(this.offsetX, -512.0D, 512.0D);
        this.offsetY = clamp(this.offsetY, -512.0D, 512.0D);
        this.offsetZ = clamp(this.offsetZ, -512.0D, 512.0D);
        this.pivotX = clamp(this.pivotX, -512.0D, 512.0D);
        this.pivotY = clamp(this.pivotY, -512.0D, 512.0D);
        this.pivotZ = clamp(this.pivotZ, -512.0D, 512.0D);
        this.yaw = clamp(this.yaw, -3600.0D, 3600.0D);
        this.pitch = clamp(this.pitch, -3600.0D, 3600.0D);
        this.roll = clamp(this.roll, -3600.0D, 3600.0D);
        this.interactionMargin = clamp(this.interactionMargin, 0.0D, 16.0D);
        return this;
    }

    public GiantMonsterPartDefinition copy() {
        GiantMonsterPartDefinition copy = new GiantMonsterPartDefinition(id);
        copy.bone = bone;
        copy.type = type;
        copy.sizeX = sizeX;
        copy.sizeY = sizeY;
        copy.sizeZ = sizeZ;
        copy.offsetX = offsetX;
        copy.offsetY = offsetY;
        copy.offsetZ = offsetZ;
        copy.hasPivot = hasPivot;
        copy.pivotX = pivotX;
        copy.pivotY = pivotY;
        copy.pivotZ = pivotZ;
        copy.yaw = yaw;
        copy.pitch = pitch;
        copy.roll = roll;
        copy.climbable = climbable;
        copy.interactionMargin = interactionMargin;
        copy.description = description;
        return copy;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    // ==================== 序列化 ====================

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_ID, id);
        json.addProperty(KEY_BONE, getBone());
        json.addProperty(KEY_TYPE, type.id());
        json.add(KEY_SIZE, vectorToJson(sizeX, sizeY, sizeZ));
        json.add(KEY_OFFSET, vectorToJson(offsetX, offsetY, offsetZ));
        json.add(KEY_PIVOT, vectorToJson(getPivotX(), getPivotY(), getPivotZ()));
        json.add(KEY_ROTATION, vectorToJson(yaw, pitch, roll));
        json.addProperty(KEY_CLIMBABLE, climbable);
        json.addProperty(KEY_INTERACTION_MARGIN, interactionMargin);
        json.addProperty(KEY_DESCRIPTION, description);
        return json;
    }

    private static JsonArray vectorToJson(double x, double y, double z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    public static GiantMonsterPartDefinition fromJson(JsonObject json) {
        GiantMonsterPartDefinition definition = new GiantMonsterPartDefinition(
                GsonHelper.getAsString(json, KEY_ID, "part"));
        definition.bone = GsonHelper.getAsString(json, KEY_BONE, GiantMonsterAnimator.BONE_BODY);
        definition.type = GiantMonsterPartType.parse(GsonHelper.getAsString(json, KEY_TYPE, null),
                GiantMonsterPartType.SOLID_INTERACTIVE);
        double[] size = readVector(json, KEY_SIZE, new double[]{2.0D, 2.0D, 2.0D});
        definition.sizeX = size[0];
        definition.sizeY = size[1];
        definition.sizeZ = size[2];
        double[] offset = readVector(json, KEY_OFFSET, new double[]{0.0D, 0.0D, 0.0D});
        definition.offsetX = offset[0];
        definition.offsetY = offset[1];
        definition.offsetZ = offset[2];
        if (json.has(KEY_PIVOT) && json.get(KEY_PIVOT).isJsonArray()) {
            double[] pivot = readVector(json, KEY_PIVOT, new double[]{offset[0], offset[1], offset[2]});
            definition.pivot(pivot[0], pivot[1], pivot[2]);
        }
        double[] rotation = readVector(json, KEY_ROTATION, new double[]{0.0D, 0.0D, 0.0D});
        definition.yaw = rotation[0];
        definition.pitch = rotation[1];
        definition.roll = rotation[2];
        definition.climbable = GsonHelper.getAsBoolean(json, KEY_CLIMBABLE, true);
        definition.interactionMargin = GsonHelper.getAsDouble(json, KEY_INTERACTION_MARGIN, 0.1D);
        definition.description = GsonHelper.getAsString(json, KEY_DESCRIPTION, "");
        return definition.sanitize();
    }

    private static double[] readVector(JsonObject json, String key, double[] defaults) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return defaults;
        }
        JsonArray array = json.getAsJsonArray(key);
        double[] result = defaults.clone();
        for (int i = 0; i < Math.min(3, array.size()); i++) {
            result[i] = array.get(i).getAsDouble();
        }
        return result;
    }

    @Override
    public String toString() {
        return "Part[" + id + " bone=" + getBone() + " type=" + type.id()
                + " size=(" + sizeX + "," + sizeY + "," + sizeZ + ")]";
    }
}
