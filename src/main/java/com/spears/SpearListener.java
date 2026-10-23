package com.spears;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpearListener implements Listener {

    /** Normal survival melee reach. Anything closer is already handled by vanilla. */
    private static final double VANILLA_REACH = 3.05;

    private final SpearsPlugin plugin;
    private final Map<UUID, Long> lastLunge = new HashMap<>();

    public SpearListener(SpearsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Reach + Lunge (both happen on the swing / jab)
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        final Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        SpearItems.Tier tier = SpearItems.getTier(hand);
        if (tier == null) return;
        if (!p.hasPermission("spears.use")) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;

        final double reach = plugin.reach();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World world = p.getWorld();
        Vector eyeVec = eye.toVector();

        // Blocks that stop the jab (grass, webs etc. are ignored, like a real spear)
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, reach, FluidCollisionMode.NEVER, true);
        double blockDist = blockHit == null ? Double.MAX_VALUE : blockHit.getHitPosition().distance(eyeVec);

        // Closest living thing in reach
        RayTraceResult entityHit = world.rayTraceEntities(eye, dir, reach, 0.1, ent -> isTargetable(p, ent));
        Entity target = null;
        double entityDist = 0;
        if (entityHit != null && entityHit.getHitEntity() != null) {
            entityDist = entityHit.getHitPosition().distance(eyeVec);
            if (entityDist < blockDist) {
                target = entityHit.getHitEntity();
            }
        }

        // --- Reach: only needed when the target is beyond normal reach ---
        if (target != null && entityDist > VANILLA_REACH && p.getGameMode() != GameMode.CREATIVE) {
            final Entity victim = target;
            // Run one tick later on purpose: if the player swaps to another tool right after
            // swinging, the hit uses THAT tool's attributes (attribute swapping keeps working).
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || p.isDead() || !victim.isValid() || victim.isDead()) return;
                if (!p.getWorld().equals(victim.getWorld())) return;
                if (p.getEyeLocation().distance(victim.getLocation()) > reach + 2.0) return;
                p.attack(victim);
            });
        }

        // --- Lunge ---
        int lunge = SpearItems.getLunge(hand);
        if (lunge > 0) {
            boolean lookingAtBlock = blockDist < reach && target == null; // probably mining, not jabbing
            if (!lookingAtBlock) {
                tryLunge(p, hand, tier, lunge);
            }
        }
    }

    private boolean isTargetable(Player self, Entity ent) {
        if (ent.equals(self) || !(ent instanceof LivingEntity)) return false;
        if (ent.equals(self.getVehicle())) return false;
        if (ent.isDead()) return false;
        if (ent instanceof Player && ((Player) ent).getGameMode() == GameMode.SPECTATOR) return false;
        return true;
    }

    @SuppressWarnings("deprecation")
    private void tryLunge(Player p, ItemStack hand, SpearItems.Tier tier, int level) {
        if (p.isInWater() || p.isGliding() || p.isInsideVehicle()) return;

        long now = System.currentTimeMillis();
        Long last = lastLunge.get(p.getUniqueId());
        if (last != null && now - last < tier.cooldownMs) return;

        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) return; // looking straight up or down
        dir.normalize();

        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        if (!creative) {
            int cost = level * Math.max(0, plugin.getConfig().getInt("lunge.hunger-per-level", 1));
            float saturation = p.getSaturation();
            int food = p.getFoodLevel();
            if (food + saturation < cost) return; // too hungry

            if (saturation >= cost) {
                p.setSaturation(saturation - cost);
            } else {
                int remaining = (int) Math.ceil(cost - saturation);
                p.setSaturation(0f);
                p.setFoodLevel(Math.max(0, food - remaining));
            }

            // 1 durability per lunge
            ItemMeta meta = hand.getItemMeta();
            if (meta instanceof Damageable && !meta.isUnbreakable()) {
                Damageable damageable = (Damageable) meta;
                int damage = damageable.getDamage() + 1;
                if (damage >= hand.getType().getMaxDurability()) {
                    hand.setAmount(0); // spear breaks
                    p.getInventory().setItemInMainHand(hand);
                } else {
                    damageable.setDamage(damage);
                    hand.setItemMeta(meta);
                    p.getInventory().setItemInMainHand(hand);
                }
            }
        }

        lastLunge.put(p.getUniqueId(), now);

        double speed = plugin.lungeSpeed(level) / 20.0; // blocks per tick
        if (!p.isOnGround()) {
            speed *= plugin.getConfig().getDouble("lunge.air-multiplier", 1.5);
        }
        Vector current = p.getVelocity();
        p.setVelocity(new Vector(dir.getX() * speed, current.getY(), dir.getZ() * speed));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastLunge.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------
    // Spears do not sweep
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!plugin.getConfig().getBoolean("disable-sweep", true)) return;
        if (e.getDamager() instanceof Player
                && SpearItems.getTier(((Player) e.getDamager()).getInventory().getItemInMainHand()) != null) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Lunge books on the anvil
    // ------------------------------------------------------------------

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || right == null) return;
        if (SpearItems.getTier(left) == null) return;

        int bookLevel = SpearItems.getBookLevel(right);
        if (bookLevel <= 0) return;

        int current = SpearItems.getLunge(left);
        int newLevel = (current == bookLevel) ? Math.min(3, current + 1) : Math.max(current, bookLevel);
        if (newLevel == current) {
            e.setResult(null);
            return;
        }

        ItemStack base = e.getResult();
        ItemStack result = (base != null && base.getType() != Material.AIR) ? base.clone() : left.clone();
        SpearItems.setLunge(result, newLevel);
        e.setResult(result);
        try {
            inv.setRepairCost(Math.max(2, newLevel * 2));
        } catch (Throwable ignored) {
            // cost display is cosmetic on some versions
        }
    }

    // ------------------------------------------------------------------
    // Diamond spear -> netherite spear (smithing table)
    // ------------------------------------------------------------------

    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result == null || result.getType() != Material.NETHERITE_SWORD) return;
        // Vanilla copies the diamond spear's data onto the netherite sword; it still says "diamond".
        if (SpearItems.getTier(result) != SpearItems.Tier.DIAMOND) return;

        ItemStack upgraded = result.clone();
        SpearItems.upgradeToNetherite(upgraded);
        e.setResult(upgraded);
    }

    // ------------------------------------------------------------------
    // Crafting permission + recipe book
    // ------------------------------------------------------------------

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof Keyed)) return;
        if (!((Keyed) recipe).getKey().equals(plugin.diamondRecipeKey())) return;
        for (HumanEntity viewer : e.getViewers()) {
            if (!viewer.hasPermission("spears.craft")) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(plugin.diamondRecipeKey());
    }
}
