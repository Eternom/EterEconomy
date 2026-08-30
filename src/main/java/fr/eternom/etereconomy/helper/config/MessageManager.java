package fr.eternom.etereconomy.helper.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves player-facing text from lang/&lt;code&gt;.yml files. Players are served messages in
 * their own client locale automatically, falling back to {@code language} from config.yml
 * (or always, when {@code force-language} is enabled).
 */
public class MessageManager {

    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "fr");

    private final JavaPlugin plugin;
    private final String defaultLanguage;
    private final boolean forceLanguage;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.defaultLanguage = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        this.forceLanguage = plugin.getConfig().getBoolean("force-language", false);
        loadLanguages();
    }

    private void loadLanguages() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("[EterEconomy] Failed to create lang folder.");
        }

        // Drop the bundled translations onto disk if they're not there yet, so admins can edit them.
        for (String code : BUNDLED_LANGUAGES) {
            if (!new File(langFolder, code + ".yml").exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }

        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(0, file.getName().length() - ".yml".length()).toLowerCase(Locale.ROOT);
                languages.put(code, YamlConfiguration.loadConfiguration(file));
            }
        }

        if (!languages.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("[EterEconomy] Configured language '" + defaultLanguage
                    + "' has no lang/" + defaultLanguage + ".yml file; messages will fall back to their key names.");
        }
    }

    public String get(CommandSender sender, String key, String... placeholders) {
        return resolve(languageFor(sender), key, placeholders);
    }

    /** For messages with no specific player to address (console logs, broadcasts). */
    public String get(String key, String... placeholders) {
        return resolve(defaultLanguage, key, placeholders);
    }

    private String languageFor(CommandSender sender) {
        if (!forceLanguage && sender instanceof Player player) {
            String code = playerLanguageCode(player);
            if (code != null && languages.containsKey(code)) {
                return code;
            }
        }
        return defaultLanguage;
    }

    private String playerLanguageCode(Player player) {
        Locale locale = player.locale();
        return locale != null ? locale.getLanguage().toLowerCase(Locale.ROOT) : null;
    }

    private String resolve(String languageCode, String key, String... placeholders) {
        YamlConfiguration language = languages.getOrDefault(languageCode, languages.get(defaultLanguage));
        String prefix = language != null ? language.getString("prefix", "") : "";
        String raw = language != null ? language.getString(key, key) : key;
        raw = raw.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
