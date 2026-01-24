
/*
DISCLAIMER:
This Plugin is fully vibe coded so there can be remaining artifacts of pure shit, vomit, and other disgusting stuffs.
I did my best clean, organize and remove those stuffs to make it readable and understandable atleast to me.
So don't contact me saying it's the worst code you have ever seen in your life and there are better things than this.
My goal wasn't to make something that's the best. My goal was to build something that works for me.
Also I knew shit about Java before making this project.
*/

package me.butterfly;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ButterflyMain extends JavaPlugin {

    public SaveData lifespan;
    public ButterflyBrain brain;

    public long commandCooldownMs;
    public long lifespanSavePeriodTicks;
    public int durabilityPerSecond;
    public int hungerCostOnEnable;

    private boolean debug;
    private String version;
    private String author;

    public static final float VANILLA_FLY_SPEED = 0.1f;

    public final Set<UUID> enabled = Collections.synchronizedSet(new HashSet<>());
    public final Set<UUID> interacted = ConcurrentHashMap.newKeySet();

    public final Map<UUID, Long> cooldowns = Collections.synchronizedMap(new HashMap<>());

    private volatile String latestVersion = "unknown";


    @Override
    public void onEnable() {

        version = getPluginMeta().getVersion();
        author = getPluginMeta().getAuthors().isEmpty() ? "Unknown" : getPluginMeta().getAuthors().get(0);

        saveDefaultConfig();

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL("https://api.github.com/repos/edminexus/butterfly/releases/latest");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();

                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestProperty("Accept", "application/json");

                try (InputStream in = con.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

                    String json = reader.lines().collect(Collectors.joining());
                    int idx = json.indexOf("\"tag_name\":\"");

                    if (idx != -1) {
                        int start = idx + 12;
                        int end = json.indexOf("\"", start);
                        latestVersion = json.substring(start, end);
                    }
                }
            } catch (Exception e) {
                latestVersion = "unknown";
                debug("Failed to fetch latest version");
            }
        });

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

        getLogger().info("Running v" + version + " (latest: checking...)");
    }

    @Override
    public void onDisable() {
        lifespan.flushIfDirty();
        getLogger().info("Butterfly disabled, data flushed");
    }

    // Helpers

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

    public String getLatestVersion() {
        return latestVersion;
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
