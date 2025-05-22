package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
// Unused Bukkit imports for specific types (Chunk, Material, etc.) are removed for this command's current logic.
// They would be needed if specific subcommands here performed direct world manipulation.
import org.bukkit.Bukkit; // Needed for Bukkit.getWorlds() in TabCompleter
import org.bukkit.World;  // Needed for Bukkit.getWorlds() in TabCompleter
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
// Player import is not directly used in this class if subcommands are handled by other classes or checks are internal to them
// import org.bukkit.entity.Player;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList; // Needed for TabCompleter world list
import java.util.Arrays;
import java.util.Collections;
// HashMap and Map imports are not strictly needed in this specific version of LagXpertCommand
// if InspectCommand handles its own placeholder maps.
// import java.util.HashMap;
// import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Handles the main /lagxpert command and its subcommands.
 * Provides functionalities like help, reloading configuration, inspecting chunks,
 * and informing users about other relevant commands like /chunkstatus.
 */
public class LagXpertCommand implements CommandExecutor, TabCompleter {

    // A list of root subcommands for easy management and tab-completion.
    private static final List<String> ROOT_SUBCOMMANDS = Arrays.asList("help", "reload", "inspect", "chunkload");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // If no arguments are provided, or "help" is explicitly requested, show the help message.
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(); // Process subcommand in lowercase for case-insensitivity.

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "inspect":
                // Check permission for the inspect subcommand
                if (!sender.hasPermission("lagxpert.admin")) { // Or a more specific "lagxpert.inspect" permission
                    sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
                    return true;
                }
                // Prepare arguments for InspectCommand.execute by removing "inspect" itself.
                // args[0] is "inspect", so we pass args starting from index 1.
                String[] inspectArgs = new String[0]; // Default to empty if only "/lagxpert inspect" is typed
                if (args.length > 1) {
                    inspectArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, inspectArgs, 0, args.length - 1);
                }
                // Call the static execute method from InspectCommand class
                return InspectCommand.execute(sender, inspectArgs);
            case "chunkload":
                // Inform user that /chunkstatus is the dedicated command for chunk information.
                // Assumes "chunkload.use-chunkstatus-command" key exists in messages.yml.
                sender.sendMessage(MessageManager.getPrefixedMessage("chunkload.use-chunkstatus-command"));
                return true;
            default:
                // Handle any unknown subcommands.
                sender.sendMessage(MessageManager.getPrefixedMessage("general.invalid-command"));
                return true;
        }
    }

    /**
     * Sends a formatted help message to the CommandSender.
     * The message includes available commands based on the sender's permissions
     * and the current server time.
     *
     * @param sender The CommandSender to receive the help message.
     */
    private void sendHelp(CommandSender sender) {
        String headerFooter = MessageManager.color("&8&m------------------------------------------");
        String serverTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        sender.sendMessage(headerFooter);
        // Dynamically display plugin name and version from plugin.yml.
        if (LagXpert.getInstance() != null && LagXpert.getInstance().getDescription() != null) {
            sender.sendMessage(MessageManager.color("&b&lLagXpert &7v" + LagXpert.getInstance().getDescription().getVersion() + " &8- &fHelp Menu"));
        } else {
            sender.sendMessage(MessageManager.color("&b&lLagXpert &8- &fHelp Menu")); // Fallback if instance or description is null
        }
        sender.sendMessage(MessageManager.color("&7Server Time: &e" + serverTime));
        sender.sendMessage(""); // Empty line for better readability.

        // Display command help based on sender's permissions.
        // Assumes corresponding "help.command_name" keys exist in messages.yml.
        if (sender.hasPermission("lagxpert.use")) { // General permission for basic commands
            // help.inspect is now conditional on lagxpert.admin below
            sender.sendMessage(MessageManager.getPrefixedMessage("help.chunkstatus")); // Reminds about /chunkstatus
        }
        if (sender.hasPermission("lagxpert.admin")) { // Assuming inspect is an admin command
            sender.sendMessage(MessageManager.getPrefixedMessage("help.inspect"));
        }
        if (sender.hasPermission("lagxpert.abyss")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.abyss"));
        }
        if (sender.hasPermission("lagxpert.clearitems")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.clearitems"));
        }
        if (sender.hasPermission("lagxpert.admin")) { // Admin-specific commands
            sender.sendMessage(MessageManager.getPrefixedMessage("help.reload"));
        }
        sender.sendMessage(headerFooter);
    }

    /**
     * Handles the /lagxpert reload subcommand.
     * Reloads all plugin configurations if the sender has the appropriate permission.
     *
     * @param sender The CommandSender who issued the command.
     * @return true if the command was handled.
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("lagxpert.admin")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        // Reload all plugin configurations.
        ConfigManager.loadAll();    // This reloads all YAMLs and re-initializes MessageManager.
        AbyssManager.loadConfig();  // AbyssManager fetches its reloaded config values from ConfigManager.

        sender.sendMessage(MessageManager.getPrefixedMessage("general.config-reloaded")); // Confirmation message.
        if (LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info("LagXpert configurations reloaded by " + sender.getName() + ".");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Provide tab completion for the root subcommands, filtered by what the user is typing.
            String currentArg = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String sub : ROOT_SUBCOMMANDS) {
                if (sub.toLowerCase().startsWith(currentArg)) {
                    // Permission-based tab completion
                    if (sub.equalsIgnoreCase("reload") || sub.equalsIgnoreCase("inspect")) {
                        if (sender.hasPermission("lagxpert.admin")) {
                            completions.add(sub);
                        }
                    } else { // For help, chunkload (which is just a message)
                        completions.add(sub);
                    }
                }
            }
            return completions;
        }

        // Tab completion for /lagxpert inspect <x> <z> [world]
        if (args[0].equalsIgnoreCase("inspect") && sender.hasPermission("lagxpert.admin")) {
            if (args.length == 2) { // Suggesting <x> (placeholder text)
                return Collections.singletonList("<x>");
            } else if (args.length == 3) { // Suggesting <z> (placeholder text)
                return Collections.singletonList("<z>");
            } else if (args.length == 4) { // Suggesting actual [world_name]
                List<String> worldNames = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    worldNames.add(world.getName());
                }
                String currentWorldArg = args[3].toLowerCase();
                return worldNames.stream()
                        .filter(name -> name.toLowerCase().startsWith(currentWorldArg))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList(); // No further tab completions by default.
    }
}