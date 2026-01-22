package me.butterfly;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class FlyListener implements Listener {

    private final ButterflyPlugin plugin;

    public FlyListener(ButterflyPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* =====================================================
       INSTANT MANUAL ELYTRA UNEQUIP (GROUND + MID-AIR)
       ===================================================== */
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

        // Elytra removed by player → IMMEDIATE shutdown
        if (oldWasElytra && !newIsElytra) {
            plugin.enabled.remove(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
            p.sendActionBar(Component.empty());
            p.sendMessage("§9Flight disabled§f: Elytra removed");
        }
    }

    /* =====================================================
       MAIN TICK LOOP (OLD, STABLE METHOD)
       ===================================================== */
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {

            // Never interacted → no processing
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

            // Indicator only when flight is actually possible
            if (!enabled || !hasUsableElytra) {
                p.sendActionBar(Component.empty());
                continue;
            }

            // DURABILITY + LIFESPAN ONLY WHILE ACTUALLY FLYING
            if (enabled && flying) {
                Damageable d = (Damageable) chest.getItemMeta();
                d.setDamage(d.getDamage() + 2);
                chest.setItemMeta(d);

                // Elytra broke
                if (d.getDamage() >= chest.getType().getMaxDurability()) {
                    plugin.enabled.remove(p.getUniqueId());
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.sendActionBar(Component.empty());
                    p.sendMessage("§9Flight disabled§f: Wings are broken");
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
