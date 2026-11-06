package de.clans;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("gui", "create", "invite", "accept", "kick", "leave",
            "disband", "leader", "info", "list", "chat", "ally", "color", "help");

    private final ClansPlugin plugin;

    public ClanCommand(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    private static String arg(String[] a, int i) {
        return a.length > i ? a[i] : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload") && sender.hasPermission("clans.admin")) {
            plugin.reloadConfig();
            plugin.clans().refreshAll();
            plugin.send(sender, "<green>Konfiguration neu geladen.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        ClanManager m = plugin.clans();
        if (a.length == 0) {
            plugin.gui().openFor(p);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu" -> plugin.gui().openFor(p);
            case "help" -> help(p);
            case "create" -> m.create(p, arg(a, 1));
            case "invite" -> {
                if (arg(a, 1) == null) plugin.err(p, "Verwendung: <yellow>/clan invite [Spieler]");
                else m.invite(p, Bukkit.getPlayerExact(a[1]));
            }
            case "accept" -> m.accept(p, arg(a, 1));
            case "kick" -> {
                if (arg(a, 1) == null) plugin.err(p, "Verwendung: <yellow>/clan kick [Spieler]");
                else m.kickByName(p, a[1]);
            }
            case "leave" -> m.leave(p);
            case "disband" -> m.disband(p);
            case "leader" -> {
                if (arg(a, 1) == null) plugin.err(p, "Verwendung: <yellow>/clan leader [Spieler]");
                else m.leaderByName(p, a[1]);
            }
            case "info", "stats" -> {
                Clan c = a.length > 1 ? m.byKey(a[1]) : m.getClan(p);
                if (c == null) plugin.err(p, a.length > 1 ? "Clan nicht gefunden." : "Verwendung: <yellow>/clan info [Clanname]");
                else m.sendInfo(p, c);
            }
            case "list" -> {
                p.sendMessage(Text.mm("<dark_gray><strikethrough>-----------</strikethrough> <gold>Alle Clans <dark_gray><strikethrough>-----------"));
                if (m.all().isEmpty()) p.sendMessage(Text.mm("<gray>Es gibt noch keine Clans."));
                for (Clan c : m.all()) {
                    p.sendMessage(Text.mm("<yellow>" + c.name + " <dark_gray>- <gray>" + c.members.size() + " Mitglieder"));
                }
            }
            case "chat" -> {
                if (a.length < 2) plugin.err(p, "Verwendung: <yellow>/clan chat [Nachricht]");
                else m.chat(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "ally" -> {
                if (a.length < 3) {
                    plugin.err(p, "Verwendung: <yellow>/clan ally invite|accept|remove [Clan]");
                    return true;
                }
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "invite" -> m.allyInvite(p, a[2]);
                    case "accept" -> m.allyAccept(p, a[2]);
                    case "remove" -> m.allyRemove(p, a[2]);
                    default -> plugin.err(p, "Verwendung: <yellow>/clan ally invite|accept|remove [Clan]");
                }
            }
            case "color", "colour", "farbe" -> {
                if (a.length < 2) {
                    plugin.err(p, "Verwendung: <yellow>/clan color [Farbe] ([Farbe2]) <gray>oder <yellow>/clan color reset");
                    plugin.send(p, "<gray>Farben: red, orange, yellow, green, aqua, blue, pink, purple, white, gray (+ dark...) oder #RRGGBB");
                } else {
                    m.setColor(p, a[1], arg(a, 2));
                }
            }
            default -> plugin.err(p, "Unbekannter Clan-Befehl. <gray>/clan help");
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(Text.mm("<dark_gray><strikethrough>-----------</strikethrough> <gold>Clan-Befehle <dark_gray><strikethrough>-----------"));
        String[][] lines = {
                {"/clan", "Clan-Menü öffnen"},
                {"/clan create [Name]", "Clan gründen"},
                {"/clan invite [Spieler]", "Spieler einladen (Leiter)"},
                {"/clan accept [Clan]", "Einladung annehmen"},
                {"/clan kick [Spieler]", "Mitglied kicken (Leiter)"},
                {"/clan leave", "Clan verlassen"},
                {"/clan disband", "Clan auflösen (Leiter)"},
                {"/clan leader [Spieler]", "Leitung übergeben"},
                {"/clan info [Clan]", "Clan-Übersicht"},
                {"/clan list", "Alle Clans"},
                {"/clan chat [Nachricht]", "Clan-Chat"},
                {"/clan ally invite|accept|remove [Clan]", "Allianzen"},
        };
        for (String[] l : lines) {
            p.sendMessage(Text.mm("<yellow>" + Text.esc(l[0]) + " <gray>- " + l[1]));
        }
        if (plugin.clans().hasVip(p)) {
            p.sendMessage(Text.mm("<yellow>/clan color [Farbe] ([Farbe2]) <gray>- Farbe oder Farbverlauf (VIP)"));
            p.sendMessage(Text.mm("<yellow>/clan color reset <gray>- Standardfarbe"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        ClanManager m = plugin.clans();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(SUBS);
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                case "kick", "leader" -> {
                    if (sender instanceof Player p && m.getClan(p) != null) out.addAll(m.getClan(p).members.values());
                }
                case "accept", "info", "stats" -> m.all().forEach(c -> out.add(c.name));
                case "ally" -> out.addAll(List.of("invite", "accept", "remove"));
                case "color", "colour", "farbe" -> {
                    out.add("reset");
                    for (ClanColor c : ClanColor.values()) out.add(c.id());
                }
                default -> { }
            }
        } else if (a.length == 3) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "ally" -> m.all().forEach(c -> out.add(c.name));
                case "color", "colour", "farbe" -> {
                    for (ClanColor c : ClanColor.values()) out.add(c.id());
                }
                default -> { }
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
