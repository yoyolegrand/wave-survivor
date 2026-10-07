package com.wavesurvivor.client;

import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.OpenAltarConfirmScreenPacket;
import com.wavesurvivor.network.TriggerAltarPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Écran de lancement d'une horde depuis l'autel lié.
 * Si la horde a plusieurs VARIANTES (Vanilla / Moddée...) : sélecteur ◀ ▶, infos de la variante,
 * mods requis (✓ installé / ✗ absent) et "Lancer" grisé s'il manque un mod.
 */
@OnlyIn(Dist.CLIENT)
public class AltarConfirmScreen extends Screen {

    private final OpenAltarConfirmScreenPacket data;
    private int index = 0;
    private Button launchBtn;
    /** Mutateurs cochés (gardés d'un lancement à l'autre pendant la session). */
    static final java.util.Set<com.wavesurvivor.horde.mutator.Mutator> SELECTED =
            java.util.EnumSet.noneOf(com.wavesurvivor.horde.mutator.Mutator.class);
    /** Niveau de difficulté choisi (gardé d'un lancement à l'autre pendant la session). */
    static com.wavesurvivor.horde.difficulty.Difficulty DIFF = com.wavesurvivor.horde.difficulty.Difficulty.NORMAL;
    /** Mode Kingdom : rôle choisi (« » = aucun), gardé d'un lancement à l'autre pendant la session. */
    static String ROLE = "";

    /** Infobulle du bouton de difficulté : nom du niveau + description. */
    private static String diffTooltip(com.wavesurvivor.horde.difficulty.Difficulty d) {
        return d.color + "§l" + com.wavesurvivor.i18n.WSLang.t("difficulty." + d.id) + "\n§7"
                + com.wavesurvivor.i18n.WSLang.t("difficulty." + d.id + ".desc") + "\n§8" + com.wavesurvivor.i18n.WSLang.t("difficulty.click");
    }

