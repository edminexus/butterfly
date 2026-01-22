package me.butterfly;
import me.butterfly.ButterflyMain;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

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

    // Action bar state cache (UI-only optimization)
    private enum BarState {
        NONE,
        ACTIVE,
        FLYING
    }

    private final Map<UUID, BarState> lastBarState = new HashMap<>();

    // Action bar refresh (UX)
    private static final long BAR_REFRESH_MS = 1500;
    private final Map<UUID, Long> lastBarUpdate = new HashMap<>();


    // New UpdateBar Func.
    private void updateBar(Player p, BarState newState, Component message) {
        UUID id = p.getUniqueId();
        BarState last = lastBarState.get(id);
        long now = System.currentTimeMillis();
        long lastTime = lastBarUpdate.getOrDefault(id, 0L);

        boolean stateChanged = last != newState;
        boolean shouldRefresh =
                newState != BarState.NONE &&
                now - lastTime >= BAR_REFRESH_MS;

        if (!stateChanged && !shouldRefresh) return;

        p.sendActionBar(message);
        lastBarState.put(id, newState);
        lastBarUpdate.put(id, now);
    }

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
            lastBarState.remove(p.getUniqueId());
            lastBarUpdate.remove(p.getUniqueId());
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
            lastBarState.remove(p.getUniqueId());
            lastBarUpdate.remove(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
            updateBar(p, BarState.NONE, Component.empty());
            p.sendMessage("§9Flight disabled§f: Elytra removed");

            plugin.getLogger().info(
                    "Flight disabled due to elytra removal for " + p.getName()
            );
        }
    }

    // Main Tick Loop
    private void tick() {
        Iterator<UUID> it = plugin.interacted.iterator();

        while (it.hasNext()) {
            UUID id = it.next();
            Player p = Bukkit.getPlayer(id);

            // Player offline → cleanup & skip
            if (p == null) {
                it.remove();
                lastBarState.remove(id);
                lastBarUpdate.remove(id);
                continue;
            }

            // Survival-only
            if (p.getGameMode() != GameMode.SURVIVAL) {
                updateBar(p, BarState.NONE, Component.empty());
                continue;
            }

            boolean isEnabled = plugin.enabled.contains(id);
            boolean isFlying = p.isFlying();

            ItemStack chest = p.getInventory().getChestplate();
            boolean hasUsableElytra =
                    chest != null &&
                    chest.getType() == Material.ELYTRA &&
                    ((Damageable) chest.getItemMeta()).getDamage()
                            < chest.getType().getMaxDurability();

            // Indicator only when flight is possible
            if (!isEnabled || !hasUsableElytra) {
                updateBar(p, BarState.NONE, Component.empty());
                continue;
            }

            // Durability Drain & Lifespan record when actually flying
            if (isFlying) {
                Damageable d = (Damageable) chest.getItemMeta();
                d.setDamage(d.getDamage() + 2);
                chest.setItemMeta(d);

                // Elytra breaks mid-flight
                if (d.getDamage() >= chest.getType().getMaxDurability()) {
                    plugin.enabled.remove(id);
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    updateBar(p, BarState.NONE, Component.empty());
                    p.sendMessage("§9Flight disabled§f: Wings are broken");

                    plugin.getLogger().warning(
                            "Flight disabled due to broken elytra for " + p.getName()
                    );
                    continue;
                }

                plugin.lifespan.increment(id);
                updateBar(p, BarState.FLYING, Component.text("§dButterfly§f: FLYING"));
            } else {
                // Glued but not flying yet
                updateBar(p, BarState.ACTIVE, Component.text("§dButterfly§f: ACTIVE"));
            }
        }
    }
}
