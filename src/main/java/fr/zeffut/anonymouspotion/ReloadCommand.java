package fr.zeffut.anonymouspotion;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ReloadCommand implements CommandExecutor {

    private final AnonymousPotionPlugin plugin;

    public ReloadCommand(AnonymousPotionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage : /" + label + " reload", NamedTextColor.RED));
            return true;
        }

        plugin.reloadSettings();
        sender.sendMessage(Component.text("Configuration d'AnonymousPotion rechargée.", NamedTextColor.GREEN));
        plugin.telemetry().commandUsed("reload");
        return true;
    }
}
