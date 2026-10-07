package com.wavesurvivor.horde.pact;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Événements du système Pacte de Sang :
 *  - Right-click sur AIR ou sur BLOCK avec un player_head en main → check si match un pacte, active
 *  - Respawn du joueur → reset cooldown (pour ne pas rester bloqué sans effet)
 */
public class BloodPactEventHandler {

    /** Right-click en l'air (sans viser un block). */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        handleRightClick(event);
    }

    /** Right-click en visant un block — on veut aussi trigger, mais seulement si pas un block interactif standard. */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Ne pas cancel les interactions block normales — mais si le joueur a une tête de pacte
        // en main et clic dans le vide contre un block non-interactif, on veut activer.
        // On délègue à la même logique : si match pacte, on cancel et on active.
        handleRightClick(event);
    }

    private void handleRightClick(PlayerInteractEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || held.getItem() != Items.PLAYER_HEAD) return;

        CompoundTag tag = held.getTag();
        if (tag == null) return;
        String nbtStr = tag.toString();

        BloodPactConfig pact = BloodPactRegistry.findByNbtString(nbtStr);
        if (pact == null) return;

        // Match : on prend le contrôle, on cancel l'event, on délègue au manager
        event.setCanceled(true);
        BloodPactManager.tryActivate(player, held, pact);
    }

    /** Reset du cooldown au respawn (sinon le joueur reste bloqué après mort sans avoir les effets). */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            BloodPactManager.resetCooldown(sp);
        }
    }
}
