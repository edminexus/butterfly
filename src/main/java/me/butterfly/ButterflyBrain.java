package me.butterfly;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import me.butterfly.ButterflyMain;

/**
 * Central brain for all butterfly flight behavior.
 *
 * This class is a mechanical merge of:
 * - FlyListener
 * - ModeListener
 *
 * Logic is intentionally unchanged.
 */
public class ButterflyBrain implements Listener {

    private final ButterflyMain plugin;

    public ButterflyBrain(ButterflyMain plugin) {
        this.plugin = plugin;

        // Main flight tick loop (unchanged)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        plugin.getLogger().info("ButterflyBrain initialized");
    }

    // Game Mode Change Checker
    @EventHandler
    public void onModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();

        if (plugin.enabled.remove(p.getUniqueId())) {
            p.setAllowFlight(false);
            p.setFlying(false);
            p.sendMessage("§9Flight disabled§f: Game mode changed");

            plugin.getLogger().info(
                    "Flight disabled due to gamemode change for " + p.getName()
            );
        }
    }

    // Elytra unequip checker
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (!plugin.enabled.contains(p.getUniqueId())) return;
        if (e.getSlotType() != PlayerArmorChangeEvent.SlotType.CHEST) return;

        ItemStack oldItem = e.getOldItem();
        ItemStack newItem = e.getNewItem();

        boolean oldWasElytra =
                oldItem != null && oldItem.getType() == Material.ELYTRA;
        boolean newIsElytra =
                newItem != null && newItem.getType() == Material.ELYTRA;

        if (oldWasElytra && !newIsElytra) {
            plugin.enabled.remove(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
            p.sendActionBar(Component.empty());
            p.sendMessage("§9Flight disabled§f: Elytra removed");

            plugin.getLogger().info(
                    "Flight disabled due to elytra removal for " + p.getName()
            );
        }
    }

    // Main Tick Loop
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!plugin.interacted.contains(p.getUniqueId())) continue;

            // Survival-only
            if (p.getGameMode() != GameMode.SURVIVAL) {
                p.sendActionBar(Component.empty());
                continue;
            }

            boolean enabled = plugin.enabled.contains(p.getUniqueId());
            boolean flying = p.isFlying();

            ItemStack chest = p.getInventory().getChestplate();
            boolean hasUsableElytra =
                    chest != null &&
                    chest.getType() == Material.ELYTRA &&
                    ((Damageable) chest.getItemMeta()).getDamage()
                            < chest.getType().getMaxDurability();

            // Indicator only when flight is possible
            if (!enabled || !hasUsableElytra) {
                p.sendActionBar(Component.empty());
                continue;
            }

            // Durability Drain & Lifespan record when actually flying (not touching ground)
            if (flying) {
                Damageable d = (Damageable) chest.getItemMeta();
                d.setDamage(d.getDamage() + 2);
                chest.setItemMeta(d);

                // Elytra breaks mid-flight
                if (d.getDamage() >= chest.getType().getMaxDurability()) {
                    plugin.enabled.remove(p.getUniqueId());
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.sendActionBar(Component.empty());
                    p.sendMessage("§9Flight disabled§f: Wings are broken");

                    plugin.getLogger().warning(
                            "Flight disabled due to broken elytra for " + p.getName()
                    );
                    continue;
                }

                plugin.lifespan.increment(p.getUniqueId());
                p.sendActionBar(Component.text("§dButterfly§f: FLYING"));
            } else {
                // Glued but not flying yet
                p.sendActionBar(Component.text("§dButterfly§f: ACTIVE"));
            }
        }
    }
}
