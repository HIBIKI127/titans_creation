package com.example.titans_creation.client;

import java.util.List;

import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.GiantMonsterEntry;

/**
 * 三级菜单：弱点配置。
 *
 * <p>弱点词条命中时<b>提高</b>伤害（倍率会被钳制为 ≥ 1）。
 */
public class GiantMonsterWeakPointScreen extends GiantMonsterEntryListScreen {

    public GiantMonsterWeakPointScreen(String monsterId) {
        super(monsterId, "titans_creation.giantmonster.menu.weak_points",
                GiantMonsterDefinition.KEY_WEAK_POINTS);
    }

    @Override
    protected List<GiantMonsterEntry> readEntries(GiantMonsterDefinition definition) {
        return definition.getWeakPoints();
    }

    @Override
    protected void writeEntries(GiantMonsterDefinition definition, List<GiantMonsterEntry> entries) {
        definition.setWeakPoints(entries);
    }
}
