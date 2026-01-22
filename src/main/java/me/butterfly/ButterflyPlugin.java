package me.butterfly;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ButterflyPlugin extends JavaPlugin {

    public final Set<UUID> enabled = new HashSet<>();
    public final Set<UUID> interacted = new HashSet<>();
    public final Map<UUID, Long> cooldowns = new HashMap<>();

    public LifespanStore lifespan;

    public static final long COOLDOWN_MS = 1000;

    @Override
    public void onEnable() {
        lifespan = new LifespanStore(this);
        lifespan.load();

        CommandHandler cmd = new CommandHandler(this);
        getCommand("butterfly").setExecutor(cmd);
        getCommand("butterfly").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new FlyListener(this), this);
        getServer().getPluginManager().registerEvents(new ModeListener(this), this);
    }

    @Override
    public void onDisable() {
        lifespan.save();
    }
}
