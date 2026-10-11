package com.example.titans_creation.giantmonster.part;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * 攀爬与站立配置（配置文件中 {@code climb} 段）。
 *
 * <p>玩家与怪物身体交互的核心是「接触面的倾角」：
 * <ul>
 *   <li>倾角 ≤ {@code max_stand_angle} → 视为可行走表面，玩家可以<b>站立</b>；</li>
 *   <li>倾角 &gt; {@code max_stand_angle} → 无法站立，只能通过<b>攀爬</b>机制附着在表面移动。</li>
 * </ul>
 */
public final class GiantMonsterClimbConfig {

    private boolean enabled = true;
    /** 是否允许站立在可行走表面上。 */
    private boolean allowStanding = true;
    /** 是否允许攀附陡峭表面。 */
    private boolean allowClimbing = true;
    /** 允许站立的最大倾角（度）：0 = 只能站水平面，90 = 任意面。 */
    private double maxStandAngle = 45.0D;
    /** 接触探测的水平距离（格）。 */
    private double contactDistance = 1.3D;
    /** 站立判定的向下探测距离（格）。 */
    private double groundProbeDistance = 0.7D;
    /** 碰撞推出后与表面的额外余量（格）。 */
    private double pushOutMargin = 0.15D;
    /** 攀爬移动速度（格/tick）。 */
    private double climbSpeed = 0.12D;
    /** 攀爬时身体与表面的距离（格）。 */
    private double climbOffset = 0.35D;

    // ==================== 读取 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAllowStanding() {
        return allowStanding;
    }

    public boolean isAllowClimbing() {
        return allowClimbing;
    }

    public double getMaxStandAngle() {
        return maxStandAngle;
    }

    public double getContactDistance() {
        return contactDistance;
    }

    public double getGroundProbeDistance() {
        return groundProbeDistance;
    }

    public double getPushOutMargin() {
        return pushOutMargin;
    }

    public double getClimbSpeed() {
        return climbSpeed;
    }

    public double getClimbOffset() {
        return climbOffset;
    }

    // ==================== 修改 ====================

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public void setAllowStanding(boolean value) {
        this.allowStanding = value;
    }

    public void setAllowClimbing(boolean value) {
        this.allowClimbing = value;
    }

    public void setMaxStandAngle(double value) {
        this.maxStandAngle = value;
    }

    public void setContactDistance(double value) {
        this.contactDistance = value;
    }

    public void setGroundProbeDistance(double value) {
        this.groundProbeDistance = value;
    }

    public void setPushOutMargin(double value) {
        this.pushOutMargin = value;
    }

    public void setClimbSpeed(double value) {
        this.climbSpeed = value;
    }

    public void setClimbOffset(double value) {
        this.climbOffset = value;
    }

    public GiantMonsterClimbConfig sanitize() {
        this.maxStandAngle = Mth.clamp(this.maxStandAngle, 0.0D, 89.0D);
        this.contactDistance = Mth.clamp(this.contactDistance, 0.1D, 8.0D);
        this.groundProbeDistance = Mth.clamp(this.groundProbeDistance, 0.05D, 4.0D);
        this.pushOutMargin = Mth.clamp(this.pushOutMargin, 0.0D, 2.0D);
        this.climbSpeed = Mth.clamp(this.climbSpeed, 0.0D, 2.0D);
        this.climbOffset = Mth.clamp(this.climbOffset, 0.0D, 3.0D);
        return this;
    }

    public GiantMonsterClimbConfig copy() {
        GiantMonsterClimbConfig copy = new GiantMonsterClimbConfig();
        copy.enabled = enabled;
        copy.allowStanding = allowStanding;
        copy.allowClimbing = allowClimbing;
        copy.maxStandAngle = maxStandAngle;
        copy.contactDistance = contactDistance;
        copy.groundProbeDistance = groundProbeDistance;
        copy.pushOutMargin = pushOutMargin;
        copy.climbSpeed = climbSpeed;
        copy.climbOffset = climbOffset;
        return copy;
    }

    // ==================== 序列化 ====================

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("allow_standing", allowStanding);
        json.addProperty("allow_climbing", allowClimbing);
        json.addProperty("max_stand_angle", maxStandAngle);
        json.addProperty("contact_distance", contactDistance);
        json.addProperty("ground_probe_distance", groundProbeDistance);
        json.addProperty("push_out_margin", pushOutMargin);
        json.addProperty("climb_speed", climbSpeed);
        json.addProperty("climb_offset", climbOffset);
        return json;
    }

    public static GiantMonsterClimbConfig fromJson(JsonObject json) {
        GiantMonsterClimbConfig config = new GiantMonsterClimbConfig();
        if (json == null) {
            return config;
        }
        config.enabled = GsonHelper.getAsBoolean(json, "enabled", config.enabled);
        config.allowStanding = GsonHelper.getAsBoolean(json, "allow_standing", config.allowStanding);
        config.allowClimbing = GsonHelper.getAsBoolean(json, "allow_climbing", config.allowClimbing);
        config.maxStandAngle = GsonHelper.getAsDouble(json, "max_stand_angle", config.maxStandAngle);
        config.contactDistance = GsonHelper.getAsDouble(json, "contact_distance", config.contactDistance);
        config.groundProbeDistance = GsonHelper.getAsDouble(json, "ground_probe_distance",
                config.groundProbeDistance);
        config.pushOutMargin = GsonHelper.getAsDouble(json, "push_out_margin", config.pushOutMargin);
        config.climbSpeed = GsonHelper.getAsDouble(json, "climb_speed", config.climbSpeed);
        config.climbOffset = GsonHelper.getAsDouble(json, "climb_offset", config.climbOffset);
        return config.sanitize();
    }

    @Override
    public String toString() {
        return "GiantMonsterClimbConfig[enabled=" + enabled
                + " maxStandAngle=" + maxStandAngle
                + " climbSpeed=" + climbSpeed + "]";
    }
}
