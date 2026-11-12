package com.example.rtpmap;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class RtpMapClient implements ClientModInitializer {
    private static final String CATEGORY = "category.rtpmap";

    private static KeyBinding toggleKey;
    private static KeyBinding zoomInKey;
    private static KeyBinding zoomOutKey;
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        RtpConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.rtpmap.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        zoomInKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.rtpmap.zoom_in", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY));
        zoomOutKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.rtpmap.zoom_out", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY));

        settingsKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.rtpmap.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                RtpConfig.enabled = !RtpConfig.enabled;
                RtpConfig.save();
            }
            while (settingsKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new RtpConfigScreen());
            }
            while (zoomInKey.wasPressed()) {
                RtpConfig.radiusIdx = Math.max(0, RtpConfig.radiusIdx - 1);
                RtpConfig.save();
            }
            while (zoomOutKey.wasPressed()) {
                RtpConfig.radiusIdx = Math.min(RtpConfig.RADII.length - 1, RtpConfig.radiusIdx + 1);
                RtpConfig.save();
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> RtpHud.render(ctx));

        // /rtpmap reload  -> re-download points for the current dimension
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("rtpmap")
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.world != null) {
                                RtpStore.reload(RtpHud.dimKey(mc.world));
                                ctx.getSource().sendFeedback(Text.literal("[RtpMap] Reloading points..."));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))));
    }
}
