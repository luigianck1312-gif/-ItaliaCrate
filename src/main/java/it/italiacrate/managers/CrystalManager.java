package it.italiacrate.managers;

import it.italiacrate.ItaliaCrate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrystalManager {

    private final ItaliaCrate plugin;
    private final Map<UUID, Integer> crystals = new HashMap<>();

    public CrystalManager(ItaliaCrate plugin) {
        this.plugin = plugin;
        load();
    }

    public int getCrystals(Player player) {
        return crystals.getOrDefault(player.getUniqueId(), 0);
    }

    public void addCrystals(Player player, int amount) {
        crystals.put(player.getUniqueId(), getCrystals(player) + amount);
        save();
    }

    public boolean removeCrystals(Player player, int amount) {
        if (!hasCrystals(player, amount)) return false;
        crystals.put(player.getUniqueId(), getCrystals(player) - amount);
        save();
        return true;
    }

    public boolean hasCrystals(Player player, int amount) {
        return getCrystals(player) >= amount;
    }

    public void setCrystals(Player player, int amount) {
        crystals.put(player.getUniqueId(), amount);
        save();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "crystals.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try { crystals.put(UUID.fromString(key), config.getInt(key)); } catch (Exception ignored) {}
        }
    }

    public void save() {
        File file = new File(plugin.getDataFolder(), "crystals.yml");
        YamlConfiguration config = new YamlConfiguration();
        crystals.forEach((uuid, amount) -> config.set(uuid.toString(), amount));
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }
}
