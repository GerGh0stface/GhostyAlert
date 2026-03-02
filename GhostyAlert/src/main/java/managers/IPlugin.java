package managers;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Interface so subpackages can reference GhostyAlert without importing from default package.
 * JavaPlugin methods are inherited automatically — only custom methods need explicit @Override.
 */
public interface IPlugin {
    // Custom plugin methods
    LanguageManager getLang();
    OreTracker getOreTracker();
    AlertManager getAlertManager();
    void reload();

    // Standard JavaPlugin methods used by managers (satisfied via inheritance, no override needed)
    FileConfiguration getConfig();
    void reloadConfig();
    void saveDefaultConfig();
    void saveResource(String resourcePath, boolean replace);
    InputStream getResource(String filename);
    File getDataFolder();
    Logger getLogger();
}
