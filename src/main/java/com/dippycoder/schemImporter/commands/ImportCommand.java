package com.dippycoder.schemImporter.commands;

import com.dippycoder.schemImporter.Main;
import com.dippycoder.schemImporter.config.PluginConfig;
import com.dippycoder.schemImporter.util.UrlRewriter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".schem", ".schematic", ".nbt");

    private final Main plugin;
    private final PluginConfig cfg;

    public ImportCommand(Main plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getPluginConfig2();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        final PluginConfig cfg = this.cfg;
        final Main plugin = this.plugin;

        // ── Sub-command: reload ──────────────────────────────────────────────
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(cfg.getPermissionReload())) {
                sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgNoPermission());
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgReloaded());
            return true;
        }

        // ── Usage check ──────────────────────────────────────────────────────
        if (args.length < 1) {
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgUsage());
            return true;
        }

        // ── Permission check ─────────────────────────────────────────────────
        if (!sender.hasPermission(cfg.getPermissionImport())) {
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgNoPermission());
            return true;
        }

        String rawUrl = UrlRewriter.rewrite(args[0]);

        // ── URL validation ───────────────────────────────────────────────────
        URL url;
        try {
            url = new URL(rawUrl);
            if (!url.getProtocol().equalsIgnoreCase("https") && !url.getProtocol().equalsIgnoreCase("http")) {
                sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgInvalidUrl(rawUrl));
                return true;
            }
        } catch (MalformedURLException e) {
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgInvalidUrl(rawUrl));
            return true;
        }

        // ── Host whitelist check ─────────────────────────────────────────────
        if (cfg.isWhitelistEnabled() && !sender.hasPermission(cfg.getPermissionBypassWhitelist())) {
            String host = url.getHost().toLowerCase();
            List<String> whitelist = cfg.getWhitelistedHosts();
            boolean allowed = whitelist.stream().anyMatch(entry ->
                    host.equals(entry.toLowerCase()) || host.endsWith("." + entry.toLowerCase()));
            if (!allowed) {
                sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgHostBlocked(host));
                return true;
            }
        }

        // ── Filename resolution ───────────────────────────────────────────────
        // args[1] = optional custom name; falls back to URL-derived filename.
        String fileName;

        if (args.length >= 2) {
            String customName = args[1].trim();
            // Auto-append .schem if the player didn't include an extension
            boolean hasExt = ALLOWED_EXTENSIONS.stream()
                    .anyMatch(ext -> customName.toLowerCase().endsWith(ext));
            fileName = hasExt ? customName : customName + ".schem";
        } else {
            String urlPath = url.getPath();
            fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);
            try {
                fileName = URLDecoder.decode(fileName, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {}
        }

        // ── Extension check ──────────────────────────────────────────────────
        String finalFileName1 = fileName;
        boolean validExt = ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> finalFileName1.toLowerCase().endsWith(ext));
        if (!validExt || fileName.isEmpty()) {
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgInvalidExtension());
            return true;
        }

        // ── Sanitize filename (no path traversal) ────────────────────────────
        String safeFileName = new File(fileName).getName();

        // ── Schem directory check ────────────────────────────────────────────
        File schemDir = plugin.getSchemDirectory();
        if (!schemDir.exists() && !schemDir.mkdirs()) {
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgSchemDirMissing());
            return true;
        }

        File destination = new File(schemDir, safeFileName);

        // ── Overwrite protection ─────────────────────────────────────────────
        if (destination.exists() && !cfg.isOverwriteAllowed()
                && !sender.hasPermission(cfg.getPermissionBypassOverwrite())) {
            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgFileExists(safeFileName));
            return true;
        }

        // ── Async download ───────────────────────────────────────────────────
        final URL finalUrl = url;
        final String finalFileName = safeFileName;
        final File finalDest = destination;

        sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgDownloadStart(rawUrl));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) finalUrl.openConnection();
                connection.setConnectTimeout(cfg.getDownloadTimeoutSeconds() * 1000);
                connection.setReadTimeout(cfg.getDownloadTimeoutSeconds() * 1000);
                connection.setRequestProperty("User-Agent", "SchemImporter/1.0 (PaperMC plugin)");
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    final String reason = "HTTP " + responseCode;
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgDownloadFailed(reason)));
                    return;
                }

                // Content-Length check (if server provides it)
                long contentLength = connection.getContentLengthLong();
                long maxBytes = cfg.getMaxFileSizeBytes();
                if (contentLength > 0 && contentLength > maxBytes) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgFileTooLarge(maxBytes)));
                    connection.disconnect();
                    return;
                }

                // Stream download with size enforcement
                File tempFile = new File(finalDest.getParentFile(), finalFileName + ".tmp");
                try (InputStream in = connection.getInputStream();
                     OutputStream out = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int bytesRead;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        totalRead += bytesRead;
                        if (totalRead > maxBytes) {
                            out.close();
                            tempFile.delete();
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                    sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgFileTooLarge(maxBytes)));
                            connection.disconnect();
                            return;
                        }
                        out.write(buffer, 0, bytesRead);
                    }
                }

                connection.disconnect();

                // Atomic move from temp → final
                Files.move(tempFile.toPath(), finalDest.toPath(), StandardCopyOption.REPLACE_EXISTING);

                plugin.getLogger().info(sender.getName() + " imported schematic: "
                        + finalFileName + " from " + finalUrl.getHost());

                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgDownloadSuccess(finalFileName)));

            } catch (SocketTimeoutException e) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgTimeout()));
            } catch (IOException e) {
                final String reason = e.getMessage() != null ? e.getMessage() : "Unknown IO error";
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(cfg.getMsgPrefix() + cfg.getMsgDownloadFailed(reason)));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(cfg.getPermissionReload()) && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            if (args[0].isEmpty()) {
                completions.add("<url>");
            }
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            completions.add("<name>");
        }
        return completions;
    }
}