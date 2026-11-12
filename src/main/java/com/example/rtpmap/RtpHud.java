package com.example.rtpmap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

/** Draws the map: green dots = RTP points, yellow marker = you. North is up. */
public final class RtpHud {
    private static final int PAD = 4;
    private static final int TITLE_H = 12;
    private static final int LABEL_H = 11;

    private static final int BG = 0xB0141414;
    private static final int BORDER = 0xFF6E6E6E;
    private static final int MAP_BG = 0xFF1E1E1E;
    private static final int POINT = 0xFF55E39A;
    private static final int TITLE = 0xFF55E39A;
    private static final int PLAYER = 0xFFFFC21A;
    private static final int AXIS = 0x30FFFFFF;
    private static final int TEXT = 0xFFB0B0B0;

    // cached pixel mask so a huge point cloud is not rescanned every frame
    private static int[] stamp;
    private static int[] pix;
    private static int pixN;
    private static int gen;
    private static Object lastIdx;
    private static String lastDim;
    private static int lastRad, lastKX, lastKZ, lastSize;

    private RtpHud() {}

    public static String dimKey(World world) {
        if (world.getRegistryKey().equals(World.NETHER)) return "nether";
        if (world.getRegistryKey().equals(World.END)) return "end";
        return "overworld";
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!RtpConfig.enabled || mc.player == null || mc.world == null || mc.options.hudHidden) return;

        String dim = dimKey(mc.world);
        RtpStore.ensure(dim);
        RtpStore.Cloud cloud = RtpStore.cloud(dim);
        RtpStore.Index idx = cloud.index;

        int S = RtpConfig.size;
        int radius = RtpConfig.radius();
        int px0 = RtpConfig.x, py0 = RtpConfig.y;
        int mx = px0 + PAD, my = py0 + TITLE_H;
        int w = S + PAD * 2;
        int h = TITLE_H + S + PAD + LABEL_H;
        TextRenderer font = mc.textRenderer;

        // panel
        ctx.fill(px0, py0, px0 + w, py0 + h, BG);
        ctx.fill(px0, py0, px0 + w, py0 + 1, BORDER);
        ctx.fill(px0, py0 + h - 1, px0 + w, py0 + h, BORDER);
        ctx.fill(px0, py0, px0 + 1, py0 + h, BORDER);
        ctx.fill(px0 + w - 1, py0, px0 + w, py0 + h, BORDER);
        ctx.drawText(font, "RTP MAP", px0 + PAD, py0 + 3, TITLE, true);
        ctx.fill(mx, my, mx + S, my + S, MAP_BG);

        double px = mc.player.getX(), pz = mc.player.getZ();

        // axes through the player (map is centered on you)
        ctx.fill(mx + S / 2, my, mx + S / 2 + 1, my + S, AXIS);
        ctx.fill(mx, my + S / 2, mx + S, my + S / 2 + 1, AXIS);

        String label;
        if (idx != null) {
            double bpp = 2.0 * radius / S;
            double step = Math.max(1.0, bpp);
            int kx = (int) Math.floor(px / step);
            int kz = (int) Math.floor(pz / step);
            if (idx != lastIdx || !dim.equals(lastDim) || radius != lastRad
                    || kx != lastKX || kz != lastKZ || S != lastSize) {
                rebuild(idx, px, pz, radius, S);
                lastIdx = idx;
                lastDim = dim;
                lastRad = radius;
                lastKX = kx;
                lastKZ = kz;
                lastSize = S;
            }
            int dot = radius <= 1000 ? 2 : 1;
            for (int i = 0; i < pixN; i++) {
                int p = pix[i];
                int ix = p % S, iy = p / S;
                int x = mx + ix, y = my + iy;
                ctx.fill(x, y, Math.min(x + dot, mx + S), Math.min(y + dot, my + S), POINT);
            }
            label = idx.count + " pts | r" + radius;
        } else if (cloud.error != null) {
            label = "Error (see log)";
        } else {
            label = "Loading...";
        }

        // you: yellow square + stem (like the original)
        int cx = mx + S / 2, cy = my + S / 2;
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, PLAYER);
        ctx.fill(cx, cy + 2, cx + 1, cy + 5, PLAYER);

        ctx.drawText(font, label, px0 + PAD, my + S + 3, TEXT, false);
    }

    private static void rebuild(RtpStore.Index idx, double px, double pz, int radius, int S) {
        if (stamp == null || stamp.length != S * S) {
            stamp = new int[S * S];
            pix = new int[S * S];
            gen = 0;
        }
        gen++;
        pixN = 0;

        double bpp = 2.0 * radius / S;
        double left = px - radius, top = pz - radius;
        int shift = RtpStore.SHIFT;
        int cx0 = Math.max(0, ((int) Math.floor(left) - idx.minX) >> shift);
        int cx1 = Math.min(idx.cellsW - 1, ((int) Math.floor(px + radius) - idx.minX) >> shift);
        int cz0 = Math.max(0, ((int) Math.floor(top) - idx.minZ) >> shift);
        int cz1 = Math.min(idx.cellsH - 1, ((int) Math.floor(pz + radius) - idx.minZ) >> shift);

        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                int cell = cz * idx.cellsW + cx;
                int end = idx.start[cell + 1];
                for (int i = idx.start[cell]; i < end; i++) {
                    double dx = idx.sx[i] - left;
                    double dz = idx.sz[i] - top;
                    if (dx < 0 || dz < 0) continue;
                    int ix = (int) (dx / bpp);
                    int iy = (int) (dz / bpp);
                    if (ix >= S || iy >= S) continue;
                    int p = iy * S + ix;
                    if (stamp[p] != gen) {
                        stamp[p] = gen;
                        pix[pixN++] = p;
                    }
                }
            }
        }
    }
}
