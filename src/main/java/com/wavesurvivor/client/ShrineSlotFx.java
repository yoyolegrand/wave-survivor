package com.wavesurvivor.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.shrine.ShrineGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CADRES D'EMPLACEMENT ANIMÉS des équipements améliorés à la Forge du Sanctuaire, dans tous les écrans (inventaire, coffres, Forge…) et sur la
 * barre d'objets. Le cadre (textures/gui/slot_fx/consecrated_N.png ou profaned_N.png, N = nombre d'améliorations de 1 à 3) est dessiné sur
 * l'emplacement de 16 px :
 *   • Consacré : scintillement doux ; dès 2 améliorations une lueur dorée ; à 3, un reflet qui balaie l'icône toutes les 2,5 s ;
 *   • Profané : pulsation ; dès 2 une lueur violette ; à 3 un tressaillement (l'icône se dédouble en violet, 2-3 images sur 12).
 * Barre d'objets : version plus légère (cadre et scintillement / pulsation seulement).
 * Option client : config/wavesurvivor/client_options.json › {"slotFx": "full" | "light" | "off"} (lu au lancement ; « light » = comme la barre d'objets
 * partout, « off » = aucun cadre animé).
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShrineSlotFx {

    private static final ResourceLocation[] CONSECRATED = frames("consecrated"), PROFANED = frames("profaned");
    private static volatile String mode = null;

    private ShrineSlotFx() {}

    private static ResourceLocation[] frames(String camp) {
        ResourceLocation[] r = new ResourceLocation[3];
        for (int i = 0; i < 3; i++) r[i] = new ResourceLocation(WaveSurvivorMod.MODID, "textures/gui/slot_fx/" + camp + "_" + (i + 1) + ".png");
        return r;
    }

    /** Réglage client : full (défaut), light, off. */
    private static String mode() {
        String m = mode;
        if (m != null) return m;
        m = "full";
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("client_options.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("slotFx")) m = o.get("slotFx").getAsString().toLowerCase();
            }
        } catch (Exception ignored) { }
        mode = m;
        return m;
    }

    @SubscribeEvent
    public static void register(RegisterItemDecorationsEvent e) {
        IItemDecorator deco = ShrineSlotFx::render;
        for (Item item : ForgeRegistries.ITEMS) e.register(item, deco);
    }

    /** @return false : on laisse les autres décorations (durabilité, quantité) s'afficher. */
    private static boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        if (!stack.hasTag()) return false;
        String align = ShrineGear.alignOf(stack);
        if (align == null) return false;
        int n = Math.min(3, ShrineGear.upgradesOf(stack).size());
        if (n <= 0 || mode().equals("off")) return false;
        boolean cons = ShrineGear.CONSECRATED.equals(align);
        boolean light = mode().equals("light") || Minecraft.getInstance().screen == null; // barre d'objets : version légère
        double t = (System.currentTimeMillis() % 100000L) / 1000.0;

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Lueur (dès 2 améliorations, écrans seulement) : teinte douce sur l'emplacement, coins arrondis
        if (n >= 2 && !light) {
            float a = (float) (0.16 + 0.07 * Math.sin(t * (cons ? 2.2 : 3.0)));
            int col = cons ? 0xFFE08A : 0xB54BFF;
            int argb = (Math.round(a * 255) << 24) | col;
            g.fill(x, y + 1, x + 16, y + 15, argb);
            g.fill(x + 1, y, x + 15, y + 16, argb);
        }

        // Tressaillement (profané, 3 améliorations) : l'icône se dédouble en violet 2-3 images sur 12
        if (!cons && n >= 3 && !light && ((long) (t * 12)) % 12 < 3) {
            int dx = (((long) (t * 24)) % 2 == 0) ? 1 : -1, dy = (((long) (t * 17)) % 2 == 0) ? 1 : 0;
            g.setColor(0.75f, 0.35f, 1.0f, 0.55f);
            g.pose().pushPose();
            g.pose().translate(0, 0, -50);
            g.renderItem(stack, x + dx, y + dy);
            g.pose().popPose();
            g.setColor(1f, 1f, 1f, 1f);
        }

        // Cadre : scintillement (consacré : luminosité 0,85 → 1,1) ou pulsation (profané : opacité 0,6 → 1)
        float bright, alpha;
        if (cons) {
            bright = (float) (0.975 + 0.125 * Math.sin(t * 3.0));
            alpha = 1f;
        } else {
            bright = 1f;
            alpha = (float) (0.8 + 0.2 * Math.sin(t * 4.0));
        }
        g.setColor(Math.min(1f, bright), Math.min(1f, bright), Math.min(1f, bright), alpha);
        g.blit((cons ? CONSECRATED : PROFANED)[n - 1], x, y, 16, 16, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);

        // Reflet (consacré, 3 améliorations) : bande diagonale qui balaie l'icône toutes les 2,5 s
        if (cons && n >= 3 && !light) {
            double phase = (t % 2.5) / 0.7;                 // 0 → 1 pendant 0,7 s puis pause
            if (phase < 1.0) {
                double head = phase * 34 - 9;               // position de la bande (diagonale x + y)
                for (int row = 0; row < 16; row++) {
                    int x0 = (int) Math.round(head - row), x1 = x0 + 3;
                    x0 = Math.max(0, x0);
                    x1 = Math.min(16, x1);
                    if (x1 > x0) g.fill(x + x0, y + row, x + x1, y + row + 1, 0x66FFFFFF);
                }
            }
        }
        RenderSystem.disableBlend();
        g.pose().popPose();
        return false;
    }
}
