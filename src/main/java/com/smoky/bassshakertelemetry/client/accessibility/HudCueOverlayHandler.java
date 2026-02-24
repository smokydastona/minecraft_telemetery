package com.smoky.bassshakertelemetry.client.accessibility;

import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Objects;

public final class HudCueOverlayHandler {
    @SubscribeEvent
    @SuppressWarnings("null")
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.accessibilityHudEnabled || !cfg.accessibilityHudCuesEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = Objects.requireNonNull(mc.font, "font");

        int x = 6;
        int y = 6;
        if (cfg.debugOverlayEnabled) {
            y += 52;
        }

        var lines = HudCueManager.get().getLines();
        for (int i = 0; i < lines.size(); i++) {
            var l = lines.get(i);
            int color = colorFor(l.type());

            Component text = l.text();
            if (mc.player != null) {
                String arrow = arrowFor(mc.player.position(), mc.player.getYRot(), l.sourcePos());
                if (arrow != null) {
                    text = Component.literal(arrow + " ").append(text);
                }
            }

            event.getGuiGraphics().drawString(font, text, x, y + (i * 10), color);
        }
    }

    private static String arrowFor(Vec3 playerPos, float playerYawDeg, Vec3 sourcePos) {
        if (playerPos == null || sourcePos == null) {
            return null;
        }

        double dx = sourcePos.x - playerPos.x;
        double dz = sourcePos.z - playerPos.z;
        double r2 = (dx * dx) + (dz * dz);
        if (r2 < 0.0004) {
            return null;
        }

        // World angle where 0 = +Z (south), +90 = +X (east). This matches Minecraft's yaw basis.
        double worldAngle = Math.atan2(dx, dz);
        double yawRad = Math.toRadians(playerYawDeg);
        double rel = wrapPi(worldAngle - yawRad);

        // Positive rel means "to your left" in Minecraft yaw convention.
        double step = Math.PI / 4.0;
        int idx = (int) Math.round(rel / step);
        idx = ((idx % 8) + 8) % 8;

        return switch (idx) {
            case 0 -> "↑"; // front
            case 1 -> "↖"; // front-left
            case 2 -> "←"; // left
            case 3 -> "↙"; // back-left
            case 4 -> "↓"; // back
            case 5 -> "↘"; // back-right
            case 6 -> "→"; // right
            case 7 -> "↗"; // front-right
            default -> null;
        };
    }

    private static double wrapPi(double rad) {
        double r = rad % (Math.PI * 2.0);
        if (r <= -Math.PI) r += Math.PI * 2.0;
        if (r > Math.PI) r -= Math.PI * 2.0;
        return r;
    }

    private static int colorFor(HudCueType type) {
        if (type == null) {
            return 0xFFFFFF;
        }
        return switch (type) {
            case DAMAGE, EXPLOSION, LOW_HEALTH -> 0xFFCC66;
            case THUNDER -> 0x66CCFF;
            case BOSS -> 0xCCCCCC;
            case WARDEN_HEARTBEAT -> 0xCCCCCC;
        };
    }
}
