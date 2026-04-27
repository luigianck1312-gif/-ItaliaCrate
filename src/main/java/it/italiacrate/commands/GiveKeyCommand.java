package it.italiacrate.commands;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.models.CrateRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GiveKeyCommand implements CommandExecutor {

    private final ItaliaCrate plugin;

    public GiveKeyCommand(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("italiacrate.admin")) {
            sender.sendMessage(ChatColor.RED + "Non hai i permessi!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /givekey <player> <comune|epica|leggendaria|mitica> [quantità]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Giocatore non trovato!");
            return true;
        }

        CrateRarity rarity = CrateRarity.fromString(args[1]);
        if (rarity == null) {
            sender.sendMessage(ChatColor.RED + "Rarità non valida! Usa: comune, epica, leggendaria, mitica");
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Quantità non valida!");
                return true;
            }
        }

        target.getInventory().addItem(plugin.getCrateManager().createKey(rarity, amount));
        sender.sendMessage(ChatColor.GREEN + "Dati " + amount + "x Key " + rarity.displayName + " a " + target.getName());
        target.sendMessage(rarity.primaryColor + "✦ Hai ricevuto " + amount + "x Key " + rarity.displayName + "!");
        return true;
    }
}
