package de.clans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ClanManager {

    private static final String TAG_PREFIX = "ca_clan_";

    private final ClansPlugin plugin;
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final File file;
    private boolean dirty;

    public ClanManager(ClansPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    // ------------------------------------------------------------ Persistenz

    public void load() {
        clans.clear();
        byPlayer.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("clans");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Clan c = new Clan(key, s.getString("name", key), UUID.fromString(Objects.requireNonNull(s.getString("owner"))));
                c.c1 = s.getString("c1", "");
                c.c2 = s.getString("c2", "");
                c.kills = s.getInt("kills");
                c.deaths = s.getInt("deaths");
                c.blocks = s.getInt("blocks");
                ConfigurationSection mem = s.getConfigurationSection("members");
                if (mem != null) {
                    for (String u : mem.getKeys(false)) {
                        UUID id = UUID.fromString(u);
                        c.members.put(id, mem.getString(u, "?"));
                        byPlayer.put(id, key);
                    }
                }
                c.allies.addAll(s.getStringList("allies"));
                rebuildTag(c);
                clans.put(key, c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Clan '" + key + "' konnte nicht geladen werden: " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String b = "clans." + c.key + ".";
            y.set(b + "name", c.name);
            y.set(b + "owner", c.owner.toString());
            y.set(b + "c1", c.c1);
            y.set(b + "c2", c.c2);
            y.set(b + "kills", c.kills);
            y.set(b + "deaths", c.deaths);
            y.set(b + "blocks", c.blocks);
            for (Map.Entry<UUID, String> e : c.members.entrySet()) {
                y.set(b + "members." + e.getKey(), e.getValue());
            }
            y.set(b + "allies", new ArrayList<>(c.allies));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("clans.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    public void markDirty() { dirty = true; }
    public void saveIfDirty() { if (dirty) save(); }

    // ------------------------------------------------------------ Abfragen

    public Clan getClan(UUID u) {
        String k = byPlayer.get(u);
        return k == null ? null : clans.get(k);
    }

    public Clan getClan(Player p) { return getClan(p.getUniqueId()); }

    public Clan byKey(String k) {
        return k == null ? null : clans.get(k.toLowerCase(Locale.ROOT));
    }

    public Collection<Clan> all() { return clans.values(); }

    public boolean isLeader(Player p) {
        Clan c = getClan(p);
        return c != null && c.owner.equals(p.getUniqueId());
    }

    public boolean hasVip(Player p) {
        return p.hasPermission(plugin.getConfig().getString("vip-permission", "vip.booster"));
    }

    public List<Clan> invitesFor(UUID u) {
        List<Clan> out = new ArrayList<>();
        for (Clan c : clans.values()) if (c.invites.contains(u)) out.add(c);
        return out;
    }

    public UUID memberByName(Clan c, String name) {
        for (Map.Entry<UUID, String> e : c.members.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    // ------------------------------------------------------------ Tag & Tabliste

    public Component buildTag(String name, String c1, String c2) {
        String h1 = ClanColor.toHex(c1);
        if (h1 == null) return Text.MM.deserialize("<gray>[<green>" + name + "</green>]</gray>");
        String h2 = ClanColor.toHex(c2);
        if (h2 == null) h2 = h1;
        String inner = h1.equals(h2)
                ? "<color:" + h1 + ">" + name + "</color>"
                : "<gradient:" + h1 + ":" + h2 + ">" + name + "</gradient>";
        return Text.MM.deserialize("<gray>[</gray>" + inner + "<gray>]</gray>");
    }

    public void rebuildTag(Clan c) {
        c.tag = buildTag(c.name, c.c1, c.c2);
    }

    public void refresh(Player p) {
        Clan c = getClan(p.getUniqueId());
        var cfg = plugin.getConfig();
        if (cfg.getBoolean("tab-list-name", true)) {
            Component base = Text.MM.deserialize(cfg.getString("tab-base", "<white><player>"),
                    Placeholder.unparsed("player", p.getName()));
            Component suffix;
            if (c != null) {
                suffix = Component.space().append(c.tag);
            } else {
                String nc = cfg.getString("no-clan-tab", "");
                suffix = nc.isEmpty() ? Component.empty() : Component.space().append(Text.MM.deserialize(nc));
            }
            p.playerListName(Component.empty().append(base).append(suffix));
        }
        String wanted = c == null ? null : TAG_PREFIX + c.name;
        boolean has = false;
        for (String t : new ArrayList<>(p.getScoreboardTags())) {
            if (!t.startsWith(TAG_PREFIX)) continue;
            if (t.equals(wanted)) has = true;
            else p.removeScoreboardTag(t);
        }
        if (wanted != null && !has) p.addScoreboardTag(wanted);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    // ------------------------------------------------------------ Helfer

    private Clan requireClan(Player p) {
        Clan c = getClan(p);
        if (c == null) plugin.err(p, "Du bist in keinem Clan.");
        return c;
    }

    private Clan requireLeader(Player p, String action) {
        Clan c = requireClan(p);
        if (c == null) return null;
        if (!c.owner.equals(p.getUniqueId())) {
            plugin.err(p, "Nur der Clanleiter kann " + action + ".");
            return null;
        }
        return c;
    }

    public void broadcastClan(Clan c, String mini) {
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) plugin.send(p, mini);
        }
    }

    // ------------------------------------------------------------ Aktionen

    public boolean create(Player p, String name) {
        if (getClan(p) != null) { plugin.err(p, "Du bist bereits in einem Clan."); return false; }
        if (name == null || name.isEmpty()) { plugin.err(p, "Verwendung: <yellow>/clan create [Name]"); return false; }
        if (!name.matches("[A-Za-z0-9_]+")) { plugin.err(p, "Nur Buchstaben, Zahlen und _ erlaubt."); return false; }
        int min = plugin.getConfig().getInt("min-length", 3);
        int max = plugin.getConfig().getInt("max-length", 10);
        if (name.length() < min) { plugin.err(p, "Der Name muss mindestens " + min + " Zeichen haben."); return false; }
        if (name.length() > max) { plugin.err(p, "Der Name darf höchstens " + max + " Zeichen haben."); return false; }
        String key = name.toLowerCase(Locale.ROOT);
        if (clans.containsKey(key)) { plugin.err(p, "Dieser Clanname ist bereits vergeben."); return false; }

        Clan c = new Clan(key, name, p.getUniqueId());
        c.members.put(p.getUniqueId(), p.getName());
        rebuildTag(c);
        clans.put(key, c);
        byPlayer.put(p.getUniqueId(), key);
        markDirty();
        refreshAll();
        Bukkit.broadcast(Text.mm(plugin.prefix() + "<gold>" + Text.esc(p.getName())
                + " <gray>hat den Clan <gold>" + name + " <gray>gegründet!"));
        return true;
    }

    public boolean invite(Player p, Player target) {
        Clan c = requireLeader(p, "einladen");
        if (c == null) return false;
        if (target == null) { plugin.err(p, "Spieler nicht gefunden (muss online sein)."); return false; }
        if (target.equals(p)) { plugin.err(p, "Du kannst dich nicht selbst einladen."); return false; }
        if (getClan(target) != null) { plugin.err(p, "Dieser Spieler ist schon in einem Clan."); return false; }
        c.invites.add(target.getUniqueId());
        target.sendMessage(Text.mm(plugin.prefix() + "<gray>Du wurdest in den Clan <gold>" + c.name
                + " <gray>eingeladen. <click:run_command:'/clan accept " + c.name
                + "'><hover:show_text:'<green>Einladung annehmen'><green><bold>[ANNEHMEN]</bold></green></hover></click>"));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        plugin.send(p, "<green>Einladung an <yellow>" + Text.esc(target.getName()) + " <green>gesendet.");
        return true;
    }

    public boolean accept(Player p, String keyArg) {
        UUID u = p.getUniqueId();
        if (getClan(p) != null) { plugin.err(p, "Du bist bereits in einem Clan."); return false; }
        Clan found = null;
        if (keyArg != null && !keyArg.isEmpty()) {
            Clan c = byKey(keyArg);
            if (c != null && c.invites.contains(u)) found = c;
        } else {
            for (Clan c : clans.values()) {
                if (c.invites.contains(u)) { found = c; break; }
            }
        }
        if (found == null) { plugin.err(p, "Keine offene Einladung."); return false; }
        for (Clan o : clans.values()) o.invites.remove(u);
        found.members.put(u, p.getName());
        byPlayer.put(u, found.key);
        markDirty();
        refreshAll();
        broadcastClan(found, "<green>" + Text.esc(p.getName()) + " <gray>ist dem Clan beigetreten.");
        return true;
    }

    public boolean kick(Player p, UUID target) {
        Clan c = requireLeader(p, "kicken");
        if (c == null) return false;
        if (!c.members.containsKey(target)) { plugin.err(p, "Mitglied nicht gefunden."); return false; }
        if (target.equals(p.getUniqueId())) {
            plugin.err(p, "Dich selbst kannst du nicht kicken (<yellow>/clan disband<red>).");
            return false;
        }
        String tn = c.members.remove(target);
        byPlayer.remove(target);
        Player tp = Bukkit.getPlayer(target);
        if (tp != null) plugin.err(tp, "Du wurdest aus dem Clan entfernt.");
        markDirty();
        refreshAll();
        plugin.send(p, "<yellow>" + Text.esc(tn) + " <gray>wurde aus dem Clan entfernt.");
        return true;
    }

    public boolean kickByName(Player p, String name) {
        Clan c = requireLeader(p, "kicken");
        if (c == null) return false;
        UUID t = memberByName(c, name);
        if (t == null) { plugin.err(p, "Mitglied nicht gefunden."); return false; }
        return kick(p, t);
    }

    public boolean leave(Player p) {
        Clan c = requireClan(p);
        if (c == null) return false;
        if (c.owner.equals(p.getUniqueId())) {
            plugin.err(p, "Clanleiter müssen den Clan auflösen oder die Leitung übertragen.");
            return false;
        }
        c.members.remove(p.getUniqueId());
        byPlayer.remove(p.getUniqueId());
        markDirty();
        refreshAll();
        plugin.send(p, "<gray>Du hast den Clan verlassen.");
        broadcastClan(c, "<yellow>" + Text.esc(p.getName()) + " <gray>hat den Clan verlassen.");
        return true;
    }

    public boolean disband(Player p) {
        Clan c = requireLeader(p, "den Clan auflösen");
        if (c == null) return false;
        for (UUID u : c.members.keySet()) byPlayer.remove(u);
        for (Clan o : clans.values()) {
            o.allies.remove(c.key);
            o.allyRequests.remove(c.key);
        }
        clans.remove(c.key);
        markDirty();
        refreshAll();
        Bukkit.broadcast(Text.mm(plugin.prefix() + "<gray>Der Clan <gold>" + c.name + " <gray>wurde aufgelöst."));
        return true;
    }

    public boolean leader(Player p, UUID target) {
        Clan c = requireLeader(p, "die Leitung übergeben");
        if (c == null) return false;
        if (!c.members.containsKey(target)) { plugin.err(p, "Mitglied nicht gefunden."); return false; }
        if (target.equals(p.getUniqueId())) { plugin.err(p, "Du bist bereits Leiter."); return false; }
        c.owner = target;
        markDirty();
        broadcastClan(c, "<yellow>" + Text.esc(c.members.get(target)) + " <gray>ist jetzt Clanleiter.");
        return true;
    }

    public boolean leaderByName(Player p, String name) {
        Clan c = requireLeader(p, "die Leitung übergeben");
        if (c == null) return false;
        UUID t = memberByName(c, name);
        if (t == null) { plugin.err(p, "Mitglied nicht gefunden."); return false; }
        return leader(p, t);
    }

    public void chat(Player p, String msg) {
        Clan c = requireClan(p);
        if (c == null) return;
        if (msg == null || msg.isBlank()) { plugin.err(p, "Verwendung: <yellow>/clan chat [Nachricht]"); return; }
        Component line = Component.empty()
                .append(Text.mm(plugin.prefix() + "<aqua>Clan-Chat <dark_gray>| <white>" + Text.esc(p.getName()) + "<gray>: <white>"))
                .append(Component.text(msg));
        for (UUID u : c.members.keySet()) {
            Player t = Bukkit.getPlayer(u);
            if (t != null) t.sendMessage(line);
        }
    }

    // ------------------------------------------------------------ Allianzen

    public boolean allyInvite(Player p, String otherKey) {
        Clan me = requireLeader(p, "Allianzen verwalten");
        if (me == null) return false;
        Clan other = byKey(otherKey);
        if (other == null) { plugin.err(p, "Clan nicht gefunden."); return false; }
        if (other == me) { plugin.err(p, "Das ist dein eigener Clan."); return false; }
        if (me.allies.contains(other.key)) { plugin.err(p, "Ihr seid bereits verbündet."); return false; }
        other.allyRequests.add(me.key);
        for (UUID u : other.members.keySet()) {
            Player t = Bukkit.getPlayer(u);
            if (t == null) continue;
            t.sendMessage(Text.mm(plugin.prefix() + "<gold>" + me.name
                    + " <gray>möchte sich mit euch verbünden: <click:run_command:'/clan ally accept " + me.name
                    + "'><green><bold>[ANNEHMEN]</bold></green></click>"));
        }
        plugin.send(p, "<green>Allianzanfrage an <yellow>" + other.name + " <green>gesendet.");
        return true;
    }

    public boolean allyAccept(Player p, String otherKey) {
        Clan me = requireLeader(p, "Allianzen verwalten");
        if (me == null) return false;
        Clan other = byKey(otherKey);
        if (other == null) { plugin.err(p, "Clan nicht gefunden."); return false; }
        if (!me.allyRequests.contains(other.key)) { plugin.err(p, "Keine Anfrage gefunden."); return false; }
        me.allyRequests.remove(other.key);
        me.allies.add(other.key);
        other.allies.add(me.key);
        markDirty();
        broadcastClan(me, "<green>Allianz mit <yellow>" + other.name + " <green>geschlossen.");
        broadcastClan(other, "<green>Allianz mit <yellow>" + me.name + " <green>geschlossen.");
        return true;
    }

    public boolean allyRemove(Player p, String otherKey) {
        Clan me = requireLeader(p, "Allianzen verwalten");
        if (me == null) return false;
        Clan other = byKey(otherKey);
        if (other == null) { plugin.err(p, "Clan nicht gefunden."); return false; }
        if (!me.allies.contains(other.key)) { plugin.err(p, "Ihr seid nicht verbündet."); return false; }
        me.allies.remove(other.key);
        other.allies.remove(me.key);
        markDirty();
        broadcastClan(me, "<gray>Allianz mit <yellow>" + other.name + " <gray>beendet.");
        broadcastClan(other, "<gray>Allianz mit <yellow>" + me.name + " <gray>beendet.");
        return true;
    }

    // ------------------------------------------------------------ Farbe

    public boolean setColor(Player p, String a, String b) {
        if (!hasVip(p)) { plugin.err(p, "Die Clanfarbe ist nur für VIP-Booster."); return false; }
        Clan c = requireClan(p);
        if (c == null) return false;
        if (plugin.getConfig().getBoolean("color-needs-leader", true) && !c.owner.equals(p.getUniqueId())) {
            plugin.err(p, "Nur der Clanleiter kann die Farbe ändern.");
            return false;
        }
        if (a == null || a.isBlank() || a.equalsIgnoreCase("reset")) {
            c.c1 = "";
            c.c2 = "";
        } else {
            if (ClanColor.toHex(a) == null) { plugin.err(p, "Unbekannte Farbe: <yellow>" + Text.esc(a)); return false; }
            if (b != null && !b.isBlank() && ClanColor.toHex(b) == null) {
                plugin.err(p, "Unbekannte Farbe: <yellow>" + Text.esc(b));
                return false;
            }
            c.c1 = a.toLowerCase(Locale.ROOT);
            c.c2 = (b == null || b.isBlank()) ? "" : b.toLowerCase(Locale.ROOT);
        }
        rebuildTag(c);
        markDirty();
        refreshAll();
        p.sendMessage(Component.empty()
                .append(Text.mm(plugin.prefix() + "<gray>Neuer Clan-Tag: "))
                .append(c.tag));
        return true;
    }

    // ------------------------------------------------------------ Info (Chat)

    public void sendInfo(CommandSender to, Clan c) {
        String leaderName = c.members.getOrDefault(c.owner, "?");
        to.sendMessage(Text.mm("<dark_gray><strikethrough>-----------</strikethrough> <gold>Clan <yellow>" + c.name
                + " <dark_gray><strikethrough>-----------"));
        to.sendMessage(Component.empty().append(Text.mm("<gray>Tag: ")).append(c.tag));
        to.sendMessage(Text.mm("<gray>Leiter: <gold>" + Text.esc(leaderName)));

        List<String> names = new ArrayList<>();
        for (Map.Entry<UUID, String> e : c.members.entrySet()) {
            boolean on = Bukkit.getPlayer(e.getKey()) != null;
            names.add((on ? "<green>" : "<gray>") + Text.esc(e.getValue()));
        }
        to.sendMessage(Text.mm("<gray>Mitglieder (<yellow>" + names.size() + "<gray>): " + String.join("<dark_gray>, ", names)));

        List<String> al = new ArrayList<>();
        for (String k : c.allies) {
            Clan o = clans.get(k);
            if (o != null) al.add("<aqua>" + o.name);
        }
        to.sendMessage(Text.mm("<gray>Allianzen: " + (al.isEmpty() ? "<dark_gray>keine" : String.join("<dark_gray>, ", al))));
        to.sendMessage(Text.mm("<gray>Kills: <green>" + c.kills + " <dark_gray>| <gray>Tode: <red>" + c.deaths
                + " <dark_gray>| <gray>K/D: <yellow>" + c.kd()));
        to.sendMessage(Text.mm("<gray>Abgebaute Blöcke: <yellow>" + c.blocks));
    }
}
