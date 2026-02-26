package managers;

import utils.ColorUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LanguageManager {

    private final IPlugin plugin;
    private FileConfiguration lang;

    public LanguageManager(IPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String language = plugin.getConfig().getString("language", "en");
        String fileName = "lang/" + language + ".yml";

        File langFile = new File(plugin.getDataFolder(), fileName);
        if (!langFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        if (langFile.exists()) {
            lang = YamlConfiguration.loadConfiguration(langFile);
            InputStream defaultStream = plugin.getResource(fileName);
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                lang.setDefaults(defaults);
            }
        } else {
            plugin.getLogger().warning("Language file '" + fileName + "' not found! Falling back to en.yml.");
            InputStream fallback = plugin.getResource("lang/en.yml");
            if (fallback != null) {
                lang = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(fallback, StandardCharsets.UTF_8));
            } else {
                lang = new YamlConfiguration();
                plugin.getLogger().severe("Could not load any language file!");
            }
        }
    }

    public String get(String key) {
        String value = lang.getString(key, "&cMissing key: " + key);
        return ColorUtils.color(value);
    }

    public String get(String key, String... replacements) {
        String value = get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
    }

    public String getPrefix() {
        return get("prefix");
    }
}
