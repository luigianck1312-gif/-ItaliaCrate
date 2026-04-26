package it.italiacrate.commands;

import it.italiacrate.ItaliaCrate;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

public class RemoveTagsCommand implements CommandExecutor {

    private final ItaliaCrate plugin;

    public RemoveTagsCommand(ItaliaCrate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("italiacrate.admin")) {
            sender.sendMessage(ChatColor.RED + "Non hai i permessi!");
            return true;
        }

        int count = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand as) {
                    if (as.hasMetadata("crate_nametag") || as.hasMetadata("crate_rarity")) {
                        as.setInvulnerable(false);
                        as.remove();
                        count++;
                    }
                }
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Rimossi " + count + " armor stand del plugin!");
        return true;
    }
}
