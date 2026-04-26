package it.italiacrate.managers;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.models.CrateData;
import it.italiacrate.models.CrateRarity;
import it.italiacrate.models.CrateReward;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class CrateManager {

    private final ItaliaCrate plugin;
    private final Map<Location, CrateData> crates = new HashMap<>();
    private final NamespacedKey crateKey;
    private final NamespacedKey crateRarityKey;

    // Stato temporaneo: admin in attesa di toccare la shulkerbox
    private final Map<UUID, CrateRarity> pendingPlace = new HashMap<>();

    public CrateManager(ItaliaCrate plugin) {
        this.plugin = plugin;
        this.crateKey = new NamespacedKey(plugin, "is_crate");
        this.crateRarityKey = new NamespacedKey(plugin, "crate_rarity");
        loadCrates();
    }

    // Admin inizia il processo di piazzamento
    public void startPlaceCrate(Player player, CrateRarity rarity) {
        pendingPlace.put(player.getUniqueId(), rarity);
        player.sendMessage(rarity.primaryColor + "✦ Tocca la Shulker Box che vuoi trasformare in crate " +
                rarity.displayName + "!");
    }

    public boolean isPendingPlace(UUID uuid) {
        return pendingPlace.containsKey(uuid);
    }

    public CrateRarity getPendingRarity(UUID uuid) {
        return pendingPlace.get(uuid);
    }

    public void cancelPending(UUID uuid) {
        pendingPlace.remove(uuid);
    }

    // Converti shulkerbox in crate
    public boolean convertToCrate(Player player, Block block, CrateRarity rarity) {
        if (block.getType() != Material.SHULKER_BOX &&
            !block.getType().name().contains("SHULKER_BOX")) {
            player.sendMessage(ChatColor.RED + "Questo non è una Shulker Box!");
            return false;
        }

        Location loc = block.getLocation();

        // Colore shulkerbox in base alla rarità
        Material shulkerMat = switch (rarity) {
            case COMUNE -> Material.WHITE_SHULKER_BOX;
            case EPICA -> Material.PURPLE_SHULKER_BOX;
            case LEGGENDARIA -> Material.ORANGE_SHULKER_BOX;
            case MITICA -> Material.RED_SHULKER_BOX;
        };
        block.setType(shulkerMat);

        // Aggiungi nome fluttuante sopra con armor stand
        spawnNameTag(loc, rarity);

        // Registra crate
        CrateData data = new CrateData(rarity, loc);
        crates.put(loc, data);
        saveCrates();

        pendingPlace.remove(player.getUniqueId());
        player.sendMessage(rarity.primaryColor + "✦ Crate " + rarity.displayName +
                rarity.primaryColor + " creata con successo!");
        return true;
    }

    private void spawnNameTag(Location loc, CrateRarity rarity) {
        Location nametagLoc = loc.clone().add(0.5, 1.3, 0.5);
        String locStr = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        nametagLoc.getWorld().spawn(nametagLoc, org.bukkit.entity.ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setCustomName(rarity.primaryColor + "" + ChatColor.BOLD + "✦ Crate " + rarity.displayName + " ✦");
            as.setCustomNameVisible(true);
            as.setInvulnerable(true);
            as.setSmall(true);
            as.setMetadata("crate_rarity", new org.bukkit.metadata.FixedMetadataValue(plugin, rarity.name()));
            as.setMetadata("crate_nametag", new org.bukkit.metadata.FixedMetadataValue(plugin, locStr));
        });
    }

    public boolean isCrate(Location loc) {
        return crates.containsKey(loc);
    }

    public CrateData getCrate(Location loc) {
        return crates.get(loc);
    }

    public void removeCrate(Location loc) {
        String locStr = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        loc.getWorld().getEntitiesByClass(org.bukkit.entity.ArmorStand.class).forEach(as -> {
            if (as.hasMetadata("crate_nametag")) {
                String tag = as.getMetadata("crate_nametag").get(0).asString();
                if (locStr.equals(tag)) as.remove();
            }
        });
        crates.remove(loc);
        saveCrates();
    }

    public Map<Location, CrateData> getCrates() { return crates; }

    // Crea item chiave
    public ItemStack createKey(CrateRarity rarity, int amount) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(rarity.primaryColor + "" + ChatColor.BOLD + "✦ Key " + rarity.displayName + " ✦");
            List<String> lore = new ArrayList<>();
            lore.add(rarity.secondaryColor + "Usa questa chiave per aprire");
            lore.add(rarity.secondaryColor + "una Crate " + rarity.displayName);
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Costo: " + rarity.keyCost + " cristalli");
            lore.add(ChatColor.BLACK + "key:" + rarity.name());
            meta.setLore(lore);
            key.setItemMeta(meta);
        }
        return key;
    }

    public CrateRarity getKeyRarity(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;
        for (String line : meta.getLore()) {
            if (line.startsWith(ChatColor.BLACK + "key:")) {
                String rarityName = line.replace(ChatColor.BLACK + "key:", "");
                return CrateRarity.fromString(rarityName);
            }
        }
        return null;
    }

    private void loadCrates() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            try {
                World world = Bukkit.getWorld(config.getString(key + ".world"));
                if (world == null) continue;
                int x = config.getInt(key + ".x");
                int y = config.getInt(key + ".y");
                int z = config.getInt(key + ".z");
                CrateRarity rarity = CrateRarity.valueOf(config.getString(key + ".rarity"));
                Location loc = new Location(world, x, y, z);
                CrateData data = new CrateData(rarity, loc);

                // Carica rewards
                if (config.contains(key + ".rewards")) {
                    for (String rKey : config.getConfigurationSection(key + ".rewards").getKeys(false)) {
                        String rType = config.getString(key + ".rewards." + rKey + ".type", "ITEM");
                        double chance = config.getDouble(key + ".rewards." + rKey + ".chance");
                        if (rType.equals("MONEY")) {
                            double amount = config.getDouble(key + ".rewards." + rKey + ".amount");
                            data.addReward(new CrateReward(CrateReward.RewardType.MONEY, amount, chance));
                        } else if (rType.equals("CRYSTALS")) {
                            double amount = config.getDouble(key + ".rewards." + rKey + ".amount");
                            data.addReward(new CrateReward(CrateReward.RewardType.CRYSTALS, amount, chance));
                        } else {
                            ItemStack item = config.getItemStack(key + ".rewards." + rKey + ".item");
                            if (item != null) data.addReward(new CrateReward(item, chance));
                        }
                    }
                }
                crates.put(loc, data);
            } catch (Exception e) {
                plugin.getLogger().warning("Errore caricamento crate: " + key);
            }
        }
    }

    public void saveCrates() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        YamlConfiguration config = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, CrateData> entry : crates.entrySet()) {
            String key = "crate_" + i++;
            Location loc = entry.getKey();
            CrateData data = entry.getValue();
            config.set(key + ".world", loc.getWorld().getName());
            config.set(key + ".x", loc.getBlockX());
            config.set(key + ".y", loc.getBlockY());
            config.set(key + ".z", loc.getBlockZ());
            config.set(key + ".rarity", data.getRarity().name());
            for (int j = 0; j < data.getRewards().size(); j++) {
                CrateReward r = data.getRewards().get(j);
                config.set(key + ".rewards." + j + ".type", r.getType().name());
                config.set(key + ".rewards." + j + ".chance", r.getChance());
                if (r.isItem()) {
                    config.set(key + ".rewards." + j + ".item", r.getItem());
                } else {
                    config.set(key + ".rewards." + j + ".amount", r.getAmount());
                }
            }
        }
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    // Crea item display per premio soldi
    public ItemStack createMoneyRewardItem(double amount, double chance) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "💰 Premio Soldi");
            meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Quantità: " + ChatColor.WHITE + formatMoney(amount) + "$",
                ChatColor.GOLD + "Probabilità: " + ChatColor.WHITE + chance + "%",
                ChatColor.BLACK + "reward_type:MONEY",
                ChatColor.BLACK + "reward_amount:" + amount
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // Crea item display per premio cristalli
    public ItemStack createCrystalRewardItem(int amount, double chance) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "💎 Premio Cristalli");
            meta.setLore(Arrays.asList(
                ChatColor.AQUA + "Quantità: " + ChatColor.WHITE + amount + " cristalli",
                ChatColor.GOLD + "Probabilità: " + ChatColor.WHITE + chance + "%",
                ChatColor.BLACK + "reward_type:CRYSTALS",
                ChatColor.BLACK + "reward_amount:" + amount
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.0fMld", amount / 1_000_000_000);
        if (amount >= 1_000_000) return String.format("%.0fMln", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000);
        return String.valueOf((long) amount);
    }

    public NamespacedKey getCrateKey() { return crateKey; }
}
