package it.italiacrate.models;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum CrateRarity {
    COMUNE("Comune", ChatColor.WHITE, ChatColor.GRAY, Material.WHITE_DYE, 10),
    EPICA("Epica", ChatColor.DARK_PURPLE, ChatColor.LIGHT_PURPLE, Material.PURPLE_DYE, 50),
    LEGGENDARIA("Leggendaria", ChatColor.GOLD, ChatColor.YELLOW, Material.ORANGE_DYE, 150),
    MITICA("Mitica", ChatColor.RED, ChatColor.DARK_RED, Material.RED_DYE, 400);

    public final String displayName;
    public final ChatColor primaryColor;
    public final ChatColor secondaryColor;
    public final Material dyeColor;
    public final int keyCost; // in cristalli

    CrateRarity(String displayName, ChatColor primaryColor, ChatColor secondaryColor,
                Material dyeColor, int keyCost) {
        this.displayName = displayName;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.dyeColor = dyeColor;
        this.keyCost = keyCost;
    }

    // Probabilità drop chiave dai mob
    public double getMobDropChance() {
        return switch (this) {
            case COMUNE -> 0.08;
            case EPICA -> 0.03;
            case LEGGENDARIA -> 0.008;
            case MITICA -> 0.001;
        };
    }

    public static CrateRarity fromString(String s) {
        for (CrateRarity r : values()) {
            if (r.name().equalsIgnoreCase(s) || r.displayName.equalsIgnoreCase(s)) return r;
        }
        return null;
    }
}
