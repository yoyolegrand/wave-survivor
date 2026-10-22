package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.BlessingEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * PACKS DE BÉNÉDICTIONS (1.8, opérateurs) : exporter sa couche (catégories créées, modifications, réglages) dans un fichier
 * config/wavesurvivor/blessing_packs/&lt;nom&gt;.json, ou importer un pack déposé dans ce dossier. Sans « remplacer », un conflit
 * (même identifiant déjà à toi) est ignoré et signalé.
 */
@OnlyIn(Dist.CLIENT)
public class BlessingPackScreen extends Screen {

    private final Screen parent;
    private final List<String> packs;
    private String name = "";
    private boolean overwrite = false;
    private int left, top, W, H;

    public BlessingPackScreen(Screen parent, List<String> packs) {
        super(Component.literal(WSLang.t("blesspack.title")));
        this.parent = parent;
        this.packs = packs;
    }

    @Override
    protected void init() {
        clearWidgets();
        W = Math.min(width - 8, 320);
        H = Math.min(height - 8, 250);
        left = (width - W) / 2;
        top = (height - H) / 2;
        EditBox box = new EditBox(font, left + 10, top + 44, W - 20, 16, Component.empty());
        box.setMaxLength(40);
        box.setFilter(s -> s.matches("[A-Za-z0-9_-]*"));
        box.setValue(name);
        box.setResponder(s -> name = s);
        box.setHint(Component.literal("§7" + WSLang.t("blesspack.name_hint")));
        addRenderableWidget(box);
        Button ex = Button.builder(Component.literal("§a" + WSLang.t("blesspack.export")), b -> send(BlessingEditorPackets.PackAction.EXPORT))
                .bounds(left + 10, top + 66, 96, 18).build();
        ex.setTooltip(Tooltip.create(WSLang.c("blesspack.export_tip")));
        ex.active = !name.isBlank();
        addRenderableWidget(ex);
        Button im = Button.builder(Component.literal("§b" + WSLang.t("blesspack.import")), b -> send(overwrite ? BlessingEditorPackets.PackAction.IMPORT_OVERWRITE : BlessingEditorPackets.PackAction.IMPORT))
                .bounds(left + 110, top + 66, 96, 18).build();
        im.setTooltip(Tooltip.create(WSLang.c("blesspack.import_tip")));
        im.active = !name.isBlank();
        addRenderableWidget(im);
        addRenderableWidget(Button.builder(Component.literal((overwrite ? "§e✔ " : "§7") + WSLang.t("blesspack.overwrite")), b -> { overwrite = !overwrite; init(); })
                .bounds(left + 210, top + 66, W - 220, 18).tooltip(Tooltip.create(WSLang.c("blesspack.overwrite_tip"))).build());
        // Packs trouvés dans le dossier : un clic remplit le nom
        int y = top + 108;
        for (int i = 0; i < packs.size() && y < top + H - 30; i++, y += 16) {
            final String n = packs.get(i);
            addRenderableWidget(Button.builder(Component.literal("§f" + n), b -> { name = n; init(); }).bounds(left + 10, y, W - 20, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose()).bounds(left + W / 2 - 40, top + H - 22, 80, 16).build());
    }

    private void send(byte action) {
        NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.PackAction(action, name));
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xEE101018);
        g.drawCenteredString(font, Component.literal("§l" + WSLang.t("blesspack.title")), left + W / 2, top + 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("§8" + WSLang.t("blesspack.hint")), left + W / 2, top + 22, 0xAAAAAA);
        g.drawString(font, "§6" + WSLang.t("blesspack.name"), left + 10, top + 34, 0xFFFFFF);
        g.drawString(font, "§6" + WSLang.t(packs.isEmpty() ? "blesspack.none" : "blesspack.found"), left + 10, top + 94, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
