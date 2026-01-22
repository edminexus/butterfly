package me.butterfly;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public class ModeListener implements Listener {

    private final ButterflyPlugin plugin;

    public ModeListener(ButterflyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();

        if (plugin.enabled.remove(p.getUniqueId())) {
            p.setAllowFlight(false);
            p.setFlying(false);
            p.sendMessage("§9Flight disabled§: Game mode changed");
        }
    }
}
