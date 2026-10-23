package com.spears;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SpearsPlugin extends JavaPlugin {

    private NamespacedKey diamondRecipeKey;
    private boolean active;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Newer versions already have real spears: nothing to add, stay out of the way.
        boolean vanillaHasSpears = Material.getMaterial("DIAMOND_SPEAR") != null;
        if (vanillaHasSpears && !getConfig().getBoolean("force-custom-spears", false)) {
            getLogger().info("This server already has vanilla spears, so Spears is doing nothing. "
                    + "Set force-custom-spears: true in config.yml to use the custom ones anyway.");
            PluginCommand c = getCommand("spear");
            if (c != null) {
                c.setExecutor((sender, cmd, label, args) -> {
                    sender.sendMessage("Vanilla spears are already available on this server.");
                    return true;
                });
            }
            return;
        }

        active = true;
        SpearItems.init(this);
        diamondRecipeKey = new NamespacedKey(this, "diamond_spear");
        registerRecipe();

        Bukkit.getPluginManager().registerEvents(new SpearListener(this), this);

        PluginCommand c = getCommand("spear");
        if (c != null) {
            SpearCommand handler = new SpearCommand(this);
            c.setExecutor(handler);
            c.setTabCompleter(handler);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.discoverRecipe(diamondRecipeKey);
        }
        getLogger().info("Spears enabled (custom diamond + netherite spears).");
    }

    @Override
    public void onDisable() {
        if (active && diamondRecipeKey != null) {
            Bukkit.removeRecipe(diamondRecipeKey);
        }
    }

    private void registerRecipe() {
        // Same shape as the vanilla spear: material top-right, stick centre, stick bottom-left.
        ShapedRecipe recipe = new ShapedRecipe(diamondRecipeKey, SpearItems.create(SpearItems.Tier.DIAMOND, 0));
        recipe.shape("  D", " S ", "S  ");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(recipe);
        // Netherite spear = smithing upgrade of a diamond spear (handled in SpearListener).
    }

    public NamespacedKey diamondRecipeKey() {
        return diamondRecipeKey;
    }

    public double reach() {
        return Math.min(6.0, Math.max(3.5, getConfig().getDouble("reach", 4.5)));
    }

    public double lungeSpeed(int level) {
        List<Double> speeds = getConfig().getDoubleList("lunge.speeds");
        double[] defaults = {9.16, 18.32, 27.48};
        int i = Math.max(1, Math.min(3, level)) - 1;
        double base = i < speeds.size() ? speeds.get(i) : defaults[i];
        return base * getConfig().getDouble("lunge.velocity-multiplier", 1.0);
    }
}
