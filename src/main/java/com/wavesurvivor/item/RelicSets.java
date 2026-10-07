package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * BONUS DE SET DES RELIQUES (1.5). Une famille compte les reliques DIFFÉRENTES qu'un joueur porte (inventaire,
 * main secondaire ou Curios). Paliers à 2 et 3 pièces, plus 4 pièces pour les familles de 4 reliques.
 *  🛡 Rempart     2 : Monolithe +10 % PV max · 3 : porteur près du Monolithe → il régénère 1 %/5 s, Résistance I autour
 *  🗡 Traque      2 : +10 % de dégâts aux cibles en surbrillance · 3 : élite tuée → Force I 8 s à l'équipe · 4 : Curée
 *  🌀 Abîme       2 : Brèche/Catalyseur détruit → +1 Essence (Kingdom) / 3 émeraudes · 3 : Sang de faille
 *  🔥 Éléments    2 : −20 % de dégâts magiques · 3 : aura élémentaire (20 % : feu / poison / lenteur sur l'attaquant)
 *  🪨 Inébranlable 2 : +2 robustesse · 3 : sous 30 % PV +4 armure et Résistance I · 4 : pas de chute, Talisman ×2
 *  💰 Fortune     2 : −10 % de plus chez les marchands · 3 : +10 % de PR en fin de horde · 4 : Jackpot (roulette)
 */
public class RelicSets {

    public enum Family {
        RAMPART("rampart", 3, ModItems.SCEAU_GARDIEN, ModItems.BANNIERE_RALLIEMENT, ModItems.PIERRE_FONDATION),
        HUNT("hunt", 4, ModItems.MARQUE_CHASSEUR, ModItems.OS_SACRE, ModItems.COEUR_ASSOIFFE, ModItems.CROC_ALPHA),
        ABYSS("abyss", 3, ModItems.ECLAT_BRECHE, ModItems.PRISME_CATALYSEUR, ModItems.FRAGMENT_PORTE),
        ELEMENTS("elements", 3, ModItems.AMULETTE_BRASIER, ModItems.CHARME_PHYLACTERE, ModItems.CRISTAL_GIVRE),
        STEADFAST("steadfast", 4, ModItems.ANNEAU_ANCRAGE, ModItems.CEINTURE_PLOMB, ModItems.OEIL_VEILLEUR, ModItems.TALISMAN_SOUFFLE),
        FORTUNE("fortune", 4, ModItems.LANGUE_ARGENT, ModItems.CLE_GARDIEN, ModItems.ANNEAU_PROSPECTEUR, ModItems.BOURSE_COLPORTEUR);

        public final String id;
        public final int max;
        private final List<Supplier<Item>> relics;

        @SafeVarargs
        Family(String id, int max, Supplier<Item>... relics) {
            this.id = id;
            this.max = max;
            this.relics = List.of(relics);
        }

        public List<Item> items() {
            return relics.stream().map(Supplier::get).toList();
        }
    }

    /** Famille d'une relique (une légendaire appartient à la famille de ses reliques d'origine), ou null. */
    public static Family familyOf(Item item) {
        for (Family f : Family.values()) for (Supplier<Item> s : f.relics) if (s.get() == item) return f;
        RelicFusions.Fusion fu = RelicFusions.ofResult(item);
        return fu == null ? null : familyOf(fu.a().get());
    }

    /** Pièces portées (calcul direct — utilisé aussi côté client pour l'infobulle). */
    public static int countNow(Player p, Family f) {
        int n = 0;
        for (Supplier<Item> s : f.relics) if (RelicEffects.hasRelic(p, s.get())) n++;
        return n;
    }

    // ─── Cache serveur (mis à jour chaque seconde) ───

    private static final Map<UUID, int[]> COUNTS = new ConcurrentHashMap<>();

    /** Pièces portées par ce joueur dans cette famille (cache d'une seconde côté serveur). */
    public static int count(Player p, Family f) {
        if (p == null) return 0;
        int[] c = COUNTS.get(p.getUUID());
        return c == null ? 0 : c[f.ordinal()];
    }

    public static boolean has(Player p, Family f, int pieces) {
        return count(p, f) >= pieces;
    }

    public static boolean anyone(MinecraftServer server, Family f, int pieces) {
        if (server == null) return false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p.isAlive() && !p.isSpectator() && has(p, f, pieces)) return true;
        return false;
    }

    /** Fortune 3 : multiplicateur de PR gagnés en fin de horde (branché avec les « PR gagnés en jouant »). */
    public static double prMultiplier(Player p) {
        return has(p, Family.FORTUNE, 3) ? 1.10 : 1.0;
    }

    /** Inébranlable 4 : nombre d'utilisations du Talisman du Dernier Souffle par vague. */
    public static int talismanUses(Player p) {
        return has(p, Family.STEADFAST, 4) ? 2 : 1;
    }

    // ─── Tick joueur : cache, messages de palier, bonus permanents ───

    private static final UUID TOUGHNESS_ID = UUID.fromString("8c3f1a20-5b7e-4d19-9e2a-1f6b7c3d5a01");
    private static final UUID ARMOR_LOW_ID = UUID.fromString("8c3f1a20-5b7e-4d19-9e2a-1f6b7c3d5a02");
    private static final UUID RIFT_SPEED_ID = UUID.fromString("8c3f1a20-5b7e-4d19-9e2a-1f6b7c3d5a03");

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        int[] now = new int[Family.values().length];
        for (Family f : Family.values()) now[f.ordinal()] = countNow(p, f);
        int[] before = COUNTS.put(p.getUUID(), now);
        if (before != null) {
            for (Family f : Family.values()) {
                int a = tierOf(before[f.ordinal()]), b = tierOf(now[f.ordinal()]);
                if (b > a) p.sendSystemMessage(WSLang.c("set.activated", WSLang.t("set." + f.id), b, f.max, WSLang.t("set." + f.id + "." + b)));
                else if (b < a) p.sendSystemMessage(WSLang.c("set.lost", WSLang.t("set." + f.id), now[f.ordinal()], f.max));
            }
        }
        // 🪨 Inébranlable 2 : +2 robustesse
        modifier(p, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "ws_set_toughness", 2.0, AttributeModifier.Operation.ADDITION,
                now[Family.STEADFAST.ordinal()] >= 2);
        // 🪨 Inébranlable 3 : sous 30 % PV, +4 armure et Résistance I
        boolean low = now[Family.STEADFAST.ordinal()] >= 3 && p.getHealth() < p.getMaxHealth() * 0.3f;
        modifier(p, Attributes.ARMOR, ARMOR_LOW_ID, "ws_set_last_stand", 4.0, AttributeModifier.Operation.ADDITION, low);
        if (low) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, false, true));
        // 🌀 Abîme 3 : Sang de faille — une Brèche ou un Catalyseur ouvert à ≤ 24 blocs : +15 % vitesse, Régénération I
        boolean rift = now[Family.ABYSS.ordinal()] >= 3 && riftNear(p, 24);
        modifier(p, Attributes.MOVEMENT_SPEED, RIFT_SPEED_ID, "ws_set_rift_blood", 0.15, AttributeModifier.Operation.MULTIPLY_BASE, rift);
        if (rift) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false, true));
    }

    private static int tierOf(int pieces) { return pieces >= 2 ? pieces : 0; }

    private static void modifier(Player p, net.minecraft.world.entity.ai.attributes.Attribute attr, UUID id, String name,
                                 double amount, AttributeModifier.Operation op, boolean on) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        boolean has = inst.getModifier(id) != null;
        if (on && !has) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
        else if (!on && has) inst.removeModifier(id);
    }

    private static boolean riftNear(Player p, double r) {
        return !p.level().getEntitiesOfClass(com.wavesurvivor.entity.BrecheEntity.class, p.getBoundingBox().inflate(r),
                b -> b.isAlive() && !b.isGreat() && b.distanceToSqr(p) <= r * r).isEmpty();
    }

    // ─── Tick serveur : 🛡 Rempart (Monolithe) ───

    private static float rampartBonus = 0f;
    private static BlockPos rampartSession = null;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 20 != 0) return;
        BlockPos mono = com.wavesurvivor.altar.AltarDefense.sessionPos();
        if (mono == null || !com.wavesurvivor.altar.AltarDefense.isActive()) {
            rampartBonus = 0f;
            rampartSession = null;
            return;
        }
        if (!mono.equals(rampartSession)) { rampartSession = mono; rampartBonus = 0f; }
        float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
        if (snap == null) return;
        // 2 pièces : +10 % de PV max tant qu'un porteur est connecté
        boolean on2 = anyone(server, Family.RAMPART, 2);
        if (on2 && rampartBonus == 0f) {
            rampartBonus = (snap[1] / 1f) * 0.10f;
            com.wavesurvivor.altar.AltarDefense.addMaxHp(rampartBonus);
        } else if (!on2 && rampartBonus > 0f) {
            com.wavesurvivor.altar.AltarDefense.addMaxHp(-rampartBonus);
            rampartBonus = 0f;
        }
        // 3 pièces : un porteur à ≤ 8 blocs → 1 % de PV toutes les 5 s + Résistance I aux joueurs proches
        String dim = com.wavesurvivor.altar.AltarDefense.sessionDim();
        boolean guard = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || !has(p, Family.RAMPART, 3)) continue;
            if (dim != null && !p.level().dimension().location().toString().equals(dim)) continue;
            if (p.distanceToSqr(mono.getX() + 0.5, mono.getY() + 0.5, mono.getZ() + 0.5) <= 64) { guard = true; break; }
        }
        if (guard) {
            if (server.getTickCount() % 100 == 0) com.wavesurvivor.altar.AltarDefense.healMonolith(snap[1] * 0.01f);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (dim != null && !p.level().dimension().location().toString().equals(dim)) continue;
                if (p.distanceToSqr(mono.getX() + 0.5, mono.getY() + 0.5, mono.getZ() + 0.5) <= 64) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true));
                }
            }
        }
    }

    // ─── Dégâts ───

    /** 🗡 Traque 4 — Curée : joueur → [enchaînement, tick du dernier kill]. */
    private static final Map<UUID, long[]> FRENZY = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        // Coups portés par un joueur
        if (e.getSource().getEntity() instanceof Player p && target != p) {
            // 🗡 Traque 2 : +10 % aux cibles en surbrillance
            if (has(p, Family.HUNT, 2) && (target.hasEffect(MobEffects.GLOWING) || target.isCurrentlyGlowing())) {
                e.setAmount(e.getAmount() * 1.10f);
            }
            // 🗡 Traque 4 : Curée (+5 % par kill enchaîné, 5 max), valable 3 s après le dernier kill
            if (has(p, Family.HUNT, 4)) {
                long[] fr = FRENZY.get(p.getUUID());
                if (fr != null && fr[0] > 0 && p.level().getGameTime() - fr[1] <= 60) e.setAmount(e.getAmount() * (1f + 0.05f * fr[0]));
            }
        }
        // Coups reçus par un joueur
        if (target instanceof Player p) {
            // 🪨 Inébranlable 4 : aucun dégât de chute
            if (e.getSource().is(DamageTypeTags.IS_FALL) && has(p, Family.STEADFAST, 4)) {
                e.setCanceled(true);
                return;
            }
            // 🔥 Éléments 2 : −20 % de dégâts magiques
            if (e.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO) && has(p, Family.ELEMENTS, 2)) {
                e.setAmount(e.getAmount() * 0.8f);
            }
            // 🔥 Éléments 3 : aura élémentaire — 20 % de chance de punir l'attaquant
            if (has(p, Family.ELEMENTS, 3) && e.getSource().getEntity() instanceof LivingEntity att && att instanceof Enemy
                    && p.getRandom().nextFloat() < 0.20f) {
                switch (p.getRandom().nextInt(3)) {
                    case 0 -> att.setSecondsOnFire(4);
                    case 1 -> att.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    default -> att.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                }
            }
        }
    }

    // ─── Kills ───

    @SubscribeEvent
    public void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity dead = e.getEntity();
        // 🌀 Abîme 2 : Brèche ou Catalyseur détruit
        if (dead instanceof com.wavesurvivor.entity.BrecheEntity b && !b.isGreat()) {
            if (has(p, Family.ABYSS, 2)) {
                if (com.wavesurvivor.horde.kingdom.KingdomTreasury.active()) {
                    com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.ESSENCE, 1);
                } else {
                    ItemStack em = new ItemStack(Items.EMERALD, 3);
                    if (!p.getInventory().add(em)) p.drop(em, false);
                }
                p.displayClientMessage(WSLang.c("set.abyss.reward"), true);
            }
            return;
        }
        if (!(dead instanceof Enemy)) return;
        // 🗡 Traque 3 : élite / champion / gardien / boss tué → Force I 8 s à toute l'équipe
        if (has(p, Family.HUNT, 3) && RelicEffects.isEliteTarget(dead)) {
            for (ServerPlayer q : p.server.getPlayerList().getPlayers()) {
                if (q.isAlive() && !q.isSpectator() && q.level() == p.level() && q.distanceToSqr(p) < 64 * 64) {
                    q.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0, false, true, true));
                }
            }
            p.displayClientMessage(WSLang.c("set.hunt.rally"), true);
        }
        // 🗡 Traque 4 : Curée — enchaînement de kills
        if (has(p, Family.HUNT, 4)) {
            long t = p.level().getGameTime();
            long[] fr = FRENZY.computeIfAbsent(p.getUUID(), k -> new long[2]);
            fr[0] = (t - fr[1] <= 60) ? Math.min(5, fr[0] + 1) : 0;
            fr[1] = t;
            if (fr[0] > 0) p.displayClientMessage(WSLang.c("set.hunt.frenzy", fr[0] * 5), true);
        }
    }

    // ─── 💰 Fortune 2 : −10 % de plus chez les marchands ───

    @SubscribeEvent
    public void onMerchantOpen(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open e) {
        if (!(e.getContainer() instanceof net.minecraft.world.inventory.MerchantMenu menu)) return;
        Player p = e.getEntity();
        if (p.level().isClientSide || !com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        if (!(p instanceof ServerPlayer sp) || countNow(sp, Family.FORTUNE) < 2) return;
        for (net.minecraft.world.item.trading.MerchantOffer o : menu.getOffers()) {
            int cut = (int) Math.floor(o.getBaseCostA().getCount() * 0.10);
            if (cut > 0) o.addToSpecialPriceDiff(-cut);
        }
    }

    /** 💰 Fortune 4 — Jackpot : 15 % de chance qu'un coffre roulette fasse un 2e tirage gratuit. */
    public static boolean jackpot(ServerPlayer p) {
        return p != null && has(p, Family.FORTUNE, 4) && p.getRandom().nextFloat() < 0.15f;
    }

    /** Joueur déconnecté : on oublie son cache. */
    public static void forget(UUID id) {
        COUNTS.remove(id);
        FRENZY.remove(id);
    }

    @SubscribeEvent
    public void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.getEntity().getUUID());
    }
}
