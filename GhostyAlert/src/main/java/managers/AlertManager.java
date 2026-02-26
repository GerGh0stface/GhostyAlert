package managers;

import managers.OreTracker.AlertSnapshot;
import managers.OreTracker.OreEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {

    private final IPlugin plugin;
    private final JavaPlugin javaPlugin;
    private final Map<UUID, Long> lastAlertTime = new ConcurrentHashMap<>();

    private long alertCooldownMillis;
    private boolean discordEnabled;
    private String webhookUrl;
    private int embedColor;
    private String mentionRole;

    public AlertManager(IPlugin plugin) {
        this.plugin = plugin;
        this.javaPlugin = (JavaPlugin) plugin;
        reload();
    }

    public void reload() {
        alertCooldownMillis = plugin.getConfig().getLong("alert-cooldown", 30) * 1000L;
        discordEnabled = plugin.getConfig().getBoolean("discord.enabled", false);
        webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        embedColor = plugin.getConfig().getInt("discord.embed-color", 16753920);
        mentionRole = plugin.getConfig().getString("discord.mention-role", "");
    }

    public void fireAlert(Player player, int count, Material mostMinedOre, Location location) {
        long now = System.currentTimeMillis();
        Long last = lastAlertTime.get(player.getUniqueId());
        if (last != null && (now - last) < alertCooldownMillis) return;

        lastAlertTime.put(player.getUniqueId(), now);

        plugin.getOreTracker().addAlertSnapshot(player.getUniqueId(),
                new AlertSnapshot(player.getName(), player.getUniqueId(), mostMinedOre, count, location, now));

        String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        long seconds = plugin.getOreTracker().getTimeWindowSeconds();

        String msg = plugin.getLang().get("messages.alert-chat",
                "{player}", player.getName(),
                "{count}", String.valueOf(count),
                "{seconds}", String.valueOf(seconds),
                "{ore}", formatOreName(mostMinedOre),
                "{world}", worldName);

        String prefix = plugin.getLang().getPrefix();

        Bukkit.getScheduler().runTask(javaPlugin, () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("ghostyalert.use")) staff.sendMessage(prefix + msg);
            }
            Bukkit.getConsoleSender().sendMessage(prefix + msg);
        });

        if (discordEnabled && webhookUrl != null && !webhookUrl.isEmpty() && !webhookUrl.contains("YOUR_WEBHOOK")) {
            final String pName = player.getName();
            final int fCount = count;
            final Material fOre = mostMinedOre;
            final Location fLoc = location.clone();
            final String fWorld = worldName;
            final long fSeconds = seconds;
            final long fNow = now;
            Bukkit.getScheduler().runTaskAsynchronously(javaPlugin,
                    () -> sendDiscordWebhook(pName, fCount, fOre, fLoc, fWorld, fSeconds, fNow));
        }
    }

    // Package-private to avoid "never used locally" false positive from IDEs
    void sendDiscordWebhook(String playerName, int count, Material ore,
                             Location loc, String world, long seconds, long timestamp) {
        try {
            String time = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(timestamp));
            String mention = (mentionRole != null && !mentionRole.isEmpty()) ? mentionRole + " " : "";
            String pos = String.format("X: %d, Y: %d, Z: %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            String countValue = plugin.getLang().get("discord.embed-count-value",
                    "{count}", String.valueOf(count), "{seconds}", String.valueOf(seconds));

            String json = "{\"content\":\"" + escapeJson(mention) + "\","
                    + "\"embeds\":[{\"title\":\"" + escapeJson(plugin.getLang().get("discord.embed-title")) + "\","
                    + "\"color\":" + embedColor + ","
                    + "\"fields\":["
                    + field(plugin.getLang().get("discord.embed-player"), playerName)
                    + field(plugin.getLang().get("discord.embed-ore"), formatOreName(ore))
                    + field(plugin.getLang().get("discord.embed-count"), countValue)
                    + field(plugin.getLang().get("discord.embed-position"), pos)
                    + field(plugin.getLang().get("discord.embed-world"), world)
                    + fieldLast(plugin.getLang().get("discord.embed-time"), time)
                    + "],\"footer\":{\"text\":\"" + escapeJson(plugin.getLang().get("discord.embed-footer")) + "\"}}]}";

            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "GhostyAlert/1.0.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200 && code != 204)
                plugin.getLogger().warning("Discord Webhook failed! HTTP " + code);
            conn.disconnect();

        } catch (Exception e) {
            plugin.getLogger().warning("Error sending Discord Webhook: " + e.getMessage());
        }
    }

    private String field(String name, String value) {
        return "{\"name\":\"" + escapeJson(name) + "\",\"value\":\"" + escapeJson(value) + "\",\"inline\":true},";
    }

    private String fieldLast(String name, String value) {
        return "{\"name\":\"" + escapeJson(name) + "\",\"value\":\"" + escapeJson(value) + "\",\"inline\":true}";
    }

    public Material getMostMinedOre(List<OreEvent> events) {
        Map<Material, Integer> counts = new HashMap<>();
        for (OreEvent e : events) counts.merge(e.ore, 1, Integer::sum);
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Material.DIAMOND_ORE);
    }

    private String formatOreName(Material ore) {
        return ore.name().replace("_", " ");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
