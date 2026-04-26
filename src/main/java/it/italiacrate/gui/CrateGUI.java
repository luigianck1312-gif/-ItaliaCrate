package it.italiacrate.gui;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.models.CrateData;
import it.italiacrate.models.CrateRarity;
import it.italiacrate.models.CrateReward;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

    public CrateGUI(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    // GUI apertura crate - animazione
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
                    ChatColor.GRAY + "Trascina un item qui",
                    ChatColor.GRAY + "per aggiungerlo come premio"
                )));

        inv.setItem(46, createItemWithLore(Material.GOLD_NUGGET,
                ChatColor.GOLD + "Aggiungi Premio Soldi",
                Arrays.asList(
                    ChatColor.GRAY + "Scrivi in chat:",
                    ChatColor.GRAY + "add_money <quantità> <chance%>",
                    ChatColor.GRAY + "Es: add_money 5000000 20"
                )));

        inv.setItem(47, createItemWithLore(Material.AMETHYST_SHARD,
                ChatColor.AQUA + "Aggiungi Premio Cristalli",
                Arrays.asList(
                    ChatColor.GRAY + "Scrivi in chat:",
                    ChatColor.GRAY + "add_crystals <quantità> <chance%>",
                    ChatColor.GRAY + "Es: add_crystals 100 15"
                )));

        inv.setItem(45, page > 0 ? createItem(Material.ARROW, ChatColor.WHITE + "« Precedente") :
                createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(53, (end < rewards.size()) ? createItem(Material.ARROW, ChatColor.WHITE + "Successiva »") :
                createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(50, createItem(Material.BARRIER, ChatColor.RED + "Chiudi"));

        editingCrate.put(player.getUniqueId(), crate);
        editPage.put(player.getUniqueId(), page);
        openGUI.put(player.getUniqueId(), "crate_edit");
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
}
