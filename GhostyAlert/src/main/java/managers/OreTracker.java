package managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OreTracker {

    private final IPlugin plugin;

    // Player UUID -> ore -> events
    private final Map<UUID, Map<Material, List<OreEvent>>> oreEvents = new ConcurrentHashMap<>();
    private final Map<UUID, List<AlertSnapshot>> alertHistory = new ConcurrentHashMap<>();
    private final Map<Material, OreConfig> oreConfigs = new HashMap<>();

    public OreTracker(IPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        oreConfigs.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tracked-ores");
        if (section == null) {
            plugin.getLogger().warning("No 'tracked-ores' section found in config!");
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                ConfigurationSection ore = section.getConfigurationSection(key);
                if (ore == null) continue;
                oreConfigs.put(mat, new OreConfig(
                        ore.getLong("time-window", 60) * 1000L,
                        ore.getInt("alert-threshold", 30),
                        ore.getLong("alert-cooldown", 30) * 1000L,
                        ore.getInt("max-history", 50)
                ));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material in config: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + oreConfigs.size() + " tracked ore(s).");
    }

    public boolean isTracked(Material material) {
        return oreConfigs.containsKey(material);
    }

    public OreConfig getOreConfig(Material material) {
        return oreConfigs.get(material);
    }

    /** Records a break event and returns the count within that ore's time window. */
    public int recordAndCount(UUID playerId, Material ore, Location location) {
        OreConfig cfg = oreConfigs.get(ore);
        if (cfg == null) return 0;

        long now = System.currentTimeMillis();
        oreEvents
            .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(ore, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new OreEvent(ore, location.clone(), now));

        long windowStart = now - cfg.timeWindowMillis;
        List<OreEvent> events = oreEvents.get(playerId).get(ore);
        synchronized (events) {
            return (int) events.stream().filter(e -> e.timestamp >= windowStart).count();
        }
    }

    /** Returns recent events for a specific ore type within its time window. */
    public List<OreEvent> getRecentEvents(UUID playerId, Material ore) {
        OreConfig cfg = oreConfigs.get(ore);
        if (cfg == null) return Collections.emptyList();
        Map<Material, List<OreEvent>> playerMap = oreEvents.get(playerId);
        if (playerMap == null) return Collections.emptyList();

        long windowStart = System.currentTimeMillis() - cfg.timeWindowMillis;
        List<OreEvent> events = playerMap.getOrDefault(ore, Collections.emptyList());
        synchronized (events) {
            List<OreEvent> result = new ArrayList<>();
            for (OreEvent e : events) {
                if (e.timestamp >= windowStart) result.add(e);
            }
            return result;
        }
    }

    public void addAlertSnapshot(UUID playerId, AlertSnapshot snapshot) {
        OreConfig cfg = oreConfigs.get(snapshot.mostMinedOre);
        int maxHistory = cfg != null ? cfg.maxHistory : 50;

        alertHistory.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(snapshot);
        List<AlertSnapshot> history = alertHistory.get(playerId);
        synchronized (history) {
            while (history.size() > maxHistory) history.remove(0);
        }
    }

    public Map<UUID, List<AlertSnapshot>> getAllAlertHistory() { return alertHistory; }
    public List<AlertSnapshot> getAlertHistory(UUID playerId) {
        return alertHistory.getOrDefault(playerId, Collections.emptyList());
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        oreEvents.forEach((playerId, oreMap) ->
            oreMap.forEach((ore, events) -> {
                OreConfig cfg = oreConfigs.get(ore);
                if (cfg == null) return;
                long cutoff = now - (cfg.timeWindowMillis * 2);
                synchronized (events) { events.removeIf(e -> e.timestamp < cutoff); }
            })
        );
    }

    public int getTrackedOreCount() { return oreConfigs.size(); }

    // ---- Inner classes ----

    public static class OreConfig {
        public final long timeWindowMillis;
        public final int alertThreshold;
        public final long alertCooldownMillis;
        public final int maxHistory;

        public OreConfig(long timeWindowMillis, int alertThreshold, long alertCooldownMillis, int maxHistory) {
            this.timeWindowMillis    = timeWindowMillis;
            this.alertThreshold      = alertThreshold;
            this.alertCooldownMillis = alertCooldownMillis;
            this.maxHistory          = maxHistory;
        }
    }

    public static class OreEvent {
        public final Material ore;
        public final Location location;
        public final long timestamp;

        public OreEvent(Material ore, Location location, long timestamp) {
            this.ore = ore; this.location = location; this.timestamp = timestamp;
        }
    }

    public static class AlertSnapshot {
        public final String playerName;
        public final UUID playerId;
        public final Material mostMinedOre;
        public final int count;
        public final Location location;
        public final long timestamp;
        public final String worldName;

        public AlertSnapshot(String playerName, UUID playerId, Material mostMinedOre,
                             int count, Location location, long timestamp) {
            this.playerName   = playerName;
            this.playerId     = playerId;
            this.mostMinedOre = mostMinedOre;
            this.count        = count;
            this.location     = location.clone();
            this.timestamp    = timestamp;
            this.worldName    = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        }
    }
}
