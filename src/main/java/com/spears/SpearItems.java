package com.spears;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds and reads spear items. A spear is a sword (diamond/netherite) tagged with
 * PersistentData, with spear attributes: diamond 4 damage / 0.95 speed,
 * netherite 5 damage / 0.87 speed (same as the vanilla spears).
 */
public final class SpearItems {

    private static final String SECTION = "\u00a7";
    private static final String LORE_PREFIX = SECTION + "7Lunge ";

    public enum Tier {
        DIAMOND("diamond", Material.DIAMOND_SWORD, SECTION + "rDiamond Spear", 3.0, -3.05, 1050L, 7101),
        NETHERITE("netherite", Material.NETHERITE_SWORD, SECTION + "rNetherite Spear", 4.0, -3.13, 1150L, 7102);

        public final String id;
        public final Material base;
        public final String displayName;
        public final double damageBonus;   // player base damage is 1.0, so total = 1 + bonus
        public final double speedBonus;    // player base attack speed is 4.0, so total = 4 + bonus
        public final long cooldownMs;      // 1 / attack speed
        public final int modelData;

        Tier(String id, Material base, String displayName, double damageBonus,
             double speedBonus, long cooldownMs, int modelData) {
            this.id = id;
            this.base = base;
            this.displayName = displayName;
            this.damageBonus = damageBonus;
            this.speedBonus = speedBonus;
            this.cooldownMs = cooldownMs;
            this.modelData = modelData;
        }

        public static Tier byId(String id) {
            for (Tier t : values()) {
                if (t.id.equals(id)) return t;
            }
            return null;
        }
    }

    private static Plugin plugin;
    private static NamespacedKey tierKey;
    private static NamespacedKey lungeKey;
    private static NamespacedKey bookKey;
    private static Attribute attackDamage;
    private static Attribute attackSpeed;

    private SpearItems() {}

    public static void init(Plugin p) {
        plugin = p;
        tierKey = new NamespacedKey(p, "spear_tier");
        lungeKey = new NamespacedKey(p, "lunge_level");
        bookKey = new NamespacedKey(p, "lunge_book");
        attackDamage = findAttribute("attack_damage");
        attackSpeed = findAttribute("attack_speed");
        if (attackDamage == null || attackSpeed == null) {
            p.getLogger().warning("Could not find attack attributes; spear damage will not be adjusted.");
        }
    }

    // ---------- creating items ----------

    public static ItemStack create(Tier tier, int lunge) {
        ItemStack item = new ItemStack(tier.base);
        ItemMeta meta = item.getItemMeta();
        applyTier(meta, tier, true);
        item.setItemMeta(meta);
        if (lunge > 0) setLunge(item, lunge);
        return item;
    }

    public static ItemStack createLungeBook(int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.INTEGER, level);
        List<String> lore = new ArrayList<>();
        lore.add(LORE_PREFIX + roman(level));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Turns a diamond spear (that vanilla smithing turned into a netherite sword) into a netherite spear. */
    public static void upgradeToNetherite(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean hadDefaultName = !meta.hasDisplayName()
                || meta.getDisplayName().equals(Tier.DIAMOND.displayName);
        applyTier(meta, Tier.NETHERITE, hadDefaultName);
        item.setItemMeta(meta);
    }

    private static void applyTier(ItemMeta meta, Tier tier, boolean setName) {
        if (setName) meta.setDisplayName(tier.displayName);
        meta.setCustomModelData(tier.modelData);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.id);

        if (attackDamage != null && attackSpeed != null) {
            meta.removeAttributeModifier(attackDamage);
            meta.removeAttributeModifier(attackSpeed);
            meta.addAttributeModifier(attackDamage, modifier("spear_damage", tier.damageBonus));
            meta.addAttributeModifier(attackSpeed, modifier("spear_speed", tier.speedBonus));
        }
    }

    // ---------- reading items ----------

    public static Tier getTier(ItemStack item) {
        if (item == null) return null;
        Material type = item.getType();
        if (type != Material.DIAMOND_SWORD && type != Material.NETHERITE_SWORD) return null;
        if (!item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return id == null ? null : Tier.byId(id);
    }

    public static int getLunge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer lvl = item.getItemMeta().getPersistentDataContainer().get(lungeKey, PersistentDataType.INTEGER);
        return lvl == null ? 0 : Math.max(0, Math.min(3, lvl));
    }

    public static int getBookLevel(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return 0;
        Integer lvl = item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.INTEGER);
        return lvl == null ? 0 : Math.max(0, Math.min(3, lvl));
    }

    public static void setLunge(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.startsWith(LORE_PREFIX));
        if (level > 0) {
            pdc.set(lungeKey, PersistentDataType.INTEGER, level);
            lore.add(0, LORE_PREFIX + roman(level));
        } else {
            pdc.remove(lungeKey);
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    public static String roman(int n) {
        switch (n) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            default: return String.valueOf(n);
        }
    }

    // ---------- version-safe attribute helpers ----------

    /** Finds an Attribute on both old (enum) and new (registry) servers without hard-coding names. */
    private static Attribute findAttribute(String modernName) {
        try {
            Object registry = Registry.class.getField("ATTRIBUTE").get(null);
            Method get = Registry.class.getMethod("get", NamespacedKey.class);
            for (String key : new String[]{modernName, "generic." + modernName}) {
                Object found = get.invoke(registry, NamespacedKey.minecraft(key));
                if (found instanceof Attribute) return (Attribute) found;
            }
        } catch (Throwable ignored) {
            // older server: fall through to enum fields
        }
        String legacy = "GENERIC_" + modernName.toUpperCase(Locale.ROOT);
        for (String field : new String[]{legacy, modernName.toUpperCase(Locale.ROOT)}) {
            try {
                Object found = Attribute.class.getField(field).get(null);
                if (found instanceof Attribute) return (Attribute) found;
            } catch (Throwable ignored) {
                // try next name
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AttributeModifier modifier(String name, double amount) {
        // Newer servers: (NamespacedKey, amount, operation, EquipmentSlotGroup)
        try {
            Class<?> groupClass = Class.forName("org.bukkit.inventory.EquipmentSlotGroup");
            Object mainHand = groupClass.getField("MAINHAND").get(null);
            Constructor<AttributeModifier> ctor = AttributeModifier.class.getConstructor(
                    NamespacedKey.class, double.class, AttributeModifier.Operation.class, groupClass);
            return ctor.newInstance(new NamespacedKey(plugin, name), amount,
                    AttributeModifier.Operation.ADD_NUMBER, mainHand);
        } catch (Throwable ignored) {
            // 1.20.x style below
        }
        UUID id = UUID.nameUUIDFromBytes(("spears:" + name).getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
    }
}
