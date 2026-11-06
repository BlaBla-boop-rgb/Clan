# Clans (Paper 1.21.11)

Umsetzung des Skript-Clansystems als Paper-Plugin mit eigener GUI.

## Bauen
Java 21 + Maven:

    mvn clean package

Die fertige Datei liegt danach in `target/Clans-1.0.0.jar` -> in den `plugins`-Ordner legen.
Optional: PlaceholderAPI (`%clan_name%`, `%clan_tab%`, `%clan_tag%`, zusätzlich `kills`, `deaths`, `blocks`, `members`, `leader`).

## Bedienung
`/clan` öffnet die GUI. Alle Befehle aus dem Skript funktionieren weiterhin (`/clan help`).
`/clan reload` (Permission `clans.admin`) lädt die config.yml neu.

## GUI-Animationen (config.yml -> gui)
- `animation`: Lichtstreifen im Rahmen, Aufbau beim Öffnen, Aufblitzen bei Klicks, pulsierende Umrandung der Auswahl
- `animation-speed`: Ticks pro Frame (2 = flüssig, 4 = sparsamer)
- `sounds`: Klick-/Auswahl-Sounds
