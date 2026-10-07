package com.wavesurvivor.client;

import com.google.gson.Gson;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.CustomChestData;
import com.wavesurvivor.horde.roulette.ParticlePatterns;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.DeleteCustomChestPacket;
import com.wavesurvivor.network.SaveCustomChestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Screen de création / édition d'un roulette chest custom.
 *
 * PHASE 3 (actuelle) :
 *   - Popup ChestItemConfigPrompt avec % + qty + tier + message custom
 *   - Clic gauche sur ligne de reward → édition (ré-ouvre le popup pré-rempli)
 *   - Bouton [✕] → supprime
 *   - Bouton "Valider" → envoie SaveCustomChestPacket au serveur (Phase 5 basique)
 *   - Affichage tier dans la liste (avec couleur)
 *
 * PHASE 4 (à venir) : sélecteur particules + édition des templates par tier + édition du nom
 */
@OnlyIn(Dist.CLIENT)
public class ChestBuilderScreen extends Screen {

    private static final ChatFormatting[] TIER_COLORS = {
            ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD
    };
    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final String[] PARTICLE_PRESETS = {
            "none", "flame", "bubble", "enchant", "portal", "soul_flame", "dust_glow", "crimson_spore"
    };

    // État persistant
    private String chestName;
    private String particlesPreset = "none"; // Phase 4 permettra de changer
    private String pattern = "circle";        // Phase 6 : forme d'émission des particules
    private String chestColor = "auto";      // couleur du coffre et de sa clé (auto ou colorant Minecraft)
    private Button colorBtn;
    private String keyMaterial = "or";       // matériau du corps de la clé
    private Button materialBtn;
    private final Map<String, String> tierTemplates = CustomChestData.defaultTierMessages();
    private final List<CustomChestData.Reward> rewards = new ArrayList<>();
    private int rewardsScrollOffset = 0;

    // Mode édition : true si constructeur (CustomChestData) utilisé
    private boolean isEditMode = false;
    private String originalName = null;   // nom d'origine (pour delete même après rename)
    private boolean deletePending = false; // pattern 2-clics confirm

    // Layout
    private static final int INV_SLOT_SIZE = 18;
    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 4;
    private static final int REWARD_ROW_HEIGHT = 22;

    private int invX, invY;
    private int rewardListX, rewardListY, rewardListW;
    private int rewardListVisibleRows;
    private EditBox nameBox;
    private Button particlesBtn;
    private Button patternBtn;
    private Button deleteBtn;

