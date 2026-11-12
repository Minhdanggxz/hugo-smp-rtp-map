package com.example.rtpmap;

import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads, caches and indexes the RTP points for each dimension. */
public final class RtpStore {
    public static final String API = "https://269153435.de/api/mod/rtp";
    public static final int SHIFT = 8; // 256-block cells
    private static final int MAGIC = 0x52544D50; // "RTMP"
    private static final long STALE_MS = 30L * 60_000L;
    private static final long RETRY_MS = 60_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Map<String, Cloud> CLOUDS = new ConcurrentHashMap<>();

    private RtpStore() {}

    public static final class Cloud {
        public volatile Index index;
        public volatile boolean busy;
        public volatile String error;
        public volatile long fetchedAt;
        volatile long lastAttempt;
    }

    /** Spatial grid: points sorted by cell, so a view window only touches nearby cells. */
    public static final class Index {
        public final int count;
        public final int[] sx, sz, start;
        public final int minX, minZ, cellsW, cellsH;

        Index(int count, int[] sx, int[] sz, int[] start, int minX, int minZ, int cellsW, int cellsH) {
            this.count = count;
            this.sx = sx;
            this.sz = sz;
            this.start = start;
            this.minX = minX;
            this.minZ = minZ;
            this.cellsW = cellsW;
            this.cellsH = cellsH;
        }
    }

    public static Cloud cloud(String dim) {
        return CLOUDS.computeIfAbsent(dim, d -> new Cloud());
    }

    /** Called every frame; starts a load only when needed. */
    public static void ensure(String dim) {
        Cloud c = cloud(dim);
        long now = System.currentTimeMillis();
        if (c.busy) return;
        if (c.index != null && now - c.fetchedAt < STALE_MS) return;
        if (now - c.lastAttempt < RETRY_MS) return;
        start(dim, c, false);
    }

    public static void reload(String dim) {
        Cloud c = cloud(dim);
        if (c.busy) return;
        start(dim, c, true);
    }

    private static void start(String dim, Cloud c, boolean force) {
        c.busy = true;
        c.lastAttempt = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                if (!force && c.index == null) {
                    Loaded cached = readCache(dim);
                    if (cached != null) {
                        c.index = buildIndex(cached.xs, cached.zs, cached.n);
                        c.fetchedAt = cached.fetchedAt;
                        c.error = null;
                        if (System.currentTimeMillis() - cached.fetchedAt < STALE_MS) return;
                    }
                }
                Loaded fresh = fetch(dim);
                Index idx = buildIndex(fresh.xs, fresh.zs, fresh.n);
                writeCache(dim, fresh);
                c.index = idx;
                c.fetchedAt = fresh.fetchedAt;
                c.error = null;
            } catch (Throwable t) {
                c.error = t.getClass().getSimpleName() + ": " + t.getMessage();
                System.err.println("[RtpMap] load failed for " + dim + ": " + c.error);
            } finally {
                c.busy = false;
            }
        });
    }

    private static final class Loaded {
        int[] xs, zs;
        int n;
        long fetchedAt;
    }

    /** Response shape: {"ok":true,"dim":"overworld","area":5000,"count":N,"p":[x,z,y, x,z,y, ...]} */
    private static Loaded fetch(String dim) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "?dim=" + dim))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", "rtpmap-mod")
                .GET()
                .build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            throw new IOException("HTTP " + res.statusCode());
        }
        Loaded out = new Loaded();
        int[] xs = new int[1 << 16];
        int[] zs = new int[1 << 16];
        int n = 0;
        try (JsonReader r = new JsonReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if (name.equals("ok")) {
                    if (!r.nextBoolean()) throw new IOException("server returned ok=false");
                } else if (name.equals("p")) {
                    r.beginArray();
                    int k = 0;
                    int pendingX = 0;
                    while (r.hasNext()) {
                        int v = r.nextInt();
                        int m = k % 3;
                        if (m == 0) {
                            pendingX = v;
                        } else if (m == 1) {
                            if (n == xs.length) {
                                xs = java.util.Arrays.copyOf(xs, n * 2);
                                zs = java.util.Arrays.copyOf(zs, n * 2);
                            }
                            xs[n] = pendingX;
                            zs[n] = v;
                            n++;
                        } // m == 2 is the height, not needed for a 2D map
                        k++;
                    }
                    r.endArray();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
        }
        if (n == 0) throw new IOException("empty response");
        out.xs = xs;
        out.zs = zs;
        out.n = n;
        out.fetchedAt = System.currentTimeMillis();
        return out;
    }

    private static Index buildIndex(int[] xs, int[] zs, int n) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        int cellsW = ((maxX - minX) >> SHIFT) + 1;
        int cellsH = ((maxZ - minZ) >> SHIFT) + 1;
        int cells = cellsW * cellsH;
        int[] start = new int[cells + 1];
        for (int i = 0; i < n; i++) {
            start[cellOf(xs[i], zs[i], minX, minZ, cellsW) + 1]++;
        }
        for (int i = 0; i < cells; i++) start[i + 1] += start[i];
        int[] cursor = java.util.Arrays.copyOf(start, cells);
        int[] sx = new int[n];
        int[] sz = new int[n];
        for (int i = 0; i < n; i++) {
            int cell = cellOf(xs[i], zs[i], minX, minZ, cellsW);
            int at = cursor[cell]++;
            sx[at] = xs[i];
            sz[at] = zs[i];
        }
        return new Index(n, sx, sz, start, minX, minZ, cellsW, cellsH);
    }

    private static int cellOf(int x, int z, int minX, int minZ, int cellsW) {
        return ((z - minZ) >> SHIFT) * cellsW + ((x - minX) >> SHIFT);
    }

    private static Path cacheFile(String dim) {
        return FabricLoader.getInstance().getConfigDir().resolve("rtpmap").resolve(dim + ".bin");
    }

    private static void writeCache(String dim, Loaded l) {
        Path p = cacheFile(dim);
        try {
            Files.createDirectories(p.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p), 1 << 16))) {
                out.writeInt(MAGIC);
                out.writeLong(l.fetchedAt);
                out.writeInt(l.n);
                for (int i = 0; i < l.n; i++) {
                    out.writeInt(l.xs[i]);
                    out.writeInt(l.zs[i]);
                }
            }
        } catch (Exception e) {
            System.err.println("[RtpMap] Could not write cache " + p + ": " + e.getMessage());
        }
    }

    private static Loaded readCache(String dim) {
        Path p = cacheFile(dim);
        if (!Files.exists(p)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(p), 1 << 16))) {
            if (in.readInt() != MAGIC) return null;
            Loaded l = new Loaded();
            l.fetchedAt = in.readLong();
            l.n = in.readInt();
            if (l.n <= 0 || l.n > 50_000_000) return null;
            l.xs = new int[l.n];
            l.zs = new int[l.n];
            for (int i = 0; i < l.n; i++) {
                l.xs[i] = in.readInt();
                l.zs[i] = in.readInt();
            }
            return l;
        } catch (Exception e) {
            System.err.println("[RtpMap] Could not read cache " + p + ": " + e.getMessage());
            return null;
        }
    }
}
