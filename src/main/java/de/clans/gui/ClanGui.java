package de.clans.gui;

import de.clans.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Eine geöffnete Clan-GUI (54 Slots).
 *
 * Layout:  Reihe 0 = Tabs/Rahmen, Reihe 1-4 = Inhalt (Spalten 1-7), Reihe 5 = Aktionen/Seiten.
 * Animationen: umlaufender Lichtstreifen im Rahmen, pulsierende Umrandung um die Auswahl,
 *              Aufbau-Animation beim Öffnen, Aufblitzen bei Klicks.
 */
public final class ClanGui implements InventoryHolder {

    // Rahmen im Uhrzeigersinn
    private static final int[] RING = {0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 35, 44, 53, 52, 51, 50, 49, 48, 47, 46, 45, 36, 27, 18, 9};
    private static final int[] RING_INDEX = new int[54];

    static {
        Arrays.fill(RING_INDEX, -1);
        for (int i = 0; i < RING.length; i++) RING_INDEX[RING[i]] = i;
    }

    private static final Material BLACK = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material DARK = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material WHITE = Material.WHITE_STAINED_GLASS_PANE;
    private static final String DEFAULT_ACCENT = "#ffb347";

    private record Button(ItemStack item, Consumer<ClickType> action) {}

    private record Tab(View view, int slot, Material icon, String name, String desc) {}

    private final ClansPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final boolean animated;
    private final boolean sounds;
    private final String stamp;

    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Map<Integer, String> marked = new HashMap<>();
    private final Material[] shown = new Material[54];

    private View view;
    private int page;
    private String selected;
    private String c1 = "";
    private String c2 = "";

    private String confirmText = "";
    private Supplier<Boolean> confirmAct;
    private View confirmBack = View.HOME;
    private View confirmNext = View.HOME;

    private String toast;
    private long toastUntil;

    private int frame;
    private int intro;
    private int flash;
    private int sinceRefresh;

