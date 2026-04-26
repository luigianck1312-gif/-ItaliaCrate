package it.italiacrate.models;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrateData {
    private final CrateRarity rarity;
    private final Location location;
    private final List<CrateReward> rewards = new ArrayList<>();

    public CrateData(CrateRarity rarity, Location location) {
        this.rarity = rarity;
        this.location = location;
    }

    public CrateRarity getRarity() { return rarity; }
    public Location getLocation() { return location; }
    public List<CrateReward> getRewards() { return rewards; }

    public void addReward(CrateReward reward) { rewards.add(reward); }
    public void removeReward(int index) { if (index >= 0 && index < rewards.size()) rewards.remove(index); }
    public void setReward(int index, CrateReward reward) { if (index >= 0 && index < rewards.size()) rewards.set(index, reward); }

    // Estrai un premio casuale in base alle probabilità
    public CrateReward rollReward() {
        if (rewards.isEmpty()) return null;
        Random rand = new Random();

        // Sistema a peso: somma tutte le probabilità e pesca casualmente
        double total = rewards.stream().mapToDouble(CrateReward::getChance).sum();
        double roll = rand.nextDouble() * total;
        double cumulative = 0;
        for (CrateReward reward : rewards) {
            cumulative += reward.getChance();
            if (roll <= cumulative) return reward;
        }
        return rewards.get(rewards.size() - 1);
    }
}
