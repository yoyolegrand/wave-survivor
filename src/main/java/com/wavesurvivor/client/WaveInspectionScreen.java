package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.model.SpecialWave;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen d'inspection des vagues, accessible à tous les joueurs via /ws info.
 *
 * Navigation à 3 niveaux :
 *   HORDES      -> liste des hordes disponibles
 *   WAVES       -> liste des vagues 1..N d'une horde
 *   WAVE_DETAIL -> détail des mobs (type, count, HP, DMG) d'une vague donnée
 *
 * Data source : ClientConfigCache (rempli par SyncConfigPacket au login/reload).
 * Read-only : jamais d'édition depuis ce screen.
 */
@OnlyIn(Dist.CLIENT)
public class WaveInspectionScreen extends Screen {

    private enum Level { HORDES, WAVES, WAVE_DETAIL }

    private static final int PANEL_WIDTH = 400;
    private static final int ITEM_HEIGHT = 22;
    private static final int VISIBLE_ITEMS = 10;
    private static final int TITLE_Y = 15;
    private static final int LIST_Y = 40;
    private static final int BOTTOM_BAR_Y_FROM_BOTTOM = 30;

    private Level currentLevel = Level.HORDES;
    private HordeConfigMultiData selectedHorde;
    private int selectedWave = -1;
    private int scrollOffset = 0;

    /** Lignes précalculées pour le WAVE_DETAIL (rendues manuellement dans render). */
    private final List<Component> detailLines = new ArrayList<>();

    public WaveInspectionScreen() {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.inspection_des_vagues")));
    }

    // ==========================================================================
    // Init / rebuild
    // ==========================================================================

    @Override
    protected void init() {
        int panelX = (width - PANEL_WIDTH) / 2;

        switch (currentLevel) {
            case HORDES -> buildHordeList(panelX, LIST_Y);
            case WAVES -> buildWaveList(panelX, LIST_Y);
            case WAVE_DETAIL -> buildWaveDetail(); // remplit detailLines
        }

        // Bouton du bas : Retour ou Fermer
        String bottomLabel = (currentLevel == Level.HORDES) ? com.wavesurvivor.i18n.WSLang.t("ui.fermer") : com.wavesurvivor.i18n.WSLang.t("ui.retour");
        addRenderableWidget(Button.builder(
                Component.literal(bottomLabel),
                b -> goBack()
        ).bounds(width / 2 - 50, height - BOTTOM_BAR_Y_FROM_BOTTOM, 100, 20).build());
    }

