package it.italiacrate.models;

import org.bukkit.inventory.ItemStack;

public class CrateReward {
    private final ItemStack item;
    private final double chance; // 0.0 - 100.0

    public CrateReward(ItemStack item, double chance) {
        this.item = item;
        this.chance = Math.min(100.0, Math.max(0.01, chance));
    }

    public ItemStack getItem() { return item.clone(); }
    public double getChance() { return chance; }
}
