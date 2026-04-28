package it.italiacrate.managers;

import it.italiacrate.ItaliaCrate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;

public class CrystalManager {

    private final ItaliaCrate plugin;

    public CrystalManager(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    private Object getItaliaShopManager() {
        try {
            Plugin italiaShop = plugin.getServer().getPluginManager().getPlugin("ItaliaShop");
            if (italiaShop == null) return null;
            Method m = italiaShop.getClass().getMethod("getCrystalManager");
            return m.invoke(italiaShop);
        } catch (Exception e) {
            return null;
        }
    }

    public int getCrystals(Player player) {
        try {
            Object mgr = getItaliaShopManager();
            if (mgr != null) {
                Method m = mgr.getClass().getMethod("getCrystals", Player.class);
                return (int) m.invoke(mgr, player);
            }
        } catch (Exception ignored) {}
        File file = getFile();
        if (!file.exists()) return 0;
        return YamlConfiguration.loadConfiguration(file).getInt(player.getUniqueId().toString(), 0);
    }

    public void addCrystals(Player player, int amount) {
        try {
            Object mgr = getItaliaShopManager();
            if (mgr != null) {
                Method m = mgr.getClass().getMethod("addCrystals", Player.class, int.class);
                m.invoke(mgr, player, amount);
                return;
            }
        } catch (Exception ignored) {}
        File file = getFile();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set(player.getUniqueId().toString(), config.getInt(player.getUniqueId().toString(), 0) + amount);
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean removeCrystals(Player player, int amount) {
        try {
            Object mgr = getItaliaShopManager();
            if (mgr != null) {
                Method m = mgr.getClass().getMethod("removeCrystals", Player.class, int.class);
                return (boolean) m.invoke(mgr, player, amount);
            }
        } catch (Exception ignored) {}
        if (!hasCrystals(player, amount)) return false;
        File file = getFile();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set(player.getUniqueId().toString(), config.getInt(player.getUniqueId().toString(), 0) - amount);
        try { config.save(file); return true; } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean hasCrystals(Player player, int amount) {
        return getCrystals(player) >= amount;
    }

    public void setCrystals(Player player, int amount) {
        try {
            Object mgr = getItaliaShopManager();
            if (mgr != null) {
                Method m = mgr.getClass().getMethod("setCrystals", Player.class, int.class);
                m.invoke(mgr, player, amount);
                return;
            }
        } catch (Exception ignored) {}
        File file = getFile();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set(player.getUniqueId().toString(), amount);
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    private File getFile() {
        Plugin italiaShop = plugin.getServer().getPluginManager().getPlugin("ItaliaShop");
        if (italiaShop != null) return new File(italiaShop.getDataFolder(), "crystals.yml");
        return new File(plugin.getDataFolder(), "crystals.yml");
    }

    public void saveData() {}
}
