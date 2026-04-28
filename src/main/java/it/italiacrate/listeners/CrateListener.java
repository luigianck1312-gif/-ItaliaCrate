package it.italiacrate.listeners;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.gui.CrateGUI;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CrateListener implements Listener {

    private final ItaliaCrate plugin;
    private final Map<UUID, String> awaitingInput = new HashMap<>();
    private final Map<UUID, CrateData> adminEditingCrate = new HashMap<>();
    private final Map<UUID, Integer> adminEditPage = new HashMap<>();

    public CrateListener(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVoucherUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        for (String line : meta.getLore()) {
            if (!line.startsWith(ChatColor.BLACK + "voucher:")) continue;
            e.setCancelled(true);
            String[] parts = line.replace(ChatColor.BLACK + "voucher:", "").split(":");
            if (parts.length < 2) return;
            String type = parts[0];
            double amount = Double.parseDouble(parts[1]);

            if (type.equals("money")) {
                if (plugin.getEconomy() != null) {
                    plugin.getEconomy().depositPlayer(player, amount);
                    player.sendMessage(ChatColor.GOLD + "💰 Hai riscattato " + ChatColor.WHITE + formatMoney(amount) + "$ !");
                } else {
                    player.sendMessage(ChatColor.RED + "Vault non disponibile!");
                    return;
                }
            } else if (type.equals("crystals")) {
                plugin.getCrystalManager().addCrystals(player, (int) amount);
                player.sendMessage(ChatColor.AQUA + "💎 Hai riscattato " + ChatColor.WHITE + (int) amount + " cristalli!");
            }

            item.setAmount(item.getAmount() - 1);
            return;
        }
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Player player = e.getPlayer();
        Location loc = block.getLocation();

        if (plugin.getCrateManager().isPendingPlace(player.getUniqueId())) {
            e.setCancelled(true);
            plugin.getCrateManager().convertToCrate(player, block, plugin.getCrateManager().getPendingRarity(player.getUniqueId()));
            return;
        }

        if (plugin.getNpcManager().isKeyshop(loc)) { e.setCancelled(true); plugin.getCrateGUI().openKeyshop(player); return; }
        if (plugin.getNpcManager().isDaily(loc)) { e.setCancelled(true); plugin.getCrateGUI().openDaily(player); return; }
        if (!plugin.getCrateManager().isCrate(loc)) return;

        e.setCancelled(true);
        CrateData crate = plugin.getCrateManager().getCrate(loc);
        if (crate == null) return;

        if (player.hasPermission("italiacrate.admin") && player.isSneaking()) {
            openEditor(player, crate, 0);
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        CrateRarity keyRarity = plugin.getCrateManager().getKeyRarity(inHand);
        if (keyRarity == null) { player.sendMessage(ChatColor.RED + "Ti serve: " + crate.getRarity().primaryColor + "Key " + crate.getRarity().displayName); return; }
        if (keyRarity != crate.getRarity()) { player.sendMessage(ChatColor.RED + "Chiave sbagliata!"); return; }
        if (crate.getRewards().isEmpty()) { player.sendMessage(ChatColor.RED + "Crate senza premi!"); return; }
        inHand.setAmount(inHand.getAmount() - 1);
        CrateReward reward = crate.rollReward();
        if (reward != null) plugin.getCrateGUI().openSpinAnimation(player, crate, reward);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        if (plugin.getCrateManager().isCrate(loc)) {
            if (!e.getPlayer().hasPermission("italiacrate.admin")) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Non puoi rompere una crate!"); }
            else { plugin.getCrateManager().removeCrate(loc); e.getPlayer().sendMessage(ChatColor.GREEN + "Crate rimossa!"); }
        }
        if ((plugin.getNpcManager().isKeyshop(loc) || plugin.getNpcManager().isDaily(loc)) && !e.getPlayer().hasPermission("italiacrate.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String gui = CrateGUI.openGUI.get(player.getUniqueId());
        if (gui == null) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        switch (gui) {
            case "crate_edit" -> handleEditClick(player, clicked, e);
            case "keyshop" -> handleKeyshopClick(player, clicked);
            case "daily" -> handleDailyClick(player, clicked, e.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        CrateGUI.openGUI.remove(uuid);
        if (!awaitingInput.containsKey(uuid)) {
            adminEditingCrate.remove(uuid);
            adminEditPage.remove(uuid);
        }
    }

    private void openEditor(Player player, CrateData crate, int page) {
        awaitingInput.remove(player.getUniqueId());
        adminEditingCrate.put(player.getUniqueId(), crate);
        adminEditPage.put(player.getUniqueId(), page);
        plugin.getCrateGUI().openCrateEdit(player, crate, page);
    }

    private void handleEditClick(Player player, ItemStack clicked, InventoryClickEvent e) {
        UUID uuid = player.getUniqueId();
        CrateData crate = adminEditingCrate.get(uuid);
        if (crate == null) return;
        if (clicked == null || clicked.getType().isAir()) return;

        if (clicked.getType() == Material.BARRIER) {
            adminEditingCrate.remove(uuid); adminEditPage.remove(uuid); awaitingInput.remove(uuid);
            player.closeInventory(); return;
        }
        if (clicked.getType() == Material.ARROW) {
            int page = adminEditPage.getOrDefault(uuid, 0);
            if (e.getSlot() == 45 && page > 0) plugin.getServer().getScheduler().runTask(plugin, () -> openEditor(player, crate, page - 1));
            if (e.getSlot() == 53) plugin.getServer().getScheduler().runTask(plugin, () -> openEditor(player, crate, page + 1));
            return;
        }
        if (clicked.getType() == Material.GOLD_NUGGET) {
            awaitingInput.put(uuid, "add_money");
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Scrivi: <quantità> <probabilità%> — Es: 5000000 20");
            return;
        }
        if (clicked.getType() == Material.AMETHYST_SHARD) {
            awaitingInput.put(uuid, "add_crystals");
            player.closeInventory();
            player.sendMessage(ChatColor.AQUA + "Scrivi: <quantità> <probabilità%> — Es: 100 15");
            return;
        }
        if (clicked.getType() == Material.LIME_WOOL) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.getType().isAir()) { player.sendMessage(ChatColor.RED + "Tieni l'oggetto in mano!"); return; }
            crate.addReward(new CrateReward(inHand.clone(), 10.0));
            plugin.getCrateManager().saveCrates();
            player.sendMessage(ChatColor.GREEN + "Aggiunto " + inHand.getAmount() + "x con 10%! Click sinistro per cambiare probabilità.");
            int page = adminEditPage.getOrDefault(uuid, 0);
            plugin.getServer().getScheduler().runTask(plugin, () -> openEditor(player, crate, page));
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        for (String line : meta.getLore()) {
            if (!line.startsWith(ChatColor.BLACK + "reward_index:")) continue;
            int index = Integer.parseInt(line.replace(ChatColor.BLACK + "reward_index:", ""));
            if (e.isLeftClick()) {
                awaitingInput.put(uuid, "set_chance_" + index);
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Scrivi la probabilità per il premio " + (index + 1) + " (es: 25):");
            } else if (e.isRightClick() && index < crate.getRewards().size()) {
                crate.removeReward(index);
                plugin.getCrateManager().saveCrates();
                player.sendMessage(ChatColor.RED + "Premio rimosso!");
                int page = adminEditPage.getOrDefault(uuid, 0);
                plugin.getServer().getScheduler().runTask(plugin, () -> openEditor(player, crate, page));
            }
            return;
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!awaitingInput.containsKey(uuid)) return;
        e.setCancelled(true);
        String msg = e.getMessage().trim();
        String type = awaitingInput.get(uuid);
        CrateData crate = adminEditingCrate.get(uuid);
        if (crate == null) { awaitingInput.remove(uuid); return; }

        if (type.equals("add_money") || type.equals("add_crystals")) {
            String[] parts = msg.split(" ");
            if (parts.length < 2) { player.sendMessage(ChatColor.RED + "Formato: <quantità> <probabilità%> — Es: 5000000 20"); return; }
            try {
                double amount = Double.parseDouble(parts[0]);
                double chance = Double.parseDouble(parts[1]);
                if (type.equals("add_money")) {
                    crate.addReward(new CrateReward(CrateReward.RewardType.MONEY, amount, chance));
                    player.sendMessage(ChatColor.GREEN + "Premio soldi: " + formatMoney(amount) + "$ con " + chance + "%!");
                } else {
                    crate.addReward(new CrateReward(CrateReward.RewardType.CRYSTALS, amount, chance));
                    player.sendMessage(ChatColor.GREEN + "Premio cristalli: " + (int) amount + " con " + chance + "%!");
                }
                plugin.getCrateManager().saveCrates();
                awaitingInput.remove(uuid);
                int page = adminEditPage.getOrDefault(uuid, 0);
                plugin.getServer().getScheduler().runTask(plugin, () -> openEditor(player, crate, page));
            } catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Valori non validi! Es: 5000000 20"); }

        } else if (type.startsWith("set_chance_")) {
            int index = Integer.parseInt(type.replace("set_chance_", ""));
            try {
                double chance = Double.parseDouble(msg);
                if (index < crate.getRewards().size()) {
                    CrateReward old = crate.getRewards().get(index);
                    crate.getRewards().set(index, old.isItem() ? new CrateReward(old.getItem(), chance) : new CrateReward(old.getType(), old.getAmount(), chance));
                    plugin.getCrateManager().saveCrates();
                    player.sendMessage(ChatColor.GREEN + "Probabilità aggiornata a " + chance + "%!");
                }
                awaitingInput.remove(uuid);
                int page = adminEditPage.getOrDefault(uuid, 0);
                plugin.getServer().getScheduler().runTask(plugin, () -> openEditor(player, crate, page));
            } catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Numero non valido!"); }
        }
    }

    private void handleKeyshopClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        for (String line : meta.getLore()) {
            if (!line.startsWith(ChatColor.BLACK + "buy_key:")) continue;
            CrateRarity rarity = CrateRarity.fromString(line.replace(ChatColor.BLACK + "buy_key:", ""));
            if (rarity == null) return;
            if (!plugin.getCrystalManager().hasCrystals(player, rarity.keyCost)) { player.sendMessage(ChatColor.RED + "Cristalli insufficienti! Ti servono " + rarity.keyCost); return; }
            plugin.getCrystalManager().removeCrystals(player, rarity.keyCost);
            player.getInventory().addItem(plugin.getCrateManager().createKey(rarity, 1));
            player.closeInventory();
            player.sendMessage(rarity.primaryColor + "✦ Hai comprato una Key " + rarity.displayName + "!");
            return;
        }
    }

    private void handleDailyClick(Player player, ItemStack clicked, int slot) {
        if (clicked == null || clicked.getType().isAir()) return;
        if (slot != 22 || clicked.getType() != Material.LIME_WOOL) return;
        if (!plugin.getDailyManager().canClaim(player)) { player.sendMessage(ChatColor.RED + "Già riscattato oggi!"); return; }
        CrateRarity reward = plugin.getDailyManager().claim(player);
        if (reward == null) return;
        player.getInventory().addItem(plugin.getCrateManager().createKey(reward, 1));
        player.closeInventory();
        player.sendMessage(reward.primaryColor + "✦ Hai riscattato: Key " + reward.displayName + "! Streak: " + ChatColor.GOLD + plugin.getDailyManager().getStreak(player) + " giorni");
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;
        if (e.getEntity().getKiller() == null) return;
        Random rand = new Random();
        for (CrateRarity rarity : CrateRarity.values()) {
            if (rand.nextDouble() < rarity.getMobDropChance()) {
                e.getDrops().add(plugin.getCrateManager().createKey(rarity, 1));
                e.getEntity().getKiller().sendMessage(rarity.primaryColor + "✦ Hai trovato una Key " + rarity.displayName + "!");
                break;
            }
        }
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.0fMld", amount / 1_000_000_000);
        if (amount >= 1_000_000) return String.format("%.0fMln", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000);
        return String.valueOf((long) amount);
    }
}
