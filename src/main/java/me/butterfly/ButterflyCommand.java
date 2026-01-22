/*1. Push to github before making changes
Changes for 1.2.0:
  2. add /butterfly help
  3. add fall damage while flying and double jumping to drop
  4. reduce the speed of flying 
  5. add config file for almost every value (durability_consume, hunger_depletion, 
  flying_speed, fall_damage(T/F), command_cooldown, etc if missing anything)
  6. add logs
  7. upload to plugin sites
  8. and nothing else i hope*/

package me.butterfly;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Set;

public class ButterflyCommand implements CommandExecutor {

    private final ButterflyPlugin plugin;
    private static final Set<String> SUBS =
            Set.of("glue", "cut", "toggle", "canfly", "lifespan");

    public ButterflyCommand(ButterflyPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean cooldown(Player p) {
        long now = System.currentTimeMillis();
        long last = plugin.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < ButterflyPlugin.COOLDOWN_MS) {
            p.sendMessage("§cCannot perform action§f: Slow down");
            return true;
        }
        plugin.cooldowns.put(p.getUniqueId(), now);
        return false;
    }

    private boolean survival(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL;
    }

    private enum ElytraState { OK, NONE, BROKEN }

    private ElytraState elytra(Player p) {
        ItemStack c = p.getInventory().getChestplate();
        if (c == null || c.getType() != Material.ELYTRA) return ElytraState.NONE;
        Damageable d = (Damageable) c.getItemMeta();
        return d.getDamage() >= c.getType().getMaxDurability()
                ? ElytraState.BROKEN : ElytraState.OK;
    }

    private void enable(Player p) {
        plugin.interacted.add(p.getUniqueId());

        if (!survival(p)) {
            p.sendMessage("§cMode restriction§f: Only works in Survival mode");
            return;
        }

        if (plugin.enabled.contains(p.getUniqueId())) {
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

        plugin.enabled.add(p.getUniqueId());
        p.setAllowFlight(true);
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - 10));
        p.sendMessage("§aEnable flight§f: Wings glued");
    }

    private void disable(Player p, String reason) {
        if (!plugin.enabled.remove(p.getUniqueId())) {
            p.sendMessage("§cCannot disable flight§f: Already disabled");
            return;
        }
        p.setFlying(false);
        p.setAllowFlight(false);
        p.sendMessage("§9Flight disabled§f: " + reason);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {

        if (!(s instanceof Player p)) return true;
        if (cooldown(p)) return true;

        if (args.length == 0) {
            p.sendMessage("§dButterfly§f v" +
                    plugin.getDescription().getVersion() +
                    " by " + plugin.getDescription().getAuthors().get(0));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (!SUBS.contains(sub)) {
            p.sendMessage("§cUnknown subcommand");
            p.sendMessage("§9Use /butterfly");
            return true;
        }

        switch (sub) {
            case "glue" -> enable(p);

            case "cut" -> disable(p, "Wings cut");

            case "toggle" -> {
                if (plugin.enabled.contains(p.getUniqueId()))
                    disable(p, "Wings cut");
                else enable(p);
            }

            case "canfly" -> {
                GameMode gm = p.getGameMode();

                if (gm == GameMode.CREATIVE)
                    p.sendMessage("§dButterfly status§f: Creative mode");
                else if (gm == GameMode.SPECTATOR)
                    p.sendMessage("§dButterfly status§f: Spectator mode");
                else if (gm == GameMode.ADVENTURE)
                    p.sendMessage("§dButterfly status§f: Adventure mode");
                else if (!plugin.enabled.contains(p.getUniqueId()))
                    p.sendMessage("§dButterfly status§f: Disabled (wings not glued)");
                else {
                    ElytraState e = elytra(p);
                    if (e == ElytraState.NONE)
                        p.sendMessage("§dButterfly status§f: Disabled (no elytra)");
                    else if (e == ElytraState.BROKEN)
                        p.sendMessage("§dButterfly status§f: Disabled (wings are broken)");
                    else
                        p.sendMessage("§dButterfly status§f: Enabled");
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
        }
        return true;
    }
}
