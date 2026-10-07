package com.wavesurvivor.item;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * EFFETS DES RELIQUES (actives dans l'inventaire OU équipées dans un emplacement Curios, si Curios est chargé).
 *   Anneau d'Ancrage       : immunité Lévitation · −25 % dégâts de chute
 *   Amulette du Brasier    : immunité feu / lave, ne brûle jamais
 *   Charme de Phylactère   : immunité Wither + Poison
 *   Œil du Veilleur        : immunité Cécité + Ténèbres ; monstres à ≤ 16 blocs en surbrillance
 *   Ceinture de Plomb      : aucun recul (+ poussées des compétences du mod via immovable())
 */
public class RelicEffects {

    // ─── Curios (optionnel, par réflexion) ───
    private static Boolean curiosLoaded = null;
    private static Method getInventory, isEquipped;

    /** Curios est-il installé (et son API compatible) ? Sinon, les reliques s'équipent dans l'autel de Renaissance. */
    public static boolean curiosAvailable() {
        if (curiosLoaded == null) curiosEquipped(null, net.minecraft.world.item.Items.AIR);
        return Boolean.TRUE.equals(curiosLoaded);
    }

    private static boolean curiosEquipped(LivingEntity e, Item item) {
        if (curiosLoaded == null) {
            curiosLoaded = ModList.get().isLoaded("curios");
            if (curiosLoaded) {
                try {
                    Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                    getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                    Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                    isEquipped = handler.getMethod("isEquipped", Predicate.class);
                    WaveSurvivorMod.LOGGER.info("[Reliques] Intégration Curios active.");
                } catch (Exception ex) {
                    curiosLoaded = false;
                    WaveSurvivorMod.LOGGER.warn("[Reliques] Curios détecté mais API incompatible : {}", ex.getMessage());
                }
            }
        }
        if (!curiosLoaded || e == null) return false;
        try {
            Object lazy = getInventory.invoke(null, e);
            if (!(lazy instanceof LazyOptional<?> lo)) return false;
            Optional<?> h = lo.resolve();
            if (h.isEmpty()) return false;
            Predicate<ItemStack> match = s -> s.is(item);
            return (Boolean) isEquipped.invoke(h.get(), match);
        } catch (Exception ex) {
            return false;
        }
    }

    /** L'entité porte-t-elle cette relique (inventaire, main secondaire ou Curios) — ou une légendaire qui la contient ? */
    public static boolean hasRelic(LivingEntity e, Item item) {
        if (hasDirect(e, item)) return true;
        for (RelicFusions.Fusion f : RelicFusions.all()) {
            if ((f.a().get() == item || f.b().get() == item) && hasDirect(e, f.result().get())) return true;
        }
        return false;
    }

    /** Cet objet précis est-il ÉQUIPÉ (Curios, ou autel de Renaissance sans Curios) ? Le sac ne compte plus (1.5). */
    public static boolean hasDirect(LivingEntity e, Item item) {
        if (!(e instanceof Player p)) return false; // les reliques ne concernent que les joueurs
        if (curiosAvailable()) return curiosEquipped(e, item);
        return RelicEquip.has(p, item);
    }

    /** Ceinture de Plomb : à utiliser par les compétences qui poussent / attirent les joueurs. */
    public static boolean immovable(LivingEntity e) {
        return hasRelic(e, ModItems.CEINTURE_PLOMB.get());
    }

    /** Relique qui immunise contre cet effet, ou null. */
    private static Item immunityFor(MobEffect effect) {
        if (effect == MobEffects.LEVITATION) return ModItems.ANNEAU_ANCRAGE.get();
        if (effect == MobEffects.WITHER || effect == MobEffects.POISON) return ModItems.CHARME_PHYLACTERE.get();
        if (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS) return ModItems.OEIL_VEILLEUR.get();
        if (effect == MobEffects.MOVEMENT_SLOWDOWN) return ModItems.CRISTAL_GIVRE.get();
        if (effect == MobEffects.WEAKNESS || effect == MobEffects.CONFUSION) return ModItems.EGIDE_FLEAUX.get();
        return null;
    }

    /** Un joueur connecté (vivant, hors spectateur) porte-t-il cette relique ? (effets d'équipe) */
    public static boolean anyoneHas(net.minecraft.server.MinecraftServer server, Item item) {
        if (server == null) return false;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isAlive() && !p.isSpectator() && hasRelic(p, item)) return true;
        }
        return false;
    }

    // ─── Niveaux I → III (1.5) ───

    /** NBT du niveau d'une relique (absent = niveau I). */
    public static final String LEVEL_TAG = "ws_relic_lvl";

    public static int levelOf(ItemStack st) {
        if (st == null || st.isEmpty() || !st.hasTag()) return 1;
        int l = st.getTag().getInt(LEVEL_TAG);
        return Math.max(1, Math.min(3, l == 0 ? 1 : l));
    }

    public static void setLevel(ItemStack st, int lvl) {
        st.getOrCreateTag().putInt(LEVEL_TAG, Math.max(1, Math.min(3, lvl)));
    }

    /** Multiplicateur d'effet selon le niveau : I = ×1, II = ×1,5, III = ×2. */
    public static float mult(int lvl) {
        return lvl >= 3 ? 2f : lvl == 2 ? 1.5f : 1f;
    }

    private static Method findCurios, slotStack;

    /** Meilleur niveau de cette relique portée (inventaire, main secondaire, Curios) ; 0 = non portée. */
    public static int level(LivingEntity e, Item item) {
        if (!(e instanceof Player p)) return 0;
        int best = 0;
        // Sans Curios : reliques équipées dans l'autel de Renaissance
        if (!curiosAvailable()) {
            for (ItemStack st : RelicEquip.list(p)) if (st.is(item)) best = Math.max(best, levelOf(st));
        }
        if (best >= 3) return best;
        // Légendaire portée : ses deux reliques d'origine agissent au niveau II
        if (best < RelicFusions.PARENT_LEVEL) {
            for (RelicFusions.Fusion f : RelicFusions.all()) {
                if ((f.a().get() == item || f.b().get() == item) && hasDirect(e, f.result().get())) {
                    best = RelicFusions.PARENT_LEVEL;
                    break;
                }
            }
        }
        if (curiosAvailable() && curiosEquipped(e, item)) {
            int c = 1;
            try {
                Object lazy = getInventory.invoke(null, e);
                if (lazy instanceof LazyOptional<?> lo && lo.resolve().isPresent()) {
                    Object h = lo.resolve().get();
                    if (findCurios == null) findCurios = h.getClass().getMethod("findCurios", Item.class);
                    for (Object r : (java.util.List<?>) findCurios.invoke(h, item)) {
                        if (slotStack == null) slotStack = r.getClass().getMethod("stack");
                        c = Math.max(c, levelOf((ItemStack) slotStack.invoke(r)));
                    }
                }
            } catch (Exception ignored) {}
            best = Math.max(best, c);
        }
        return best;
    }

    /** Bonus d'une relique selon son niveau ({@code base} × 1 / 1,5 / 2), ou 0 si elle n'est pas portée. */
    public static float scaled(LivingEntity e, Item item, float base) {
        int l = level(e, item);
        return l <= 0 ? 0f : base * mult(l);
    }

    /** Meilleur niveau de cette relique parmi les joueurs connectés (effets d'équipe) ; 0 = personne. */
    public static int bestLevel(net.minecraft.server.MinecraftServer server, Item item) {
        if (server == null) return 0;
        int best = 0;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isAlive() && !p.isSpectator()) best = Math.max(best, level(p, item));
        }
        return best;
    }

    /** Élites, champions, gardiens et boss (Croc de l'Alpha). */
    public static boolean isEliteTarget(LivingEntity e) {
        if (e == null) return false;
        var data = e.getPersistentData();
        if (data.getBoolean("ws_elite") || data.getBoolean("ws_kingdom_guardian") || data.getBoolean("ws_obj_champion")) return true;
        if (com.wavesurvivor.horde.boss.BossManager.isBoss(e.getUUID())) return true;
        return e instanceof net.minecraft.world.entity.boss.wither.WitherBoss || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || e instanceof net.minecraft.world.entity.monster.warden.Warden;
    }

    // ─── Immunités aux effets ───

    @SubscribeEvent
    public void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Player p)) return;
        MobEffectInstance inst = event.getEffectInstance();
        Item relic = immunityFor(inst.getEffect());
        int lvl = relic == null ? 0 : level(p, relic);
        if (lvl <= 0) return;
        event.setResult(Event.Result.DENY);
        // Niveau II : l'effet bloqué donne Régénération I (3 s) — Niveau III : il est renvoyé sur un monstre proche
        if (lvl >= 2 && !p.hasEffect(MobEffects.REGENERATION)) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
        if (lvl >= 3) {
            LivingEntity near = p.level().getNearestEntity(LivingEntity.class,
                    net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(6), p, p.getX(), p.getY(), p.getZ(),
                    p.getBoundingBox().inflate(6));
            if (near instanceof Enemy) near.addEffect(new MobEffectInstance(inst.getEffect(), Math.max(40, inst.getDuration()), inst.getAmplifier()));
        }
    }

    /** Sceau du Gardien : facteur de dégâts du Monolithe (−30 / −45 / −60 % selon le niveau), 1 = aucun porteur proche. */
    public static float guardianFactor(net.minecraft.server.MinecraftServer server, String dim, net.minecraft.core.BlockPos pos) {
        if (server == null || pos == null) return 1f;
        int best = 0;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            // Clé de Voûte (légendaire) : le Sceau protège le Monolithe où que soit son porteur
            if (hasDirect(p, ModItems.CLE_VOUTE.get())) best = Math.max(best, RelicFusions.PARENT_LEVEL);
            if (dim != null && !p.level().dimension().location().toString().equals(dim)) continue;
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) continue;
            best = Math.max(best, level(p, ModItems.SCEAU_GARDIEN.get()));
        }
        return best <= 0 ? 1f : 1f - 0.30f * mult(best);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        if (event.phase != TickEvent.Phase.END || p.level().isClientSide || p.tickCount % 10 != 0) return;
        // Effets déjà actifs (relique récupérée pendant l'effet)
        for (MobEffectInstance inst : p.getActiveEffects().toArray(new MobEffectInstance[0])) {
            Item relic = immunityFor(inst.getEffect());
            if (relic != null && hasRelic(p, relic)) p.removeEffect(inst.getEffect());
        }
        // Amulette du Brasier : ne brûle jamais
        if (p.isOnFire() && hasRelic(p, ModItems.AMULETTE_BRASIER.get())) p.clearFire();
        // Cristal de Givre : jamais gelé (neige poudreuse, compétences de givre)
        if (p.getTicksFrozen() > 0 && hasRelic(p, ModItems.CRISTAL_GIVRE.get())) p.setTicksFrozen(0);
        // Œil du Veilleur : monstres proches en surbrillance
        if (p.tickCount % 20 == 0 && hasRelic(p, ModItems.OEIL_VEILLEUR.get())) {
            for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16),
                    e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(p) <= 256)) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
            }
        }
    }

    // ─── Dégâts ───

    /** Amulette du Brasier : aucun dégât de feu / lave. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) return;
        if (event.getSource().is(DamageTypeTags.IS_FIRE) && hasRelic(p, ModItems.AMULETTE_BRASIER.get())) {
            event.setCanceled(true);
            p.clearFire();
        }
    }

    /** Anneau d'Ancrage : −25 % de dégâts de chute. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            // Ancre du Colosse (légendaire) : aucun dégât de chute
            if (hasDirect(p, ModItems.ANCRE_COLOSSE.get())) { event.setCanceled(true); return; }
            float cut = Math.min(0.9f, scaled(p, ModItems.ANNEAU_ANCRAGE.get(), 0.25f)); // 25 / 37 / 50 %
            if (cut > 0) event.setAmount(event.getAmount() * (1f - cut));
        }
    }

    /** Ceinture de Plomb : aucun recul. */
    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && immovable(p)) event.setCanceled(true);
    }

    // ─── R2 : combat ───

    /** Coups portés par un joueur : Os Sacré (morts-vivants), Éclat de Brèche (Brèches), Marque du Chasseur (surbrillance). */
    @SubscribeEvent
    public void onHurtByPlayer(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player p) || p.level().isClientSide) return;
        LivingEntity target = event.getEntity();
        if (target == p) return;
        if (target.getMobType() == net.minecraft.world.entity.MobType.UNDEAD) {
            event.setAmount(event.getAmount() * (1f + scaled(p, ModItems.OS_SACRE.get(), 0.25f)));
        }
        if (target instanceof com.wavesurvivor.entity.BrecheEntity) {
            event.setAmount(event.getAmount() * (1f + scaled(p, ModItems.ECLAT_BRECHE.get(), 1.0f)));
        }
        // Prisme du Catalyseur : dégâts ×2 aux Catalyseurs (×2,5 / ×3 aux niveaux II / III)
        if (target instanceof com.wavesurvivor.entity.BrecheEntity b && b.isCatalyst()) {
            event.setAmount(event.getAmount() * (1f + scaled(p, ModItems.PRISME_CATALYSEUR.get(), 1.0f)));
        }
        // Fragment de Porte : +25 % aux Portes
        if (target instanceof com.wavesurvivor.entity.BrecheEntity b2 && b2.isGreat() && !b2.isCatalyst()) {
            event.setAmount(event.getAmount() * (1f + scaled(p, ModItems.FRAGMENT_PORTE.get(), 0.25f)));
        }
        // Croc de l'Alpha : +30 % aux élites, champions, gardiens et boss
        if (isEliteTarget(target)) {
            event.setAmount(event.getAmount() * (1f + scaled(p, ModItems.CROC_ALPHA.get(), 0.30f)));
        }
        int mark = level(p, ModItems.MARQUE_CHASSEUR.get());
        if (mark > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (100 * mult(mark)), 0, false, false));
        }
        // Trophée du Grand Veneur (légendaire) : la cible marquée subit +20 % de TOUTE l'équipe pendant 8 s
        long gt = p.level().getGameTime();
        if (target.getPersistentData().getLong("ws_veneur") > gt) event.setAmount(event.getAmount() * 1.2f);
        if (hasDirect(p, ModItems.TROPHEE_VENEUR.get())) target.getPersistentData().putLong("ws_veneur", gt + 160);
        // Cœur des Saisons (légendaire) : un coup brûle, le suivant gèle
        if (hasDirect(p, ModItems.COEUR_SAISONS.get())) {
            boolean fire = SEASON.getOrDefault(p.getUUID(), true);
            if (fire) target.setSecondsOnFire(3);
            else {
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
            }
            SEASON.put(p.getUUID(), !fire);
        }
    }

    /** Cœur des Saisons : prochain coup brûlant (vrai) ou gelant (faux), par joueur. */
    private static final java.util.Map<java.util.UUID, Boolean> SEASON = new java.util.concurrent.ConcurrentHashMap<>();

    /** Une clé de Reliquaire au hasard (mêmes poids que la Clé du Gardien), ou vide. */
    public static ItemStack randomKeyStack() {
        int total = 0;
        for (String[] k : KEYS) total += Integer.parseInt(k[1]);
        if (total <= 0) return ItemStack.EMPTY;
        int roll = RNG.nextInt(total);
        String key = KEYS[0][0];
        for (String[] k : KEYS) { roll -= Integer.parseInt(k[1]); if (roll < 0) { key = k[0]; break; } }
        return com.wavesurvivor.horde.loot.LootItems.resolve(key, 1);
    }

    /** Une clé de Reliquaire au hasard (mêmes poids que la Clé du Gardien), lâchée à cet endroit. */
    private static void dropRandomKey(net.minecraft.server.level.ServerLevel sl, double x, double y, double z) {
        int total = 0;
        for (String[] k : KEYS) total += Integer.parseInt(k[1]);
        int roll = RNG.nextInt(total);
        String key = KEYS[0][0];
        for (String[] k : KEYS) { roll -= Integer.parseInt(k[1]); if (roll < 0) { key = k[0]; break; } }
        ItemStack st = com.wavesurvivor.horde.loot.LootItems.resolve(key, 1);
        if (st.isEmpty()) return;
        net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(sl, x, y + 0.5, z, st);
        it.setDefaultPickUpDelay();
        sl.addFreshEntity(it);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, x, y + 1, z, 12, 0.3, 0.4, 0.3, 0.05);
    }

    /** Kills d'un joueur : Cœur Assoiffé (+1 PV par monstre), Éclat de Brèche (+4 PV par Brèche). */
    @SubscribeEvent
    public void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player p) || p.level().isClientSide) return;
        LivingEntity dead = event.getEntity();
        // Trousseau du Pilleur (légendaire) : un gisement détruit lâche une clé de Reliquaire (25 %)
        if (dead instanceof com.wavesurvivor.entity.GisementEntity g && !g.isFoyer()) {
            if (hasDirect(p, ModItems.TROUSSEAU_PILLEUR.get()) && RNG.nextDouble() < 0.25
                    && dead.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                dropRandomKey(sl, dead.getX(), dead.getY(), dead.getZ());
            }
            return;
        }
        if (dead instanceof com.wavesurvivor.entity.BrecheEntity) {
            // Clé des Portes (légendaire) : un Catalyseur détruit arrache 10 % de PV à la Porte la plus proche
            if (dead instanceof com.wavesurvivor.entity.BrecheEntity cat && cat.isCatalyst() && hasDirect(p, ModItems.CLE_PORTES.get())) {
                var gates = dead.level().getEntitiesOfClass(com.wavesurvivor.entity.BrecheEntity.class, dead.getBoundingBox().inflate(96),
                        b -> b.isAlive() && b.isGreat() && !b.isCatalyst());
                gates.sort(java.util.Comparator.comparingDouble(b -> b.distanceToSqr(dead)));
                if (!gates.isEmpty()) gates.get(0).hurt(p.damageSources().playerAttack(p), gates.get(0).getMaxHealth() * 0.10f);
            }
            float h = scaled(p, ModItems.ECLAT_BRECHE.get(), 4f);
            if (h > 0) p.heal(h);
            return;
        }
        float thirst = dead instanceof Enemy ? scaled(p, ModItems.COEUR_ASSOIFFE.get(), 1f) : 0f;
        // Calice de Sang Sacré (légendaire) : +2 PV par kill, le surplus devient de l'Absorption (8 max)
        if (dead instanceof Enemy && hasDirect(p, ModItems.CALICE_SANG.get())) {
            float missing = p.getMaxHealth() - p.getHealth();
            p.heal(2f);
            float over = 2f - Math.max(0f, missing);
            if (over > 0) p.setAbsorptionAmount(Math.min(8f, p.getAbsorptionAmount() + over));
        }
        if (thirst > 0) {
            p.heal(thirst);
            if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, p.getX(), p.getY() + 2.1, p.getZ(), 1, 0.2, 0.1, 0.2, 0);
            }
        }
    }

    // ─── R3 : survie ───

    /** Sceau du Gardien : un porteur à ≤ 8 blocs du Monolithe (même dimension) ? */
    public static boolean guardianNear(net.minecraft.server.MinecraftServer server, String dim, net.minecraft.core.BlockPos pos) {
        if (server == null || pos == null) return false;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (dim != null && !p.level().dimension().location().toString().equals(dim)) continue;
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) continue;
            if (hasRelic(p, ModItems.SCEAU_GARDIEN.get())) return true;
        }
        return false;
    }

    /** Talisman du Dernier Souffle : vague (ou tick) de la dernière utilisation par joueur. */
    private static final java.util.Map<java.util.UUID, Integer> TALISMAN_WAVE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> TALISMAN_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> TALISMAN_COUNT = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onLethalDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        if (event.getAmount() < p.getHealth() || p.isCreative()) return;
        if (!hasRelic(p, ModItems.TALISMAN_SOUFFLE.get())) return;
        var hm = com.wavesurvivor.horde.HordeManager.get();
        long now = p.serverLevel().getGameTime();
        if (hm.isRunning()) {
            Integer used = TALISMAN_WAVE.get(p.getUUID());
            if (used != null && used == hm.getCurrentWave()) {
                // Set Inebéranlable (4 pièces) : un 2e déclenchement par vague
                int n = TALISMAN_COUNT.getOrDefault(p.getUUID(), 1);
                if (n >= RelicSets.talismanUses(p)) return; // déjà utilisé cette vague
                TALISMAN_COUNT.put(p.getUUID(), n + 1);
            } else {
                TALISMAN_COUNT.put(p.getUUID(), 1);
            }
            TALISMAN_WAVE.put(p.getUUID(), hm.getCurrentWave());
        } else {
            Long last = TALISMAN_TIME.get(p.getUUID());
            if (last != null && now - last < 6000) return; // 5 min hors horde
        }
        TALISMAN_TIME.put(p.getUUID(), now);
        event.setAmount(Math.max(0f, p.getHealth() - 1f));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) (60 * mult(level(p, ModItems.TALISMAN_SOUFFLE.get()))), 4, false, true));
        var sl = p.serverLevel();
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 0.8, 0.5, 0.5);
        sl.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.3f);
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("relic.last_breath"), true);
        // Regard du Dernier Veilleur (légendaire) : 5 s d'invulnérabilité et monstres révélés à 32 blocs
        if (hasDirect(p, ModItems.REGARD_VEILLEUR.get())) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, true));
            for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32),
                    en -> en instanceof Enemy && en.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            }
        }
    }

    /** Bannière de Ralliement : derniers bénéficiaires de l'aura (UUID → tick). */
    private static final java.util.Map<java.util.UUID, Long> BANNER_BUFF = new java.util.concurrent.ConcurrentHashMap<>();
    /** Niveau de la Bannière dont chaque allié profite (+2 / +3 / +4 armure). */
    private static final java.util.Map<java.util.UUID, Integer> BANNER_LVL = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bannière : tick de la dernière impulsion de soin, par porteur. */
    private static final java.util.Map<java.util.UUID, Long> BANNER_PULSE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.UUID BANNER_ARMOR = java.util.UUID.fromString("4b1e7c2a-9d3f-4e61-8a70-5c2d9e1f0b33");

    @SubscribeEvent
    public void onBannerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        if (event.phase != TickEvent.Phase.END || p.level().isClientSide || p.tickCount % 20 != 0) return;
        long now = p.level().getGameTime();
        // Porteur : aura sur les joueurs proches
        if (hasRelic(p, ModItems.BANNIERE_RALLIEMENT.get())) {
            // Impulsion de soin toutes les 10 s, minuteur propre à chaque porteur (indépendant de l'horloge du monde,
            // qui ne s'alignait plus après une mort ou un changement de dimension)
            long last = BANNER_PULSE.getOrDefault(p.getUUID(), 0L);
            boolean regen = now - last >= 200 || now < last;
            if (regen) BANNER_PULSE.put(p.getUUID(), now);
            int bannerLvl = level(p, ModItems.BANNIERE_RALLIEMENT.get());
            int regenTicks = (int) (100 * mult(Math.max(1, bannerLvl))); // 1 cœur (×1,5 / ×2 aux niveaux II / III)
            // Étendard du Bastion (légendaire) : aura à 16 blocs, avec Résistance I
            boolean bastion = hasDirect(p, ModItems.ETENDARD_BASTION.get());
            double r = bastion ? 16 : 8;
            for (Player ally : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(r),
                    a -> a.isAlive() && !a.isSpectator() && a.distanceToSqr(p) <= r * r)) {
                if (bastion) ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true));
                BANNER_BUFF.put(ally.getUUID(), now);
                BANNER_LVL.put(ally.getUUID(), bannerLvl);
                if (regen) {
                    ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenTicks, 0, false, true));
                    if (p.level() instanceof net.minecraft.server.level.ServerLevel sl0) {
                        sl0.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3,
                                ally.getZ(), 3, 0.3, 0.2, 0.3, 0);
                    }
                }
            }
            if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 2.2, p.getZ(), 2, 0.3, 0.1, 0.3, 0);
            }
        }
        // Tous : +2 armure tant que l'aura est récente
        var armor = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armor == null) return;
        Long t = BANNER_BUFF.get(p.getUUID());
        boolean active = t != null && now - t <= 40;
        boolean has = armor.getModifier(BANNER_ARMOR) != null;
        int bl = BANNER_LVL.getOrDefault(p.getUUID(), 1);
        double want = 2.0 * mult(bl);
        var cur = armor.getModifier(BANNER_ARMOR);
        if (active && (!has || cur == null || cur.getAmount() != want)) {
            if (has) armor.removeModifier(BANNER_ARMOR);
            armor.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BANNER_ARMOR, "Bannière de Ralliement", want,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        } else if (!active && has) {
            armor.removeModifier(BANNER_ARMOR);
        }
    }

    // ─── R4 : économie ───

    /** Clés possibles (pondérées) pour la Clé du Gardien. */
    private static final String[][] KEYS = {
            {"roulettechest:key_custom_reliquaire_des_tresors", "35"},
            {"roulettechest:key_custom_reliquaire_du_nether", "15"},
            {"roulettechest:key_custom_reliquaire_arcanique", "15"},
            {"roulettechest:key_custom_reliquaire_des_abysses", "10"},
            {"roulettechest:key_custom_reliquaire_de_l_end", "15"},
            {"roulettechest:key_custom_reliquaire_du_boss", "10"}};
    private static final java.util.Random RNG = new java.util.Random();

    /** Clé du Gardien : pendant une horde, 10 % qu'un monstre tué lâche une clé de Reliquaire. */
    @SubscribeEvent
    public void onKillKey(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player p) || p.level().isClientSide) return;
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || dead instanceof com.wavesurvivor.entity.BrecheEntity
                || dead instanceof com.wavesurvivor.entity.TotemEntity) return;
        if (!com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        if (RNG.nextDouble() >= scaled(p, ModItems.CLE_GARDIEN.get(), 0.10f)) return; // 10 / 15 / 20 %
        int total = 0;
        for (String[] k : KEYS) total += Integer.parseInt(k[1]);
        int roll = RNG.nextInt(total);
        String key = KEYS[0][0];
        for (String[] k : KEYS) { roll -= Integer.parseInt(k[1]); if (roll < 0) { key = k[0]; break; } }
        ItemStack st = com.wavesurvivor.horde.loot.LootItems.resolve(key, 1);
        if (st.isEmpty() || !(dead.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(sl, dead.getX(), dead.getY() + 0.5, dead.getZ(), st);
        it.setDefaultPickUpDelay();
        sl.addFreshEntity(it);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, dead.getX(), dead.getY() + 1, dead.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        sl.playSound(null, dead.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    /** Langue d'Argent : −25 % sur les prix des marchands pendant une horde (réinitialisé à la fermeture par le jeu). */
    @SubscribeEvent
    public void onMerchantOpen(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof net.minecraft.world.inventory.MerchantMenu menu)) return;
        Player p = event.getEntity();
        if (p.level().isClientSide || !com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        float tongue = Math.min(0.5f, scaled(p, ModItems.LANGUE_ARGENT.get(), 0.25f)); // 25 / 37 / 50 %
        if (tongue <= 0) return;
        for (net.minecraft.world.item.trading.MerchantOffer o : menu.getOffers()) {
            int base = o.getBaseCostA().getCount();
            int cut = (int) Math.floor(base * tongue);
            if (cut > 0) o.addToSpecialPriceDiff(-cut);
        }
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("relic.silver_tongue"), true);
    }

    // ─── Bourse du Colporteur ───

    /** Sceau du Marchand-Roi (légendaire) : 20 % de chance qu'un achat soit remboursé. */
    @SubscribeEvent
    public void onTrade(net.minecraftforge.event.entity.player.TradeWithVillagerEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide || !hasDirect(p, ModItems.SCEAU_MARCHAND_ROI.get()) || RNG.nextDouble() >= 0.20) return;
        var offer = e.getMerchantOffer();
        for (ItemStack cost : new ItemStack[]{offer.getCostA(), offer.getCostB()}) {
            if (cost.isEmpty()) continue;
            ItemStack back = cost.copy();
            if (!p.getInventory().add(back)) p.drop(back, false);
        }
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("relic.merchant_king"), true);
    }

    /** Offres déjà rechargées par un porteur (une seule recharge par offre et par joueur). */
    private static final java.util.Set<String> RESTOCKED = new java.util.HashSet<>();

    /** Marchands pendant une horde : chaque offre épuisée se recharge d'un achat pour le porteur. */
    @SubscribeEvent
    public void onMerchantOpenPurse(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof net.minecraft.world.inventory.MerchantMenu menu)) return;
        Player p = event.getEntity();
        if (p.level().isClientSide || !com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        if (!hasRelic(p, ModItems.BOURSE_COLPORTEUR.get())) return;
        int n = 0;
        for (net.minecraft.world.item.trading.MerchantOffer o : menu.getOffers()) {
            if (!o.isOutOfStock()) continue;
            String key = p.getUUID() + "#" + System.identityHashCode(o);
            if (!RESTOCKED.add(key)) continue;
            o.resetUses();
            for (int i = 0; i < o.getMaxUses() - 1; i++) o.increaseUses(); // exactement un achat de plus
            n++;
        }
        if (RESTOCKED.size() > 4000) RESTOCKED.clear();
        if (n > 0) p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("relic.purse_restock", n), true);
    }

    /** Monnaie lâchée par les victimes du porteur : +15 % (arrondi au hasard). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onDropsPurse(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof Player p) || p.level().isClientSide) return;
        if (!hasRelic(p, ModItems.BOURSE_COLPORTEUR.get())) return;
        var rnd = e.getEntity().getRandom();
        for (net.minecraft.world.entity.item.ItemEntity ie : e.getDrops()) {
            ItemStack st = ie.getItem();
            if (!com.wavesurvivor.altar.AltarDefense.isCurrency(st)) continue;
            double extra = st.getCount() * scaled(p, ModItems.BOURSE_COLPORTEUR.get(), 0.15f);
            int add = (int) Math.floor(extra) + (rnd.nextDouble() < extra - Math.floor(extra) ? 1 : 0);
            if (add > 0) {
                ItemStack copy = st.copy();
                copy.grow(add);
                ie.setItem(copy);
            }
        }
    }
}
