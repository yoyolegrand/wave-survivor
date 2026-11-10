package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.TitanCinePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 2.0 — Côté client de la cinématique du Titan : musique et ambiance coupées, bandes noires qui glissent, caméra qui pivote en douceur vers le point
 * d'apparition puis revient, brouillard teinté de violet, joueur figé. Maintenir S'accroupir 1 s passe la cinématique.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TitanCineClient {

    private static boolean active, skippable;
    private static int total, tick, held;
    private static double tx, ty, tz;
    private static float fromYaw, fromPitch;

    private TitanCineClient() {}

    public static void onPacket(TitanCinePackets.Cine p) {
        Minecraft mc = Minecraft.getInstance();
        if (!p.start) { active = false; return; }
        active = true;
        skippable = p.skippable;
        total = Math.max(40, p.ticks);
        tick = 0;
        held = 0;
        tx = p.x; ty = p.y; tz = p.z;
        if (mc.player != null) { fromYaw = mc.player.getYRot(); fromPitch = mc.player.getXRot(); }
    }

    private static float smooth(float f) { f = Mth.clamp(f, 0f, 1f); return f * f * (3 - 2 * f); }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { active = false; return; }
        tick++;
        // Silence : plus de musique ni de sons d'ambiance
        if (tick <= total) {
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().stop(null, SoundSource.AMBIENT);
            mc.getSoundManager().stop(null, SoundSource.MUSIC);
        }
        // Passer : maintenir S'accroupir 1 s
        if (skippable && mc.options.keyShift.isDown()) held++; else held = 0;
        if (held >= 20) {
            NetworkHandler.CHANNEL.sendToServer(new TitanCinePackets.Skip());
            active = false;
            return;
        }
        if (tick > total + 20) active = false;   // sécurité : le serveur n'a pas envoyé l'arrêt
    }

    /** Joueur figé : aucun déplacement ni saut ni accroupissement pendant la cinématique. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent ev) {
        if (!active) return;
        var in = ev.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }

    /** La caméra pivote en douceur vers le point d'apparition (début), reste dessus, puis revient au joueur (fin). */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles ev) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = tick + (float) ev.getPartialTick();
        float f = total / 240f;
        double dx = tx - mc.player.getX(), dy = ty - mc.player.getEyeY(), dz = tz - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        float in = smooth(t / (40f * f));
        float out = smooth((t - (total - 12f)) / 12f);
        float k = in * (1f - out);
        ev.setYaw(Mth.rotLerp(k, fromYaw, yaw));
        ev.setPitch(Mth.lerp(k, fromPitch, pitch));
    }

    /** Brouillard tinté de violet quand l'abîme s'éveille (de 1/6 à la fin de la cinématique). */
    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor ev) {
        if (!active) return;
        float f = total / 240f, t = tick + (float) ev.getPartialTick();
        float s = smooth((t - 40f * f) / (40f * f)) * (1f - smooth((t - (total - 20f)) / 20f));
        if (s <= 0f) return;
        ev.setRed(Mth.lerp(s, ev.getRed(), 0.38f));
        ev.setGreen(Mth.lerp(s, ev.getGreen(), 0.08f));
        ev.setBlue(Mth.lerp(s, ev.getBlue(), 0.60f));
    }

    /** Bandes noires (letterbox) qui glissent depuis le haut et le bas, et indication pour passer. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post ev) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = ev.getGuiGraphics();
        int w = ev.getWindow().getGuiScaledWidth(), h = ev.getWindow().getGuiScaledHeight();
        float t = tick + ev.getPartialTick();
        float slide = smooth(t / 20f) * (1f - smooth((t - (total - 20f)) / 20f));
        int bar = (int) (h * 0.13f * slide);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (skippable && slide > 0.5f) {
            String hint = "§7Maintiens §fMaj §7pour passer" + (held > 0 ? " §8[" + "|".repeat(held * 10 / 20) + "§0" + "|".repeat(10 - held * 10 / 20) + "§8]" : "");
            g.drawCenteredString(mc.font, hint, w / 2, h - bar + 6, 0xFFFFFFFF);
        }
    }
}
