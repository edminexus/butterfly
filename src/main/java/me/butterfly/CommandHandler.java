package me.butterfly;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


public class CommandHandler implements CommandExecutor, TabCompleter {

    private final ButterflyMain plugin;

    private static final Set<String> SUBS = Set.of("help", "glue", "cut", "toggle", "canfly", "lifespan", "debug", "reload");

    private static final List<String[]> HELP_ENTRIES = List.of(
        new String[]{"/butterfly help", "Shows this help menu"},
        new String[]{"/butterfly glue", "Glue Butterfly wings (Enable fly)"},
        new String[]{"/butterfly cut", "Cut Butterfly wings (Disable fly)"},
        new String[]{"/butterfly toggle", "Toggle butterfly flight"},
        new String[]{"/butterfly canfly", "You can fly, or may be not. Find out"},
        new String[]{"/butterfly lifespan", "Shows your total flying time"},
        new String[]{"/butterfly lifespan all", "Show all players' flying time (admin)"},
        new String[]{"/butterfly debug", "Toggle debug logging (admin)"},
        new String[]{"/butterfly reload", "Reload configuration (admin)"}
    );

    public CommandHandler(ButterflyMain plugin) {
        this.plugin = plugin;
    }

    // Helper funcs

    // Rate limiter for commands
    private boolean cooldown(Player p) {
        long now = System.currentTimeMillis();
        long last = plugin.cooldowns.getOrDefault(p.getUniqueId(), 0L);

        if (now - last < plugin.commandCooldownMs) {
            p.sendMessage("§cCannot perform action§f: Slow down");
            return true;
        }

        plugin.cooldowns.put(p.getUniqueId(), now);
        return false;
    }

    private boolean isEnabled(Player p) {
        return plugin.enabled.contains(p.getUniqueId());
    }

    private void resetFly(Player p) {
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setFlySpeed(ButterflyMain.VANILLA_FLY_SPEED);
    }

    private enum ElytraState { OK, NONE, BROKEN }

    // Gets Elytra State
    private ElytraState elytra(Player p) {
        ItemStack c = p.getInventory().getChestplate();
        if (c == null || c.getType() != Material.ELYTRA) return ElytraState.NONE;

        Damageable d = (Damageable) c.getItemMeta();
        return d.getDamage() >= c.getType().getMaxDurability()
                ? ElytraState.BROKEN
                : ElytraState.OK;
    }

    private void enable(Player p) {
        plugin.debug("Player " + p.getName() + " entered Butterfly system");

        if (p.getGameMode() != GameMode.SURVIVAL) {
            p.sendMessage("§cMode restriction§f: Only works in Survival mode");
            return;
        }

        if (isEnabled(p)) {
            p.sendMessage("§cCannot enable flight§f: Already enabled");
            return;
        }

        ElytraState e = elytra(p);
        if (e == ElytraState.NONE) {
            p.sendMessage("§cCannot enable flight§f: Elytra not equipped");
            return;
        }
        if (e == ElytraState.BROKEN) {
            p.sendMessage("§cCannot enable flight§f: Wings are broken");
            return;
        }

        plugin.markEnabled(p.getUniqueId());
        p.setAllowFlight(true);

        // PREVENT speed leak: apply butterfly fly speed immediately
        p.setFlySpeed(plugin.brain.getFlySpeed());

        p.setFoodLevel(Math.max(0, p.getFoodLevel() - plugin.hungerCostOnEnable));
        p.sendMessage("§aEnable flight§f: Wings glued");
    }

    private void disable(Player p, String reason) {
        UUID id = p.getUniqueId();
        if (!plugin.enabled.remove(p.getUniqueId())) {
            p.sendMessage("§cCannot disable flight§f: Already disabled");
            return;
        }

        plugin.brain.clearFallState(id);
        resetFly(p);
        p.sendMessage("§9Flight disabled§f: " + reason);
    }

    // Executor

