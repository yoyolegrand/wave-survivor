package com.wavesurvivor.item;

import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * VOILE DU DÉSERT (1.7) — effets de vitesse : +6 % partout et +14 % de plus en marchant sur du sable ou du grès,
 * multipliés par le niveau de la relique (I ×1, II ×1,5, III ×2). L'immunité à la tempête de sable et aux sables
 * mouvants est lue directement par la tempête (StormManager) et les sables mouvants (SkillQuicksand) ; le séisme,
 * lui, n'est jamais évité par la relique. Enregistré dans WaveSurvivorMod.
 */
public class DesertRelicEvents {

    private static final UUID SPEED = UUID.nameUUIDFromBytes("ws_voile_desert_speed".getBytes(StandardCharsets.UTF_8));

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        Player p = e.player;
        if (e.phase != TickEvent.Phase.END || p.level().isClientSide || p.tickCount % 10 != 0) return;
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        double want = 0;
        var item = ModItems.VOILE_DESERT.get();
        if (RelicEffects.hasRelic(p, item)) {
            double m = RelicEffects.mult(RelicEffects.level(p, item));
            want = 0.06 * m + (onSand(p) ? 0.14 * m : 0);
        }
        AttributeModifier cur = inst.getModifier(SPEED);
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        if (cur != null) inst.removeModifier(SPEED);
        if (want > 0) inst.addTransientModifier(new AttributeModifier(SPEED, "Voile du Désert", want, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /** Sable, sable rouge, grès (toutes variantes) sous les pieds ; pas le sable des âmes (il ralentit). */
    private static boolean onSand(Player p) {
        BlockState st = p.level().getBlockState(p.blockPosition().below());
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        return id.contains("sand") && !id.contains("soul");
    }
}
