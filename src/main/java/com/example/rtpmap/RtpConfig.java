package com.example.rtpmap;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class RtpConfig {
    /** Zoom level 1..100, each level = 500 blocks of map radius (level 2 = 1000 blocks). */
    public static final int ZOOM_MIN = 1;
    public static final int ZOOM_MAX = 100;
    public static final int BLOCKS_PER_LEVEL = 500;

    public static boolean enabled = true;
    public static int zoomLevel = 2;
    public static int x = 6;
    public static int y = 6;
    public static int size = 128;

    private RtpConfig() {}

    /** Map radius in blocks (half the width of the visible area). */
    public static int radius() {
        return zoomLevel * BLOCKS_PER_LEVEL;
    }

    public static void setZoom(int level) {
        zoomLevel = clamp(level, ZOOM_MIN, ZOOM_MAX);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rtpmap.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        try (InputStream in = Files.newInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            setZoom(parse(p, "zoomLevel", 2));
            x = parse(p, "x", 6);
            y = parse(p, "y", 6);
            size = clamp(parse(p, "size", 128), 64, 400);
        } catch (Exception e) {
            System.err.println("[RtpMap] Could not read config: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("enabled", Boolean.toString(enabled));
            p.setProperty("zoomLevel", Integer.toString(zoomLevel));
            p.setProperty("x", Integer.toString(x));
            p.setProperty("y", Integer.toString(y));
            p.setProperty("size", Integer.toString(size));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "RTP Map config (zoomLevel 1-100, 500 blocks per level; x,y = HUD position in GUI pixels; size = map size in GUI pixels)");
            }
        } catch (IOException e) {
            System.err.println("[RtpMap] Could not write config: " + e.getMessage());
        }
    }

    private static int parse(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
