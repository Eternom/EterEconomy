package fr.eternom.etereconomy.core;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * Registers modules and manages their lifecycle.
 */
public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    /** Failures are not swallowed - modules depend on each other, so the caller should catch, log, and disable the plugin. */
    public void register(Module module) {
        modules.add(module);
        module.enable();
    }

    /**
     * Disables in reverse registration order: a module registered later may depend on one
     * registered earlier (e.g. backup reads the economy/bank stores), so it must shut down first,
     * while that dependency is still open.
     */
    public void disableAll() {
        ListIterator<Module> iterator = modules.listIterator(modules.size());
        while (iterator.hasPrevious()) {
            Module module = iterator.previous();
            try {
                module.disable();
            } catch (Exception e) {
                Bukkit.getLogger().severe("[EterEconomy] Failed to disable module '" + module.getClass().getSimpleName() + "': " + e.getMessage());
            }
        }
        modules.clear();
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }
}
