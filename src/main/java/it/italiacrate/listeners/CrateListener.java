package it.italiacrate.listeners;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.gui.CrateGUI;
import it.italiacrate.managers.CrateManager;
import it.italiacrate.models.CrateData;
import it.italiacrate.models.CrateRarity;
import it.italiacrate.models.CrateReward;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CrateListener implements Listener {

    private final ItaliaCrate plugin;
    // Stato: admin in attesa di impostare probabilità
    private final Map<UUID, Integer> settingChance = new HashMap<>();

    public CrateListener(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    // Click su blocco - gestisce crate, keyshop, daily
    @EventHandler
    public void onBlockClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Player player = e.getPlayer();
        Location loc = block.getLocation();

        // Admin in attesa di toccare shulkerbox per convertirla in crate
        if (plugin.getCrateManager().isPendingPlace(player.getUniqueId())) {
            e.setCancelled(true);
            CrateRarity rarity = plugin.getCrateManager().getPendingRarity(player.getUniqueId());
            plugin.getCrateManager().convertToCrate(player, block, rarity);
            return;
        }

        // Keyshop
        if (plugin.getNpcManager().isKeyshop(loc)) {
            e.setCancelled(true);
            plugin.getCrateGUI().openKeyshop(player);
            return;
        }

        // Daily
        if (plugin.getNpcManager().isDaily(loc)) {
            e.setCancelled(true);
            plugin.getCrateGUI().openDaily(player);
            return;
        }

        // Crate
        if (!plugin.getCrateManager().isCrate(loc)) return;
        e.setCancelled(true);

        CrateData crate = plugin.getCrateManager().getCrate(loc);
        if (crate == null) return;

        // Admin con shift click → edit mode
        if (player.hasPermission("italiacrate.admin") && player.isSneaking()) {
            plugin.getCrateGUI().openCrateEdit(player, crate, 0);
            return;
        }

        // Check chiave in mano
        ItemStack inHand = player.getInventory().getItemInMainHand();
        CrateRarity keyRarity = plugin.getCrateManager().getKeyRarity(inHand);

        if (keyRarity == null) {
            player.sendMessage(ChatColor.RED + "Devi avere una chiave in mano per aprire questa crate!");
            player.sendMessage(ChatColor.GRAY + "Ti serve: " + crate.getRarity().primaryColor +
                    "Key " + crate.getRarity().displayName);
            return;
        }

        if (keyRarity != crate.getRarity()) {
            player.sendMessage(ChatColor.RED + "Questa chiave non corrisponde alla crate!");
            player.sendMessage(ChatColor.GRAY + "Ti serve: " + crate.getRarity().primaryColor +
                    "Key " + crate.getRarity().displayName);
            return;
        }

        if (crate.getRewards().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Questa crate non ha premi configurati!");
            return;
        }

        // Apri la crate!
        openCrate(player, crate, inHand);
    }

    private void openCrate(Player player, CrateData crate, ItemStack key) {
        key.setAmount(key.getAmount() - 1);

        CrateReward reward = crate.rollReward();
        if (reward == null) {
            player.sendMessage(ChatColor.RED + "Errore nel estrarre il premio!");
            return;
        }

        // Avvia animazione CSGO
        plugin.getCrateGUI().openSpinAnimation(player, crate, reward);
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.0fMld", amount / 1_000_000_000);
        if (amount >= 1_000_000) return String.format("%.0fMln", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000);
        return String.valueOf((long) amount);
    }

    // Drop chiavi dai mob
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;
        if (e.getEntity().getKiller() == null) return;

        Random rand = new Random();
        for (CrateRarity rarity : CrateRarity.values()) {
            if (rand.nextDouble() < rarity.getMobDropChance()) {
                e.getDrops().add(plugin.getCrateManager().createKey(rarity, 1));
                Player killer = e.getEntity().getKiller();
                killer.sendMessage(rarity.primaryColor + "✦ Hai trovato una Key " + rarity.displayName + "!");
                break; // Drop solo una chiave
            }
        }
    }

    // Impedisci rottura crate
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (plugin.getCrateManager().isCrate(e.getBlock().getLocation())) {
            if (!e.getPlayer().hasPermission("italiacrate.admin")) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(ChatColor.RED + "Non puoi rompere una crate!");
            } else {
                // Admin può rimuovere
                plugin.getCrateManager().removeCrate(e.getBlock().getLocation());
                e.getPlayer().sendMessage(ChatColor.GREEN + "Crate rimossa!");
            }
        }
        // Impedisci rottura keyshop/daily
        Location loc = e.getBlock().getLocation();
        if (plugin.getNpcManager().isKeyshop(loc) || plugin.getNpcManager().isDaily(loc)) {
            if (!e.getPlayer().hasPermission("italiacrate.admin")) {
                e.setCancelled(true);
            }
        }
    }

    // Click nelle GUI
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String gui = CrateGUI.openGUI.get(uuid);
        if (gui == null) return;

        ItemStack clicked = e.getCurrentItem();

        if (gui.equals("crate_edit")) {
            // Nella GUI edit non cancelliamo sempre — gestiamo caso per caso
            handleEditClick(player, clicked, e);
            return;
        }

        // Per tutte le altre GUI cancelliamo sempre
        e.setCancelled(true);

        switch (gui) {
            case "keyshop" -> handleKeyshopClick(player, clicked);
            case "daily" -> handleDailyClick(player, clicked, e.getSlot());
        }
    }

    private void handleKeyshopClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        for (String line : meta.getLore()) {
            if (line.startsWith(ChatColor.BLACK + "buy_key:")) {
                String rarityName = line.replace(ChatColor.BLACK + "buy_key:", "");
                CrateRarity rarity = CrateRarity.fromString(rarityName);
                if (rarity == null) return;

                if (!plugin.getCrystalManager().hasCrystals(player, rarity.keyCost)) {
                    player.sendMessage(ChatColor.RED + "Non hai abbastanza cristalli!");
                    return;
                }
                plugin.getCrystalManager().removeCrystals(player, rarity.keyCost);
                player.getInventory().addItem(plugin.getCrateManager().createKey(rarity, 1));
                player.closeInventory();
                player.sendMessage(rarity.primaryColor + "✦ Hai comprato una Key " + rarity.displayName + "!");
                player.sendMessage(ChatColor.AQUA + "💎 Cristalli rimasti: " +
                        plugin.getCrystalManager().getCrystals(player));
            }
        }
    }

    private void handleDailyClick(Player player, ItemStack clicked, int slot) {
        if (clicked == null || clicked.getType().isAir()) return;
        if (slot == 22 && clicked.getType() == Material.LIME_WOOL) {
            if (!plugin.getDailyManager().canClaim(player)) {
                player.sendMessage(ChatColor.RED + "Hai già riscattato la ricompensa oggi!");
                return;
            }
            CrateRarity reward = plugin.getDailyManager().claim(player);
            if (reward == null) return;
            player.getInventory().addItem(plugin.getCrateManager().createKey(reward, 1));
            player.closeInventory();
            player.sendMessage(reward.primaryColor + "✦ Hai riscattato: Key " + reward.displayName + "!");
            player.sendMessage(ChatColor.GRAY + "Streak: " + ChatColor.GOLD +
                    plugin.getDailyManager().getStreak(player) + " giorni");
        }
    }

    private void handleEditClick(Player player, ItemStack clicked, InventoryClickEvent e) {
        CrateData crate = CrateGUI.editingCrate.get(player.getUniqueId());
        if (crate == null) return;

        e.setCancelled(true);

        // Click su BARRIER → chiudi
        if (clicked != null && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Click su bottone add_money o add_crystals
        if (clicked != null && clicked.getItemMeta() != null && clicked.getItemMeta().hasLore()) {
            for (String line : clicked.getItemMeta().getLore()) {
                if (line.equals(ChatColor.BLACK + "btn:add_money")) {
                    CrateGUI.pendingInput.put(player.getUniqueId(), "add_money");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.closeInventory();
                        player.sendMessage(ChatColor.GOLD + "Scrivi in chat: " + ChatColor.WHITE + "<quantità> <probabilità%>");
                        player.sendMessage(ChatColor.GRAY + "Es: " + ChatColor.WHITE + "5000000 20");
                    });
                    return;
                }
                if (line.equals(ChatColor.BLACK + "btn:add_crystals")) {
                    CrateGUI.pendingInput.put(player.getUniqueId(), "add_crystals");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.closeInventory();
                        player.sendMessage(ChatColor.AQUA + "Scrivi in chat: " + ChatColor.WHITE + "<quantità> <probabilità%>");
                        player.sendMessage(ChatColor.GRAY + "Es: " + ChatColor.WHITE + "100 15");
                    });
                    return;
                }
            }
        }
        if (clicked != null && clicked.getType() == Material.ARROW) {
            int page = CrateGUI.editPage.getOrDefault(player.getUniqueId(), 0);
            if (e.getSlot() == 45) plugin.getCrateGUI().openCrateEdit(player, crate, page - 1);
            if (e.getSlot() == 53) plugin.getCrateGUI().openCrateEdit(player, crate, page + 1);
            return;
        }

        // Click su slot vuoto (< 45) con item in mano → aggiungi premio
        if (e.getSlot() < 45 && (clicked == null || clicked.getType().isAir())) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Tieni in mano l'oggetto che vuoi aggiungere!");
                return;
            }
            // Usa la quantità reale dell'item in mano!
            ItemStack toAdd = inHand.clone();
            crate.addReward(new CrateReward(toAdd, 10.0));
            plugin.getCrateManager().saveCrates();
            player.sendMessage(ChatColor.GREEN + "Premio aggiunto (" + toAdd.getAmount() + "x) con probabilità 10%!");
            player.sendMessage(ChatColor.GRAY + "Click sinistro sul premio per cambiare la probabilità.");
            int page = CrateGUI.editPage.getOrDefault(player.getUniqueId(), 0);
            plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getCrateGUI().openCrateEdit(player, crate, page));
            return;
        }

        // Click sinistro su premio esistente → imposta probabilità
        if (e.isLeftClick() && clicked != null && !clicked.getType().isAir()) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (line.startsWith(ChatColor.BLACK + "reward_index:")) {
                        int index = Integer.parseInt(line.replace(ChatColor.BLACK + "reward_index:", ""));
                        // Metti PRIMA in mappa, POI chiudi
                        CrateGUI.settingChanceIndex.put(player.getUniqueId(), index);
                        // NON chiamare closeInventory qui - usiamo scheduler
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.closeInventory();
                            player.sendMessage(ChatColor.YELLOW + "Scrivi la probabilità per il premio " +
                                    ChatColor.WHITE + (index + 1) + ChatColor.YELLOW + " (es: 25.5):");
                        });
                        return;
                    }
                }
            }
        }

        // Click destro su premio esistente → rimuovi
        if (e.isRightClick() && clicked != null && !clicked.getType().isAir()) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (line.startsWith(ChatColor.BLACK + "reward_index:")) {
                        int index = Integer.parseInt(line.replace(ChatColor.BLACK + "reward_index:", ""));
                        if (index < crate.getRewards().size()) {
                            crate.removeReward(index);
                            plugin.getCrateManager().saveCrates();
                            player.sendMessage(ChatColor.RED + "Premio rimosso!");
                            int page = CrateGUI.editPage.getOrDefault(player.getUniqueId(), 0);
                            plugin.getServer().getScheduler().runTask(plugin,
                                () -> plugin.getCrateGUI().openCrateEdit(player, crate, page));
                        }
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean hasPending = CrateGUI.editingCrate.containsKey(uuid) || 
                             CrateGUI.settingChanceIndex.containsKey(uuid) ||
                             CrateGUI.pendingInput.containsKey(uuid);
        if (!hasPending) return;

        String msg = e.getMessage().trim();
        e.setCancelled(true);

        // Impostare probabilità di un premio esistente
        if (CrateGUI.settingChanceIndex.containsKey(uuid)) {
            CrateData crate = CrateGUI.editingCrate.get(uuid);
            if (crate == null) { CrateGUI.settingChanceIndex.remove(uuid); return; }
            try {
                double chance = Double.parseDouble(msg);
                int index = CrateGUI.settingChanceIndex.get(uuid);
                if (index < crate.getRewards().size()) {
                    CrateReward old = crate.getRewards().get(index);
                    CrateReward updated = old.isItem() ?
                        new CrateReward(old.getItem(), chance) :
                        new CrateReward(old.getType(), old.getAmount(), chance);
                    crate.getRewards().set(index, updated);
                    plugin.getCrateManager().saveCrates();
                    player.sendMessage(ChatColor.GREEN + "Probabilità aggiornata a " + chance + "%!");
                }
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Numero non valido!");
            }
            CrateGUI.settingChanceIndex.remove(uuid);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                CrateData crate2 = CrateGUI.editingCrate.get(uuid);
                if (crate2 != null) {
                    int page = CrateGUI.editPage.getOrDefault(uuid, 0);
                    plugin.getCrateGUI().openCrateEdit(player, crate2, page);
                }
            });
            return;
        }

        // add_money o add_crystals da bottone
        if (CrateGUI.pendingInput.containsKey(uuid)) {
            String type = CrateGUI.pendingInput.get(uuid);
            CrateData crate = CrateGUI.editingCrate.get(uuid);
            if (crate == null) { CrateGUI.pendingInput.remove(uuid); return; }

            String[] parts = msg.split(" ");
            if (parts.length < 2) {
                player.sendMessage(ChatColor.RED + "Formato: <quantità> <probabilità%> — Es: 5000000 20");
                return;
            }
            try {
                double amount = Double.parseDouble(parts[0]);
                double chance = Double.parseDouble(parts[1]);
                if (type.equals("add_money")) {
                    crate.addReward(new CrateReward(CrateReward.RewardType.MONEY, amount, chance));
                    plugin.getCrateManager().saveCrates();
                    player.sendMessage(ChatColor.GREEN + "Premio soldi aggiunto: " + formatMoney(amount) + "$ con " + chance + "%!");
                } else {
                    crate.addReward(new CrateReward(CrateReward.RewardType.CRYSTALS, amount, chance));
                    plugin.getCrateManager().saveCrates();
                    player.sendMessage(ChatColor.GREEN + "Premio cristalli aggiunto: " + (int)amount + " 💎 con " + chance + "%!");
                }
                CrateGUI.pendingInput.remove(uuid);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    int page = CrateGUI.editPage.getOrDefault(uuid, 0);
                    plugin.getCrateGUI().openCrateEdit(player, crate, page);
                });
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Valori non validi! Es: 5000000 20");
            }
            return;
        }

        // Vecchio sistema add_money/add_crystals scritto manualmente
        if (!msg.startsWith("add_money") && !msg.startsWith("add_crystals")) return;
        CrateData crate = CrateGUI.editingCrate.get(uuid);
        if (crate == null) return;

        String[] parts = msg.split(" ");
        if (parts.length < 3) {
            player.sendMessage(ChatColor.RED + "Uso: add_money <quantità> <chance%>");
            return;
        }
        try {
            double amount = Double.parseDouble(parts[1]);
            double chance = Double.parseDouble(parts[2]);
            if (msg.startsWith("add_money")) {
                crate.addReward(new CrateReward(CrateReward.RewardType.MONEY, amount, chance));
                plugin.getCrateManager().saveCrates();
                player.sendMessage(ChatColor.GREEN + "Premio soldi aggiunto: " + formatMoney(amount) + "$ con " + chance + "%!");
            } else {
                crate.addReward(new CrateReward(CrateReward.RewardType.CRYSTALS, amount, chance));
                plugin.getCrateManager().saveCrates();
                player.sendMessage(ChatColor.GREEN + "Premio cristalli aggiunto: " + (int)amount + " 💎 con " + chance + "%!");
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int page = CrateGUI.editPage.getOrDefault(uuid, 0);
                plugin.getCrateGUI().openCrateEdit(player, crate, page);
            });
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Valori non validi!");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        CrateGUI.openGUI.remove(uuid);
        // Non rimuovere editingCrate se stiamo aspettando input chat
        if (!CrateGUI.settingChanceIndex.containsKey(uuid) && !CrateGUI.pendingInput.containsKey(uuid)) {
            CrateGUI.editingCrate.remove(uuid);
            CrateGUI.editPage.remove(uuid);
        }
    }
}
