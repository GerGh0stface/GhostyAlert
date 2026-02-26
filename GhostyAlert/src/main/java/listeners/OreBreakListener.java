package listeners;

import managers.IPlugin;
import managers.OreTracker.OreEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;

public class OreBreakListener implements Listener {

    private final IPlugin plugin;

    public OreBreakListener(IPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.hasPermission("ghostyalert.bypass")) return;

        Material broken = event.getBlock().getType();
        if (!plugin.getOreTracker().isTracked(broken)) return;

        int count = plugin.getOreTracker().recordAndCount(
                player.getUniqueId(), broken, event.getBlock().getLocation());

        if (count >= plugin.getOreTracker().getAlertThreshold()) {
            List<OreEvent> recent = plugin.getOreTracker().getRecentEvents(player.getUniqueId());
            Material mostMined = plugin.getAlertManager().getMostMinedOre(recent);
            plugin.getAlertManager().fireAlert(player, count, mostMined, event.getBlock().getLocation());
        }
    }
}
