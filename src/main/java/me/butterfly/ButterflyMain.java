package me.butterfly;

import org.bukkit.plugin.java.JavaPlugin;
import me.butterfly.SaveData;
import java.util.*;

public class ButterflyMain extends JavaPlugin {

    public final Set<UUID> enabled = new HashSet<>();
    public final Set<UUID> interacted = new HashSet<>();
    public final Map<UUID, Long> cooldowns = new HashMap<>();

    public SaveData lifespan;
    private boolean debug;

    public long commandCooldownMs;
    public long lifespanSavePeriodTicks;
    public int durabilityPerTick;
    public int hungerCostOnEnable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);

        commandCooldownMs = getConfig().getLong("cooldown.command_ms", 1000L);
        long lifespanSeconds = getConfig().getLong("lifespan.save_period_seconds", 300L);
        lifespanSavePeriodTicks = lifespanSeconds * 20L;
        durabilityPerTick = getConfig().getInt("flight.durability_per_sec", 2);
        hungerCostOnEnable = getConfig().getInt("flight.hunger_cost_on_enable", 10);
        
        lifespan = new SaveData(this);
        lifespan.load();

        CommandHandler cmd = new CommandHandler(this);
        getCommand("butterfly").setExecutor(cmd);
        getCommand("butterfly").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new ButterflyBrain(this), this);

        // Periodic lifespan save (default 5 minutes, can be configured from config.yml)
        getServer().getScheduler().runTaskTimer(this, () -> lifespan.saveIfDirty(), lifespanSavePeriodTicks, lifespanSavePeriodTicks);
    }

    @Override
    public void onDisable() {
        lifespan.saveIfDirty();
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean value) {
        this.debug = value;
        getConfig().set("debug", value);
        saveConfig();
    }

    public void debug(String msg) {
        if (!debug) return;
        getLogger().info("[DEBUG] " + msg);
    }
}
