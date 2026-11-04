package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particules du mod : flamme dorée (bougie votive). Le rendu client est enregistré dans ClientModEvents. */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, WaveSurvivorMod.MODID);

    public static final RegistryObject<SimpleParticleType> GOLD_FLAME = PARTICLES.register("gold_flame", () -> new SimpleParticleType(false));

    private ModParticles() {}

    public static void register(IEventBus bus) { PARTICLES.register(bus); }
}
