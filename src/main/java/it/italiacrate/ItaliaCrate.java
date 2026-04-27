package it.italiacrate;

import it.italiacrate.commands.GiveKeyCommand;
import it.italiacrate.commands.PlaceCommand;
import it.italiacrate.gui.CrateGUI;
import it.italiacrate.listeners.CrateListener;
import it.italiacrate.managers.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ItaliaCrate extends JavaPlugin {

    private CrateManager crateManager;
    private CrateNPCManager npcManager;
    private CrateGUI crateGUI;
    private DailyManager dailyManager;
    private CrystalManager crystalManager;
    private Economy economy;

    @Override
    public void onEnable() {
        getLogger().info("ItaliaCrate avviato!");
        getDataFolder().mkdirs();

        setupEconomy();

        crateManager = new CrateManager(this);
        npcManager = new CrateNPCManager(this);
        crateGUI = new CrateGUI(this);
        dailyManager = new DailyManager(this);
        crystalManager = new CrystalManager(this);

        getCommand("place").setExecutor(new PlaceCommand(this));
        getCommand("givekey").setExecutor(new GiveKeyCommand(this));
        getServer().getPluginManager().registerEvents(new CrateListener(this), this);

        getLogger().info("ItaliaCrate caricato con successo!");
    }

    @Override
    public void onDisable() {
        if (crateManager != null) crateManager.saveCrates();
        if (dailyManager != null) dailyManager.saveData();
        if (crystalManager != null) crystalManager.save();
        if (npcManager != null) npcManager.save();
        getLogger().info("ItaliaCrate disattivato!");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    public CrateManager getCrateManager() { return crateManager; }
    public CrateNPCManager getNpcManager() { return npcManager; }
    public CrateGUI getCrateGUI() { return crateGUI; }
    public DailyManager getDailyManager() { return dailyManager; }
    public CrystalManager getCrystalManager() { return crystalManager; }
    public Economy getEconomy() { return economy; }
}
