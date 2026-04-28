package it.italiacrate.managers;

import it.italiacrate.ItaliaCrate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class CrystalManager {

    private final ItaliaCrate plugin;

    public CrystalManager(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    public int getCrystals(Player player) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(getCrystalsFile());
        return config.getInt(player.getUniqueId().toString(), 0);
    }

    public void addCrystals(Player player, int amount) {
        File file = getCrystalsFile();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        int current = config.getInt(player.getUniqueId().toString(), 0);
        config.set(player.getUniqueId().toString(), current + amount);
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean removeCrystals(Player player, int amount) {
        if (!hasCrystals(player, amount)) return false;
        File file = getCrystalsFile();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        int current = config.getInt(player.getUniqueId().toString(), 0);
        config.set(player.getUniqueId().toString(), current - amount);
        try { config.save(file); return true; } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean hasCrystals(Player player, int amount) {
        return getCrystals(player) >= amount;
    }

    public void setCrystals(Player player, int amount) {
        File file = getCrystalsFile();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set(player.getUniqueId().toString(), amount);
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    private File getCrystalsFile() {
        org.bukkit.plugin.Plugin italiaShop = plugin.getServer().getPluginManager().getPlugin("ItaliaShop");
        if (italiaShop != null) return new File(italiaShop.getDataFolder(), "crystals.yml");
        return new File(plugin.getDataFolder(), "crystals.yml");
    }
}
