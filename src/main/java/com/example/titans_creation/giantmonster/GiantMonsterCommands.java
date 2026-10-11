package com.example.titans_creation.giantmonster;

import java.util.List;

import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.giantmonster.part.GiantMonsterClimbConfig;
import com.example.titans_creation.giantmonster.part.GiantMonsterClimbManager;
import com.example.titans_creation.giantmonster.part.GiantMonsterPartDefinition;
import com.example.titans_creation.giantmonster.part.GiantMonsterPartInstance;
import com.example.titans_creation.giantmonster.part.GiantMonsterSurfaceHit;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 巨型怪物管理指令（服务端）。
 *
 * <ul>
 *   <li>{@code /giantmonster reload} —— 重新读取配置文件，并让世界中已生成的巨型怪物立即套用；</li>
 *   <li>{@code /giantmonster list} —— 列出所有巨型怪物及其当前配置（属性 / 词条 / 生成 / 仇恨）；</li>
 *   <li>{@code /giantmonster spawn <怪物id>} —— 立即执行一次生成尝试并报告结果（调试生成条件）；</li>
 *   <li>{@code /giantmonster state <怪物id> <状态>} —— 切换世界中该怪物的状态模式，
 *       例如 {@code /giantmonster state giant_monster none} 可把怪物切换为「无状态（测试用）」。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID)
public final class GiantMonsterCommands {

    private static final String ROOT = "giantmonster";

