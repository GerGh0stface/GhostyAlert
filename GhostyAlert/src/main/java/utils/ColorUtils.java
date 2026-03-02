package utils;

import net.md_5.bungee.api.ChatColor;

public class ColorUtils {

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