    // Main Command Handler
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {

        if (!(s instanceof Player p)) return true;
        if (cooldown(p)) return true;

        if (args.length == 0) {
            p.sendMessage("§dButterfly§f v" + plugin.getVersion() + " by " + plugin.getAuthor() + " §7(latest: checking...)");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (!SUBS.contains(sub)) {
            p.sendMessage("§cUnknown subcommand");
            p.sendMessage("§eUse /butterfly");
            return true;
        }

        switch (sub) {
            case "help" -> {
                p.sendMessage("§dButterfly Commands§f:");
                for (String[] entry : HELP_ENTRIES) {
                    p.sendMessage("§e" + entry[0] + "§f - " + entry[1]);
                }
            }

            case "reload" -> {
                if (!p.hasPermission("butterfly.admin")) {
                    p.sendMessage("§cCannot reload§f: Insufficient permissions");
                    return true;
                }

                plugin.reloadButterfly();
                p.sendMessage("§aButterfly reloaded§f: Configuration updated");
            }

            case "glue" -> enable(p);

            case "cut" -> disable(p, "Wings cut");

            case "toggle" -> {
                if (isEnabled(p))
                    disable(p, "Wings cut");
                else
                    enable(p);
            }

            case "canfly" -> {
                GameMode gm = p.getGameMode();

                switch (gm) {
                    case CREATIVE -> p.sendMessage("§dButterfly status§f: Creative mode");
                    case SPECTATOR -> p.sendMessage("§dButterfly status§f: Spectator mode");
                    case ADVENTURE -> p.sendMessage("§dButterfly status§f: Adventure mode");
                    default -> {
                        if (!isEnabled(p)) {
                            p.sendMessage("§dButterfly status§f: Disabled (wings not glued)");
                            return true;
                        }

                        ElytraState e = elytra(p);
                        switch (e) {
                            case NONE -> p.sendMessage("§dButterfly status§f: Disabled (no elytra)");
                            case BROKEN -> p.sendMessage("§dButterfly status§f: Disabled (wings are broken)");
                            case OK -> p.sendMessage("§dButterfly status§f: Enabled");
                        }
                    }
                }
            }

            case "lifespan" -> {
                if (args.length == 2 && args[1].equalsIgnoreCase("all")) {
                    if (!p.hasPermission("butterfly.admin")) {
                        p.sendMessage("§cCannot show lifespan§f: Insufficient permissions");
                        return true;
                    }
                    plugin.lifespan.printAll(p);
                } else {
                    plugin.lifespan.printSelf(p);
                }
            }

            case "debug" -> {
                if (!p.hasPermission("butterfly.admin")) {
                    p.sendMessage("§cCannot use debug§f: Insufficient permissions");
                    return true;
                }

                if (args.length != 2) {
                    p.sendMessage("§eUsage: /butterfly debug <on | off | toggle>");
                    return true;
                }

                boolean before = plugin.isDebug();
                String mode = args[1].toLowerCase();

                switch (mode) {
                    case "on" -> {
                        if (before) {
                            p.sendMessage("§cCannot enable debug§f: Debug already enabled");
                            return true;
                        }
                        plugin.setDebug(true);
                        p.sendMessage("§aDebug enabled§f: Logging active");
                    }

                    case "off" -> {
                        if (!before) {
                            p.sendMessage("§cCannot disable debug§f: Debug already disabled");
                            return true;
                        }
                        plugin.setDebug(false);
                        p.sendMessage("§eDebug disabled§f: Logging stopped");
                    }

                    case "toggle" -> {
                        plugin.setDebug(!before);
                        if (before)
                            p.sendMessage("§eDebug disabled§f: Logging stopped");
                        else
                            p.sendMessage("§aDebug enabled§f: Logging active");
                    }

                    default -> {
                        p.sendMessage("§eUsage: /butterfly debug <on | off | toggle>");
                        return true;
                    }
                }
            }
        }

        return true;
    }

    // Tab Completor
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {

        if (!(s instanceof Player)) return List.of();

        if (args.length == 1)
            return SUBS.stream()
                    .filter(x -> x.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());

        if (args.length == 2 && args[0].equalsIgnoreCase("lifespan"))
            return List.of("all");
        
        if (args.length == 2 && args[0].equalsIgnoreCase("debug"))
            return List.of("on", "off", "toggle");

        return List.of();
    }
}
