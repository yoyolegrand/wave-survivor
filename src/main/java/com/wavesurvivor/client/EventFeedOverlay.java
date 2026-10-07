package com.wavesurvivor.client;

import com.wavesurvivor.network.EventFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * FIL D'ÉVÉNEMENTS (HUD) — encart à gauche de l'écran, au tiers supérieur : 4 notifications visibles au plus
 * (les suivantes attendent leur tour), 8 s chacune (12 s pour les importantes), entrée en glissant, sortie en fondu.
 */
@OnlyIn(Dist.CLIENT)
public final class EventFeedOverlay implements IGuiOverlay {

    public static final EventFeedOverlay INSTANCE = new EventFeedOverlay();
    private static final int MAX = 4, WIDTH = 240;

    private record Entry(Component text, ItemStack icon, int color, boolean important, long start, long life) {}

    private static final List<Entry> SHOWN = new ArrayList<>();
    private static final ArrayDeque<EventFeedPacket> WAITING = new ArrayDeque<>();

    private EventFeedOverlay() {}

    public static void push(EventFeedPacket p) {
        WAITING.add(p);
        if (WAITING.size() > 30) WAITING.poll(); // rafale : on ne garde que les plus récents
    }

    private static ItemStack icon(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        long now = System.currentTimeMillis();
        SHOWN.removeIf(e -> now - e.start() > e.life());
        while (SHOWN.size() < MAX && !WAITING.isEmpty()) {
            EventFeedPacket p = WAITING.poll();
            SHOWN.add(new Entry(p.text, icon(p.icon), p.color, p.important, now, p.important ? 12000 : 8000));
        }
        if (SHOWN.isEmpty()) return;
        Font font = mc.font;
        int y = Math.max(28, (int) (screenH * 0.14));
        for (Entry e : SHOWN) {
            long age = now - e.start();
            float alpha = age > e.life() - 1000 ? Math.max(0f, (e.life() - age) / 1000f) : 1f;  // fondu la dernière seconde
            float slide = Math.min(1f, age / 180f);                                            // entrée en glissant
            int a = Math.max(8, (int) (alpha * 255));
            List<FormattedCharSequence> lines = font.split(e.text(), WIDTH - 30);
            if (lines.size() > 2) lines = lines.subList(0, 2);
            int h = 6 + lines.size() * 10 + (e.important() ? 4 : 0);
            int x = (int) (-WIDTH + (WIDTH + 6) * slide);
            int bg = ((int) (alpha * (e.important() ? 0xD0 : 0xA8)) << 24) | 0x101010;
            int col = (a << 24) | (e.color() & 0xFFFFFF);
            g.fill(x, y, x + WIDTH, y + h, bg);
            g.fill(x, y, x + 2, y + h, col);                                                  // barre de couleur
            if (e.important()) {
                g.fill(x, y, x + WIDTH, y + 1, col);
                g.fill(x, y + h - 1, x + WIDTH, y + h, col);
                g.fill(x + WIDTH - 1, y, x + WIDTH, y + h, col);
            }
            if (!e.icon().isEmpty() && alpha > 0.3f) {
                g.pose().pushPose();
                g.pose().translate(x + 6, y + (h - 12) / 2f, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.renderItem(e.icon(), 0, 0);
                g.pose().popPose();
            }
            int ty = y + 4 + (e.important() ? 2 : 0);
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 22, ty, (a << 24) | 0xFFFFFF, true);
                ty += 10;
            }
            y += h + 3;
        }
    }
}
