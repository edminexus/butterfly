package me.butterfly;

import me.butterfly.util.PlayerFallDamageCalculator;

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

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import net.kyori.adventure.text.Component;


public class ButterflyBrain implements Listener {

    private final ButterflyMain plugin;

    // Elytra Gliding (Fall Damage Enabled)
    private boolean fallDamageEnabled;  

    private float fallStartVelocity;
    private float fallDecayMultiplier;
    private float minFallDistance;
    private double maxFallDamage;

    private final Map<UUID, Float> fallDistance = new HashMap<>();
    private final Map<UUID, Float> pendingFallDamage = new HashMap<>();

    // Action bar refresh (UX)
    private long barRefreshMs;

    private final Map<UUID, BarState> lastBarState = new HashMap<>();
    private final Map<UUID, Long> lastBarUpdate = new HashMap<>();
    private enum BarState {NONE, ACTIVE, FLYING}

    private float flySpeed;
    private int durabilityPerSecond;


    public ButterflyBrain(ButterflyMain plugin) {
        this.plugin = plugin;

        this.barRefreshMs = plugin.getConfig().getLong("actionbar.refresh_ms", 1500L);

        this.durabilityPerSecond = plugin.durabilityPerSecond;

        this.flySpeed = (float) plugin.getConfig().getDouble("flight.speed", 0.05);

        this.fallDamageEnabled = plugin.getConfig().getBoolean("flight.fall_damage.enabled", true);

        this.fallStartVelocity = (float) plugin.getConfig().getDouble("flight.fall_damage.start_velocity", -0.5);
        this.fallDecayMultiplier = (float) plugin.getConfig().getDouble("flight.fall_damage.fall_decay_multiplier", 0.85);
        this.minFallDistance = (float) plugin.getConfig().getDouble("flight.fall_damage.min_fall_distance", 0.1);
        this.maxFallDamage = plugin.getConfig().getDouble("flight.fall_damage.max_damage", 40.0);

        // Main flight tick loop
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        plugin.debug("ButterflyBrain initialized, tick task scheduled");
    }

    // Reload func
    public void reloadFromConfig() {
        this.barRefreshMs = plugin.getConfig().getLong("actionbar.refresh_ms", 1500L);

        this.durabilityPerSecond = plugin.durabilityPerSecond;

        this.flySpeed = (float) plugin.getConfig().getDouble("flight.speed", 0.05f);

        this.fallDamageEnabled = plugin.getConfig().getBoolean("flight.fall_damage.enabled", true);

        this.fallStartVelocity = (float) plugin.getConfig().getDouble("flight.fall_damage.start_velocity", -0.5);
        this.fallDecayMultiplier = (float) plugin.getConfig().getDouble("flight.fall_damage.fall_decay_multiplier", 0.85);
        this.minFallDistance = (float) plugin.getConfig().getDouble("flight.fall_damage.min_fall_distance", 0.1);
        this.maxFallDamage = plugin.getConfig().getDouble("flight.fall_damage.max_damage", 40.0);

        plugin.debug("ButterflyBrain reloaded from config");
    }

    // Action Bar func
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

    // Helpers

    public float getFlySpeed() {
        return flySpeed;
    }

    public void clearFallState(UUID id) {
        fallDistance.remove(id);
    }

    // Was using this one in place of the isOnGround as it is deprecated
    // but this one causes some early tick error with not full blocks that's why it is not used but still keeping it for any future use.
    private boolean hasSolidBlockBelow(Player p) {
        return p.getLocation().clone().add(0, -1, 0).getBlock().getType() != Material.AIR;
    }

    // Cleans Player State from everything that tracks it. And also removes the permissions
    private void cleanupPlayer(UUID id, Player p) {
        plugin.enabled.remove(id);
        fallDistance.remove(id);
        lastBarState.remove(id);
        lastBarUpdate.remove(id);

        if (p != null) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setFlySpeed(ButterflyMain.VANILLA_FLY_SPEED);
            updateBar(p, BarState.NONE, Component.empty());
        }
    }


    // Main Fall Damage Tracker
    // Has weird behaviors with blocks that negates damage like slime_block, water, etc. Can't be able to fix it nor do I want now.
    // Don't do acrobatics while butterfly is active.
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // guards
        if (!fallDamageEnabled) return;
        if (!plugin.enabled.contains(id)) return;

        if (p.isFlying()) {
            fallDistance.remove(id);
            pendingFallDamage.remove(id);
            return;
        }

        // APPLY deferred damage (tick AFTER landing)
        Float pending = pendingFallDamage.remove(id);
        if (pending != null) {

            Block landingBlock = p.getLocation().subtract(0, 1, 0).getBlock();

            if (!PlayerFallDamageCalculator.negatesAllDamage(landingBlock.getType())
                    && pending > 3f) {

                double damage = PlayerFallDamageCalculator.calculate(p, pending, landingBlock);
                damage = Math.min(damage, maxFallDamage);

                if (damage > 0) {
                    p.damage(damage);
                    plugin.debug("Butterfly fall damage: " + p.getName()
                            + " dist=" + pending + " dmg=" + damage);
                }
            }
            return;
        }

        // DETECT landing (NO damage here)
        if (p.isOnGround() && fallDistance.containsKey(id)) {
            pendingFallDamage.put(id, fallDistance.remove(id));
            return;
        }

        // ACCUMULATE / DECAY while airborne
        double velY = p.getVelocity().getY();
        float current = fallDistance.getOrDefault(id, 0f);

        if (velY < fallStartVelocity) {
            current += (float) -velY;
        } else if (current > 0f) {
            current *= fallDecayMultiplier;
        }

        if (current < minFallDistance) {
            fallDistance.remove(id);
        } else {
            fallDistance.put(id, current);
        }
    }

    // Game Mode Change Tracker
    @EventHandler
    public void onModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (plugin.enabled.remove(p.getUniqueId())) {
            cleanupPlayer(id, p);
            p.sendMessage("§9Flight disabled§f: Game mode changed");
        }
    }

    // Elytra unequip Tracker
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
