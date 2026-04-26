package it.italiacrate.commands;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.models.CrateRarity;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlaceCommand implements CommandExecutor {

    private final ItaliaCrate plugin;

    public PlaceCommand(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo i giocatori!");
            return true;
        }
        if (!player.hasPermission("italiacrate.admin")) {
            player.sendMessage(ChatColor.RED + "Non hai i permessi!");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /place <crate <rarità>|keyshop|daily>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "crate" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Specifica la rarità: comune, epica, leggendaria, mitica");
                    return true;
                }
                CrateRarity rarity = CrateRarity.fromString(args[1]);
                if (rarity == null) {
                    player.sendMessage(ChatColor.RED + "Rarità non valida! Usa: comune, epica, leggendaria, mitica");
                    return true;
                }
                plugin.getCrateManager().startPlaceCrate(player, rarity);
            }
            case "keyshop" -> {
                plugin.getNpcManager().spawnKeyshop(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✦ Key Shop piazzato!");
            }
            case "daily" -> {
                plugin.getNpcManager().spawnDaily(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✦ Ricompensa Giornaliera piazzata!");
            }
            default -> player.sendMessage(ChatColor.RED + "Uso: /place <crate <rarità>|keyshop|daily>");
        }
        return true;
    }
}