    public AltarConfirmScreen(OpenAltarConfirmScreenPacket data) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.autel_runique")));
        this.data = data;
    }

    private OpenAltarConfirmScreenPacket.Variant current() {
        return data.variants.get(Math.max(0, Math.min(index, data.variants.size() - 1)));
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int centerX = width / 2;

        // Sélecteur de variante (si plusieurs)
        if (data.variants.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> { cycle(-1); })
                    .bounds(centerX - 150, 110, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { cycle(1); })
                    .bounds(centerX + 130, 110, 20, 20).build());
        }

        launchBtn = Button.builder(
                        Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.lancer_la_horde")).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        btn -> {
                            NetworkHandler.CHANNEL.sendToServer(new TriggerAltarPacket(data.altarPos, current().hordeName(),
                                    com.wavesurvivor.horde.mutator.Mutator.join(SELECTED), DIFF.id,
                                    current().kingdom() ? ROLE : ""));
                            this.onClose();
                        })
                .bounds(centerX - 110, height - 60, 100, 22)
                .build();
        launchBtn.active = current().launchable();
        addRenderableWidget(launchBtn);

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.annuler")).withStyle(ChatFormatting.GRAY), btn -> this.onClose())
                .bounds(centerX + 10, height - 60, 100, 22).build());

        // Mutateurs : ouvre l'écran de sélection (nombre coché + multiplicateur de récompenses)
        String mult = String.format(java.util.Locale.ROOT, "%.2f", com.wavesurvivor.horde.mutator.Mutator.multiplier(SELECTED)).replace('.', ',');
        String mLabel = SELECTED.isEmpty() ? com.wavesurvivor.i18n.WSLang.t("mutator.button_none")
                : com.wavesurvivor.i18n.WSLang.t("mutator.button", SELECTED.size(), mult);
        addRenderableWidget(Button.builder(Component.literal(mLabel),
                        btn -> { if (minecraft != null) minecraft.setScreen(new MutatorScreen(this)); })
                .bounds(centerX - 16, height - 86, 126, 20).build());

        // Niveau de difficulté : clic = niveau suivant parmi ceux autorisés par la horde
        java.util.List<com.wavesurvivor.horde.difficulty.Difficulty> allowed =
                new java.util.ArrayList<>(com.wavesurvivor.horde.difficulty.Difficulty.allowed(current().difficulties()));
        if (!allowed.contains(DIFF)) DIFF = com.wavesurvivor.horde.difficulty.Difficulty.defaultFor(current().difficulties());
        Button diffBtn = Button.builder(Component.literal("⚔ " + DIFF.color + com.wavesurvivor.i18n.WSLang.t("difficulty." + DIFF.id)),
                        btn -> {
                            DIFF = allowed.get((allowed.indexOf(DIFF) + 1) % allowed.size());
                            init();
                        })
                .bounds(centerX - 110, height - 86, 90, 20).build();
        diffBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(diffTooltip(DIFF))));
        addRenderableWidget(diffBtn);

        // Mode Kingdom : rôle de celui qui lance (clic = rôle suivant parmi ceux proposés par la horde)
        if (current().kingdom()) {
            java.util.List<String> roles = new java.util.ArrayList<>();
            roles.add("");
            for (String r : current().roles().split(",")) if (!r.isBlank()) roles.add(r.trim());
            if (!roles.contains(ROLE)) ROLE = "";
            String rName = ROLE.isEmpty() ? com.wavesurvivor.i18n.WSLang.t("kingdom.role.none")
                    : com.wavesurvivor.i18n.WSLang.t("kingdom.role." + ROLE);
            Button roleBtn = Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.altar.role", rName)),
                            btn -> {
                                ROLE = roles.get((roles.indexOf(ROLE) + 1) % roles.size());
                                init();
                            })
                    .bounds(centerX - 110, height - 32, 220, 18).build();
            roleBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(ROLE.isEmpty()
                    ? com.wavesurvivor.i18n.WSLang.c("kingdom.role.none.desc")
                    : com.wavesurvivor.i18n.WSLang.c("kingdom.role." + ROLE + ".desc")));
            addRenderableWidget(roleBtn);
        }

        // Bouton "Custom" (haut droite) : couleur / particules de l'autel
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.custom_0434")),
                        btn -> NetworkHandler.CHANNEL.sendToServer(
                                new com.wavesurvivor.network.AltarCustomPackets.Request(data.altarPos)))
                .bounds(width - 90, 10, 80, 18).build());

        // Bouton "Codex" (haut gauche) : guide des mécaniques
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("codex.button")),
                        btn -> CodexScreen.open(this))
                .bounds(10, 10, 80, 18).build());
    }

    private void cycle(int dir) {
        int n = data.variants.size();
        index = ((index + dir) % n + n) % n;
        init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.fill(0, 0, width, height, 0xCC000000);
        OpenAltarConfirmScreenPacket.Variant v = current();

        g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.autel_runique_cfec")), width / 2, 30, 0xFFFF5555);
        g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.voulez_vous_sceller_le_pacte_et_invoquer")),
                width / 2, 55, 0xFFAAAAAA);

        int panelX = width / 2 - 150;
        int panelY = 80;
        int panelW = 300;
        int modsLines = v.mods().isEmpty() ? 1 : 1 + (v.mods().size() + 1) / 2;
        String[] lockLines = v.lock() == null || v.lock().isEmpty() ? new String[0] : v.lock().split("\n");
        int lockH = lockLines.length == 0 ? 0 : 14 + lockLines.length * 11;
        int panelH = 110 + modsLines * 12 + lockH;
        boolean ok = v.launchable();
        int border = ok ? 0xFFAA2222 : 0xFF666666;
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE1A1A2E);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, border);
        g.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, border);

        int cy = panelY + 10;
        g.drawCenteredString(font, Component.literal("§6§l📜 " + com.wavesurvivor.i18n.WSLang.t(data.familyName)), width / 2, cy, 0xFFFFAA00);
        // Difficulté affichée de la horde (étoiles), en haut à droite du panneau
        if (v.stars() > 0) {
            StringBuilder st = new StringBuilder();
            for (int s = 1; s <= 5; s++) st.append(s <= v.stars() ? "§6★" : "§8☆");
            g.drawString(font, Component.literal(st.toString()), panelX + panelW - 8 - font.width("★★★★★"), panelY + 6, 0xFFFFFFFF, false);
        }
        cy += 18;

        // Variante choisie
        String vl = com.wavesurvivor.i18n.WSLang.t(v.label());
        String label = v.mods().isEmpty() ? "§a" + vl : (ok ? "§b" + vl : "§7" + vl);
        String pos = data.variants.size() > 1 ? " §8(" + (index + 1) + "/" + data.variants.size() + ")" : "";
        g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.version") + label + pos), width / 2, cy + 6, 0xFFFFFFFF);
        cy += 26;

        g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.vagues_totales") + v.totalWaves() + com.wavesurvivor.i18n.WSLang.t("ui.boss") + v.totalBosses()),
                panelX + 20, cy, 0xFFAAAAAA, false);
        cy += 13;
        if (!v.specialInfo().isBlank()) {
            g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.speciales_5f11") + v.specialInfo()), panelX + 20, cy, 0xFFAAAAAA, false);
        }
        cy += 16;

        // Mods requis
        if (v.mods().isEmpty()) {
            g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.standalone_aucun_autre_mod_requis")), panelX + 20, cy, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.mods_requis")), panelX + 20, cy, 0xFFFFFFFF, false);
            cy += 12;
            for (int i = 0; i < v.mods().size(); i++) {
                OpenAltarConfirmScreenPacket.Mod m = v.mods().get(i);
                int x = panelX + 26 + (i % 2) * 138;
                int y = cy + (i / 2) * 12;
                String name = m.id();
                if (font.width(name) > 118) name = font.plainSubstrByWidth(name, 112) + "…";
                g.drawString(font, Component.literal((m.installed() ? "§a✔ " : "§c✘ ") + "§f" + name), x, y, 0xFFFFFFFF, false);
            }
        }

        // Déblocage : conditions de la horde (✔ remplie / ✖ manquante), au-dessus de la position de l'autel
        if (lockLines.length > 0) {
            int ly = panelY + panelH - 18 - lockH + 4;
            g.drawString(font, Component.literal((v.locked() ? "§c§l🔒 " : "§a§l🔓 ") + com.wavesurvivor.i18n.WSLang.t("unlock.title")),
                    panelX + 20, ly, 0xFFFFFFFF, false);
            for (int i = 0; i < lockLines.length; i++) {
                String l = lockLines[i];
                boolean done = l.startsWith("1|");
                String txt = l.length() > 2 ? l.substring(2) : l;
                g.drawString(font, Component.literal((done ? "§a✔ §7" : "§c✖ §f") + txt), panelX + 26, ly + 12 + i * 11, 0xFFFFFFFF, false);
            }
        }

        g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.position") + data.altarPos.getX() + ", " + data.altarPos.getY() + ", "
                + data.altarPos.getZ() + "]"), panelX + 20, panelY + panelH - 14, 0xFF666666, false);

        if (v.locked()) {
            g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("unlock.screen_locked")),
                    width / 2, height - 102, 0xFFFF6666);
        } else if (!ok) {
            g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.mods_manquants_installe_les_ou_choisis_u")),
                    width / 2, height - 102, 0xFFFF6666);
        } else {
            g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.une_fois_lancee_la_horde_ne_pourra_pas_e")),
                    width / 2, height - 102, 0xFFFF6666);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
