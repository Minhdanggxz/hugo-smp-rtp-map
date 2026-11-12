package com.example.rtpmap;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class RtpConfig {
    /** Map radius steps in blocks (half the width of the visible area). */
    public static final int[] RADII = {500, 1000, 2500, 5000, 10000, 25000, 50000, 125000};

    public static boolean enabled = true;
    public static int radiusIdx = 3;
    public static int x = 6;
    public static int y = 6;
    public static int size = 128;

    private RtpConfig() {}

    public static int radius() {
        return RADII[Math.max(0, Math.min(RADII.length - 1, radiusIdx))];
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
            radiusIdx = clamp(parse(p, "radiusIdx", 3), 0, RADII.length - 1);
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
            p.setProperty("radiusIdx", Integer.toString(radiusIdx));
            p.setProperty("x", Integer.toString(x));
            p.setProperty("y", Integer.toString(y));
            p.setProperty("size", Integer.toString(size));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "RTP Map config (x,y = HUD position in GUI pixels, size = map size in GUI pixels)");
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
