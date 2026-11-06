package de.clans.gui;

import de.clans.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Items {
    private Items() {}

    private static void flags(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
    }

    private static List<Component> lore(String... lines) {
        return Arrays.stream(lines).map(Text::mm).toList();
    }

    public static ItemStack make(Material m, String name, String... lore) {
        return named(m, Text.mm(name), lore);
    }

    public static ItemStack named(Material m, Component name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (lore.length > 0) meta.lore(lore(lore));
        flags(meta);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack head(OfflinePlayer op, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(op);
        meta.displayName(Text.mm(name));
        if (lore.length > 0) meta.lore(lore(lore));
        flags(meta);
        it.setItemMeta(meta);
        return it;
    }

    /** Glasscheibe ohne Tooltip (für Rahmen/Hintergrund). */
    public static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Hebt ein ausgewähltes Item hervor: Glanz, ▶-Marker im Namen und optionale Hinweiszeile. */
    public static ItemStack marked(ItemStack src, String accentHex, String label) {
        ItemStack it = src.clone();
        ItemMeta meta = it.getItemMeta();
        Component old = meta.hasDisplayName() ? meta.displayName() : Component.text(it.getType().name());
        TextColor col = TextColor.fromHexString(accentHex);
        meta.displayName(Component.text("▶ ", col).decoration(TextDecoration.ITALIC, false).append(old));
        meta.setEnchantmentGlintOverride(true);
        if (label != null) {
            List<Component> l = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            l.add(Component.empty());
            l.add(Text.mm("<green>✔ " + label));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }
}
