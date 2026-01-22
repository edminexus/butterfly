package me.butterfly;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ButterflyTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (!(s instanceof Player)) return List.of();

        if (args.length == 1)
            return List.of("glue","cut","toggle","canfly","lifespan")
                    .stream().filter(x -> x.startsWith(args[0])).collect(Collectors.toList());

        if (args.length == 2 && args[0].equalsIgnoreCase("lifespan"))
            return List.of("all");

        return List.of();
    }
}
