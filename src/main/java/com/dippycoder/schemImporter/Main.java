package com.dippycoder.schemImporter;

import com.dippycoder.schemImporter.commands.ImportCommand;
import com.dippycoder.schemImporter.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {

    private static Main instance;
    private PluginConfig pluginConfig;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);

        ensureSchemDir();

        ImportCommand importCmd = new ImportCommand(this);
        getCommand("import").setExecutor(importCmd);
        getCommand("import").setTabCompleter(importCmd);

        getLogger().info("SchemImporter enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SchemImporter disabled.");
    }

    private void ensureSchemDir() {
        File schemDir = getSchemDirectory();
        if (!schemDir.exists()) {
            boolean created = schemDir.mkdirs();
            if (created) {
                getLogger().info("Created schematic directory: " + schemDir.getAbsolutePath());
            } else {
                getLogger().warning("Could not create schematic directory: " + schemDir.getAbsolutePath());
            }
        }
    }

    public File getSchemDirectory() {
        String path = pluginConfig.getSchemDirectory();
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            dir = new File(getDataFolder().getParentFile().getParentFile(), path);
        }
        return dir;
    }

    public PluginConfig getPluginConfig2() {
        return pluginConfig;
    }

    public static Main getInstance() {
        return instance;
    }
}