    public ChestBuilderScreen(String initialName) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.creer_un_roulette_chest")));
        this.chestName = initialName != null && !initialName.isBlank() ? initialName : com.wavesurvivor.i18n.WSLang.t("ui.coffre_custom");
    }

    /**
     * Constructeur mode édition : reprend un chest existant.
     */
    public ChestBuilderScreen(CustomChestData existing) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.editer_un_roulette_chest")));
        this.chestName = existing.name;
        this.originalName = existing.name;
        this.isEditMode = true;
        this.particlesPreset = existing.particles != null ? existing.particles : "none";
        this.pattern = existing.pattern != null && !existing.pattern.isBlank() ? existing.pattern : "circle";
        this.chestColor = existing.color != null && !existing.color.isBlank() ? existing.color : "auto";
        this.keyMaterial = existing.keyMaterial != null && !existing.keyMaterial.isBlank() ? existing.keyMaterial : "or";
        if (existing.tierMessages != null && !existing.tierMessages.isEmpty()) {
            this.tierTemplates.putAll(existing.tierMessages);
        }
        if (existing.rewards != null) {
            for (CustomChestData.Reward r : existing.rewards) {
                CustomChestData.Reward copy = new CustomChestData.Reward();
                copy.item = r.item;
                copy.chance = r.chance;
                copy.minQty = r.minQty;
                copy.maxQty = r.maxQty;
                copy.tier = r.tier;
                copy.customMessage = r.customMessage;
                this.rewards.add(copy);
            }
        }
    }

    @Override
    protected void init() {
        int invGridW = INV_COLS * INV_SLOT_SIZE;
        int invGridH = INV_ROWS * INV_SLOT_SIZE + 4;
        invX = (width - invGridW) / 2;
        invY = height - invGridH - 42;

        rewardListX = 20;
        rewardListY = 120;   // repoussé vers le bas pour laisser place aux boutons config
        rewardListW = width - 40;
        rewardListVisibleRows = Math.max(1, (invY - rewardListY - 20) / REWARD_ROW_HEIGHT);

        // EditBox pour le nom du chest (au-dessus du titre "Total")
        nameBox = new EditBox(this.font, width / 2 - 120, 28, 240, 18, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.nom_du_coffre")));
        nameBox.setMaxLength(60);
        nameBox.setValue(chestName);
        nameBox.setResponder(v -> chestName = v);
        addRenderableWidget(nameBox);

        // Bouton cycle couleur (à droite du nom) : auto + 16 colorants. Maj+clic = couleur précédente
        colorBtn = Button.builder(colorLabel(), b -> cycleColor(hasShiftDown() ? -1 : 1))
                .bounds(width / 2 + 126, 27, 130, 20).build();
        addRenderableWidget(colorBtn);

        // Matériau du corps de la clé (sous la couleur). Maj+clic = précédent
        materialBtn = Button.builder(materialLabel(), b -> cycleMaterial(hasShiftDown() ? -1 : 1))
                .bounds(width / 2 + 126, 49, 130, 20).build();
        addRenderableWidget(materialBtn);

        // Bouton cycle particules (à gauche)
        particlesBtn = Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.particules_e33b") + particlesPreset + "  ↻"),
                b -> cycleParticles()
        ).bounds(20, 84, 200, 20).build();
        addRenderableWidget(particlesBtn);

        // Bouton cycle pattern (centre)
        patternBtn = Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.pattern") + pattern + "  ↻"),
                b -> cyclePattern()
        ).bounds(width / 2 - 100, 84, 200, 20).build();
        addRenderableWidget(patternBtn);

        // Bouton templates (à droite)
        addRenderableWidget(Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.templates_par_tier_5046")),
                b -> openTemplatesEditor()
        ).bounds(width - 220, 84, 200, 20).build());

        // Bouton Fermer + Valider + (édition) Supprimer
        if (isEditMode) {
            // Layout édition : [Supprimer] [Fermer] [Sauvegarder]
            deleteBtn = Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.supprimer")).withStyle(ChatFormatting.RED),
                    b -> onDeleteClicked()
            ).bounds(width / 2 - 235, height - 26, 130, 20).build();
            addRenderableWidget(deleteBtn);

            addRenderableWidget(Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")).withStyle(ChatFormatting.GRAY),
                    b -> onClose()
            ).bounds(width / 2 - 100, height - 26, 100, 20).build());

            addRenderableWidget(Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.sauvegarder")).withStyle(ChatFormatting.GREEN),
                    b -> save()
            ).bounds(width / 2 + 5, height - 26, 200, 20).build());
        } else {
            // Layout création : [Fermer] [Sauvegarder]
            addRenderableWidget(Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer_sans_sauver")).withStyle(ChatFormatting.GRAY),
                    b -> onClose()
            ).bounds(width / 2 - 155, height - 26, 150, 20).build());

            addRenderableWidget(Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.sauvegarder")).withStyle(ChatFormatting.GREEN),
                    b -> save()
            ).bounds(width / 2 + 5, height - 26, 150, 20).build());
        }

        rebuildRemoveButtons();
    }

    /** Pattern 2-clics : 1er clic transforme le label, 2ème clic envoie le packet. */
    private void onDeleteClicked() {
        if (!deletePending) {
            deletePending = true;
            if (deleteBtn != null) {
                deleteBtn.setMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.confirmer_1df0")).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            }
            return;
        }
        // 2ème clic : delete pour de vrai
        String toDelete = originalName != null ? originalName : chestName;
        NetworkHandler.CHANNEL.sendToServer(new DeleteCustomChestPacket(toDelete));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.suppression_demandee_pour") + toDelete + "'…")
                            .withStyle(ChatFormatting.RED), false);
        }
        onClose();
    }

    private void cycleParticles() {
        int idx = 0;
        for (int i = 0; i < PARTICLE_PRESETS.length; i++) {
            if (PARTICLE_PRESETS[i].equals(particlesPreset)) { idx = i; break; }
        }
        idx = (idx + 1) % PARTICLE_PRESETS.length;
        particlesPreset = PARTICLE_PRESETS[idx];
        if (particlesBtn != null) {
            particlesBtn.setMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.particules_e33b") + particlesPreset + "  ↻"));
        }
    }

    private void cycleMaterial(int dir) {
        var mats = com.wavesurvivor.horde.roulette.RouletteKeyItem.MATERIALS;
        int idx = 0;
        for (int i = 0; i < mats.length; i++) if (mats[i].id().equals(keyMaterial)) { idx = i; break; }
        idx = ((idx + dir) % mats.length + mats.length) % mats.length;
        keyMaterial = mats[idx].id();
        if (materialBtn != null) materialBtn.setMessage(materialLabel());
    }

    private Component materialLabel() {
        var m = com.wavesurvivor.horde.roulette.RouletteKeyItem.material(keyMaterial);
        return Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.cle"))
                .append(Component.literal("■ " + m.nameFr()).withStyle(s -> s.withColor(m.color())))
                .append(Component.literal("  ↻"));
    }

    private void cycleColor(int dir) {
        net.minecraft.world.item.DyeColor[] dyes = net.minecraft.world.item.DyeColor.values();
        int n = dyes.length + 1; // index 0 = auto
        int idx = 0;
        for (int i = 0; i < dyes.length; i++) if (dyes[i].getName().equals(chestColor)) { idx = i + 1; break; }
        idx = ((idx + dir) % n + n) % n;
        chestColor = idx == 0 ? "auto" : dyes[idx - 1].getName();
        if (colorBtn != null) colorBtn.setMessage(colorLabel());
    }

    private Component colorLabel() {
        net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byName(chestColor, null);
        if (dye == null) {
            return Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.couleur_ea1e")).append(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.auto")).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  ↻"));
        }
        int rgb = com.wavesurvivor.altar.AltarColors.rgb(dye);
        return Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.couleur_ea1e"))
                .append(Component.literal("■ " + com.wavesurvivor.altar.AltarColors.nameFr(dye))
                        .withStyle(s -> s.withColor(rgb)))
                .append(Component.literal("  ↻"));
    }

    private void cyclePattern() {
        String[] all = ParticlePatterns.ALL;
        int idx = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(pattern)) { idx = i; break; }
        }
        idx = (idx + 1) % all.length;
        pattern = all[idx];
        if (patternBtn != null) {
            patternBtn.setMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.pattern") + pattern + "  ↻"));
        }
    }

    private void openTemplatesEditor() {
        ChestTierTemplatesPrompt popup = new ChestTierTemplatesPrompt(this, tierTemplates, updated -> {
            tierTemplates.clear();
            tierTemplates.putAll(updated);
        });
        Minecraft.getInstance().setScreen(popup);
    }

    private void rebuildRemoveButtons() {
        int y = rewardListY;
        int endIdx = Math.min(rewards.size(), rewardsScrollOffset + rewardListVisibleRows);
        for (int i = rewardsScrollOffset; i < endIdx; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(
                    Component.literal("✕").withStyle(ChatFormatting.RED),
                    b -> removeReward(idx)
            ).bounds(rewardListX + rewardListW - 22, y + 2, 18, 18).build());
            y += REWARD_ROW_HEIGHT;
        }
    }

    // ─── Rendering ───

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        g.drawCenteredString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.roulette_chest_builder"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2, 12, 0xFFFFFFFF);
        // Le nom du coffre est maintenant éditable via nameBox (widget) — pas de drawCenteredString ici

        double total = totalChance();
        ChatFormatting totalColor = total > 100 ? ChatFormatting.RED
                : total >= 100 ? ChatFormatting.GREEN : ChatFormatting.WHITE;
        g.drawCenteredString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.total") + fmt(total) + "% / 100%")
                        .withStyle(totalColor, ChatFormatting.BOLD),
                width / 2, 54, 0xFFFFFFFF);

        // Ligne des boutons config (particules + templates) est placée via init(), pas de rendu texte ici

        g.drawString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.rewards") + rewards.size() + com.wavesurvivor.i18n.WSLang.t("ui.clique_une_ligne_pour_editer"))
                        .withStyle(ChatFormatting.GOLD),
                rewardListX, rewardListY - 14, 0xFFFFFFFF);

        renderRewardList(g, mouseX, mouseY);

        g.drawString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.ton_inventaire_clique_un_item_pour_l_ajo"))
                        .withStyle(ChatFormatting.GRAY),
                invX, invY - 12, 0xFFAAAAAA);

        renderPlayerInventory(g, mouseX, mouseY);

        // Version en haut droite
        String ver = "v" + getModVersion();
        int verWidth = this.font.width(ver);
        g.drawString(this.font,
                Component.literal(ver).withStyle(ChatFormatting.DARK_GRAY),
                width - verWidth - 6, 6, 0xFF888888);

        super.render(g, mouseX, mouseY, partialTick);

        ItemStack hovered = getInventoryStackAt(mouseX, mouseY);
        if (hovered != null && !hovered.isEmpty()) {
            g.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    private void renderRewardList(GuiGraphics g, int mouseX, int mouseY) {
        int y = rewardListY;
        int endIdx = Math.min(rewards.size(), rewardsScrollOffset + rewardListVisibleRows);

        if (rewards.isEmpty()) {
            g.drawString(this.font,
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.aucun_reward_clique_un_item_de_ton_inven"))
                            .withStyle(ChatFormatting.DARK_GRAY),
                    rewardListX + 10, y, 0xFF888888);
            return;
        }

        for (int i = rewardsScrollOffset; i < endIdx; i++) {
            CustomChestData.Reward r = rewards.get(i);
            boolean rowHovered = mouseX >= rewardListX && mouseX < rewardListX + rewardListW - 24
                    && mouseY >= y && mouseY < y + REWARD_ROW_HEIGHT - 2;
            int bgColor = rowHovered ? 0x80404060 : 0x60000000;
            g.fill(rewardListX, y, rewardListX + rewardListW, y + REWARD_ROW_HEIGHT - 2, bgColor);

            ItemStack stack = itemStackOf(r.item);
            if (!stack.isEmpty()) {
                g.renderItem(stack, rewardListX + 3, y + 2);
            }

            String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : r.item;
            ChatFormatting tierColor = tierColorOf(r.tier);
            g.drawString(this.font,
                    Component.literal(displayName).withStyle(tierColor),
                    rewardListX + 24, y + 3, 0xFFFFFFFF);

            // Ligne 2 : tier label + custom msg indicator
            String subInfo = tierLabelOf(r.tier);
            if (r.customMessage != null && !r.customMessage.isBlank()) subInfo += com.wavesurvivor.i18n.WSLang.t("ui.msg_custom");
            g.drawString(this.font,
                    Component.literal(subInfo).withStyle(ChatFormatting.DARK_GRAY),
                    rewardListX + 24, y + 12, 0xFF888888);

            // Qty + %
            String qty = "x" + r.minQty + (r.maxQty > r.minQty ? "-" + r.maxQty : "");
            g.drawString(this.font,
                    Component.literal(qty).withStyle(ChatFormatting.GRAY),
                    rewardListX + rewardListW - 130, y + 7, 0xFFAAAAAA);
            g.drawString(this.font,
                    Component.literal(fmt(r.chance) + "%").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                    rewardListX + rewardListW - 80, y + 7, 0xFFFFFF55);

            y += REWARD_ROW_HEIGHT;
        }

        if (rewards.size() > rewardListVisibleRows) {
            String scrollStr = "[" + (rewardsScrollOffset + 1) + "-" + endIdx + " / " + rewards.size() + "]";
            g.drawString(this.font,
                    Component.literal(scrollStr).withStyle(ChatFormatting.DARK_GRAY),
                    rewardListX + rewardListW - 60, rewardListY - 14, 0xFF666666);
        }
    }

    private void renderPlayerInventory(GuiGraphics g, int mouseX, int mouseY) {
        if (Minecraft.getInstance().player == null) return;
        var inv = Minecraft.getInstance().player.getInventory();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = 9 + row * 9 + col;
                int sx = invX + col * INV_SLOT_SIZE;
                int sy = invY + row * INV_SLOT_SIZE;
                renderSlot(g, sx, sy, inv.getItem(slotIdx), mouseX, mouseY);
            }
        }

        int hotbarY = invY + 3 * INV_SLOT_SIZE + 4;
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * INV_SLOT_SIZE;
            renderSlot(g, sx, hotbarY, inv.getItem(col), mouseX, mouseY);
        }
    }

    private void renderSlot(GuiGraphics g, int sx, int sy, ItemStack stack, int mouseX, int mouseY) {
        boolean hovered = mouseX >= sx && mouseX < sx + INV_SLOT_SIZE
                && mouseY >= sy && mouseY < sy + INV_SLOT_SIZE;
        g.fill(sx, sy, sx + INV_SLOT_SIZE, sy + INV_SLOT_SIZE,
                hovered ? 0xFF555555 : 0xFF373737);
        g.fill(sx + 1, sy + 1, sx + INV_SLOT_SIZE - 1, sy + INV_SLOT_SIZE - 1,
                hovered ? 0xFF888888 : 0xFF555555);

        if (!stack.isEmpty()) {
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(this.font, stack, sx + 1, sy + 1);
        }
    }

    // ─── Input ───

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            // Clic sur ligne de reward (hors bouton [X]) → édition
            int rewardIdx = getRewardIdxAt((int) mouseX, (int) mouseY);
            if (rewardIdx >= 0) {
                openConfigPromptForEdit(rewardIdx);
                return true;
            }
            // Clic sur item de l'inventaire → ajout
            ItemStack clicked = getInventoryStackAt((int) mouseX, (int) mouseY);
            if (clicked != null && !clicked.isEmpty()) {
                openConfigPromptForAdd(clicked);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (rewards.size() > rewardListVisibleRows) {
            int max = rewards.size() - rewardListVisibleRows;
            int newOffset = Math.max(0, Math.min(max, rewardsScrollOffset - (int) Math.signum(delta)));
            if (newOffset != rewardsScrollOffset) {
                rewardsScrollOffset = newOffset;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getRewardIdxAt(int mouseX, int mouseY) {
        // Zone cliquable : toute la ligne SAUF le bouton [X] à droite
        if (mouseX < rewardListX || mouseX > rewardListX + rewardListW - 26) return -1;
        int endIdx = Math.min(rewards.size(), rewardsScrollOffset + rewardListVisibleRows);
        for (int i = rewardsScrollOffset; i < endIdx; i++) {
            int y = rewardListY + (i - rewardsScrollOffset) * REWARD_ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + REWARD_ROW_HEIGHT - 2) return i;
        }
        return -1;
    }

    private ItemStack getInventoryStackAt(int mouseX, int mouseY) {
        if (Minecraft.getInstance().player == null) return null;
        var inv = Minecraft.getInstance().player.getInventory();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = invX + col * INV_SLOT_SIZE;
                int sy = invY + row * INV_SLOT_SIZE;
                if (mouseX >= sx && mouseX < sx + INV_SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + INV_SLOT_SIZE) {
                    return inv.getItem(9 + row * 9 + col);
                }
            }
        }
        int hotbarY = invY + 3 * INV_SLOT_SIZE + 4;
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * INV_SLOT_SIZE;
            if (mouseX >= sx && mouseX < sx + INV_SLOT_SIZE
                    && mouseY >= hotbarY && mouseY < hotbarY + INV_SLOT_SIZE) {
                return inv.getItem(col);
            }
        }
        return null;
    }

    // ─── Actions ───

    private void openConfigPromptForAdd(ItemStack stack) {
        String itemId = idOf(stack);
        // Vérifier doublon
        for (CustomChestData.Reward r : rewards) {
            if (r.item.equals(itemId)) return; // déjà présent, il faut cliquer sur sa ligne pour l'éditer
        }

        double remaining = 100 - totalChance();
        if (remaining <= 0) return;

        ChestItemConfigPrompt popup = new ChestItemConfigPrompt(this, stack, remaining, null, r -> {
            rewards.add(r);
            rebuildWidgets();
        });
        Minecraft.getInstance().setScreen(popup);
    }

    private void openConfigPromptForEdit(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        CustomChestData.Reward existing = rewards.get(idx);
        ItemStack stack = itemStackOf(existing.item);
        if (stack.isEmpty()) return;

        // % restant = 100 - total actuel + le % de l'existing (car sera remplacé)
        double remaining = 100 - totalChance() + existing.chance;

        ChestItemConfigPrompt popup = new ChestItemConfigPrompt(this, stack, remaining, existing, updated -> {
            // updated est le même objet que existing (modifié in-place), pas besoin de re-set
            rebuildWidgets();
        });
        Minecraft.getInstance().setScreen(popup);
    }

    private void removeReward(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        rewards.remove(idx);
        int max = Math.max(0, rewards.size() - rewardListVisibleRows);
        if (rewardsScrollOffset > max) rewardsScrollOffset = max;
        rebuildWidgets();
    }

    private void save() {
        // Vérifie que le nom n'est pas vide
        String finalName = chestName != null ? chestName.trim() : "";
        if (finalName.isEmpty()) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.nom_vide_impossible_de_sauver"))
                                .withStyle(ChatFormatting.RED), false);
            }
            return;
        }

        // Construit le CustomChestData et envoie au serveur
        CustomChestData data = new CustomChestData();
        data.name = finalName;
        data.particles = particlesPreset;
        data.pattern = pattern;
        data.color = chestColor;
        data.keyMaterial = keyMaterial;
        data.tierMessages = tierTemplates;
        data.rewards = new ArrayList<>(rewards);

        String json = new Gson().toJson(data);
        NetworkHandler.CHANNEL.sendToServer(new SaveCustomChestPacket(json));

        // Feedback local (le serveur enverra aussi un chat msg)
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.coffre") + finalName + com.wavesurvivor.i18n.WSLang.t("ui.envoye_au_serveur"))
                            .withStyle(ChatFormatting.GREEN), false);
        }
        onClose();
    }

    // ─── Helpers ───

    private double totalChance() {
        double t = 0;
        for (CustomChestData.Reward r : rewards) t += r.chance;
        return t;
    }

    private static ChatFormatting tierColorOf(String tier) {
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(tier)) return TIER_COLORS[i];
        }
        return ChatFormatting.WHITE;
    }

    private static String tierLabelOf(String tier) {
        return switch (tier != null ? tier : "COMMON") {
            case "UNCOMMON" -> com.wavesurvivor.i18n.WSLang.t("Peu commun");
            case "RARE" -> com.wavesurvivor.i18n.WSLang.t("Rare");
            case "EPIC" -> com.wavesurvivor.i18n.WSLang.t("Épique");
            case "LEGENDARY" -> com.wavesurvivor.i18n.WSLang.t("Légendaire");
            default -> com.wavesurvivor.i18n.WSLang.t("ui.commun");
        };
    }

    private static ItemStack itemStackOf(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item == null) return ItemStack.EMPTY;
            return new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    private static String getModVersion() {
        return ModList.get().getModContainerById(WaveSurvivorMod.MODID)
                .map(mc -> mc.getModInfo().getVersion().toString())
                .orElse("?");
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