    public ClanGui(ClansPlugin plugin, Player player, View start) {
        this.plugin = plugin;
        this.player = player;
        var cfg = plugin.getConfig();
        this.animated = cfg.getBoolean("gui.animation", true);
        this.sounds = cfg.getBoolean("gui.sounds", true);
        this.intro = animated ? -2 : RING.length;
        Clan c = clan();
        this.stamp = computeStamp();
        this.view = start != null ? start : (c != null ? View.OVERVIEW : View.HOME);
        if (c != null) {
            c1 = c.c1;
            c2 = c.c2;
        }
        this.inventory = Bukkit.createInventory(this, 54, title());
        render();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    // ------------------------------------------------------------ Grundlagen

    private Clan clan() {
        return plugin.clans().getClan(player);
    }

    private boolean leader() {
        return plugin.clans().isLeader(player);
    }

    private String computeStamp() {
        Clan c = clan();
        return c == null ? "-" : c.name + "|" + c.c1 + "|" + c.c2;
    }

    private Component title() {
        Clan c = clan();
        String g1 = DEFAULT_ACCENT, g2 = "#ffe29a", name = "CLANS";
        if (c != null) {
            name = c.name.toUpperCase(Locale.ROOT);
            String h1 = ClanColor.toHex(c.c1);
            if (h1 != null) {
                g1 = h1;
                String h2 = ClanColor.toHex(c.c2);
                g2 = h2 != null ? h2 : h1;
            }
        }
        return Text.MM.deserialize("<gradient:" + g1 + ":" + g2 + "><bold>⚔ " + name + "</bold></gradient>");
    }

    private String accentHex() {
        Clan c = clan();
        if (c != null) {
            String h = ClanColor.toHex(c.c1);
            if (h != null) return h;
        }
        return DEFAULT_ACCENT;
    }

    private Material accentPane() {
        Clan c = clan();
        if (c != null) {
            Material m = ClanColor.paneOf(c.c1);
            if (m != null) return m;
        }
        return Material.ORANGE_STAINED_GLASS_PANE;
    }

    private void snd(Sound s, float pitch) {
        if (sounds) player.playSound(player.getLocation(), s, 0.6f, pitch);
    }

    private void click() {
        snd(Sound.UI_BUTTON_CLICK, 1.3f);
        flash = 4;
    }

    private void pick() {
        snd(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f);
    }

    private void result(boolean ok) {
        if (ok) snd(Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
        else snd(Sound.ENTITY_VILLAGER_NO, 1.0f);
        flash = 6;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void prompt(String hint, Consumer<String> cb) {
        later(() -> plugin.prompts().ask(player, hint, cb));
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int cs(int i) {
        return (1 + i / 7) * 9 + 1 + i % 7;
    }

    private void set(int slot, ItemStack it, Consumer<ClickType> act) {
        buttons.put(slot, new Button(it, act));
        inventory.setItem(slot, it);
    }

    private void go(View v) {
        view = v;
        page = 0;
        selected = null;
        render();
    }

    private void back(View v) {
        set(46, Items.make(Material.ARROW, "<yellow>« Zurück"), t -> {
            click();
            go(v);
        });
    }

    public void toast(String mini) {
        toast = mini;
        toastUntil = System.currentTimeMillis() + 6000L;
        render();
    }

    public void opened() {
        snd(Sound.BLOCK_ENDER_CHEST_OPEN, 1.5f);
    }

    // ------------------------------------------------------------ Klicks

    public void click(int slot, ClickType type) {
        Button b = buttons.get(slot);
        if (b == null || b.action() == null) return;
        if (!(type.isLeftClick() || type.isRightClick())) return;
        b.action().accept(type);
    }

    // ------------------------------------------------------------ Tick / Animation

    public void tick(int refreshEvery) {
        if (++sinceRefresh >= refreshEvery) {
            sinceRefresh = 0;
            if (!stamp.equals(computeStamp())) {
                // Clan/Farbe hat sich geändert -> Titel neu aufbauen
                plugin.gui().open(player, view);
                return;
            }
            render();
        }
        if (animated) {
            frame++;
            if (intro < RING.length) intro += 2;
        }
        paintFree();
        if (flash > 0) flash--;
    }

    private Set<Integer> outline() {
        Set<Integer> out = new HashSet<>();
        for (int s : marked.keySet()) {
            int r = s / 9, c = s % 9;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = r + dr, nc = c + dc;
                    if (nr < 0 || nr > 5 || nc < 0 || nc > 8) continue;
                    int n = nr * 9 + nc;
                    if (!buttons.containsKey(n)) out.add(n);
                }
            }
        }
        return out;
    }

    private static int dist(int idx, int head) {
        return (head - idx + RING.length) % RING.length;
    }

    /** Malt alle Slots ohne Button: Rahmen-Lichtstreifen, Auswahl-Umrandung, Hintergrund. */
    private void paintFree() {
        Set<Integer> outline = outline();
        Material accent = accentPane();
        int h1 = frame % RING.length;
        int h2 = (frame + RING.length / 2) % RING.length;
        boolean pulse = (frame / 2) % 2 == 0;

        for (int s = 0; s < 54; s++) {
            if (buttons.containsKey(s)) continue;
            Material m;
            int ri = RING_INDEX[s];
            if (outline.contains(s)) {
                m = (animated && !pulse) ? WHITE : accent;
            } else if (ri >= 0) {
                if (!animated) {
                    m = accent;
                } else if (ri > intro) {
                    m = BLACK;
                } else if (flash > 0) {
                    m = (flash % 2 == 0) ? WHITE : accent;
                } else {
                    int d = Math.min(dist(ri, h1), dist(ri, h2));
                    m = switch (d) {
                        case 0 -> WHITE;
                        case 1, 2 -> accent;
                        case 3 -> DARK;
                        default -> BLACK;
                    };
                }
            } else {
                m = BLACK;
            }
            if (shown[s] != m) {
                shown[s] = m;
                inventory.setItem(s, Items.pane(m));
            }
        }
    }

    // ------------------------------------------------------------ Rendern

    private void validate() {
        boolean hasClan = clan() != null;
        switch (view) {
            case HOME, INVITES -> {
                if (hasClan) view = View.OVERVIEW;
            }
            case OVERVIEW, MEMBERS, ALLIES, COLOR -> {
                if (!hasClan) view = View.HOME;
            }
            case ALLY_PICK -> {
                if (!hasClan) view = View.HOME;
                else if (!leader()) view = View.ALLIES;
            }
            case INVITE_PICK -> {
                if (!hasClan) view = View.HOME;
                else if (!leader()) view = View.OVERVIEW;
            }
            case CONFIRM -> {
                if (confirmAct == null) view = confirmBack;
            }
            default -> { }
        }
    }

    public void render() {
        validate();
        buttons.clear();
        marked.clear();
        inventory.clear();
        Arrays.fill(shown, null);

        switch (view) {
            case HOME -> buildHome();
            case INVITES -> buildInvites();
            case CLANS -> buildClans();
            case OVERVIEW -> buildOverview();
            case MEMBERS -> buildMembers();
            case ALLIES -> buildAllies();
            case ALLY_PICK -> buildAllyPick();
            case INVITE_PICK -> buildInvitePick();
            case COLOR -> buildColor();
            case CONFIRM -> buildConfirm();
        }
        buildTabs();
        if (toast != null) {
            if (System.currentTimeMillis() < toastUntil) set(52, Items.make(Material.OAK_SIGN, toast), null);
            else toast = null;
        }

        for (Map.Entry<Integer, String> e : marked.entrySet()) {
            Button b = buttons.get(e.getKey());
            if (b == null) continue;
            ItemStack it = Items.marked(b.item(), accentHex(), e.getValue());
            buttons.put(e.getKey(), new Button(it, b.action()));
            inventory.setItem(e.getKey(), it);
        }
        paintFree();
    }

    // ------------------------------------------------------------ Tabs

    private View tabOf(View v) {
        return switch (v) {
            case INVITE_PICK -> View.OVERVIEW;
            case ALLY_PICK -> View.ALLIES;
            case CONFIRM -> tabOf(confirmBack);
            default -> v;
        };
    }

    private void buildTabs() {
        boolean vip = plugin.clans().hasVip(player);
        List<Tab> tabs;
        if (clan() == null) {
            tabs = List.of(
                    new Tab(View.HOME, 3, Material.CAMPFIRE, "Start", "Gründe einen Clan oder tritt einem bei"),
                    new Tab(View.INVITES, 4, Material.WRITABLE_BOOK, "Einladungen", "Deine offenen Einladungen"),
                    new Tab(View.CLANS, 5, Material.BOOKSHELF, "Alle Clans", "Alle Clans des Servers"));
        } else {
            tabs = List.of(
                    new Tab(View.OVERVIEW, 2, Material.SHIELD, "Übersicht", "Statistiken & Aktionen"),
                    new Tab(View.MEMBERS, 3, Material.PLAYER_HEAD, "Mitglieder", "Mitglieder verwalten"),
                    new Tab(View.ALLIES, 4, Material.BELL, "Allianzen", "Verbündete Clans"),
                    new Tab(View.COLOR, 5, Material.PINK_DYE, "Clanfarbe", vip ? "Farbe & Farbverlauf" : "Nur für VIP-Booster"),
                    new Tab(View.CLANS, 6, Material.BOOKSHELF, "Alle Clans", "Alle Clans des Servers"));
        }
        View active = tabOf(view);
        for (Tab t : tabs) {
            boolean act = t.view() == active;
            ItemStack it = Items.make(t.icon(),
                    (act ? "<color:" + accentHex() + ">" : "<gray>") + "<bold>" + t.name(),
                    "<dark_gray>" + t.desc());
            set(t.slot(), it, c -> {
                click();
                go(t.view());
            });
            if (act) marked.put(t.slot(), null);
        }
    }

    // ------------------------------------------------------------ Listen-Helfer

    private <T> List<T> slice(List<T> all) {
        int pages = Math.max(1, (all.size() + 27) / 28);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        if (pages > 1) {
            set(49, Items.make(Material.PAPER, "<gray>Seite <white>" + (page + 1) + "<gray>/<white>" + pages), null);
            if (page > 0) {
                set(45, Items.make(Material.ARROW, "<yellow>« Vorherige Seite"), t -> {
                    click();
                    page--;
                    render();
                });
            }
            if (page < pages - 1) {
                set(53, Items.make(Material.ARROW, "<yellow>Nächste Seite »"), t -> {
                    click();
                    page++;
                    render();
                });
            }
        }
        return all.subList(page * 28, Math.min(all.size(), page * 28 + 28));
    }

    private void entry(int idx, ItemStack it, String id) {
        int slot = cs(idx);
        set(slot, it, t -> {
            pick();
            selected = id;
            render();
        });
        if (id.equals(selected)) marked.put(slot, "Ausgewählt");
    }

    private void emptyHint(String text) {
        set(22, Items.make(Material.BARRIER, "<red>" + text), null);
    }

    private ItemStack clanItem(Clan c, String... extra) {
        ClanManager m = plugin.clans();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Leiter: <gold>" + Text.esc(c.members.getOrDefault(c.owner, "?")));
        lore.add("<gray>Mitglieder: <yellow>" + c.members.size());
        lore.add("<gray>Kills: <green>" + c.kills + " <dark_gray>| <gray>Tode: <red>" + c.deaths
                + " <dark_gray>| <gray>K/D: <yellow>" + String.format(Locale.ROOT, "%.2f", c.kd()));
        lore.add("<gray>Allianzen: <aqua>" + c.allies.size());
        Clan mine = m.getClan(player);
        if (mine != null && mine.allies.contains(c.key)) lore.add("<aqua>✦ Verbündet mit deinem Clan");
        if (mine != null && mine == c) lore.add("<gold>★ Dein Clan");
        for (String e : extra) lore.add(e);
        return Items.named(ClanColor.bannerOf(c.c1), c.tag, lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------ HOME (ohne Clan)

    private void buildHome() {
        ClanManager m = plugin.clans();
        int min = plugin.getConfig().getInt("min-length", 3);
        int max = plugin.getConfig().getInt("max-length", 10);

        set(20, Items.make(Material.EMERALD_BLOCK, "<green><bold>Clan gründen",
                "<gray>Gründe deinen eigenen Clan.", "",
                "<dark_gray>Länge: <gray>" + min + "-" + max + " Zeichen",
                "<dark_gray>Erlaubt: <gray>A-Z, 0-9 und _", "",
                "<yellow>» Klicken, dann Namen in den Chat schreiben"), t -> {
            click();
            prompt("<gray>Schreibe den <gold>Clannamen <gray>in den Chat.", name -> {
                boolean ok = m.create(player, name);
                plugin.gui().open(player, ok ? View.OVERVIEW : View.HOME);
            });
        });

        int inv = m.invitesFor(player.getUniqueId()).size();
        set(22, Items.make(Material.WRITABLE_BOOK, "<yellow><bold>Einladungen",
                "<gray>Offene Einladungen: " + (inv > 0 ? "<green>" : "<red>") + inv, "",
                "<yellow>» Klicken zum Ansehen"), t -> {
            click();
            go(View.INVITES);
        });

        set(24, Items.make(Material.BOOKSHELF, "<aqua><bold>Alle Clans",
                "<gray>Clans auf dem Server: <white>" + m.all().size(), "",
                "<yellow>» Klicken zum Ansehen"), t -> {
            click();
            go(View.CLANS);
        });

        set(31, Items.make(Material.BOOK, "<gold><bold>Befehle",
                "<gray>Zeigt alle Clan-Befehle im Chat.", "",
                "<yellow>» Klicken"), t -> {
            click();
            later(() -> {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "clan help");
            });
        });
    }

    // ------------------------------------------------------------ INVITES

    private void buildInvites() {
        ClanManager m = plugin.clans();
        List<Clan> invs = new ArrayList<>(m.invitesFor(player.getUniqueId()));
        invs.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
        if (invs.isEmpty()) {
            emptyHint("Keine offenen Einladungen");
            return;
        }
        List<Clan> sub = slice(invs);
        for (int i = 0; i < sub.size(); i++) {
            entry(i, clanItem(sub.get(i)), sub.get(i).key);
        }
        Clan sel = m.byKey(selected);
        if (sel != null && sel.invites.contains(player.getUniqueId())) {
            set(47, Items.make(Material.RED_CONCRETE, "<red><bold>Ablehnen", "<gray>Einladung von <gold>" + sel.name + " <gray>ablehnen."), t -> {
                sel.invites.remove(player.getUniqueId());
                result(true);
                selected = null;
                render();
            });
            set(51, Items.make(Material.LIME_CONCRETE, "<green><bold>Annehmen", "<gray>Dem Clan <gold>" + sel.name + " <gray>beitreten."), t -> {
                boolean ok = m.accept(player, sel.key);
                result(ok);
                later(() -> plugin.gui().open(player, ok ? View.OVERVIEW : View.INVITES));
            });
        }
    }

    // ------------------------------------------------------------ CLANS

    private void buildClans() {
        ClanManager m = plugin.clans();
        List<Clan> all = new ArrayList<>(m.all());
        all.sort(Comparator.<Clan>comparingInt(c -> -c.members.size()).thenComparing(c -> c.name.toLowerCase(Locale.ROOT)));
        if (all.isEmpty()) {
            emptyHint("Es gibt noch keine Clans");
            return;
        }
        List<Clan> sub = slice(all);
        for (int i = 0; i < sub.size(); i++) {
            entry(i, clanItem(sub.get(i)), sub.get(i).key);
        }
        Clan sel = m.byKey(selected);
        if (sel != null) {
            set(47, Items.make(Material.BOOK, "<yellow><bold>Info im Chat", "<gray>Zeigt <gold>" + sel.name + " <gray>im Chat an."), t -> {
                click();
                later(() -> {
                    player.closeInventory();
                    m.sendInfo(player, sel);
                });
            });
            Clan mine = m.getClan(player);
            if (mine != null && mine != sel && leader() && !mine.allies.contains(sel.key)) {
                set(51, Items.make(Material.BELL, "<aqua><bold>Allianz anfragen", "<gray>Schickt <gold>" + sel.name + " <gray>eine Anfrage."), t -> {
                    result(m.allyInvite(player, sel.key));
                });
            }
        }
    }

    // ------------------------------------------------------------ OVERVIEW

    private void buildOverview() {
        ClanManager m = plugin.clans();
        Clan c = clan();
        if (c == null) return;
        boolean lead = leader();

        set(13, Items.named(ClanColor.bannerOf(c.c1), c.tag,
                "<gray>Leiter: <gold>" + Text.esc(c.members.getOrDefault(c.owner, "?")),
                "<gray>Mitglieder: <yellow>" + c.members.size(),
                "<gray>Allianzen: <aqua>" + c.allies.size(),
                "", lead ? "<gold>★ Du bist Clanleiter" : "<gray>Du bist Mitglied"), null);

        set(20, Items.make(Material.DIAMOND_SWORD, "<green><bold>Kills", "<white>" + c.kills), null);
        set(21, Items.make(Material.SKELETON_SKULL, "<red><bold>Tode", "<white>" + c.deaths), null);
        set(22, Items.make(Material.EXPERIENCE_BOTTLE, "<yellow><bold>K/D", "<white>" + String.format(Locale.ROOT, "%.2f", c.kd())), null);
        set(23, Items.make(Material.DIAMOND_PICKAXE, "<aqua><bold>Abgebaute Blöcke", "<white>" + c.blocks), null);
        set(24, Items.make(Material.PLAYER_HEAD, "<gold><bold>Mitglieder", "<white>" + c.members.size(), "", "<yellow>» Klicken für die Liste"), t -> {
            click();
            go(View.MEMBERS);
        });

        set(29, Items.make(Material.WRITABLE_BOOK, "<aqua><bold>Clan-Chat",
                "<gray>Sende eine Nachricht an alle", "<gray>Clan-Mitglieder.", "", "<yellow>» Klicken, dann in den Chat schreiben"), t -> {
            click();
            prompt("<gray>Schreibe deine <aqua>Clan-Nachricht <gray>in den Chat.", msg -> {
                m.chat(player, msg);
                plugin.gui().open(player, View.OVERVIEW);
            });
        });

        if (lead) {
            set(31, Items.make(Material.EMERALD, "<green><bold>Spieler einladen",
                    "<gray>Lade einen Spieler ohne Clan ein.", "", "<yellow>» Klicken"), t -> {
                click();
                go(View.INVITE_PICK);
            });
            set(33, Items.make(Material.TNT, "<red><bold>Clan auflösen",
                    "<gray>Löscht den Clan unwiderruflich.", "", "<yellow>» Klicken"), t -> {
                click();
                confirm("Willst du den Clan <gold>" + c.name + " <gray>wirklich auflösen?", View.OVERVIEW, View.HOME,
                        () -> m.disband(player));
            });
        } else {
            set(31, Items.make(Material.GRAY_DYE, "<dark_gray>Spieler einladen", "<red>Nur der Clanleiter kann einladen."), null);
            set(33, Items.make(Material.OAK_DOOR, "<red><bold>Clan verlassen",
                    "<gray>Du verlässt den Clan <gold>" + c.name + "<gray>.", "", "<yellow>» Klicken"), t -> {
                click();
                confirm("Willst du den Clan <gold>" + c.name + " <gray>wirklich verlassen?", View.OVERVIEW, View.HOME,
                        () -> m.leave(player));
            });
        }
    }

    // ------------------------------------------------------------ MEMBERS

    private void buildMembers() {
        ClanManager m = plugin.clans();
        Clan c = clan();
        if (c == null) return;
        List<UUID> ids = new ArrayList<>(c.members.keySet());
        ids.sort(Comparator.<UUID>comparingInt(u -> u.equals(c.owner) ? 0 : (Bukkit.getPlayer(u) != null ? 1 : 2))
                .thenComparing(u -> c.members.get(u).toLowerCase(Locale.ROOT)));
        List<UUID> sub = slice(ids);
        for (int i = 0; i < sub.size(); i++) {
            UUID u = sub.get(i);
            boolean on = Bukkit.getPlayer(u) != null;
            boolean owner = u.equals(c.owner);
            String name = c.members.get(u);
            ItemStack head = Items.head(Bukkit.getOfflinePlayer(u),
                    (on ? "<green>" : "<gray>") + Text.esc(name),
                    owner ? "<gold>★ Clanleiter" : "<gray>Mitglied",
                    on ? "<green>● Online" : "<dark_gray>● Offline");
            entry(i, head, u.toString());
        }
        UUID sel = uuid(selected);
        if (leader() && sel != null && c.members.containsKey(sel) && !sel.equals(player.getUniqueId())) {
            String sn = Text.esc(c.members.get(sel));
            set(47, Items.make(Material.IRON_BOOTS, "<red><bold>Kicken", "<gray>Entfernt <yellow>" + sn + " <gray>aus dem Clan."), t -> {
                click();
                confirm("Willst du <yellow>" + sn + " <gray>wirklich kicken?", View.MEMBERS, View.MEMBERS,
                        () -> m.kick(player, sel));
            });
            set(51, Items.make(Material.GOLDEN_HELMET, "<gold><bold>Zum Leiter machen", "<gray>Übergibt die Leitung an <yellow>" + sn + "<gray>."), t -> {
                click();
                confirm("Willst du die Leitung wirklich an <yellow>" + sn + " <gray>übergeben?", View.MEMBERS, View.MEMBERS,
                        () -> m.leader(player, sel));
            });
        }
    }

    // ------------------------------------------------------------ ALLIES

    private record AllyEntry(Clan clan, boolean ally) {}

    private void buildAllies() {
        ClanManager m = plugin.clans();
        Clan me = clan();
        if (me == null) return;
        boolean lead = leader();

        List<AllyEntry> list = new ArrayList<>();
        for (String k : me.allyRequests) {
            Clan o = m.byKey(k);
            if (o != null) list.add(new AllyEntry(o, false));
        }
        for (String k : me.allies) {
            Clan o = m.byKey(k);
            if (o != null) list.add(new AllyEntry(o, true));
        }

        if (list.isEmpty()) emptyHint("Keine Allianzen oder Anfragen");
        List<AllyEntry> sub = slice(list);
        for (int i = 0; i < sub.size(); i++) {
            AllyEntry e = sub.get(i);
            ItemStack it = e.ally()
                    ? clanItem(e.clan())
                    : clanItem(e.clan(), "", "<yellow>✉ Allianzanfrage an euch");
            entry(i, it, e.clan().key);
        }

        if (lead) {
            set(47, Items.make(Material.BELL, "<aqua><bold>Allianz anfragen", "<gray>Wähle einen Clan aus.", "", "<yellow>» Klicken"), t -> {
                click();
                go(View.ALLY_PICK);
            });
            Clan sel = m.byKey(selected);
            if (sel != null) {
                if (me.allies.contains(sel.key)) {
                    set(51, Items.make(Material.RED_CONCRETE, "<red><bold>Allianz beenden", "<gray>Beendet die Allianz mit <gold>" + sel.name + "<gray>."), t -> {
                        click();
                        confirm("Allianz mit <gold>" + sel.name + " <gray>wirklich beenden?", View.ALLIES, View.ALLIES,
                                () -> m.allyRemove(player, sel.key));
                    });
                } else if (me.allyRequests.contains(sel.key)) {
                    set(51, Items.make(Material.LIME_CONCRETE, "<green><bold>Annehmen", "<gray>Allianz mit <gold>" + sel.name + " <gray>schließen."), t -> {
                        result(m.allyAccept(player, sel.key));
                        selected = null;
                        render();
                    });
                }
            }
        }
    }

    private void buildAllyPick() {
        ClanManager m = plugin.clans();
        Clan me = clan();
        if (me == null) return;
        List<Clan> list = new ArrayList<>();
        for (Clan c : m.all()) {
            if (c != me && !me.allies.contains(c.key)) list.add(c);
        }
        list.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
        back(View.ALLIES);
        if (list.isEmpty()) {
            emptyHint("Keine Clans verfügbar");
            return;
        }
        List<Clan> sub = slice(list);
        for (int i = 0; i < sub.size(); i++) {
            Clan c = sub.get(i);
            ItemStack it = c.allyRequests.contains(me.key)
                    ? clanItem(c, "", "<yellow>✉ Anfrage bereits gesendet")
                    : clanItem(c);
            entry(i, it, c.key);
        }
        Clan sel = m.byKey(selected);
        if (sel != null) {
            set(51, Items.make(Material.LIME_CONCRETE, "<green><bold>Anfrage senden", "<gray>An <gold>" + sel.name + "<gray>."), t -> {
                result(m.allyInvite(player, sel.key));
            });
        }
    }

    // ------------------------------------------------------------ INVITE_PICK

    private void buildInvitePick() {
        ClanManager m = plugin.clans();
        back(View.OVERVIEW);
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && m.getClan(p) == null) list.add(p);
        }
        list.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        if (list.isEmpty()) {
            emptyHint("Keine Spieler ohne Clan online");
            return;
        }
        List<Player> sub = slice(list);
        Clan me = clan();
        for (int i = 0; i < sub.size(); i++) {
            Player p = sub.get(i);
            boolean invited = me != null && me.invites.contains(p.getUniqueId());
            ItemStack head = Items.head(p, "<green>" + Text.esc(p.getName()),
                    "<gray>Hat keinen Clan",
                    invited ? "<yellow>✉ Bereits eingeladen" : "<dark_gray>Noch nicht eingeladen");
            entry(i, head, p.getUniqueId().toString());
        }
        UUID sel = uuid(selected);
        Player target = sel == null ? null : Bukkit.getPlayer(sel);
        if (target != null) {
            set(51, Items.make(Material.LIME_CONCRETE, "<green><bold>Einladen", "<gray>Lädt <yellow>" + Text.esc(target.getName()) + " <gray>ein."), t -> {
                result(m.invite(player, target));
                render();
            });
        }
    }

    // ------------------------------------------------------------ COLOR

    private void buildColor() {
        ClanManager m = plugin.clans();
        Clan c = clan();
        if (c == null) return;
        boolean vip = m.hasVip(player);
        boolean needsLeader = plugin.getConfig().getBoolean("color-needs-leader", true);
        boolean can = vip && (!needsLeader || leader());

        if (!can) {
            String why = !vip ? "Die Clanfarbe ist nur für <gold>VIP-Booster <gray>verfügbar."
                    : "Nur der <gold>Clanleiter <gray>kann die Farbe ändern.";
            set(22, Items.make(Material.BARRIER, "<red><bold>Gesperrt", "<gray>" + why), null);
            set(31, Items.named(Material.NAME_TAG, c.tag, "<gray>Aktueller Clan-Tag"), null);
            return;
        }

        ClanColor[] cols = ClanColor.values();
        ClanColor sel1 = ClanColor.parse(c1);
        ClanColor sel2 = ClanColor.parse(c2);
        for (int i = 0; i < cols.length; i++) {
            ClanColor col = cols[i];
            int slot = (1 + i / 5) * 9 + 2 + (i % 5);
            ItemStack it = Items.make(col.item(), "<color:" + col.hex() + "><bold>" + col.german(),
                    "<dark_gray>" + col.hex(), "",
                    "<gray>Linksklick: <white>Farbe 1", "<gray>Rechtsklick: <white>Farbe 2 (Verlauf)");
            set(slot, it, t -> {
                pick();
                if (t.isRightClick()) c2 = col.id();
                else c1 = col.id();
                render();
            });
            if (col == sel1 && col == sel2) marked.put(slot, "Farbe 1 + Farbe 2");
            else if (col == sel1) marked.put(slot, "Farbe 1");
            else if (col == sel2) marked.put(slot, "Farbe 2");
        }

        set(38, Items.make(Material.BARRIER, "<red><bold>Zurücksetzen", "<gray>Standardfarbe wiederherstellen."), t -> {
            click();
            boolean ok = m.setColor(player, "reset", null);
            if (ok) {
                c1 = "";
                c2 = "";
            }
            result(ok);
            render();
        });
        set(39, Items.make(Material.PAPER, "<aqua><bold>Eigene Farbe (Hex)",
                "<gray>Gib im Chat Farben ein, z.B.:", "<white>#ff8800 #ffee00", "<white>red blue", "", "<yellow>» Klicken"), t -> {
            click();
            prompt("<gray>Schreibe <aqua>Farbe 1 <gray>und optional <aqua>Farbe 2 <gray>(z.B. <white>#ff8800 #ffee00<gray>).", text -> {
                String[] parts = text.trim().split("\\s+");
                m.setColor(player, parts[0], parts.length > 1 ? parts[1] : null);
                plugin.gui().open(player, View.COLOR);
            });
        });
        set(40, Items.named(Material.NAME_TAG, m.buildTag(c.name, c1, c2),
                "<gray>Vorschau deines Clan-Tags", "",
                "<gray>Farbe 1: <white>" + (c1.isEmpty() ? "Standard" : Text.esc(c1)),
                "<gray>Farbe 2: <white>" + (c2.isEmpty() ? "–" : Text.esc(c2))), null);
        if (!c2.isEmpty()) {
            set(41, Items.make(Material.GRAY_DYE, "<gray><bold>Farbe 2 entfernen", "<gray>Nur eine Farbe, kein Verlauf."), t -> {
                click();
                c2 = "";
                render();
            });
        }
        set(42, Items.make(Material.EMERALD, "<green><bold>Übernehmen", "<gray>Speichert die gewählte Farbe.", "", "<yellow>» Klicken"), t -> {
            if (c1.isEmpty()) {
                plugin.err(player, "Wähle zuerst Farbe 1 aus.");
                result(false);
                return;
            }
            result(m.setColor(player, c1, c2.isEmpty() ? null : c2));
            render();
        });
    }

    // ------------------------------------------------------------ CONFIRM

    private void confirm(String text, View back, View next, Supplier<Boolean> act) {
        confirmText = text;
        confirmBack = back;
        confirmNext = next;
        confirmAct = act;
        view = View.CONFIRM;
        page = 0;
        selected = null;
        render();
    }

    private void buildConfirm() {
        set(22, Items.make(Material.PAPER, "<gold><bold>Bestätigung", "<gray>" + confirmText), null);
        set(30, Items.make(Material.LIME_CONCRETE, "<green><bold>✔ Ja", "<gray>Bestätigen"), t -> {
            Supplier<Boolean> act = confirmAct;
            confirmAct = null;
            boolean ok = act != null && act.get();
            result(ok);
            View next = ok ? confirmNext : confirmBack;
            later(() -> plugin.gui().open(player, next));
        });
        set(32, Items.make(Material.RED_CONCRETE, "<red><bold>✘ Nein", "<gray>Abbrechen"), t -> {
            click();
            confirmAct = null;
            go(confirmBack);
        });
    }
}
