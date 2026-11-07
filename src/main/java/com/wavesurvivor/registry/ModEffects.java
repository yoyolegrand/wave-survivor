package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Effets de potion du mod. « Fraîcheur du désert » (Gourde fraîche) : protège du Soleil de plomb et réduit de moitié la durée des brûlures. */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, WaveSurvivorMod.MODID);

    public static final RegistryObject<MobEffect> DESERT_FRESHNESS = EFFECTS.register("desert_freshness",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x4FC3E8) {});

    private ModEffects() {}

    public static void register(IEventBus bus) { EFFECTS.register(bus); }
}
