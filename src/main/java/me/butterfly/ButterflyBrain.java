package me.butterfly;

import me.butterfly.util.PlayerFallDamageCalculator;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;


public class ButterflyBrain implements Listener {

    private final ButterflyMain plugin;

    private enum BarState {NONE, ACTIVE, FLYING}

    private final Map<UUID, BarState> lastBarState = new HashMap<>();

    // Action bar refresh (UX)
    private long barRefreshMs;
    private final Map<UUID, Long> lastBarUpdate = new HashMap<>();

    private int durabilityPerSecond;

    private boolean fallDamageEnabled;
    private final Map<UUID, Double> fallStartY = new HashMap<>();

    private float flySpeed;

    // New UpdateBar Func.
    private void updateBar(Player p, BarState newState, Component message) {
        UUID id = p.getUniqueId();
        BarState last = lastBarState.get(id);
        long now = System.currentTimeMillis();
        long lastTime = lastBarUpdate.getOrDefault(id, 0L);

        boolean stateChanged = last != newState;
        boolean shouldRefresh = newState != BarState.NONE && now - lastTime >= barRefreshMs;
        
        if (!stateChanged && !shouldRefresh) return;

        if (stateChanged) {
            plugin.debug("Player " + p.getName() + " state " + (last == null ? "NONE" : last) + " -> " + newState);
        }

        p.sendActionBar(message);
        lastBarState.put(id, newState);
        lastBarUpdate.put(id, now);
    }

    public ButterflyBrain(ButterflyMain plugin) {
        this.plugin = plugin;
        this.barRefreshMs = plugin.getConfig().getLong("actionbar.refresh_ms", 1500L);
        this.durabilityPerSecond = plugin.durabilityPerSecond;
        this.fallDamageEnabled = plugin.getConfig().getBoolean("flight.fall_damage", true);
        this.flySpeed = (float) plugin.getConfig().getDouble("flight.speed", 0.05);

        // Main flight tick loop
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        plugin.debug("ButterflyBrain initialized, tick task scheduled");
    }

    public void reloadFromConfig() {
        this.barRefreshMs = plugin.getConfig().getLong("actionbar.refresh_ms", 1500L);
        this.durabilityPerSecond = plugin.durabilityPerSecond;
        this.fallDamageEnabled = plugin.getConfig().getBoolean("flight.fall_damage", true);
        this.flySpeed = (float) plugin.getConfig().getDouble("flight.speed", 0.05f);

        plugin.debug("ButterflyBrain reloaded from config");
    }

    private boolean hasSolidBlockBelow(Player p) {
        return p.getLocation().clone().add(0, -1, 0).getBlock().getType() != Material.AIR;
    }

    public void clearFallState(UUID id) {
        fallStartY.remove(id);
    }

    public float getFlySpeed() {
        return flySpeed;
    }

    private void cleanupPlayer(UUID id, Player p) {
        plugin.enabled.remove(id);
        fallStartY.remove(id);
        lastBarState.remove(id);
        lastBarUpdate.remove(id);

        if (p != null) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setFlySpeed(ButterflyMain.VANILLA_FLY_SPEED);
            updateBar(p, BarState.NONE, Component.empty());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // Config gate
        if (!fallDamageEnabled) return;

        // Only when Butterfly flight is enabled
        if (!plugin.enabled.contains(id)) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        // Ignore while actively flying
        if (p.isFlying()) {
            return;
        }

        // Elytra gliding cancels fall tracking
        if (p.isGliding()) {
            fallStartY.remove(id);
            return;
        }

        double y = p.getLocation().getY();
        double velY = p.getVelocity().getY();

        // Start of fall
        if (velY < -0.08 && !fallStartY.containsKey(id)) {
            fallStartY.put(id, y);
        }

        if (fallStartY.containsKey(id) && hasSolidBlockBelow(p)) {
            double startY = fallStartY.remove(id);
            float fallDistance = (float) (startY - p.getLocation().getY());

            if (fallDistance <= 0) return;

            Block landingBlock = p.getLocation().subtract(0, 1, 0).getBlock();

            double damage = PlayerFallDamageCalculator.calculate(p, fallDistance, landingBlock);

            if (damage > 0) {
                p.damage(damage);
                plugin.debug("Butterfly fall damage: " + p.getName() + " dist=" + fallDistance + " dmg=" + damage);
            }
        }
    }

    // Game Mode Change Checker
    @EventHandler
    public void onModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (plugin.enabled.remove(p.getUniqueId())) {
            cleanupPlayer(id, p);
            p.sendMessage("§9Flight disabled§f: Game mode changed");
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

        boolean oldWasElytra = oldItem != null && oldItem.getType() == Material.ELYTRA;
        boolean newIsElytra = newItem != null && newItem.getType() == Material.ELYTRA;

        if (oldWasElytra && !newIsElytra) {
            UUID id = p.getUniqueId();

            cleanupPlayer(id, p);
            p.sendMessage("§9Flight disabled§f: Elytra removed");
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
                plugin.enabled.remove(id);
                plugin.debug("Cleaning up offline player UUID " + id);
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

            if (chest == null || chest.getType() != Material.ELYTRA) {
                updateBar(p, BarState.NONE, Component.empty());
                continue;
            }

            Damageable elytraMeta = (Damageable) chest.getItemMeta();

            if (elytraMeta == null) {
                cleanupPlayer(id, p);
                continue;
            }
            
            boolean hasUsableElytra = elytraMeta.getDamage() < chest.getType().getMaxDurability();

            // Indicator only when flight is possible
            if (!isEnabled || !hasUsableElytra) {
                updateBar(p, BarState.NONE, Component.empty());
                continue;
            }

            // Durability Drain & Lifespan record when actually flying
            if (isFlying) {
                // Apply reduced fly speed once when flight starts
                if (Math.abs(p.getFlySpeed() - flySpeed) > 0.0001f) {
                    p.setFlySpeed(flySpeed);
                }

                elytraMeta.setDamage(elytraMeta.getDamage() + durabilityPerSecond);
                chest.setItemMeta(elytraMeta);

                // Elytra breaks mid-flight
                if (elytraMeta.getDamage() >= chest.getType().getMaxDurability()) {
                    cleanupPlayer(id, p);
                    p.sendMessage("§9Flight disabled§f: Wings are broken");
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
