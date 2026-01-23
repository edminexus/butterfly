package me.butterfly;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class ButterflyMain extends JavaPlugin {

    public final Set<UUID> enabled = Collections.synchronizedSet(new HashSet<>());
    public final Set<UUID> interacted = Collections.synchronizedSet(new HashSet<>());
    public final Map<UUID, Long> cooldowns = Collections.synchronizedMap(new HashMap<>());

    public static final float VANILLA_FLY_SPEED = 0.1f;

    public SaveData lifespan;

    private boolean debug;
    private String version;
    private String author;

    public long commandCooldownMs;
    public long lifespanSavePeriodTicks;
    public int durabilityPerSecond;
    public int hungerCostOnEnable;

    public ButterflyBrain brain;

    @Override
    public void onEnable() {

        version = getPluginMeta().getVersion();
        author = getPluginMeta().getAuthors().isEmpty() ? "Unknown" : getPluginMeta().getAuthors().get(0);

        saveDefaultConfig();

        debug = getConfig().getBoolean("debug", false);

        commandCooldownMs = getConfig().getLong("cooldown.command_ms", 1000L);

        long lifespanSeconds = getConfig().getLong("lifespan.save_period_seconds", 300L);
        lifespanSavePeriodTicks = lifespanSeconds * 20L;

        durabilityPerSecond = getConfig().getInt("flight.durability_per_sec", 2);

        hungerCostOnEnable = getConfig().getInt("flight.hunger_cost_on_enable", 10);
        
        lifespan = new SaveData(this);
        lifespan.load();

        CommandHandler cmd = new CommandHandler(this);
        getCommand("butterfly").setExecutor(cmd);
        getCommand("butterfly").setTabCompleter(cmd);

        brain = new ButterflyBrain(this);
        getServer().getPluginManager().registerEvents(brain, this);

        getServer().getScheduler().runTaskTimer(this, () -> lifespan.flushIfDirty(), lifespanSavePeriodTicks, lifespanSavePeriodTicks);

        getLogger().info("Butterfly v" + version + " enabled");
    }

    @Override
    public void onDisable() {
        lifespan.flushIfDirty();
        getLogger().info("Butterfly disabled, data flushed");
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

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public void markEnabled(UUID id) {
        enabled.add(id);
        interacted.add(id);
    }

    public void reloadButterfly() {
        reloadConfig();

        debug = getConfig().getBoolean("debug", false);

        commandCooldownMs = getConfig().getLong("cooldown.command_ms", 1000L);

        long lifespanSeconds = getConfig().getLong("lifespan.save_period_seconds", 300L);
        lifespanSavePeriodTicks = lifespanSeconds * 20L;

        durabilityPerSecond = getConfig().getInt("flight.durability_per_sec", 2);
        hungerCostOnEnable = getConfig().getInt("flight.hunger_cost_on_enable", 10);

        if (brain != null) {
            brain.reloadFromConfig();
        }

        getLogger().info("Butterfly configuration reloaded");
    }
}
