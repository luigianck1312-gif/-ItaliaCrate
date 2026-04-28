package it.italiacrate.gui;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.models.CrateData;
import it.italiacrate.models.CrateRarity;
import it.italiacrate.models.CrateReward;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CrateGUI {

    private final ItaliaCrate plugin;
    public static final Map<UUID, String> openGUI = new HashMap<>();
    public static final Map<UUID, CrateData> editingCrate = new HashMap<>();
    public static final Map<UUID, Integer> editPage = new HashMap<>();
    public static final Map<UUID, Integer> settingChanceIndex = new HashMap<>();
    public static final Map<UUID, String> pendingInput = new HashMap<>(); // "add_money" o "add_crystals"

    public CrateGUI(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    // Animazione CSGO - scorre gli item e rallenta sul premio
    public void openSpinAnimation(Player player, CrateData crate, CrateReward finalReward) {
        CrateRarity rarity = crate.getRarity();
        String title = rarity.primaryColor + "✦ " + rarity.displayName + " ✦";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Slot della roulette: riga centrale (slot 9-17)
        int[] spinSlots = {9, 10, 11, 12, 13, 14, 15, 16, 17};
        int centerSlot = 13; // slot vincente

        // Frecce indicatrici sopra e sotto il centro
        inv.setItem(4, createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "▼"));
        inv.setItem(22, createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "▲"));

        // Bordi
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, createGlass(rarity));
        }
        for (int i = 18; i < 27; i++) {
            if (i != 22) inv.setItem(i, createGlass(rarity));
        }

        // Lista item per la roulette
        List<ItemStack> spinItems = buildSpinItems(crate, finalReward);

        openGUI.put(player.getUniqueId(), "spin_" + rarity.name());
        player.openInventory(inv);

        // Schedulazione animazione
        // Velocità: inizia veloce, rallenta progressivamente
        // Fasi: veloce (2 tick), medio (4 tick), lento (8 tick), ferma
        int[] delays = {2,2,2,2,2,2,2,2,3,3,3,3,4,4,4,5,5,6,7,8,10,12,15,18,20};
        int totalFrames = delays.length;
        final int[] frame = {0};
        final int[] spinPos = {0};

        scheduleSpinFrame(player, inv, spinSlots, centerSlot, spinItems, finalReward,
                delays, frame, spinPos, totalFrames, crate);
    }

    private void scheduleSpinFrame(Player player, Inventory inv, int[] spinSlots,
            int centerSlot, List<ItemStack> spinItems, CrateReward finalReward,
            int[] delays, int[] frame, int[] spinPos, int totalFrames, CrateData crate) {

        if (frame[0] >= totalFrames) {
            // Animazione finita - mostra premio finale
            finishSpin(player, inv, centerSlot, finalReward, crate);
            return;
        }

        long delay = delays[frame[0]];
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory() == null) return;

            // Aggiorna slot roulette
            for (int i = 0; i < spinSlots.length; i++) {
                int itemIndex = (spinPos[0] + i) % spinItems.size();
                inv.setItem(spinSlots[i], spinItems.get(itemIndex));
            }
            spinPos[0] = (spinPos[0] + 1) % spinItems.size();

            // Suono tick
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f,
                    1.0f + (frame[0] * 0.02f));

            frame[0]++;
            scheduleSpinFrame(player, inv, spinSlots, centerSlot, spinItems,
                    finalReward, delays, frame, spinPos, totalFrames, crate);

        }, delay);
    }

    private void finishSpin(Player player, Inventory inv, int centerSlot,
            CrateReward finalReward, CrateData crate) {

        // Metti il premio nel centro
        inv.setItem(centerSlot, getRewardDisplayItem(finalReward));

        // Suono vittoria
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);

        // Particelle
        Location loc = crate.getLocation().clone().add(0.5, 1, 0.5);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 60, 0.5, 0.5, 0.5, 0.1);

        // Dai il premio dopo 1 secondo
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            giveReward(player, finalReward, crate);
            // Chiudi inventario dopo altri 2 secondi
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.closeInventory();
            }, 40L);
        }, 20L);
    }

    private List<ItemStack> buildSpinItems(CrateData crate, CrateReward finalReward) {
        List<ItemStack> items = new ArrayList<>();
        List<CrateReward> rewards = crate.getRewards();

        // Aggiungi premi casuali per riempire la roulette (almeno 30 item)
        Random rand = new Random();
        for (int i = 0; i < 30; i++) {
            CrateReward r = rewards.get(rand.nextInt(rewards.size()));
            items.add(getRewardDisplayItem(r));
        }
        // Metti il premio finale vicino alla fine
        items.add(items.size() - 3, getRewardDisplayItem(finalReward));
        return items;
    }

    private ItemStack getRewardDisplayItem(CrateReward reward) {
        if (reward.getType() == CrateReward.RewardType.MONEY) {
            return createItemWithLore(Material.GOLD_NUGGET,
                ChatColor.GOLD + "💰 " + formatMoney(reward.getAmount()) + "$",
                Arrays.asList(ChatColor.YELLOW + "Premio Soldi"));
        } else if (reward.getType() == CrateReward.RewardType.CRYSTALS) {
            return createItemWithLore(Material.AMETHYST_SHARD,
                ChatColor.AQUA + "💎 " + (int)reward.getAmount() + " Cristalli",
                Arrays.asList(ChatColor.AQUA + "Premio Cristalli"));
        } else {
            return reward.getItem().clone();
        }
    }

    public void giveReward(Player player, CrateReward reward, CrateData crate) {
        String winMessage;
        if (reward.getType() == CrateReward.RewardType.MONEY) {
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(player, reward.getAmount());
            }
            winMessage = ChatColor.GOLD + "💰 " + formatMoney(reward.getAmount()) + "$";
        } else if (reward.getType() == CrateReward.RewardType.CRYSTALS) {
            plugin.getCrystalManager().addCrystals(player, (int) reward.getAmount());
            winMessage = ChatColor.AQUA + "💎 " + (int) reward.getAmount() + " cristalli";
        } else {
            player.getInventory().addItem(reward.getItem());
            String itemName = reward.getItem().getItemMeta() != null && reward.getItem().getItemMeta().hasDisplayName()
                    ? reward.getItem().getItemMeta().getDisplayName()
                    : reward.getItem().getType().name().toLowerCase().replace("_", " ");
            winMessage = ChatColor.WHITE + itemName + " x" + reward.getItem().getAmount();
        }

        player.sendMessage(crate.getRarity().primaryColor + "✦ Hai aperto una Crate " +
                crate.getRarity().displayName + crate.getRarity().primaryColor + "!");
        player.sendMessage(ChatColor.YELLOW + "Hai vinto: " + winMessage);

        if (crate.getRarity() == CrateRarity.MITICA || crate.getRarity() == CrateRarity.LEGGENDARIA) {
            Bukkit.broadcastMessage(crate.getRarity().primaryColor + "" + ChatColor.BOLD +
                    "✦ " + player.getName() + " ha aperto una Crate " + crate.getRarity().displayName +
                    " e ha vinto: " + winMessage + "!");
        }
    }

    // GUI apertura crate (non animata - usata internamente)
    public void openCrate(Player player, CrateData crate) {
        CrateRarity rarity = crate.getRarity();
        String title = rarity.primaryColor + "✦ Crate " + rarity.displayName + " ✦";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Bordo con vetro colorato
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, createGlass(rarity));
            }
        }

        // Centro - mostra premi possibili
        if (crate.getRewards().isEmpty()) {
            inv.setItem(13, createItem(Material.BARRIER, ChatColor.RED + "Nessun premio configurato!"));
        } else {
            // Mostra 3 premi casuali al centro
            List<CrateReward> rewards = crate.getRewards();
            int[] slots = {10, 13, 16};
            for (int i = 0; i < Math.min(3, rewards.size()); i++) {
                ItemStack display = rewards.get(i % rewards.size()).getItem();
                inv.setItem(slots[i], display);
            }
        }

        inv.setItem(22, createItemWithLore(Material.TRIPWIRE_HOOK,
                rarity.primaryColor + "✦ Apri con chiave",
                Arrays.asList(
                    ChatColor.GRAY + "Hai una " + rarity.primaryColor + "Key " + rarity.displayName + ChatColor.GRAY + " in mano?",
                    ChatColor.YELLOW + "Clicca per aprire!"
                )));

        openGUI.put(player.getUniqueId(), "crate_open_" + rarity.name());
        player.openInventory(inv);
    }

    // GUI admin per modificare i premi
    public void openCrateEdit(Player player, CrateData crate, int page) {
        CrateRarity rarity = crate.getRarity();
        String title = ChatColor.DARK_PURPLE + "Edit: Crate " + rarity.displayName;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        List<CrateReward> rewards = crate.getRewards();
        int start = page * 45;
        int end = Math.min(start + 45, rewards.size());

        // Mostra premi esistenti
        for (int i = start; i < end; i++) {
            CrateReward reward = rewards.get(i);
            ItemStack display = reward.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GOLD + "Probabilità: " + ChatColor.WHITE + reward.getChance() + "%");
                lore.add(ChatColor.RED + "Click destro per rimuovere");
                lore.add(ChatColor.BLACK + "reward_index:" + i);
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(i - start, display);
        }

        // Slot 49 - aggiungi premio (metti item nell'inventario e clicca)
        inv.setItem(49, createItemWithLore(Material.LIME_WOOL,
                ChatColor.GREEN + "Aggiungi Item",
                Arrays.asList(
                    ChatColor.GRAY + "Tieni l'oggetto in mano",
                    ChatColor.GRAY + "e clicca uno slot vuoto"
                )));

        inv.setItem(46, createItemWithLore(Material.GOLD_NUGGET,
                ChatColor.GOLD + "Aggiungi Premio Soldi",
                Arrays.asList(
                    ChatColor.GRAY + "Clicca qui, poi scrivi",
                    ChatColor.GRAY + "in chat: <quantità> <chance%>",
                    ChatColor.GRAY + "Es: 5000000 20",
                    ChatColor.BLACK + "btn:add_money"
                )));

        inv.setItem(47, createItemWithLore(Material.AMETHYST_SHARD,
                ChatColor.AQUA + "Aggiungi Premio Cristalli",
                Arrays.asList(
                    ChatColor.GRAY + "Clicca qui, poi scrivi",
                    ChatColor.GRAY + "in chat: <quantità> <chance%>",
                    ChatColor.GRAY + "Es: 100 15",
                    ChatColor.BLACK + "btn:add_crystals"
                )));

        inv.setItem(45, page > 0 ? createItem(Material.ARROW, ChatColor.WHITE + "« Precedente") :
                createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(53, (end < rewards.size()) ? createItem(Material.ARROW, ChatColor.WHITE + "Successiva »") :
                createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(50, createItem(Material.BARRIER, ChatColor.RED + "Chiudi"));

        editingCrate.put(player.getUniqueId(), crate);
        editPage.put(player.getUniqueId(), page);
        openGUI.put(player.getUniqueId(), "crate_edit");
        // Pulisci eventuali input pendenti da crate precedenti
        pendingInput.remove(player.getUniqueId());
        settingChanceIndex.remove(player.getUniqueId());
        player.openInventory(inv);
    }

    // GUI Keyshop
    public void openKeyshop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "✦ Key Shop ✦");

        int[] slots = {10, 12, 14, 16};
        CrateRarity[] rarities = CrateRarity.values();
        for (int i = 0; i < rarities.length; i++) {
            CrateRarity rarity = rarities[i];
            int crystals = plugin.getCrystalManager().getCrystals(player);
            boolean canAfford = crystals >= rarity.keyCost;

            ItemStack item = createItemWithLore(Material.TRIPWIRE_HOOK,
                    rarity.primaryColor + "" + ChatColor.BOLD + "✦ Key " + rarity.displayName + " ✦",
                    Arrays.asList(
                        rarity.secondaryColor + "Apre una Crate " + rarity.displayName,
                        "",
                        ChatColor.DARK_AQUA + "💎 Costo: " + ChatColor.AQUA + rarity.keyCost + " cristalli",
                        ChatColor.GRAY + "Hai: " + (canAfford ? ChatColor.GREEN : ChatColor.RED) + crystals + " cristalli",
                        "",
                        canAfford ? ChatColor.GREEN + "Clicca per comprare!" : ChatColor.RED + "Cristalli insufficienti!",
                        ChatColor.BLACK + "buy_key:" + rarity.name()
                    ));
            inv.setItem(slots[i], item);
        }

        inv.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "Chiudi"));
        openGUI.put(player.getUniqueId(), "keyshop");
        player.openInventory(inv);
    }

    // GUI Daily reward
    public void openDaily(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "✦ Ricompensa Giornaliera ✦");

        boolean canClaim = plugin.getDailyManager().canClaim(player);
        CrateRarity todayReward = plugin.getDailyManager().getTodayReward(player);
        int streak = plugin.getDailyManager().getStreak(player);
        int dayIndex = plugin.getDailyManager().getNextDayIndex(player);

        // Bordo oro
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, createItem(Material.YELLOW_STAINED_GLASS_PANE, " "));
            }
        }

        // Premio del giorno al centro
        ItemStack reward = plugin.getCrateManager().createKey(todayReward, 1);
        ItemMeta meta = reward.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Streak: " + ChatColor.GOLD + streak + " giorni");
            lore.add(ChatColor.GRAY + "Giorno: " + ChatColor.WHITE + dayIndex + "/21");
            lore.add("");
            if (canClaim) {
                lore.add(ChatColor.GREEN + "✔ Disponibile!");
            } else {
                lore.add(ChatColor.RED + "✘ Già riscattato oggi");
                lore.add(ChatColor.GRAY + "Torna domani!");
            }
            meta.setLore(lore);
            reward.setItemMeta(meta);
        }
        inv.setItem(13, reward);

        // Tasto riscatta
        if (canClaim) {
            inv.setItem(22, createItemWithLore(Material.LIME_WOOL,
                    ChatColor.GREEN + "✦ Riscatta!",
                    Arrays.asList(ChatColor.GRAY + "Clicca per ottenere",
                        todayReward.primaryColor + "Key " + todayReward.displayName)));
        } else {
            inv.setItem(22, createItemWithLore(Material.RED_WOOL,
                    ChatColor.RED + "✘ Già riscattato",
                    Arrays.asList(ChatColor.GRAY + "Torna domani!")));
        }

        openGUI.put(player.getUniqueId(), "daily");
        player.openInventory(inv);
    }

    private ItemStack createGlass(CrateRarity rarity) {
        Material mat = switch (rarity) {
            case COMUNE -> Material.WHITE_STAINED_GLASS_PANE;
            case EPICA -> Material.PURPLE_STAINED_GLASS_PANE;
            case LEGGENDARIA -> Material.ORANGE_STAINED_GLASS_PANE;
            case MITICA -> Material.RED_STAINED_GLASS_PANE;
        };
        return createItem(mat, " ");
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack createItemWithLore(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.0fMld", amount / 1_000_000_000);
        if (amount >= 1_000_000) return String.format("%.0fMln", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000);
        return String.valueOf((long) amount);
    }
}
