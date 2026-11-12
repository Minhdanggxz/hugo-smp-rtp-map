package com.example.rtpmap;

import net.minecraft.client.MinecraftClient;

/** Scaled window size helpers. */
final class RtpScreens {
    private RtpScreens() {}

    static int width() {
        return MinecraftClient.getInstance().getWindow().getScaledWidth();
    }

    static int height() {
        return MinecraftClient.getInstance().getWindow().getScaledHeight();
    }
}
