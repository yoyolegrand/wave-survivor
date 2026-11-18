package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import java.util.Map;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Effets de potion du mod. « Fraîcheur du désert » (Gourde fraîche) : protège du Soleil de plomb et réduit de moitié la durée des brûlures. */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, WaveSurvivorMod.MODID);

    public static final RegistryObject<MobEffect> DESERT_FRESHNESS = EFFECTS.register("desert_freshness",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x4FC3E8) {});

    /** « Soleil de plomb » (1.9) : posé en continu sur les joueurs exposés pendant l'événement du même nom ; donne l'icône du HUD et le voile orangé. */
    public static final RegistryObject<MobEffect> SCORCHING_SUN = EFFECTS.register("scorching_sun",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xF2A33A) {});

    /** « Effet de Faille » (2.1) : posé par les ennemis de la Faille, cumulable ; soins reçus −10 % par niveau (voir horde/rift/RiftEffect). */
    public static final RegistryObject<MobEffect> RIFT_EFFECT = EFFECTS.register("rift_effect", RiftEffectType::new);

    /** Attribut « Soins reçus » (2.1) : sert à afficher « −10 % Soins reçus » (en rouge, ×niveau) dans l'infobulle de l'Effet de Faille. */
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, WaveSurvivorMod.MODID);
    public static final RegistryObject<Attribute> HEALING_RECEIVED = ATTRIBUTES.register("healing_received",
            () -> new RangedAttribute("attribute.name.wavesurvivor.healing_received", 1.0, 0.0, 1024.0).setSyncable(true));

    /** Effet de Faille : modificateur −10 % (multiplicatif) par niveau sur « Soins reçus », ajouté à la première lecture
     *  (l'attribut est enregistré après les effets). La réduction réelle est faite par horde/rift/RiftEffect (LivingHealEvent). */
    private static final class RiftEffectType extends MobEffect {
        private boolean linked;

        RiftEffectType() { super(MobEffectCategory.HARMFUL, 0x8B5CF6); }

        @Override
        public Map<Attribute, AttributeModifier> getAttributeModifiers() {
            if (!linked && HEALING_RECEIVED.isPresent()) {
                linked = true;
                addAttributeModifier(HEALING_RECEIVED.get(), "6d1f7d52-3c1b-4e0e-9a51-2f6c0b5e7a11", -0.10, AttributeModifier.Operation.MULTIPLY_TOTAL);
            }
            return super.getAttributeModifiers();
        }
    }

    private ModEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
        ATTRIBUTES.register(bus);
        bus.addListener((EntityAttributeModificationEvent e) -> {
            if (!e.has(EntityType.PLAYER, HEALING_RECEIVED.get())) e.add(EntityType.PLAYER, HEALING_RECEIVED.get());
        });
    }
}
