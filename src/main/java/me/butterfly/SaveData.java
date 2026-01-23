package me.butterfly;

import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SaveData {

    private final ButterflyMain plugin;
    private final Map<UUID, Long> data = new HashMap<>();
    private final File file;
    private final FileConfiguration cfg;
    private boolean dirty = false;

    public void flushIfDirty() {
        if (!dirty) return;

        data.forEach((u, t) -> cfg.set(u.toString(), t));
        try {
            cfg.save(file);
            dirty = false;
            plugin.debug("Lifespan data saved to disk");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save lifespan.yml");
            e.printStackTrace();
        }
    }

    public SaveData(ButterflyMain plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "lifespan.yml");
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void load() {
        for (String k : cfg.getKeys(false)) {
            data.put(UUID.fromString(k), cfg.getLong(k));
        }
    }

    public void save() {
        data.forEach((u, t) -> cfg.set(u.toString(), t));
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save lifespan.yml");
            e.printStackTrace();
        }
    }

    public void increment(UUID u) {
        data.merge(u, 1L, (oldVal, one) -> oldVal + one);

        if (!dirty) {
            dirty = true;
            plugin.debug("Lifespan marked dirty (pending save)");
        }
    }

    private String fmt(long s) {
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    public void printSelf(Player p) {
        long t = data.getOrDefault(p.getUniqueId(), 0L);
        p.sendMessage("§eYour total flying time: §a" + fmt(t));
    }

    public void printAll(Player p) {
        data.forEach((u, t) -> {
            Player pl = plugin.getServer().getPlayer(u);
            if (pl != null)
                p.sendMessage(pl.getName() + " → " + fmt(t));
        });
    }
}
