package com.example.titans_creation;

import com.example.titans_creation.client.accessory.ClientAccessoryData;
import com.example.titans_creation.client.grapple.ClientGrappleData;
import com.example.titans_creation.climb.ClimbGearScanner;
import com.example.titans_creation.grapple.GrappleCrossbowItem;
import com.example.titans_creation.grapple.GrappleManager;
import com.example.titans_creation.item.accessory.AccessoryLookup;
import com.example.titans_creation.item.accessory.AccessoryManager;
import com.example.titans_creation.item.accessory.AccessorySlot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 战斗模式的网络通信。
 *
 * 客户端在战斗模式下开始一次方向挥舞时，发送 {@link CombatSwingStartPacket} 到服务端。
 * 包中携带挥动方向（屏幕角度）。服务端据此计算挥舞平面，并通过 {@link CombatToolTypeRegistry}
 * 查询手持物品的工具类型，在随后的若干 tick 内由 {@link CombatSwingManager} 沿"起点 -> 终点" 方向逐片渐进造成
 * AoE 伤害。
 *
 * 工具类型判定使用 {@link CombatToolTypeRegistry#isCombatTool}，支持原版工具和自定义武器。
 */
public final class CombatNetwork {

    private CombatNetwork() {
    }

    private static final String PROTOCOL_VERSION = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(TitansCreationMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, CombatSwingStartPacket.class,
                (msg, buf) -> buf.writeFloat(msg.swingAngleScreen),
                buf -> new CombatSwingStartPacket(buf.readFloat()),
                CombatNetwork::handleSwingStart);

        CHANNEL.registerMessage(1, TreeFellPacket.class,
                (msg, buf) -> buf.writeFloat(msg.swingAngleScreen),
                buf -> new TreeFellPacket(buf.readFloat()),
                CombatNetwork::handleTreeFell);

        CHANNEL.registerMessage(2, CombatModePacket.class,
                (msg, buf) -> buf.writeBoolean(msg.enabled),
                buf -> new CombatModePacket(buf.readBoolean()),
                CombatNetwork::handleCombatMode);

        // 攀爬输入（客户端 -> 服务端）：装备驱动的攀爬需要服务端知道玩家是否按住"朝表面方向"的移动键
        CHANNEL.registerMessage(3, ClimbInputPacket.class,
                (msg, buf) -> {
                    buf.writeFloat(msg.xxa());
                    buf.writeFloat(msg.zza());
                    buf.writeBoolean(msg.jumping());
                },
                buf -> new ClimbInputPacket(buf.readFloat(), buf.readFloat(), buf.readBoolean()),
                CombatNetwork::handleClimbInput);

        // ===== 饰品栏 =====
        CHANNEL.registerMessage(4, AccessorySyncPacket.class,
                (msg, buf) -> {
                    buf.writeVarInt(msg.stacks().size());
                    for (ItemStack stack : msg.stacks()) {
                        writeStack(buf, stack);
                    }
                },
                buf -> {
                    int size = buf.readVarInt();
                    List<ItemStack> stacks = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        stacks.add(readStack(buf));
                    }
                    return new AccessorySyncPacket(stacks);
                },
                CombatNetwork::handleAccessorySync);

        CHANNEL.registerMessage(5, AccessoryClickPacket.class,
                (msg, buf) -> buf.writeVarInt(msg.slotIndex()),
                buf -> new AccessoryClickPacket(buf.readVarInt()),
                CombatNetwork::handleAccessoryClick);

        // 快速穿戴（Shift+左键）：携带被点击的容器槽位序号
        CHANNEL.registerMessage(6, AccessoryEquipPacket.class,
                (msg, buf) -> buf.writeVarInt(msg.menuSlotIndex()),
                buf -> new AccessoryEquipPacket(buf.readVarInt()),
                CombatNetwork::handleAccessoryEquip);

        // 手套功能键（客户端 -> 服务端）：触发左/右手套槽里主动装备的功能
        CHANNEL.registerMessage(7, GloveTriggerPacket.class,
                (msg, buf) -> buf.writeBoolean(msg.leftSlot()),
                buf -> new GloveTriggerPacket(buf.readBoolean()),
                CombatNetwork::handleGloveTrigger);

        // 抓钩状态同步（服务端 -> 客户端）：客户端据此本地预测与渲染绳索
        CHANNEL.registerMessage(8, GrappleSyncPacket.class,
                (msg, buf) -> {
                    buf.writeVarInt(msg.phase());
                    buf.writeDouble(msg.x());
                    buf.writeDouble(msg.y());
                    buf.writeDouble(msg.z());
                    buf.writeDouble(msg.pullSpeed());
                },
                buf -> new GrappleSyncPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble()),
                CombatNetwork::handleGrappleSync);
    }

    /**
     * 写入一个物品栈（显式编码：是否存在 + 物品 id + 数量 + NBT），空栈也能安全传输。
     */
    private static void writeStack(FriendlyByteBuf buf, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeVarInt(Item.getId(stack.getItem()));
        buf.writeVarInt(stack.getCount());
        buf.writeNbt(stack.getTag());
    }

    /** 读取一个物品栈（与 {@link #writeStack} 对应）。 */
    private static ItemStack readStack(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY;
        }
        Item item = Item.byId(buf.readVarInt());
        int count = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        if (item == null || item == Items.AIR || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);
        if (tag != null) {
            stack.setTag(tag);
        }
        return stack;
    }

    /**
     * 饰品栏同步包（服务端 -> 客户端）：携带全部 5 个饰品槽位的内容。
     */
    public static record AccessorySyncPacket(List<ItemStack> stacks) {

    }

    /**
     * 饰品栏点击包（客户端 -> 服务端）：携带被点击的槽位序号。
     */
    public static record AccessoryClickPacket(int slotIndex) {

    }

    /**
     * 快速穿戴包（客户端 -> 服务端）：Shift+左键点击背包里的饰品时发送，
     * 携带被点击的容器槽位序号。
     */
    public static record AccessoryEquipPacket(int menuSlotIndex) {

    }

    /**
     * 手套功能键包（客户端 -> 服务端）：true 表示左手套槽，false 表示右手套槽。
     */
    public static record GloveTriggerPacket(boolean leftSlot) {

    }

    /**
     * 抓钩状态同步包（服务端 -> 客户端）。
     *
     * @param phase     0 = 无钩爪，1 = 飞行中，2 = 拉动中
     * @param x/y/z     钩爪当前位置（飞行中）或锚点位置（拉动中）
     * @param pullSpeed 拉动速度（格/tick）
     */
    public static record GrappleSyncPacket(int phase, double x, double y, double z, double pullSpeed) {

    }

    /**
     * 攀爬输入包：客户端上报移动/跳跃按键状态，服务端据此判定攀爬与滞留。
     *
     * <p>之所以要自建输入包：原版 {@code xxa/zza} 在服务端每 tick 会衰减，
     * 而原版输入包只在按键变化时发送，长时间按住会失效。
     */
    public static record ClimbInputPacket(float xxa, float zza, boolean jumping) {

    }

    /**
     * 战斗模式开关包：客户端切换战斗模式时同步给服务端，
     * 供生成机制的「玩家状态」条件等服务端逻辑判断。
     */
    public static record CombatModePacket(boolean enabled) {

    }

    /**
     * 战挥开始包：携带挥动方向的屏幕角度（度，顺时针，正上方为 0）。
     */
    public static record CombatSwingStartPacket(float swingAngleScreen) {

    }

    /**
     * 伐树包：携带挥动方向的屏幕角度。服务端通过玩家视线射线检测目标原木方块，BFS 搜索整棵树并破坏。
     */
    public static record TreeFellPacket(float swingAngleScreen) {

    }

    private static void handleSwingStart(CombatSwingStartPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 通过注册表查询工具类型（支持原版工具和自定义武器）
            CombatToolType toolType = CombatToolTypeRegistry.getForStack(player.getMainHandItem());
            if (toolType == null) {
                return;
            }

            // 计算挥舞几何（forward0 / swingDir），与客户端保持一致
            Vec3 forward = player.getLookAngle().normalize();
            Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
            Vec3 camRight = forward.cross(worldUp);
            if (camRight.lengthSqr() < 1.0E-8) {
                camRight = new Vec3(1.0, 0.0, 0.0);
            } else {
                camRight = camRight.normalize();
            }
            Vec3 camUp = camRight.cross(forward).normalize();
            double angleRad = Math.toRadians(msg.swingAngleScreen());
            Vec3 swingDir = camRight.scale(Math.sin(angleRad)).add(camUp.scale(Math.cos(angleRad)));
            if (swingDir.lengthSqr() < 1.0E-12) {
                swingDir = camUp;
            } else {
                swingDir = swingDir.normalize();
            }

            CombatSwingManager.startSwing(player, forward, swingDir, msg.swingAngleScreen(), toolType);
        });
        context.setPacketHandled(true);
    }

    private static void handleTreeFell(TreeFellPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            CombatToolType toolType = CombatToolTypeRegistry.getForStack(player.getMainHandItem());
            if (toolType == null || !toolType.canFellTrees()) {
                return;
            }
            TreeFellManager.fellTree(player, toolType);
        });
        context.setPacketHandled(true);
    }

    private static void handleCombatMode(CombatModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                CombatServerState.setCombatMode(player.getUUID(), msg.enabled());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleClimbInput(ClimbInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClimbGearScanner.setInput(player.getUUID(), msg.xxa(), msg.zza(), msg.jumping());
            }
        });
        context.setPacketHandled(true);
    }

    /** 饰品栏同步：只在客户端处理，写入客户端缓存供装备栏 UI 显示。 */
    private static void handleAccessorySync(AccessorySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAccessoryData.setStacks(msg.stacks())));
        context.setPacketHandled(true);
    }

    /** 饰品栏点击：服务端权威处理装备/卸下/交换。 */
    private static void handleAccessoryClick(AccessoryClickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AccessoryManager.handleClick(player, msg.slotIndex());
            }
        });
        context.setPacketHandled(true);
    }

    /** 快速穿戴：服务端权威地把背包槽位里的饰品装进空槽位。 */
    private static void handleAccessoryEquip(AccessoryEquipPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AccessoryManager.equipFromMenuSlot(player, msg.menuSlotIndex());
            }
        });
        context.setPacketHandled(true);
    }

    /** 手套功能键：触发对应手套槽里主动装备（目前为抓钩手弩）的功能。 */
    private static void handleGloveTrigger(GloveTriggerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            AccessorySlot slot = msg.leftSlot() ? AccessorySlot.GLOVES_LEFT : AccessorySlot.GLOVES_RIGHT;
            ItemStack equipped = AccessoryLookup.get(player, slot);
            if (equipped.getItem() instanceof GrappleCrossbowItem crossbow) {
                GrappleManager.toggle(player, crossbow);
            }
        });
        context.setPacketHandled(true);
    }

    /** 抓钩状态同步：只在客户端处理。 */
    private static void handleGrappleSync(GrappleSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientGrappleData.set(msg.phase(), msg.x(), msg.y(), msg.z(), msg.pullSpeed())));
        context.setPacketHandled(true);
    }
}
