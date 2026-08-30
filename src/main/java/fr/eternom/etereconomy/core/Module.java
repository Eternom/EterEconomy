package fr.eternom.etereconomy.core;

/**
 * A self-contained feature (commands, listeners, its own managers) that can be
 * plugged into the plugin independently of the others.
 */
public interface Module {

    /**
     * Wires up the module (register commands, listeners, ...).
     * May disable the plugin via {@code Bukkit.getPluginManager().disablePlugin(...)}
     * if a hard requirement is missing.
     */
    void enable();

    /**
     * Releases any resource held by the module (connections, schedulers, ...).
     */
    void disable();
}
