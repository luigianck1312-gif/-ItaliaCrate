package it.italiacrate.commands;

import it.italiacrate.ItaliaCrate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class VoucherCommand implements CommandExecutor {

    private final ItaliaCrate plugin;

    public VoucherCommand(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("italiacrate.admin")) {
            sender.sendMessage(ChatColor.RED + "Non hai i permessi!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /voucher <money|crystals> <quantità> [player]");
            return true;
        }

        String type = args[0].toLowerCase();
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Quantità non valida!");
            return true;
        }

        Player target = args.length >= 3 ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Giocatore non trovato!");
            return true;
        }

        ItemStack voucher;
        if (type.equals("money")) {
            voucher = createMoneyVoucher(amount);
        } else if (type.equals("crystals")) {
            voucher = createCrystalVoucher((int) amount);
        } else {
            sender.sendMessage(ChatColor.RED + "Tipo non valido! Usa: money o crystals");
            return true;
        }

        target.getInventory().addItem(voucher);
        sender.sendMessage(ChatColor.GREEN + "Voucher creato e dato a " + target.getName() + "!");
        target.sendMessage(ChatColor.YELLOW + "Hai ricevuto un voucher! Tasto destro per riscattarlo.");
        return true;
    }

    public static ItemStack createMoneyVoucher(double amount) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "💰 Voucher Soldi");
            meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Valore: " + ChatColor.WHITE + formatMoney(amount) + "$",
                "",
                ChatColor.GREEN + "Tasto destro per riscattare!",
                ChatColor.BLACK + "voucher:money:" + (long) amount
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createCrystalVoucher(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "💎 Voucher Cristalli");
            meta.setLore(Arrays.asList(
                ChatColor.AQUA + "Quantità: " + ChatColor.WHITE + amount + " cristalli",
                "",
                ChatColor.GREEN + "Tasto destro per riscattare!",
                ChatColor.BLACK + "voucher:crystals:" + amount
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.0fMld", amount / 1_000_000_000);
        if (amount >= 1_000_000) return String.format("%.0fMln", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000);
        return String.valueOf((long) amount);
    }
}
