package com.example.rtpmap;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Small settings screen (default key: O). The map stays visible behind it as a live preview. */
public class RtpConfigScreen extends Screen {
    private static final int SIZE_MIN = 64;
    private static final int SIZE_MAX = 400;

    public RtpConfigScreen() {
        super(Text.literal("RTP Map"));
    }

    @Override
    protected void init() {
        int w = 200, h = 20;
        int x = this.width / 2 - w / 2;
        int y = this.height - 100;

        addDrawableChild(new ZoomSlider(x, y, w, h));
        addDrawableChild(new SizeSlider(x, y + 24, w, h));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y + 52, w, h).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // no dim/blur, so the HUD map behind stays readable
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height - 116, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        RtpConfig.save();
    }

    private static final class ZoomSlider extends SliderWidget {
        ZoomSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), (double) RtpConfig.radiusIdx / (RtpConfig.RADII.length - 1));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Zoom: " + RtpConfig.radius() + " blocks"));
        }

        @Override
        protected void applyValue() {
            RtpConfig.radiusIdx = (int) Math.round(this.value * (RtpConfig.RADII.length - 1));
            updateMessage();
        }
    }

    private static final class SizeSlider extends SliderWidget {
        SizeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), (double) (RtpConfig.size - SIZE_MIN) / (SIZE_MAX - SIZE_MIN));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Map size: " + RtpConfig.size));
        }

        @Override
        protected void applyValue() {
            RtpConfig.size = SIZE_MIN + (int) Math.round(this.value * (SIZE_MAX - SIZE_MIN));
            updateMessage();
        }
    }
}
