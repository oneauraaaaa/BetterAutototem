package com.oneaura.betterautototem.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "betterautototem")
public class ModConfig implements ConfigData {

    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip(count = 2)
    @ConfigEntry.Gui.CollapsibleObject
    public boolean modEnabled = true;

    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip(count = 2)
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100) // 0-100 ticks slider
    public int restockDelayTicks = 0; // 0 = next tick, 20 = 1 second

    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip(count = 3)
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100) // 0-100 ticks slider
    public int inventoryDelayTicks = 0; // 0 = instant swap, >0 = open inventory for this long
}

