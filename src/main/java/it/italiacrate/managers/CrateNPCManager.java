package it.italiacrate.managers;

import it.italiacrate.ItaliaCrate;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.UUID;

public class CrateNPCManager {

    private final ItaliaCrate plugin;
    private UUID keyshopUUID = null;
    private UUID dailyUUID = null;
    private Location keyshopLoc = null;
    private Location dailyLoc = null;

    public CrateNPCManager(ItaliaCrate plugin) {
        this.plugin = plugin;
        load();
    }

    public void spawnKeyshop(Location loc) {
        removeKeyshop();
        keyshopLoc = loc.clone();
        ArmorStand npc = spawnNPC(loc, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "✦ Key Shop ✦");
        keyshopUUID = npc.getUniqueId();
        save();
    }

    public void spawnDaily(Location loc) {
        removeDaily();
        dailyLoc = loc.clone();
        ArmorStand npc = spawnNPC(loc, ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Ricompensa Giornaliera ✦");
        dailyUUID = npc.getUniqueId();
        save();
    }

    private ArmorStand spawnNPC(Location loc, String name) {
        // Usa shulkerbox come display
        Location blockLoc = loc.getBlock().getLocation();

        // Piazza una shulkerbox colorata
        blockLoc.getBlock().setType(Material.YELLOW_SHULKER_BOX);

        // Armor stand per il nome
        Location nameLoc = blockLoc.clone().add(0.5, 1.3, 0.5);
        return nameLoc.getWorld().spawn(nameLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setCustomName(name);
            as.setCustomNameVisible(true);
            as.setInvulnerable(true);
            as.setSmall(true);
        });
    }

    public void removeKeyshop() {
        if (keyshopUUID != null) removeEntity(keyshopUUID);
        if (keyshopLoc != null) keyshopLoc.getBlock().setType(Material.AIR);
        keyshopUUID = null; keyshopLoc = null;
        save();
    }

    public void removeDaily() {
        if (dailyUUID != null) removeEntity(dailyUUID);
        if (dailyLoc != null) dailyLoc.getBlock().setType(Material.AIR);
        dailyUUID = null; dailyLoc = null;
        save();
    }

    private void removeEntity(UUID uuid) {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getUniqueId().equals(uuid)) { e.remove(); return; }
            }
        }
    }

    public boolean isKeyshop(Location loc) {
        if (keyshopLoc == null) return false;
        return loc.getBlockX() == keyshopLoc.getBlockX() &&
               loc.getBlockY() == keyshopLoc.getBlockY() &&
               loc.getBlockZ() == keyshopLoc.getBlockZ();
    }

    public boolean isDaily(Location loc) {
        if (dailyLoc == null) return false;
        return loc.getBlockX() == dailyLoc.getBlockX() &&
               loc.getBlockY() == dailyLoc.getBlockY() &&
               loc.getBlockZ() == dailyLoc.getBlockZ();
    }

    public UUID getKeyshopUUID() { return keyshopUUID; }
    public UUID getDailyUUID() { return dailyUUID; }

    private void load() {
        File file = new File(plugin.getDataFolder(), "npcs.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try {
            if (config.contains("keyshop")) {
                World w = Bukkit.getWorld(config.getString("keyshop.world"));
                if (w != null) {
                    keyshopLoc = new Location(w, config.getInt("keyshop.x"),
                        config.getInt("keyshop.y"), config.getInt("keyshop.z"));
                    String uuidStr = config.getString("keyshop.uuid");
                    if (uuidStr != null) keyshopUUID = UUID.fromString(uuidStr);
                }
            }
            if (config.contains("daily")) {
                World w = Bukkit.getWorld(config.getString("daily.world"));
                if (w != null) {
                    dailyLoc = new Location(w, config.getInt("daily.x"),
                        config.getInt("daily.y"), config.getInt("daily.z"));
                    String uuidStr = config.getString("daily.uuid");
                    if (uuidStr != null) dailyUUID = UUID.fromString(uuidStr);
                }
            }
        } catch (Exception ignored) {}
    }

    public void save() {
        File file = new File(plugin.getDataFolder(), "npcs.yml");
        YamlConfiguration config = new YamlConfiguration();
        if (keyshopLoc != null) {
            config.set("keyshop.world", keyshopLoc.getWorld().getName());
            config.set("keyshop.x", keyshopLoc.getBlockX());
            config.set("keyshop.y", keyshopLoc.getBlockY());
            config.set("keyshop.z", keyshopLoc.getBlockZ());
            if (keyshopUUID != null) config.set("keyshop.uuid", keyshopUUID.toString());
        }
        if (dailyLoc != null) {
            config.set("daily.world", dailyLoc.getWorld().getName());
            config.set("daily.x", dailyLoc.getBlockX());
            config.set("daily.y", dailyLoc.getBlockY());
            config.set("daily.z", dailyLoc.getBlockZ());
            if (dailyUUID != null) config.set("daily.uuid", dailyUUID.toString());
        }
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }
}
