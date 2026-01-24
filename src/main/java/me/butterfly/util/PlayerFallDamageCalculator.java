package me.butterfly.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PlayerFallDamageCalculator {

    private PlayerFallDamageCalculator() {}

    /**
     * Wiki-accurate fall damage calculation for players only.
     * @param player (Player taking damage)
     * @param fallDistance (Player#getFallDistance())
     * @param landingBlock (Block player landed on)
     * @return final damage (double, in health points)
     */
    public static double calculate(Player player, float fallDistance, Block landingBlock) {
        
        // Block Based Negation
        if (landingBlock != null) {
            Material type = landingBlock.getType();

            // Full negation
            if (negatesAllDamage(type)) {
                return 0.0;
            }
        }

        // Potion-Effect Based Negation
        // Slow Falling: complete negation
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return 0.0;
        }

        // Jump Boost: reduces effective fall distance
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jump != null) {
            fallDistance -= (jump.getAmplifier() + 1);
        }

        // Base Fall Damage
        double damage = Math.max(0.0, fallDistance - 3.0);

        if (damage <= 0.0) {
            return 0.0;
        }

        // Block Based Negation: Multipliers
        if (landingBlock != null) {
            Material type = landingBlock.getType();
            if (type == Material.HAY_BLOCK || type == Material.HONEY_BLOCK) {
                damage *= 0.20;
            } else if (type.name().endsWith("_BED")) {
                damage *= 0.50;
            }
        }

        // Enchanment Based Negation
        // Feather Falling
        int featherLevel = getFeatherFalling(player);
        if (featherLevel > 0) {
            double reduction = Math.min(featherLevel * 0.12, 0.48);
            damage *= (1.0 - reduction);
        }

        // Protection enchantment (all armor pieces)
        int protectionLevel = getProtectionLevel(player);
        if (protectionLevel > 0) {
            double reduction = Math.min(protectionLevel * 0.04, 0.80);
            damage *= (1.0 - reduction);
        }

        // Resistence Potion-Effect Negation
        // Don't know why it is here or it should be here or not but don't wanna touch it cz it works (as intended I hope)
        PotionEffect resistance = player.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance != null) {
            int level = resistance.getAmplifier() + 1;
            damage *= Math.max(0.0, 1.0 - (0.20 * level));
        }

        return Math.max(0.0, damage);
    }

    // Helpers here
    public static boolean negatesAllDamage(Material type) {
        return switch (type) {
            case WATER, BUBBLE_COLUMN, COBWEB, SWEET_BERRY_BUSH, SLIME_BLOCK, POWDER_SNOW, LADDER, VINE,
                 TWISTING_VINES, WEEPING_VINES, SCAFFOLDING -> true;
            default -> false;
        };
    }

    private static int getFeatherFalling(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null) return 0;
        return boots.getEnchantmentLevel(Enchantment.FEATHER_FALLING);
    }

    private static int getProtectionLevel(Player player) {
        int total = 0;

        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) continue;
            total += item.getEnchantmentLevel(Enchantment.PROTECTION);
        }

        return total;
    }
}