    private GiantMonsterCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(ROOT)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            GiantMonsterConfigManager.reload();
                            GiantMonsterSpawnManager.resetRuntimeState();
                            MinecraftServer server = context.getSource().getServer();
                            int applied = GiantMonsterConfigManager.applyToAllLoaded(server);
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("msg.titans_creation.giantmonster.reloaded", applied),
                                    true);
                            return applied;
                        }))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String id : GiantMonsterConfigManager.ids()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    GiantMonsterSpawnManager.SpawnResult result = GiantMonsterSpawnManager
                                            .forceSpawn(context.getSource().getServer(), id);
                                    if (result.success()) {
                                        context.getSource().sendSuccess(
                                                () -> Component.literal(result.message()), true);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(
                                            Component.translatable("msg.titans_creation.giantmonster.spawn_failed",
                                                    result.message()));
                                    return 0;
                                })))
                .then(Commands.literal("state")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String id : GiantMonsterConfigManager.ids()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (GiantMonsterState state : GiantMonsterState.values()) {
                                                builder.suggest(state.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            String stateName = StringArgumentType.getString(context, "state");
                                            GiantMonsterState state = GiantMonsterState.parse(stateName, null);
                                            if (state == null) {
                                                context.getSource().sendFailure(
                                                        Component.translatable(
                                                                "msg.titans_creation.giantmonster.bad_state",
                                                                stateName));
                                                return 0;
                                            }
                                            int count = applyState(context.getSource().getServer(), id, state);
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable(
                                                            "msg.titans_creation.giantmonster.state_applied",
                                                            count, state.id()), true);
                                            return count;
                                        }))))
                .then(Commands.literal("parts")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String id : GiantMonsterConfigManager.ids()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    GiantMonsterDefinition definition = GiantMonsterConfigManager.get(id);
                                    if (definition == null) {
                                        context.getSource().sendFailure(
                                                Component.translatable("msg.titans_creation.giantmonster.none"));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "部位模块（共 " + definition.getParts().size() + " 个）："), false);
                                    for (GiantMonsterPartDefinition part : definition.getParts()) {
                                        context.getSource().sendSuccess(() -> Component.literal(String.format(
                                                "  %s | 类型=%s（碰撞=%s 交互=%s）| 骨骼=%s | 尺寸=%.1f×%.1f×%.1f | 偏移=(%.1f, %.1f, %.1f) | 可攀附=%s",
                                                part.getId(),
                                                part.getType().id(),
                                                part.hasCollision() ? "有" : "无",
                                                part.isInteractive() ? "是" : "否",
                                                part.getBone(),
                                                part.getSizeX(), part.getSizeY(), part.getSizeZ(),
                                                part.getOffsetX(), part.getOffsetY(), part.getOffsetZ(),
                                                part.isClimbable() ? "是" : "否")), false);
                                    }
                                    reportLiveParts(context.getSource(), definition);
                                    return definition.getParts().size();
                                })))
                .then(Commands.literal("surface")
                        .executes(context -> {
                            ServerPlayer player;
                            try {
                                player = context.getSource().getPlayerOrException();
                            } catch (Exception e) {
                                context.getSource().sendFailure(Component.literal("该指令需要由玩家执行"));
                                return 0;
                            }
                            GiantMonster monster = GiantMonsterClimbManager.findMonsterFor(player);
                            if (monster == null) {
                                context.getSource().sendFailure(
                                        Component.translatable("msg.titans_creation.giantmonster.climb.no_monster"));
                                return 0;
                            }
                            GiantMonsterClimbConfig climb = monster.getClimbConfig();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "最大可站立倾角=%.0f° 接触距离=%.2f 攀爬速度=%.2f",
                                    climb.getMaxStandAngle(), climb.getContactDistance(), climb.getClimbSpeed())), false);
                            GiantMonsterSurfaceHit ground = GiantMonsterClimbManager.probeGround(monster, player);
                            context.getSource().sendSuccess(() -> Component.literal(ground == null
                                    ? "  脚下：未探测到部位表面"
                                    : String.format("  脚下：部位=%s 倾角=%.1f° 可站立=%s",
                                            ground.partId(), ground.inclinationDegrees(), ground.walkable() ? "是" : "否")), false);
                            GiantMonsterSurfaceHit contact = GiantMonsterClimbManager.probeContact(monster, player);
                            context.getSource().sendSuccess(() -> Component.literal(contact == null
                                    ? "  接触：未探测到部位表面"
                                    : String.format("  接触：部位=%s 倾角=%.1f° 可站立=%s 可攀附=%s",
                                            contact.partId(), contact.inclinationDegrees(),
                                            contact.walkable() ? "是" : "否", contact.canClimb() ? "是" : "否")), false);
                            return 1;
                        }))
                .then(Commands.literal("list")
                        .executes(context -> {
                            int count = 0;
                            for (String id : GiantMonsterConfigManager.ids()) {
                                GiantMonsterDefinition definition = GiantMonsterConfigManager.get(id);
                                if (definition == null) {
                                    continue;
                                }
                                count++;
                                context.getSource().sendSuccess(() -> Component.literal(String.format(
                                        "%s | 总血量=%.1f 攻击力=%.1f 移速=%.2f 韧性=%.1f 防御力=%.1f 侦测=%.1f 耐力=%.1f | 生成=%s",
                                        definition.getId(),
                                        definition.getMaxHealth(),
                                        definition.getAttackDamage(),
                                        definition.getMovementSpeed(),
                                        definition.getToughness(),
                                        definition.getArmor(),
                                        definition.getFollowRange(),
                                        definition.getStamina(),
                                        definition.getSpawn().isEnabled() ? "开" : "关")), false);
                                context.getSource().sendSuccess(
                                        () -> describe("弱点", definition.getWeakPoints()), false);
                                context.getSource().sendSuccess(
                                        () -> describe("抗性", definition.getResistances()), false);
                                context.getSource().sendSuccess(
                                        () -> describeSpawn(definition.getSpawn()), false);
                                context.getSource().sendSuccess(
                                        () -> describeCombat(definition.getCombat()), false);
                            }
                            if (count == 0) {
                                context.getSource().sendFailure(
                                        Component.translatable("msg.titans_creation.giantmonster.none"));
                            }
                            return count;
                        })));
    }

    /** 把世界中所有该 id 的巨型怪物切换为指定状态，返回受影响的数量。 */
    private static int applyState(MinecraftServer server, String id, GiantMonsterState state) {
        EntityType<? extends Mob> type = GiantMonsterRegistry.getEntityType(id);
        if (type == null) {
            return 0;
        }
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() == type && entity instanceof GiantMonster monster) {
                    monster.setState(state);
                    count++;
                }
            }
        }
        return count;
    }

    /** 把世界中该怪物各部位的实时碰撞箱中心输出到指令反馈里（用于验证碰撞箱随动画移动）。 */
    private static void reportLiveParts(CommandSourceStack source, GiantMonsterDefinition definition) {
        EntityType<? extends Mob> type = GiantMonsterRegistry.getEntityType(definition.getId());
        if (type == null) {
            return;
        }
        MinecraftServer server = source.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() != type || !(entity instanceof GiantMonster monster)) {
                    continue;
                }
                source.sendSuccess(() -> Component.literal("  实时碰撞箱（状态=" + monster.getState().id() + "）："), false);
                for (GiantMonsterPartInstance part : monster.getPartBoxes().all()) {
                    if (!part.hasBox()) {
                        continue;
                    }
                    Vec3 center = part.getBox().getCenter();
                    source.sendSuccess(() -> Component.literal(String.format("    %s -> (%.2f, %.2f, %.2f)",
                            part.getDefinition().getId(), center.x, center.y, center.z)), false);
                }
                return;
            }
        }
    }

    /** 把一组词条配置渲染为一行文本，例如：弱点(2): 斩击 x1.5[head]、火焰 x2.0。 */
    private static Component describe(String label, List<GiantMonsterEntry> entries) {
        MutableComponent text = Component.literal("  " + label + "(" + entries.size() + "): ");
        if (entries.isEmpty()) {
            return text.append(Component.literal("无"));
        }
        for (int i = 0; i < entries.size(); i++) {
            GiantMonsterEntry entry = entries.get(i);
            if (i > 0) {
                text.append(Component.literal("、"));
            }
            text.append(GiantMonsterEntries.displayName(entry.getEntry()));
            text.append(Component.literal(" x" + entry.getDamageMultiplier()
                    + (GiantMonsterEntry.PART_ANY.equals(entry.getPart()) ? "" : "[" + entry.getPart() + "]")));
        }
        return text;
    }

    /** 生成条件摘要。 */
    private static Component describeSpawn(GiantMonsterSpawnConfig spawn) {
        return Component.literal("  生成: 群系" + spawn.getBiomeList().size()
                + " 维度" + spawn.getDimensionList().size()
                + " 时间=" + spawn.getTimeMode().id()
                + " 天气=" + spawn.getWeatherMode().name().toLowerCase(java.util.Locale.ROOT)
                + " 间隔=" + spawn.getCheckInterval()
                + " 全世界上限=" + spawn.getMaxPerWorld()
                + " 每玩家上限=" + spawn.getMaxPerPlayer());
    }

    /** 仇恨机制摘要。 */
    private static Component describeCombat(GiantMonsterCombatConfig combat) {
        return Component.literal("  仇恨: 初始状态=" + combat.getInitialState().id()
                + " 友好程度=" + combat.getDisposition().id()
                + " 创造模式仇恨=" + (combat.isAggroInCreative() ? "开" : "关")
                + " 脱战动作间隔=" + combat.getActionInterval()
                + " 脱战判定=" + combat.getLoseTargetTicks()
                + " 死亡停留=" + combat.getDeathStayTicks());
    }
}
