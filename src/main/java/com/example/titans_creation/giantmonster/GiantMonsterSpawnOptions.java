package com.example.titans_creation.giantmonster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 生成条件的候选项扫描。
 *
 * <p>群系与维度都直接从注册表读取，因此<b>会自动包含其他模组新增的内容</b>
 * （例如 Twilight Forest 的群系、其他模组的维度），无需任何硬编码；
 * 配置界面据此把它们展示为可勾选项。
 *
 * <p>群系还可以使用<b>群系标签</b>（以 {@code #} 开头，如 {@code #minecraft:is_forest}），
 * 这样一次配置即可覆盖一整类群系（含后续新增的同类群系）。
 */
public final class GiantMonsterSpawnOptions {

    /** 标签前缀。 */
    public static final String TAG_PREFIX = "#";

    private GiantMonsterSpawnOptions() {
    }

    /** 所有已注册的群系 id（含其他模组）。 */
    public static List<ResourceLocation> biomes() {
        List<ResourceLocation> result = new ArrayList<>(ForgeRegistries.BIOMES.getKeys());
        result.sort(byNamespaceThenPath());
        return result;
    }

    /** 所有已注册的群系标签（含其他模组与数据包）。 */
    public static List<ResourceLocation> biomeTags(RegistryAccess access) {
        if (access == null) {
            return List.of();
        }
        List<ResourceLocation> result = new ArrayList<>();
        access.registryOrThrow(Registries.BIOME).getTagNames()
                .map(TagKey::location)
                .forEach(result::add);
        result.sort(byNamespaceThenPath());
        return result;
    }

    /** 所有已加载的维度 id（含其他模组）。 */
    public static List<ResourceLocation> dimensions(RegistryAccess access) {
        if (access == null) {
            return List.of();
        }
        List<ResourceLocation> result = new ArrayList<>();
        access.registryOrThrow(Registries.LEVEL_STEM).keySet().forEach(result::add);
        result.sort(byNamespaceThenPath());
        return result;
    }

    /** 按命名空间 + 路径排序，便于把同一模组的内容排在一起。 */
    private static Comparator<ResourceLocation> byNamespaceThenPath() {
        return Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath);
    }

    /** 把群系 id 转换为配置中使用的字符串。 */
    public static String toConfigValue(ResourceLocation id) {
        return id.toString();
    }

    /** 把群系标签转换为配置中使用的字符串。 */
    public static String toTagConfigValue(ResourceLocation tagId) {
        return TAG_PREFIX + tagId;
    }

    /** 判断配置值是否为群系标签。 */
    public static boolean isTag(String value) {
        return value != null && value.startsWith(TAG_PREFIX);
    }

    /** 统计某个命名空间下的条目数（界面用于展示"来自哪些模组"）。 */
    public static int countByNamespace(List<ResourceLocation> values, String namespace) {
        int count = 0;
        for (ResourceLocation value : values) {
            if (value.getNamespace().equals(namespace)) {
                count++;
            }
        }
        return count;
    }

    /** 解析配置值中的群系标签 id，非法时返回 null。 */
    public static ResourceLocation parseTagId(String value) {
        if (!isTag(value)) {
            return null;
        }
        return ResourceLocation.tryParse(value.substring(TAG_PREFIX.length()).trim());
    }

    /** 解析配置值中的群系 id，非法时返回 null。 */
    public static ResourceLocation parseId(String value) {
        return value == null ? null : ResourceLocation.tryParse(value.trim());
    }

    /** 便捷方法：判断某个群系是否命中给定标签。 */
    public static boolean isInTag(net.minecraft.core.Holder<Biome> biome, String configValue) {
        ResourceLocation tagId = parseTagId(configValue);
        if (tagId == null) {
            return false;
        }
        return biome.is(TagKey.create(Registries.BIOME, tagId));
    }
}
