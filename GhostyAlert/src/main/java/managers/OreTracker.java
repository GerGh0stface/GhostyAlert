package managers;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OreTracker {

    private final IPlugin plugin;

    private final Map<UUID, List<OreEvent>> oreEvents = new ConcurrentHashMap<>();
    private final Map<UUID, List<AlertSnapshot>> alertHistory = new ConcurrentHashMap<>();

    private long timeWindowMillis;
    private int alertThreshold;
    private int maxHistory;
    private Set<Material> trackedOres;

    public OreTracker(IPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        timeWindowMillis = plugin.getConfig().getLong("time-window", 60) * 1000L;
        alertThreshold = plugin.getConfig().getInt("alert-threshold", 30);
        maxHistory = plugin.getConfig().getInt("max-history-per-player", 50);
        trackedOres = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("tracked-ores")) {
            try {
                trackedOres.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material in config: " + name);
            }
        }
    }

    public boolean isTracked(Material material) {
        return trackedOres.contains(material);
    }

    public int recordAndCount(UUID playerId, Material ore, Location location) {
        long now = System.currentTimeMillis();
        oreEvents.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new OreEvent(ore, location.clone(), now));

        long windowStart = now - timeWindowMillis;
        List<OreEvent> events = oreEvents.get(playerId);
        synchronized (events) {
            return (int) events.stream().filter(e -> e.timestamp >= windowStart).count();
        }
    }

    public List<OreEvent> getRecentEvents(UUID playerId) {
        long windowStart = System.currentTimeMillis() - timeWindowMillis;
        List<OreEvent> events = oreEvents.getOrDefault(playerId, Collections.emptyList());
        synchronized (events) {
            List<OreEvent> result = new ArrayList<>();
            for (OreEvent e : events) {
                if (e.timestamp >= windowStart) result.add(e);
            }
            return result;
        }
    }

    public void addAlertSnapshot(UUID playerId, AlertSnapshot snapshot) {
        alertHistory.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>())).add(snapshot);
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
        long cutoff = System.currentTimeMillis() - (timeWindowMillis * 2);
        oreEvents.entrySet().removeIf(entry -> {
            List<OreEvent> events = entry.getValue();
            synchronized (events) {
                return events.stream().allMatch(e -> e.timestamp < cutoff);
            }
        });
    }

    public int getAlertThreshold() { return alertThreshold; }
    public long getTimeWindowSeconds() { return timeWindowMillis / 1000; }

    public static class OreEvent {
        public final Material ore;
        public final Location location;
        public final long timestamp;

        public OreEvent(Material ore, Location location, long timestamp) {
            this.ore = ore;
            this.location = location;
            this.timestamp = timestamp;
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
            this.playerName = playerName;
            this.playerId = playerId;
            this.mostMinedOre = mostMinedOre;
            this.count = count;
            this.location = location.clone();
            this.timestamp = timestamp;
            this.worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        }
    }
}
