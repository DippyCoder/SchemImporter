package com.dippycoder.schemImporter.config;

import com.dippycoder.schemImporter.Main;
import org.bukkit.ChatColor;

import java.util.List;

public class PluginConfig {

    private final Main plugin;

    public PluginConfig(Main plugin) {
        this.plugin = plugin;
    }

    // ── Schematic directory ─────────────────────────────────────────────────────

    public String getSchemDirectory() {
        return plugin.getConfig().getString("schematic-directory", "plugins/FastAsyncWorldEdit/schematics");
    }

    // ── URL whitelist ───────────────────────────────────────────────────────────

    public List<String> getWhitelistedHosts() {
        return plugin.getConfig().getStringList("whitelist.hosts");
    }

    public boolean isWhitelistEnabled() {
        return plugin.getConfig().getBoolean("whitelist.enabled", true);
    }

    // ── Limits ──────────────────────────────────────────────────────────────────

    public long getMaxFileSizeBytes() {
        return plugin.getConfig().getLong("limits.max-file-size-bytes", 52428800L); // 50 MB default
    }

    public int getDownloadTimeoutSeconds() {
        return plugin.getConfig().getInt("limits.download-timeout-seconds", 15);
    }

    public boolean isOverwriteAllowed() {
        return plugin.getConfig().getBoolean("limits.allow-overwrite", false);
    }

    // ── Permissions ─────────────────────────────────────────────────────────────

    public String getPermissionImport() {
        return plugin.getConfig().getString("permissions.import", "schemimporter.import");
    }

    public String getPermissionBypassWhitelist() {
        return plugin.getConfig().getString("permissions.bypass-whitelist", "schemimporter.bypass.whitelist");
    }

    public String getPermissionBypassOverwrite() {
        return plugin.getConfig().getString("permissions.bypass-overwrite", "schemimporter.bypass.overwrite");
    }

    public String getPermissionReload() {
        return plugin.getConfig().getString("permissions.reload", "schemimporter.reload");
    }

    // ── Messages ─────────────────────────────────────────────────────────────────

    private String msg(String path) {
        String raw = plugin.getConfig().getString("messages." + path, "&cMissing message: " + path);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private String msg(String path, String... replacements) {
        String s = msg(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            s = s.replace(replacements[i], replacements[i + 1]);
        }
        return s;
    }

    public String getMsgPrefix() {
        return msg("prefix");
    }

    public String getMsgUsage() {
        return msg("usage");
    }

    public String getMsgNoPermission() {
        return msg("no-permission");
    }

    public String getMsgPlayerOnly() {
        return msg("player-only");
    }

    public String getMsgDownloadStart(String url) {
        return msg("download-start", "{url}", url);
    }

    public String getMsgDownloadSuccess(String filename) {
        return msg("download-success", "{filename}", filename);
    }

    public String getMsgDownloadFailed(String reason) {
        return msg("download-failed", "{reason}", reason);
    }

    public String getMsgInvalidUrl(String url) {
        return msg("invalid-url", "{url}", url);
    }

    public String getMsgHostBlocked(String host) {
        return msg("host-blocked", "{host}", host);
    }

    public String getMsgFileTooLarge(long maxMb) {
        return msg("file-too-large", "{max_mb}", String.valueOf(maxMb / 1024 / 1024));
    }

    public String getMsgFileExists(String filename) {
        return msg("file-exists", "{filename}", filename);
    }

    public String getMsgSchemDirMissing() {
        return msg("schem-dir-missing");
    }

    public String getMsgTimeout() {
        return msg("timeout");
    }

    public String getMsgReloaded() {
        return msg("reloaded");
    }

    public String getMsgInvalidExtension() {
        return msg("invalid-extension");
    }
}