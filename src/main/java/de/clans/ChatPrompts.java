package de.clans;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Fängt die nächste Chatnachricht eines Spielers ab (für Name/Nachricht-Eingaben aus der GUI). */
public final class ChatPrompts implements Listener {

    private record Prompt(Consumer<String> callback, long expires) {}

    private final ClansPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPrompts(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String hintMini, Consumer<String> callback) {
        prompts.put(p.getUniqueId(), new Prompt(callback, System.currentTimeMillis() + 30_000L));
        p.closeInventory();
        plugin.send(p, hintMini + " <dark_gray>(<red>abbrechen<dark_gray> zum Abbruch, 30s)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Prompt pr = prompts.remove(e.getPlayer().getUniqueId());
        if (pr == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (System.currentTimeMillis() > pr.expires()) {
                plugin.err(p, "Die Eingabe ist abgelaufen.");
                return;
            }
            if (text.equalsIgnoreCase("abbrechen") || text.equalsIgnoreCase("cancel")) {
                plugin.send(p, "<gray>Eingabe abgebrochen.");
                return;
            }
            pr.callback().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
    }
}
