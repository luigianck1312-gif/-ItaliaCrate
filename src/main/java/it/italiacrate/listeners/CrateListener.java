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
        // Rimuovi chiave
        key.setAmount(key.getAmount() - 1);

        // Estrai premio
        CrateReward reward = crate.rollReward();
        if (reward == null) {
            player.sendMessage(ChatColor.RED + "Errore nel estrarre il premio!");
            return;
        }

        // Effetti
        Location loc = crate.getLocation().clone().add(0.5, 1, 0.5);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 50, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);

        // Dai il premio
        player.getInventory().addItem(reward.getItem());

        // Messaggio
        String itemName = reward.getItem().getItemMeta() != null && reward.getItem().getItemMeta().hasDisplayName()
                ? reward.getItem().getItemMeta().getDisplayName()
                : reward.getItem().getType().name().toLowerCase().replace("_", " ");

        player.sendMessage(crate.getRarity().primaryColor + "✦ Hai aperto una Crate " +
                crate.getRarity().displayName + crate.getRarity().primaryColor + "!");
        player.sendMessage(ChatColor.YELLOW + "Hai vinto: " + ChatColor.WHITE + itemName +
                ChatColor.YELLOW + " x" + reward.getItem().getAmount());

        // Broadcast se rarità alta
        if (crate.getRarity() == CrateRarity.MITICA || crate.getRarity() == CrateRarity.LEGGENDARIA) {
            Bukkit.broadcastMessage(crate.getRarity().primaryColor + "" + ChatColor.BOLD +
                    "✦ " + player.getName() + " ha aperto una Crate " + crate.getRarity().displayName +
                    " e ha vinto: " + ChatColor.WHITE + itemName + "!");
        }
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

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();

        switch (gui) {
            case "keyshop" -> handleKeyshopClick(player, clicked);
            case "daily" -> handleDailyClick(player, clicked, e.getSlot());
            case "crate_edit" -> handleEditClick(player, clicked, e);
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
        if (clicked == null || clicked.getType().isAir()) {
            // Se clicca slot vuoto con item in mano → aggiungi premio
            if (e.getCursor() != null && !e.getCursor().getType().isAir() && e.getSlot() < 45) {
                CrateData crate = CrateGUI.editingCrate.get(player.getUniqueId());
                if (crate == null) return;

                ItemStack toAdd = e.getCursor().clone();
                toAdd.setAmount(1);

                // Chiedi probabilità in chat
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Scrivi in chat la probabilità (es: 50.0) per: " +
                        toAdd.getType().name());
                settingChance.put(player.getUniqueId(), crate.getRewards().size());
                // Salva item temporaneamente
                player.getInventory().addItem(toAdd);
                // Aggiungi con chance default 10%
                crate.addReward(new CrateReward(toAdd, 10.0));
                plugin.getCrateManager().saveCrates();
                player.sendMessage(ChatColor.GREEN + "Premio aggiunto con probabilità 10%!");
                player.sendMessage(ChatColor.GRAY + "Rientra nell'edit per modificare la probabilità.");
            }
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Click destro su premio → rimuovi
        if (e.isRightClick()) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (line.startsWith(ChatColor.BLACK + "reward_index:")) {
                        int index = Integer.parseInt(line.replace(ChatColor.BLACK + "reward_index:", ""));
                        CrateData crate = CrateGUI.editingCrate.get(player.getUniqueId());
                        if (crate != null && index < crate.getRewards().size()) {
                            crate.removeReward(index);
                            plugin.getCrateManager().saveCrates();
                            player.sendMessage(ChatColor.RED + "Premio rimosso!");
                            int page = CrateGUI.editPage.getOrDefault(player.getUniqueId(), 0);
                            plugin.getCrateGUI().openCrateEdit(player, crate, page);
                        }
                    }
                }
            }
        }

        if (clicked.getType() == Material.ARROW) {
            CrateData crate = CrateGUI.editingCrate.get(player.getUniqueId());
            if (crate == null) return;
            int page = CrateGUI.editPage.getOrDefault(player.getUniqueId(), 0);
            if (e.getSlot() == 45) plugin.getCrateGUI().openCrateEdit(player, crate, page - 1);
            if (e.getSlot() == 53) plugin.getCrateGUI().openCrateEdit(player, crate, page + 1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        CrateGUI.openGUI.remove(player.getUniqueId());
        CrateGUI.editingCrate.remove(player.getUniqueId());
        CrateGUI.editPage.remove(player.getUniqueId());
    }
}
