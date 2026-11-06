package de.clans.gui;

import de.clans.ClansPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiManager implements Listener {

    private final ClansPlugin plugin;
    private final Map<UUID, ClanGui> open = new HashMap<>();
    private BukkitTask task;
    private int refreshEvery;

    public GuiManager(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int speed = Math.max(1, plugin.getConfig().getInt("gui.animation-speed", 2));
        refreshEvery = Math.max(1, 40 / speed);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, speed, speed);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (ClanGui g : new ArrayList<>(open.values())) g.player().closeInventory();
        open.clear();
    }

    public void openFor(Player p) {
        open(p, null);
    }

    public void open(Player p, View start) {
        ClanGui g = new ClanGui(plugin, p, start);
        open.put(p.getUniqueId(), g);
        p.openInventory(g.getInventory());
        g.opened();
    }

    /** Zeigt eine Meldung in der geöffneten GUI des Spielers (falls offen). */
    public void toast(Player p, String mini) {
        ClanGui g = open.get(p.getUniqueId());
        if (g != null) g.toast(mini);
    }

    private void tick() {
        for (ClanGui g : new ArrayList<>(open.values())) {
            if (!g.player().isOnline()) continue;
            g.tick(refreshEvery);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ClanGui gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        if (!(e.getClickedInventory().getHolder() instanceof ClanGui)) return;
        if (!(e.getWhoClicked() instanceof Player p) || !p.equals(gui.player())) return;
        gui.click(e.getRawSlot(), e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ClanGui) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof ClanGui g) {
            UUID u = e.getPlayer().getUniqueId();
            if (open.get(u) == g) open.remove(u);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
    }
}
