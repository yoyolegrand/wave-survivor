package com.wavesurvivor.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.item.FallenSeraphArmorItem;
import com.wavesurvivor.item.SeraphArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * 2.0 — CALQUE LUMINEUX des armures du Séraphin : redessine chaque pièce portée avec sa texture « _glow » en pleine lumière
 * (RenderType.eyes, comme les yeux des araignées). Séraphin déchu : fissures, yeux, cœur et auréole roses (texture de Claude chat) ;
 * Séraphin : auréole et gemmes (texture provisoire générée depuis seraph_armor.png, remplaçable).
 * Ajouté aux joueurs et aux supports d'armure. En mode combat d'Epic Fight, le calque n'est pas dessiné (le corps y est animé par
 * Epic Fight, le calque serait décalé).
 */
@OnlyIn(Dist.CLIENT)
public class SeraphGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation SERAPH_GLOW = new ResourceLocation("wavesurvivor", "textures/models/armor/seraph_armor_glow.png");
    private static final ResourceLocation FALLEN_GLOW = FallenSeraphArmorModel.GLOW;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public SeraphGlowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || ArmorFlattener.calledByEpicFight()) return;
        for (EquipmentSlot slot : SLOTS) {
            Item item = entity.getItemBySlot(slot).getItem();
            HumanoidModel<?> model;
            ResourceLocation tex;
            if (item instanceof FallenSeraphArmorItem) {
                model = FallenSeraphArmorClient.model(slot, this.getParentModel());
                tex = FALLEN_GLOW;
            } else if (item instanceof SeraphArmorItem) {
                model = SeraphArmorClient.model(slot, this.getParentModel());
                tex = SERAPH_GLOW;
            } else continue;
            model.renderToBuffer(ps, buffers.getBuffer(RenderType.eyes(tex)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        }
    }

    /** Ajoute le calque aux deux apparences de joueur et au support d'armure (EntityRenderersEvent.AddLayers, bus MOD). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            LivingEntityRenderer r = event.getSkin(skin);
            if (r != null && r.getModel() instanceof HumanoidModel) r.addLayer(new SeraphGlowLayer(r));
        }
        LivingEntityRenderer stand = event.getRenderer(EntityType.ARMOR_STAND);
        if (stand != null && stand.getModel() instanceof HumanoidModel) stand.addLayer(new SeraphGlowLayer(stand));
    }
}
