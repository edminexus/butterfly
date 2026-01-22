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

    public static final long COOLDOWN_MS = 1000;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        
        lifespan = new SaveData(this);
        lifespan.load();

        CommandHandler cmd = new CommandHandler(this);
        getCommand("butterfly").setExecutor(cmd);
        getCommand("butterfly").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new ButterflyBrain(this), this);

        // Periodic lifespan save (every 5 minutes)
        getServer().getScheduler().runTaskTimer(this, () -> lifespan.saveIfDirty(), 20L * 300, 20L * 300);
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
