import commands.GhostyAlertCommand;
import listeners.OreBreakListener;
import managers.AlertManager;
import managers.IPlugin;
import managers.LanguageManager;
import managers.OreTracker;
import org.bukkit.plugin.java.JavaPlugin;

public class GhostyAlert extends JavaPlugin implements IPlugin {

    private static GhostyAlert instance;
    private OreTracker oreTracker;
    private AlertManager alertManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.languageManager = new LanguageManager(this);
        this.oreTracker = new OreTracker(this);
        this.alertManager = new AlertManager(this);

        getServer().getPluginManager().registerEvents(new OreBreakListener(this), this);

        GhostyAlertCommand command = new GhostyAlertCommand(this);
        getCommand("ghostyalert").setExecutor(command);
        getCommand("ghostyalert").setTabCompleter(command);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> oreTracker.cleanup(), 600L, 600L);

        getLogger().info("╔══════════════════════════════╗");
        getLogger().info("║   GhostyAlert v1.0.0 aktiv   ║");
        getLogger().info("╚══════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        getLogger().info("GhostyAlert disabled.");
    }

    public static GhostyAlert getInstance() { return instance; }

    // Custom plugin methods
    @Override public OreTracker getOreTracker() { return oreTracker; }
    @Override public AlertManager getAlertManager() { return alertManager; }
    @Override public LanguageManager getLang() { return languageManager; }

    @Override
    public void reload() {
        reloadConfig();
        languageManager.reload();
        oreTracker.reload();
        alertManager.reload();
    }
}
