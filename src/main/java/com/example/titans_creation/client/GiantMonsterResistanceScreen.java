package com.example.titans_creation.client;

import java.util.List;

import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.GiantMonsterEntry;

/**
 * 三级菜单：抗性配置。
 *
 * <p>抗性词条命中时<b>减少</b>伤害（倍率会被钳制为 ≤ 1）。
 */
public class GiantMonsterResistanceScreen extends GiantMonsterEntryListScreen {

    public GiantMonsterResistanceScreen(String monsterId) {
        super(monsterId, "titans_creation.giantmonster.menu.resistances",
                GiantMonsterDefinition.KEY_RESISTANCES);
    }

    @Override
    protected List<GiantMonsterEntry> readEntries(GiantMonsterDefinition definition) {
        return definition.getResistances();
    }

    @Override
    protected void writeEntries(GiantMonsterDefinition definition, List<GiantMonsterEntry> entries) {
        definition.setResistances(entries);
    }
}