    private void buildHordeList(int x, int y) {
        ModConfig cfg = ClientConfigCache.get();
        if (cfg == null || cfg.hordeConfigMulti == null || cfg.hordeConfigMulti.isEmpty()) return;

        List<HordeConfigMultiData> hordes = cfg.hordeConfigMulti;
        int endIdx = Math.min(hordes.size(), scrollOffset + VISIBLE_ITEMS);
        int by = y;

        for (int i = scrollOffset; i < endIdx; i++) {
            HordeConfigMultiData h = hordes.get(i);
            int nbWaves = h.configData != null ? h.configData.totalWaves : 0;
            String label = h.hordeName + "  (" + nbWaves + com.wavesurvivor.i18n.WSLang.t("ui.vague_b8a0") + (nbWaves > 1 ? "s" : "") + ")";
            final HordeConfigMultiData ref = h;
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> selectHorde(ref)
            ).bounds(x, by, PANEL_WIDTH, ITEM_HEIGHT - 2).build());
            by += ITEM_HEIGHT;
        }
    }

    private void buildWaveList(int x, int y) {
        if (selectedHorde == null || selectedHorde.configData == null) return;
        int total = selectedHorde.configData.totalWaves;
        int endIdx = Math.min(total, scrollOffset + VISIBLE_ITEMS);
        int by = y;

        for (int i = scrollOffset; i < endIdx; i++) {
            int waveNum = i + 1;
            String label = com.wavesurvivor.i18n.WSLang.t("ui.vague_ee32") + waveNum + "  " + ChatFormatting.GRAY + "— " + detectWaveType(selectedHorde, waveNum);
            final int wn = waveNum;
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> selectWave(wn)
            ).bounds(x, by, PANEL_WIDTH, ITEM_HEIGHT - 2).build());
            by += ITEM_HEIGHT;
        }
    }

    /**
     * Remplit detailLines avec le contenu textuel de la vague sélectionnée.
     * Le rendu se fait dans render() ligne par ligne.
     */
    private void buildWaveDetail() {
        detailLines.clear();
        if (selectedHorde == null || selectedHorde.configData == null || selectedWave < 1) return;

        HordeConfigMultiData.ConfigDataInner cd = selectedHorde.configData;

        // Vague boss ?
        BossWave boss = findBossWave(cd, selectedWave);
        if (boss != null) {
            detailLines.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.boss_661e") + boss.bossName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            String type = boss.useCustomEntity && boss.customEntityName != null
                    ? com.wavesurvivor.i18n.WSLang.t("ui.customentity") + boss.customEntityName
                    : boss.entityType;
            detailLines.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.type") + type).withStyle(ChatFormatting.WHITE));
            detailLines.add(Component.literal(String.format(com.wavesurvivor.i18n.WSLang.t("ui.hp_0f_dmg_1f_speed_2f_7b5e"),
                    boss.maxHealth, boss.attackDamage, boss.movementSpeed)).withStyle(ChatFormatting.GRAY));
            if (boss.useMinionSummon && boss.minionConfig != null) {
                detailLines.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.invocation_minions_activee")).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            detailLines.add(Component.empty());
        }

        // Vague normale (hordeEntities de base)
        detailLines.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.mobs_normaux_base")).withStyle(ChatFormatting.GOLD));
        if (cd.hordeEntities == null || cd.hordeEntities.isEmpty()) {
            detailLines.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.aucun_mob_configure")).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (HordeEntity he : cd.hordeEntities) {
                int count = he.getCountForWave(selectedWave, 1);
                String name = he.customName != null && !he.customName.isEmpty() ? he.customName : he.entityType;
                detailLines.add(Component.literal("  " + count + " x " + name).withStyle(ChatFormatting.WHITE));
                detailLines.add(Component.literal(String.format(com.wavesurvivor.i18n.WSLang.t("ui.hp_0f_dmg_1f_speed_2f"),
                        he.maxHealth, he.attackDamage, he.movementSpeed)).withStyle(ChatFormatting.GRAY));
            }
        }

        // Vagues spéciales possibles (aléatoire)
        if (cd.useSpecialWaves && cd.specialWaves != null && !cd.specialWaves.isEmpty()) {
            detailLines.add(Component.empty());
            int chance = cd.specialWaveChance;
            detailLines.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.vagues_speciales_possibles_chance") + chance + "%) :")
                    .withStyle(ChatFormatting.YELLOW));
            for (SpecialWave sw : cd.specialWaves) {
                detailLines.add(Component.literal("  * " + sw.name + com.wavesurvivor.i18n.WSLang.t("ui.poids") + sw.chance + ")")
                        .withStyle(ChatFormatting.WHITE));
                if (sw.entities != null) {
                    for (SpecialWave.SpecialWaveEntity swe : sw.entities) {
                        String name = swe.customName != null && !swe.customName.isEmpty() ? swe.customName : swe.entityType;
                        detailLines.add(Component.literal("      " + swe.count + " x " + name)
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
    }

    // ==========================================================================
    // Navigation
    // ==========================================================================

    private void selectHorde(HordeConfigMultiData h) {
        selectedHorde = h;
        currentLevel = Level.WAVES;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void selectWave(int wave) {
        selectedWave = wave;
        currentLevel = Level.WAVE_DETAIL;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void goBack() {
        switch (currentLevel) {
            case HORDES -> onClose();
            case WAVES -> {
                selectedHorde = null;
                currentLevel = Level.HORDES;
                scrollOffset = 0;
                rebuildWidgets();
            }
            case WAVE_DETAIL -> {
                selectedWave = -1;
                currentLevel = Level.WAVES;
                scrollOffset = 0;
                rebuildWidgets();
            }
        }
    }

    // ==========================================================================
    // Render
    // ==========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // Titre + fil d'ariane
        g.drawCenteredString(this.font, buildTitle(), width / 2, TITLE_Y, 0xFFFFFFFF);

        // Version du mod en haut à droite (aide au debug pour vérifier le jar chargé)
        String ver = "v" + getModVersion();
        int verWidth = this.font.width(ver);
        g.drawString(this.font, Component.literal(ver).withStyle(ChatFormatting.DARK_GRAY),
                width - verWidth - 6, 6, 0xFF888888);

        // Détail de vague : rendu manuel du texte
        if (currentLevel == Level.WAVE_DETAIL) {
            renderWaveDetail(g);
        }

        // Scroll indicator si liste trop longue
        renderScrollIndicator(g);

        // Aide en bas
        String hint = switch (currentLevel) {
            case HORDES -> com.wavesurvivor.i18n.WSLang.t("Molette pour scroller  |  Clic pour ouvrir");
            case WAVES -> com.wavesurvivor.i18n.WSLang.t("Molette pour scroller  |  Clic pour voir le détail");
            case WAVE_DETAIL -> com.wavesurvivor.i18n.WSLang.t("Molette pour scroller les détails");
        };
        g.drawCenteredString(this.font, Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY),
                width / 2, height - 50, 0xFFAAAAAA);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component buildTitle() {
        return switch (currentLevel) {
            case HORDES -> Component.literal(com.wavesurvivor.i18n.WSLang.t("=== Hordes disponibles ===")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            case WAVES -> {
                String name = selectedHorde != null ? selectedHorde.hordeName : "?";
                yield Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.horde_609e") + name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }
            case WAVE_DETAIL -> {
                String name = selectedHorde != null ? selectedHorde.hordeName : "?";
                yield Component.literal(name + com.wavesurvivor.i18n.WSLang.t("ui.vague") + selectedWave).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }
        };
    }

    private void renderWaveDetail(GuiGraphics g) {
        int x = (width - PANEL_WIDTH) / 2;
        int y = LIST_Y;
        int lineHeight = 11;
        int maxVisible = (height - LIST_Y - 70) / lineHeight;
        int endIdx = Math.min(detailLines.size(), scrollOffset + maxVisible);

        for (int i = scrollOffset; i < endIdx; i++) {
            g.drawString(this.font, detailLines.get(i), x, y, 0xFFFFFFFF);
            y += lineHeight;
        }
    }

    private void renderScrollIndicator(GuiGraphics g) {
        int total = maxItemsForLevel();
        int visible = (currentLevel == Level.WAVE_DETAIL) ? (height - LIST_Y - 70) / 11 : VISIBLE_ITEMS;
        if (total <= visible) return;

        String label = "[" + (scrollOffset + 1) + "-" + Math.min(total, scrollOffset + visible) + " / " + total + "]";
        g.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.DARK_GRAY),
                width - 100, height - 50, 0xFF888888);
    }

    // ==========================================================================
    // Scroll
    // ==========================================================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int total = maxItemsForLevel();
        int visible = (currentLevel == Level.WAVE_DETAIL) ? (height - LIST_Y - 70) / 11 : VISIBLE_ITEMS;
        int max = Math.max(0, total - visible);
        if (max > 0) {
            int newOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(delta)));
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildWidgets();
            }
        }
        return true;
    }

    private int maxItemsForLevel() {
        return switch (currentLevel) {
            case HORDES -> {
                ModConfig cfg = ClientConfigCache.get();
                yield (cfg != null && cfg.hordeConfigMulti != null) ? cfg.hordeConfigMulti.size() : 0;
            }
            case WAVES -> (selectedHorde != null && selectedHorde.configData != null) ? selectedHorde.configData.totalWaves : 0;
            case WAVE_DETAIL -> detailLines.size();
        };
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private static String detectWaveType(HordeConfigMultiData h, int wave) {
        if (h.configData == null) return com.wavesurvivor.i18n.WSLang.t("ui.normale");
        if (h.configData.useBossWaves && h.configData.bossWaves != null) {
            for (BossWave bw : h.configData.bossWaves) {
                if (bw.waveNumber == wave) {
                    return ChatFormatting.RED + com.wavesurvivor.i18n.WSLang.t("ui.boss_ed54") + ChatFormatting.GRAY + "(" + bw.bossName + ")";
                }
            }
        }
        if (h.configData.useSpecialWaves) {
            return ChatFormatting.YELLOW + com.wavesurvivor.i18n.WSLang.t("ui.normale_ou_speciale_aleatoire");
        }
        return com.wavesurvivor.i18n.WSLang.t("ui.normale");
    }

    private static BossWave findBossWave(HordeConfigMultiData.ConfigDataInner cd, int wave) {
        if (!cd.useBossWaves || cd.bossWaves == null) return null;
        for (BossWave bw : cd.bossWaves) {
            if (bw.waveNumber == wave) return bw;
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Retourne la version du mod (via ModList) pour l'affichage du screen. */
    private static String getModVersion() {
        return ModList.get().getModContainerById(WaveSurvivorMod.MODID)
                .map(mc -> mc.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
