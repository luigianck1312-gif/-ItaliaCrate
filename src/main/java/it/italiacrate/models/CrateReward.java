package it.italiacrate.models;

import org.bukkit.inventory.ItemStack;

public class CrateReward {
    public enum RewardType { ITEM, MONEY, CRYSTALS }

    private final ItemStack item;
    private final double chance;
    private final RewardType type;
    private final double amount; // per MONEY e CRYSTALS

    // Item reward
    public CrateReward(ItemStack item, double chance) {
        this.item = item;
        this.chance = Math.min(100.0, Math.max(0.01, chance));
        this.type = RewardType.ITEM;
        this.amount = 0;
    }

    // Money or crystal reward
    public CrateReward(RewardType type, double amount, double chance) {
        this.item = null;
        this.chance = Math.min(100.0, Math.max(0.01, chance));
        this.type = type;
        this.amount = amount;
    }

    public ItemStack getItem() { return item != null ? item.clone() : null; }
    public double getChance() { return chance; }
    public RewardType getType() { return type; }
    public double getAmount() { return amount; }
    public boolean isItem() { return type == RewardType.ITEM; }
}
