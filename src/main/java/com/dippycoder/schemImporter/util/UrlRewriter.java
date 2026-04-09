package com.dippycoder.schemImporter.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites known schematic share-page URLs to their direct download equivalents.
 *
 * Add new platforms by implementing a private static method and calling it
 * from rewrite() in the chain below.
 */
public class UrlRewriter {

    private UrlRewriter() {}

    /**
     * Attempt to rewrite a URL to a direct download link.
     * Returns the original URL unchanged if no rule matches.
     */
    public static String rewrite(String url) {
        String result;

        if ((result = rewriteIntellectualSites(url)) != null) return result;
        if ((result = rewriteSchemSnPage(url))        != null) return result;
        if ((result = rewriteBuiltByBitPage(url))     != null) return result;

        return url; // no rule matched — pass through as-is
    }

    // ── IntellectualSites / FAWE schematic share ──────────────────────────────
    // Share URL:   https://schem.intellectualsites.com/fawe/?key=<uuid>&type=schem
    // Direct URL:  https://schem.intellectualsites.com/fawe/uploads/<uuid>.schem
    //
    // Also handles ?type=schematic and ?type=nbt.
    private static final Pattern INTELLECTUAL_SITES = Pattern.compile(
            "https?://schem\\.intellectualsites\\.com/fawe/\\?key=([\\w\\-]+)(?:&type=(schem|schematic|nbt))?"
    );

    private static String rewriteIntellectualSites(String url) {
        Matcher m = INTELLECTUAL_SITES.matcher(url);
        if (!m.find()) return null;
        String key = m.group(1);
        String type = m.group(2) != null ? m.group(2) : "schem"; // default to .schem
        return "https://schem.intellectualsites.com/fawe/uploads/" + key + "." + type;
    }

    // ── schem.sn share page ───────────────────────────────────────────────────
    // Share URL:   https://schem.sn/s/<id>
    // Direct URL:  https://schem.sn/download/<id>.schem
    private static final Pattern SCHEM_SN = Pattern.compile(
            "https?://schem\\.sn/s/([\\w\\-]+)"
    );

    private static String rewriteSchemSnPage(String url) {
        Matcher m = SCHEM_SN.matcher(url);
        if (!m.find()) return null;
        String id = m.group(1);
        return "https://schem.sn/download/" + id + ".schem";
    }

    // ── BuiltByBit / MC-Market attachment ────────────────────────────────────
    // Share URL:   https://builtbybit.com/resources/<name>.<id>/download
    // Direct URL:  https://builtbybit.com/resources/<name>.<id>/download?version=latest
    // (BuiltByBit requires the ?version param for a raw file response)
    private static final Pattern BUILT_BY_BIT = Pattern.compile(
            "https?://builtbybit\\.com/resources/[\\w\\-]+\\.\\d+/download(?!\\?)"
    );

    private static String rewriteBuiltByBitPage(String url) {
        Matcher m = BUILT_BY_BIT.matcher(url);
        if (!m.find()) return null;
        return url + "?version=latest";
    }
}