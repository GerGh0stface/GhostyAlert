package gui;

import managers.IPlugin;
import managers.OreTracker.AlertSnapshot;
import utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.*;

public class AlertGUI implements Listener {

    private final IPlugin plugin;

    // Stores open inventories per player UUID → {inventory, alert list}
    private final Map<UUID, Inventory> openInventoryMap = new HashMap<>();
    private final Map<UUID, List<AlertSnapshot>> alertMap = new HashMap<>();

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    private static final ItemFlag[] ALL_FLAGS = {
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_UNBREAKABLE,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_PLACED_ON,
            ItemFlag.HIDE_ADDITIONAL_TOOLTIP
    };

    public AlertGUI(IPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, (JavaPlugin) plugin);
    }

    @SuppressWarnings("deprecation")
    public void open(Player opener) {
        Map<UUID, List<AlertSnapshot>> allHistory = plugin.getOreTracker().getAllAlertHistory();

        List<AlertSnapshot> latestAlerts = new ArrayList<>();
        for (Map.Entry<UUID, List<AlertSnapshot>> entry : allHistory.entrySet()) {
            List<AlertSnapshot> snapshots = entry.getValue();
            if (!snapshots.isEmpty()) latestAlerts.add(snapshots.get(snapshots.size() - 1));
        }
        latestAlerts.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        // createInventory with String title is deprecated in 1.21 but still functional
        Inventory inv = Bukkit.createInventory(null, SIZE, plugin.getLang().get("gui.title"));
        fillBorder(inv);

        int[] slots = getContentSlots();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm:ss");

        for (int i = 0; i < Math.min(latestAlerts.size(), slots.length); i++) {
            inv.setItem(slots[i], createPlayerHead(latestAlerts.get(i), sdf));
        }

        if (latestAlerts.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER,
                    plugin.getLang().get("gui.no-alerts-name"),
                    Collections.singletonList(plugin.getLang().get("gui.no-alerts-lore"))));
        }

        inv.setItem(4, createItem(Material.BELL,
                plugin.getLang().get("gui.info-title"),
                Arrays.asList(
                        plugin.getLang().get("gui.info-threshold", "{threshold}",
                                String.valueOf(plugin.getOreTracker().getAlertThreshold())),
                        plugin.getLang().get("gui.info-timewindow", "{seconds}",
                                String.valueOf(plugin.getOreTracker().getTimeWindowSeconds())),
                        plugin.getLang().get("gui.info-total", "{count}",
                                String.valueOf(latestAlerts.size())),
                        "",
                        plugin.getLang().get("gui.info-click-hint"),
                        plugin.getLang().get("gui.info-click-hint2")
                )));

        // Store inventory instance — no title comparison needed
        openInventoryMap.put(opener.getUniqueId(), inv);
        alertMap.put(opener.getUniqueId(), latestAlerts);
        opener.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;

        // Compare inventory instance, not title
        Inventory stored = openInventoryMap.get(clicker.getUniqueId());
        if (stored == null || !stored.equals(event.getInventory())) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        List<AlertSnapshot> alerts = alertMap.get(clicker.getUniqueId());
        if (alerts == null) return;

        int[] slots = getContentSlots();
        int index = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == event.getSlot()) { index = i; break; }
        }
        if (index < 0 || index >= alerts.size()) return;

        AlertSnapshot snap = alerts.get(index);
        clicker.closeInventory();
        Bukkit.getScheduler().runTask((JavaPlugin) plugin, () -> {
            clicker.teleport(snap.location);
            clicker.sendMessage(plugin.getLang().getPrefix()
                    + plugin.getLang().get("messages.teleported", "{player}", snap.playerName));
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        Inventory stored = openInventoryMap.get(clicker.getUniqueId());
        if (stored != null && stored.equals(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack createPlayerHead(AlertSnapshot snap, SimpleDateFormat sdf) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(snap.playerId));
        meta.setDisplayName(ColorUtils.color("&c&l" + snap.playerName));

        String pos = String.format("X: %d, Y: %d, Z: %d",
                snap.location.getBlockX(), snap.location.getBlockY(), snap.location.getBlockZ());

        meta.setLore(Arrays.asList(
                plugin.getLang().get("gui.player-ore-type", "{ore}", snap.mostMinedOre.name().replace("_", " ")),
                plugin.getLang().get("gui.player-count", "{count}", String.valueOf(snap.count)),
                plugin.getLang().get("gui.player-world", "{world}", snap.worldName),
                plugin.getLang().get("gui.player-position", "{pos}", pos),
                plugin.getLang().get("gui.player-time", "{time}", sdf.format(new Date(snap.timestamp))),
                "",
                plugin.getLang().get("gui.player-click")
        ));

        meta.addItemFlags(ALL_FLAGS);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        skull.setItemMeta(meta);
        return skull;
    }

    @SuppressWarnings("deprecation")
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ALL_FLAGS);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack pane = createItem(Material.BLACK_STAINED_GLASS_PANE,
                ColorUtils.color("&r"), Collections.emptyList());
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        for (int i = 1; i < ROWS - 1; i++) {
            inv.setItem(i * 9, pane);
            inv.setItem(i * 9 + 8, pane);
        }
    }

    private int[] getContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
