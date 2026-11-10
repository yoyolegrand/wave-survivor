package com.wavesurvivor.horde.rift;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.entity.SiegeEngineEntity;
import com.wavesurvivor.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.0 — ENGINS DE SIÈGE de la Faille : catapultes (2 × 6, 3 de haut) et balistes célestes (3 × 3, 3 de haut), achetées à la boutique du Monolithe
 * et posées par les joueurs. Ce sont des ENTITÉS ({@link SiegeEngineEntity}) rendues par un modèle JSON (niveaux 1 et 2) ; elles regardent vers le SUD.
 * Pilotage : clic droit sur l'engin.
 * <ul>
 *   <li>Catapulte : 1 pierre taillée par boulet, rechargement 30 s (24 s au niveau 2), tir en cloche dans la direction du regard
 *       (la portée dépend de l'inclinaison). Dégâts de zone 80 au centre → 30 % au bord (rayon 4), ×1,5 contre le Titan, les avatars et les structures.</li>
 *   <li>Baliste céleste : chargeur de 16 flèches prises dans l'inventaire, un tir toutes les 0,5 s (0,4 s au niveau 2), rechargement 10 s (8 s),
 *       6 de dégâts par flèche ; elle suit le regard du dernier tireur.</li>
 * </ul>
 * Cadences réglables dans l'éditeur (page Titan). Amélioration : Maj + clic droit (niveau 2, puis variante de niveau 3).
 */
final class RiftEngines {

    private static final class Boulder {
        Vec3 pos, vel;
        UUID owner;
        float mul = 1f;
        boolean fire, frag;
    }

    /** Prix de l'amélioration au niveau 2, puis de la variante de niveau 3 (catapulte, baliste). */
    private static final int[] UP2 = {60, 50}, UP3 = {100, 80};
    private static final String[][] VARIANTS = {
            {"Infernale", "À fragmentation", "Trébuchet de siège"}, {"Séraphique", "Solaire", "Du Jugement"}};
    /** Joueur → engin dont il choisit la variante (boutons du chat). */
    private static final Map<UUID, UUID> choosing = new HashMap<>();
    private static final List<Boulder> BOULDERS = new ArrayList<>();

    /** Héritage de la Faille 1 (Cœur d'acier) : +20 % de dégâts des engins si un joueur connecté l'a. */
    private static float boost(ServerLevel l) {
        return com.wavesurvivor.horde.renaissance.RiftLegacy.anyone(l.getServer(), 1) ? 1.2f : 1f;
    }

    /** Engins enrayés par le rugissement du Titan jusqu'à ce tick de jeu. */
    private static long jamUntil;

    static void jam(ServerLevel l, int ticks) { jamUntil = l.getGameTime() + ticks; }

    static boolean jammed(ServerLevel l) { return l.getGameTime() < jamUntil; }

    /** Boulets de catapulte qui passent près de {@code c} (le rocher du Titan) : ils l'ont touché et disparaissent ; renvoie leur nombre. */
    static int interceptBoulders(ServerLevel l, Vec3 c, double r) {
        int n = 0;
        for (Boulder b : new ArrayList<>(BOULDERS)) {
            if (b.pos.distanceTo(c) > r) continue;
            BOULDERS.remove(b);
            n++;
            l.sendParticles(ParticleTypes.EXPLOSION, b.pos.x, b.pos.y, b.pos.z, 1, 0, 0, 0, 0);
        }
        return n;
    }
    /** Tours-catapultes posées (suivies à chaque tick pour viser et tirer toutes seules). */
    private static final List<UUID> TOWERS = new ArrayList<>();
    private static final Map<UUID, Long> msgGate = new HashMap<>();

    private RiftEngines() {}

    /** Sauvegarde des engins posés (reprise de la Faille). */
    static net.minecraft.nbt.ListTag save(ServerLevel l) {
        net.minecraft.nbt.ListTag out = new net.minecraft.nbt.ListTag();
        if (l == null) return out;
        for (UUID id : TOWERS) {
            if (!(l.getEntity(id) instanceof SiegeEngineEntity e) || e.isRemoved()) continue;
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putDouble("x", e.getX());
            t.putDouble("y", e.getY());
            t.putDouble("z", e.getZ());
            t.putInt("kind", e.kind());
            t.putInt("level", e.engineLevel());
            t.putInt("variant", e.variant());
            if (e.maxHp() > 0) t.putFloat("hp", e.hp() / e.maxHp());
            out.add(t);
        }
        return out;
    }

    /** Reprise : les engins sauvegardés sont reposés (niveau et variante conservés, rechargés). */
    static void restore(ServerLevel l, net.minecraft.nbt.ListTag list) {
        if (l == null || list == null) return;
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag t = list.getCompound(i);
            SiegeEngineEntity e = ModEntities.SIEGE_ENGINE.get().create(l);
            if (e == null) continue;
            boolean ballista = t.getInt("kind") == SiegeEngineEntity.BALLISTA;
            e.moveTo(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), 0f, 0f);
            e.setKind(ballista ? SiegeEngineEntity.BALLISTA : SiegeEngineEntity.CATAPULT);
            e.setEngineLevel(Math.max(1, t.getInt("level")));
            e.setVariant(t.getInt("variant"));
            e.applyMaxHp();
            if (t.contains("hp")) e.setHpRatio(t.getFloat("hp"));
            e.setAmmo(ballista ? 0 : 1);
            e.setReloadTicks(ballista ? ballistaReload(e) : catapultReload(e));
            e.setAim(0f, -18f);
            l.addFreshEntity(e);
            TOWERS.add(e.getUUID());
        }
    }

    /** Positions des engins posés (cibles des attaques du Titan contre les bâtiments). */
    static List<BlockPos> positions(ServerLevel l) {
        List<BlockPos> out = new ArrayList<>();
        for (UUID id : TOWERS) if (l.getEntity(id) instanceof SiegeEngineEntity e && !e.isRemoved()) out.add(e.blockPosition());
        return out;
    }

    /** Dégâts aux engins dont la boîte est à moins de {@code r} blocs de {@code c} (attaques du Titan, explosions). */
    static void damageNear(ServerLevel l, Vec3 c, double r, float dmg) {
        if (dmg <= 0) return;
        for (SiegeEngineEntity e : l.getEntitiesOfClass(SiegeEngineEntity.class, new AABB(c, c).inflate(r + 4))) {
            AABB b = e.getBoundingBox();
            double dx = Math.max(0, Math.max(b.minX - c.x, c.x - b.maxX)), dy = Math.max(0, Math.max(b.minY - c.y, c.y - b.maxY)),
                    dz = Math.max(0, Math.max(b.minZ - c.z, c.z - b.maxZ));
            if (dx * dx + dy * dy + dz * dz <= r * r) e.damage(dmg);
        }
    }

    /** Dégâts aux engins dont la boîte touche le rectangle (charge du Titan, effondrement de l'île). */
    static void damageRect(ServerLevel l, double x0, double x1, double z0, double z1, float dmg) {
        if (dmg <= 0) return;
        AABB zone = new AABB(x0, RiftManager.CENTER.getY() - 20, z0, x1, RiftManager.CENTER.getY() + 40, z1);
        for (SiegeEngineEntity e : l.getEntitiesOfClass(SiegeEngineEntity.class, zone)) e.damage(dmg);
    }

    /** Coup d'un monstre au contact d'un engin. @return vrai si un engin a été touché. */
    static boolean hitNear(ServerLevel l, net.minecraft.world.entity.Mob m, double reach, float dmg) {
        for (SiegeEngineEntity e : l.getEntitiesOfClass(SiegeEngineEntity.class, m.getBoundingBox().inflate(reach))) {
            if (e.isRemoved()) continue;
            m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            m.getLookControl().setLookAt(e.getX(), e.getY() + 1, e.getZ());
            l.playSound(null, e.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1f, 0.7f);
            e.damage(Math.max(1f, dmg));
            return true;
        }
        return false;
    }

    /** Engin à 0 PV : il vole en éclats (on peut en racheter un à la boutique). */
    static void destroyed(SiegeEngineEntity e) {
        if (!(e.level() instanceof ServerLevel l) || e.isRemoved()) return;
        e.ejectPassengers();
        TOWERS.remove(e.getUUID());
        double h = SiegeEngineEntity.footprint(e.kind())[1];
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY() + h * 0.5, e.getZ(), 2, 1, 1, 1, 0);
        l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState()),
                e.getX(), e.getY() + h * 0.5, e.getZ(), 60, 1.5, h * 0.4, 1.5, 0.3);
        l.playSound(null, e.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 3f, 0.6f);
        l.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2f, 0.9f);
        RiftManager.feed(l, "§c" + com.wavesurvivor.i18n.WSLang.t("rift.engine.destroyed", nameOf(e)));
        e.discard();
    }

    /** Coût d'une réparation complète (◆) : proportionnel aux PV manquants. */
    private static int repairCost(ServerPlayer p, SiegeEngineEntity e) {
        if (e.maxHp() <= 0 || e.hp() >= e.maxHp()) return 0;
        double c = Math.ceil((1f - e.hp() / e.maxHp()) * REPAIR_FULL[e.isBallista() ? 1 : 0]);
        // Héritage — Seigneur 8 : réparations −25 % (comme pour les tours du royaume)
        if (com.wavesurvivor.horde.renaissance.Heritage.has(p, com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 8)) c *= 0.75;
        return Math.max(1, (int) Math.ceil(c));
    }

    /** Prix d'une réparation complète d'un engin détruit à 99 % (tour-catapulte, baliste). */
    private static final int[] REPAIR_FULL = {80, 60};

    static void clear() {
        TOWERS.clear();
        BOULDERS.clear();
        msgGate.clear();
        choosing.clear();
    }

    private static String nameOf(SiegeEngineEntity e) {
        boolean b = e.isBallista();
        String id = b ? "ballista" : "catapult";
        int lv = e.engineLevel();
        if (lv == 2) return com.wavesurvivor.i18n.WSLang.t("rift.engine.sheet.name." + id + ".l2");
        if (lv >= 3 && e.variant() >= 1) return com.wavesurvivor.i18n.WSLang.t("rift.engine.sheet.name." + id) + " — " + com.wavesurvivor.i18n.WSLang.t("rift.engine.sheet.variant." + id + "." + (e.variant() - 1));
        return com.wavesurvivor.i18n.WSLang.t("rift.engine.sheet.name." + id);
    }

    // ─── Cadences (réglables) ───

    private static HordeConfigMultiData.RiftSettings rs() { return RiftManager.settingsPublic(); }

    /** Rechargement de la catapulte (ticks) : 30 s niveau 1, 24 s niveau 2 et variantes (le Trébuchet est plus lent). */
    private static int catapultReload(SiegeEngineEntity e) {
        if (e.engineLevel() >= 3 && e.variant() == 3) return 720;   // Trébuchet : 36 s
        return Math.max(20, e.engineLevel() >= 2 ? rs().catapultReload2 : rs().catapultReload1);
    }

    private static int ballistaInterval(SiegeEngineEntity e) {
        return Math.max(1, e.engineLevel() >= 2 ? rs().ballistaInterval2 : rs().ballistaInterval1);
    }

    private static int ballistaReload(SiegeEngineEntity e) {
        return Math.max(20, e.engineLevel() >= 2 ? rs().ballistaReload2 : rs().ballistaReload1);
    }

    // ─── Pose ───

    /**
     * Pose un engin acheté à la boutique (tourné vers le sud). {@code at} = bloc libre au-dessus du sol visé.
     * @return 0 posé · 1 pas de place / sol instable · 2 hors de l'île ou dimension incorrecte
     */
    static int place(ServerLevel l, BlockPos at, boolean ballista) {
        if (l.dimension() != RiftManager.RIFT_DIM) return 2;
        double[] f = SiegeEngineEntity.footprint(ballista ? SiegeEngineEntity.BALLISTA : SiegeEngineEntity.CATAPULT);
        int w = (int) f[0], d = (int) f[2], hgt = (int) f[1], y = at.getY();
        int ox = at.getX() - (ballista ? 1 : 2), oz = at.getZ() - (ballista ? 1 : 2);   // coin nord-ouest de l'emprise
        int rMax = Math.max(4, RiftManager.islandRadiusNow() - 2);
        long r2 = (long) rMax * rMax;
        for (int x = ox; x < ox + w; x++) for (int z = oz; z < oz + d; z++) {
            long dx = x - RiftManager.CENTER.getX(), dz = z - RiftManager.CENTER.getZ();
            if (dx * dx + dz * dz > r2) return 2;
            if (l.getBlockState(new BlockPos(x, y - 1, z)).isAir()) return 1;
            for (int h = 0; h < hgt; h++) {
                BlockState s = l.getBlockState(new BlockPos(x, y + h, z));
                if (!s.isAir() && !s.canBeReplaced()) return 1;
            }
        }
        AABB box = new AABB(ox, y, oz, ox + w, y + hgt, oz + d);
        if (!l.getEntitiesOfClass(SiegeEngineEntity.class, box).isEmpty()) return 1;   // pas sur un autre engin
        SiegeEngineEntity e = ModEntities.SIEGE_ENGINE.get().create(l);
        if (e == null) return 1;
        e.moveTo(ox + w / 2.0, y, oz + d / 2.0, 0f, 0f);
        e.setKind(ballista ? SiegeEngineEntity.BALLISTA : SiegeEngineEntity.CATAPULT);
        e.setEngineLevel(1);
        e.setAmmo(ballista ? 0 : 1);
        e.setReloadTicks(ballista ? ballistaReload(e) : catapultReload(e));
        e.setAim(0f, -18f);
        e.readyAt = 0;
        l.addFreshEntity(e);
        TOWERS.add(e.getUUID());   // suivi de tous les engins (sauvegarde) ; seules les tours-catapultes tirent seules
        l.playSound(null, e.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.2f, 0.7f);
        l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 20, w / 2.0, 0.3, d / 2.0, 0.02);
        return 0;
    }

    // ─── Pilotage ───

    /** Clic droit sur un engin. */
    static void onUse(ServerPlayer p, SiegeEngineEntity e) {
        if (!(p.level() instanceof ServerLevel l)) return;
        long now = l.getGameTime();
        if (p.isShiftKeyDown()) { openSheet(p, e); return; }   // Maj + clic droit : fiche de l'engin, amélioration (clic droit seul : tir)
        if (e.isBallista()) {   // clic droit : on s'assoit aux commandes (clic droit maintenu : tir ; Maj : descendre)
            if (e.getPassengers().isEmpty()) {
                if (p.startRiding(e)) say(p, "§b" + com.wavesurvivor.i18n.WSLang.t("rift.e.pilot_help"), now);
            } else if (!e.hasPassenger(p)) {
                say(p, "§7" + com.wavesurvivor.i18n.WSLang.t("rift.e.taken"), now);
            }
        } else {
            say(p, "§7" + com.wavesurvivor.i18n.WSLang.t("rift.e.auto", nameOf(e)), now);
        }
    }

    /** Le pilote assis maintient le clic droit : un tir (cadence, chargeur et rechargement gérés ici). */
    static void fireFromSeat(ServerPlayer p) {
        if (!(p.getVehicle() instanceof SiegeEngineEntity e) || !e.isBallista() || !(p.level() instanceof ServerLevel l)) return;
        useBallista(p, l, e, l.getGameTime());
    }

    // ─── Tour-catapulte automatique ───

    private static final UUID NO_OWNER = new UUID(0L, 0L);

    /** Chaque tick : la tour cherche une cible (le Titan, sinon l'ennemi le plus proche), tourne vers elle (30°/s) et tire dès que son rechargement est fini. */
    static void towerTick(ServerLevel l, SiegeEngineEntity e, long now) {
        if (e.isBallista() || e.isRemoved()) return;
        if (jammed(l)) {   // rugissement du Titan : la tour est enrayée
            if (now % 10 == 0) l.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 5, e.getZ(), 4, 0.6, 0.3, 0.6, 0.01);
            return;
        }
        if (now >= e.nextAcquire) {
            e.nextAcquire = now + 10;
            e.targetId = null;
            LivingEntity best = RiftTitan.entity();
            if (best == null) {
                double bd = 70 * 70;
                for (net.minecraft.world.entity.monster.Monster m : l.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                        e.getBoundingBox().inflate(70, 40, 70))) {
                    if (!m.isAlive()) continue;
                    double d = m.distanceToSqr(e);
                    if (d < bd) { bd = d; best = m; }
                }
            }
            if (best != null) e.targetId = best.getUUID();
        }
        if (e.targetId == null || !(l.getEntity(e.targetId) instanceof LivingEntity tgt) || !tgt.isAlive()) { e.targetId = null; return; }
        // Point visé : le point de la boîte de la cible le plus proche de la tour (le Titan est immense : on le touche dès qu'on l'atteint)
        AABB tb = tgt.getBoundingBox();
        Vec3 from = new Vec3(e.getX(), e.getY() + 5.1, e.getZ());
        Vec3 p = new Vec3(Math.max(tb.minX, Math.min(from.x, tb.maxX)), Math.max(tb.minY + 1, Math.min(from.y, tb.maxY)), Math.max(tb.minZ, Math.min(from.z, tb.maxZ)));
        double dx = p.x - from.x, dz = p.z - from.z;
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = net.minecraft.util.Mth.wrapDegrees(want - e.aimYaw());
        float step = Math.max(-1.5f, Math.min(1.5f, diff));     // 30° par seconde au plus
        float yaw = e.aimYaw() + step;
        e.setAim(yaw, e.aimPitch());
        if (Math.abs(diff) > 3f || now < e.readyAt) return;
        // Munitions : 1 Pierre du trésor du royaume par tir, sauf si elles sont illimitées
        boolean unlimited = rs().catapultTowerUnlimited;
        var stone = com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.STONE;
        if (!unlimited) {
            if (!com.wavesurvivor.horde.kingdom.KingdomTreasury.active() || !com.wavesurvivor.horde.kingdom.KingdomTreasury.has(stone, 1)) {
                e.readyAt = now + 40;   // pas de pierre : on réessaie dans 2 s
                if (now % 200 == 0) for (ServerPlayer pl : l.players()) if (pl.distanceToSqr(e) < 60 * 60) pl.displayClientMessage(Component.literal("§c" + com.wavesurvivor.i18n.WSLang.t("rift.e.no_stone", nameOf(e))), true);
                return;
            }
            com.wavesurvivor.horde.kingdom.KingdomTreasury.spend(stone, 1);
            com.wavesurvivor.horde.kingdom.KingdomTreasury.sync(l.getServer());
        }
        fireTower(l, e, p, yaw, now);
    }

    /** Tir : la tour lance un boulet qui retombe sur le point visé (angle calculé pour la vitesse du boulet). */
    private static void fireTower(ServerLevel l, SiegeEngineEntity e, Vec3 target, float yaw, long now) {
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        Vec3 muzzle = new Vec3(e.getX() + fx * 2.25, e.getY() + 5.1, e.getZ() + fz * 2.25);
        boolean tre = e.engineLevel() >= 3 && e.variant() == 3;
        double speed = 2.5 * (e.engineLevel() >= 2 ? 1.1 : 1.0) * (tre ? 1.25 : 1.0);
        double dist = Math.max(1, Math.hypot(target.x - muzzle.x, target.z - muzzle.z)), h = target.y - muzzle.y, g = 0.05;
        double disc = speed * speed * speed * speed - g * (g * dist * dist + 2 * h * speed * speed);
        double ang = disc >= 0 ? Math.atan((speed * speed - Math.sqrt(disc)) / (g * dist)) : Math.toRadians(45);   // hors de portée : tir à 45°
        Boulder b = new Boulder();
        b.pos = muzzle;
        b.vel = new Vec3(fx * Math.cos(ang), Math.sin(ang), fz * Math.cos(ang)).scale(speed);
        b.owner = NO_OWNER;
        b.mul = (e.engineLevel() >= 2 ? 1.25f : 1f) * (tre ? 1.6f : 1f) * boost(l);
        b.fire = e.engineLevel() >= 3 && e.variant() == 1;
        b.frag = e.engineLevel() >= 3 && e.variant() == 2;
        BOULDERS.add(b);
        int reload = catapultReload(e);
        e.readyAt = now + reload;
        e.markFired(now);
        e.setReloadTicks(reload);
        l.playSound(null, BlockPos.containing(muzzle), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3f, 0.6f);
        l.playSound(null, BlockPos.containing(muzzle), SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 3f, 0.5f);
    }

    /** Clic droit avec un lingot d'or : niveau 2 (payant), puis choix d'une variante au niveau 3. */
    private static void upgrade(ServerPlayer p, SiegeEngineEntity e, long now) {
        int k = e.isBallista() ? 1 : 0;
        if (e.engineLevel() == 1) {
            if (!com.wavesurvivor.altar.AltarDefense.trySpend(p, UP2[k])) return;
            e.setEngineLevel(2);
            e.setReloadTicks(e.isBallista() ? ballistaReload(e) : catapultReload(e));
            if (p.level() instanceof ServerLevel l) l.playSound(null, e.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.2f);
            say(p, "§a" + nameOf(e) + " !", now);
        } else if (e.engineLevel() == 2) {
            choosing.put(p.getUUID(), e.getUUID());
            net.minecraft.network.chat.MutableComponent line = Component.literal("§6" + com.wavesurvivor.i18n.WSLang.t("rift.e.variant_pick", UP3[k]) + " ");
            for (int i = 0; i < 3; i++) {
                final String cmd = "/wavesurvivor riftengine " + i;
                line.append(Component.literal("§e§l[" + com.wavesurvivor.i18n.WSLang.t("rift.engine.sheet.variant." + (k == 1 ? "ballista" : "catapult") + "." + i) + "]  ").withStyle(st -> st.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))));
            }
            p.sendSystemMessage(line);
        } else {
            say(p, "§7" + com.wavesurvivor.i18n.WSLang.t("rift.e.max", nameOf(e)), now);
        }
    }

    /** Choix de la variante (clic dans le chat) : définitif pour cet engin. */
    static void chooseVariant(ServerPlayer p, int i) {
        UUID id = choosing.get(p.getUUID());
        if (id == null || i < 0 || i > 2 || !(p.level() instanceof ServerLevel l) || !(l.getEntity(id) instanceof SiegeEngineEntity e)) return;
        if (e.engineLevel() != 2 || p.distanceToSqr(e) > 400) return;
        if (!com.wavesurvivor.altar.AltarDefense.trySpend(p, UP3[e.isBallista() ? 1 : 0])) return;
        e.setEngineLevel(3);
        e.setVariant(i + 1);
        e.setReloadTicks(e.isBallista() ? ballistaReload(e) : catapultReload(e));
        choosing.remove(p.getUUID());
        l.playSound(null, e.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
        p.displayClientMessage(Component.literal("§a" + nameOf(e) + " !"), true);
    }

    /** Monnaie disponible pour les améliorations (trésor du royaume, en ◆). */
    private static int coins(ServerPlayer p) {
        return com.wavesurvivor.horde.kingdom.KingdomTreasury.active()
                ? com.wavesurvivor.horde.kingdom.KingdomTreasury.get(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.MONEY) : 0;
    }

    /** Envoie la fiche de l'engin au joueur (écran d'amélioration). */
    private static void openSheet(ServerPlayer p, SiegeEngineEntity e) {
        int k = e.isBallista() ? 1 : 0;
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.EngineSheetPackets.Sheet(e.getId(), e.kind(), e.engineLevel(), e.engineLevel() >= 3 ? e.variant() : 0,
                        UP2[k], UP3[k], coins(p), e.ammo(), (int) Math.ceil(e.hp()), (int) e.maxHp(), repairCost(p, e)));
    }

    /** Action de la fiche : « upgrade » (niveau 2) ou « variant0 » à « variant2 » (niveau 3, définitif). */
    static void sheetAction(ServerPlayer p, int entityId, String action) {
        if (!(p.level() instanceof ServerLevel l) || !(l.getEntity(entityId) instanceof SiegeEngineEntity e) || p.distanceToSqr(e) > 20 * 20) return;
        long now = l.getGameTime();
        if ("repair".equals(action)) {
            int cost = repairCost(p, e);
            if (cost > 0 && (p.isCreative() || com.wavesurvivor.altar.AltarDefense.trySpend(p, cost))) {
                e.repair();
                l.playSound(null, e.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.1f);
                l.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 2, e.getZ(), 20, 1, 1, 1, 0);
            }
        } else if ("upgrade".equals(action)) {
            if (e.engineLevel() == 1) upgrade(p, e, now);
        } else if (action.startsWith("variant") && action.length() == 8) {
            int i = action.charAt(7) - '0';
            if (i >= 0 && i <= 2 && e.engineLevel() == 2 && com.wavesurvivor.altar.AltarDefense.trySpend(p, UP3[e.isBallista() ? 1 : 0])) {
                e.setEngineLevel(3);
                e.setVariant(i + 1);
                e.setReloadTicks(e.isBallista() ? ballistaReload(e) : catapultReload(e));
                l.playSound(null, e.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
                p.displayClientMessage(Component.literal("§a" + nameOf(e) + " !"), true);
            }
        }
        openSheet(p, e);
    }

    private static void say(ServerPlayer p, String msg, long now) {
        Long last = msgGate.get(p.getUUID());
        if (last != null && now - last < 10) return;
        msgGate.put(p.getUUID(), now);
        p.displayClientMessage(Component.literal(msg), true);
    }

    private static int take(ServerPlayer p, net.minecraft.world.item.Item item, int max) {
        int got = 0;
        for (int i = 0; i < p.getInventory().getContainerSize() && got < max; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                int n = Math.min(max - got, s.getCount());
                s.shrink(n);
                got += n;
            }
        }
        return got;
    }

    /** Bouche de la catapulte : le bout de la cuillère (modèle : centre du boulet à 1 ; 2,2 ; 5,25 depuis le coin nord-ouest). */
    private static Vec3 catapultMuzzle(SiegeEngineEntity e) {
        return new Vec3(e.getX(), e.getY() + 2.2, e.getZ() + 2.25);
    }

    private static void useCatapult(ServerPlayer p, ServerLevel l, SiegeEngineEntity e, long now) {
        if (now < e.readyAt) {
            say(p, "§7" + com.wavesurvivor.i18n.WSLang.t("rift.e.reloading", nameOf(e), (e.readyAt - now + 19) / 20), now);
            return;
        }
        if (!p.isCreative() && take(p, Items.COBBLESTONE, 1) < 1) {
            say(p, "§c" + com.wavesurvivor.i18n.WSLang.t("rift.e.need_stone"), now);
            return;
        }
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, Math.max(look.y, 0.25), look.z).normalize();
        boolean tre = e.engineLevel() >= 3 && e.variant() == 3;
        Boulder b = new Boulder();
        b.pos = catapultMuzzle(e);
        double speed = 2.5 * (e.engineLevel() >= 2 ? 1.1 : 1.0) * (tre ? 1.25 : 1.0);   // la portée se règle avec l'inclinaison du regard
        b.vel = dir.scale(speed);
        b.owner = p.getUUID();
        b.mul = (e.engineLevel() >= 2 ? 1.25f : 1f) * (tre ? 1.6f : 1f) * boost(l);
        b.fire = e.engineLevel() >= 3 && e.variant() == 1;
        b.frag = e.engineLevel() >= 3 && e.variant() == 2;
        BOULDERS.add(b);
        int reload = catapultReload(e);
        e.readyAt = now + reload;
        e.markFired(now);
        e.setReloadTicks(reload);
        e.setAmmo(0);
        l.playSound(null, BlockPos.containing(b.pos), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3f, 0.6f);
        l.playSound(null, BlockPos.containing(b.pos), SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 3f, 0.5f);
        say(p, "§6" + com.wavesurvivor.i18n.WSLang.t("rift.e.fired", nameOf(e), reload / 20), now);
    }

    private static void useBallista(ServerPlayer p, ServerLevel l, SiegeEngineEntity e, long now) {
        if (jammed(l)) {
            say(p, "§c" + com.wavesurvivor.i18n.WSLang.t("rift.titan.ballista_jammed"), now);
            return;
        }
        // Elle suit le regard du dernier tireur (visée horizontale et verticale)
        e.setAim(p.getYRot(), Math.max(-60f, Math.min(40f, p.getXRot())));
        if (e.reloading) {
            if (now < e.readyAt) {
                say(p, "§7" + com.wavesurvivor.i18n.WSLang.t("rift.e.reloading", nameOf(e), (e.readyAt - now + 19) / 20), now);
                return;
            }
            e.reloading = false;
        }
        if (e.ammo() <= 0) {
            int n = p.isCreative() ? 16 : take(p, Items.ARROW, 16);
            if (n <= 0) {
                say(p, "§c" + com.wavesurvivor.i18n.WSLang.t("rift.e.need_arrows"), now);
                return;
            }
            int reload = ballistaReload(e);
            e.setAmmo(n);
            e.reloading = true;
            e.readyAt = now + reload;
            e.markReload(now, reload);
            l.playSound(null, e.blockPosition(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.BLOCKS, 2f, 0.7f);
            say(p, "§b" + com.wavesurvivor.i18n.WSLang.t("rift.e.loading", n, reload / 20), now);
            return;
        }
        if (now < e.nextShot) return;
        boolean solar = e.engineLevel() >= 3 && e.variant() == 2, judge = e.engineLevel() >= 3 && e.variant() == 3, seraph = e.engineLevel() >= 3 && e.variant() == 1;
        e.nextShot = now + ballistaInterval(e);
        Vec3 look = p.getLookAngle();
        Vec3 muzzle = new Vec3(e.getX(), e.getY() + 1.47, e.getZ()).add(look.scale(1.5));
        if (solar || judge) {
            beam(p, l, muzzle, look, (solar ? 7f : 4f) * boost(l), judge);   // rayon de lumière (Solaire) ou rayon qui marque (Jugement)
        } else {
            Arrow a = new Arrow(EntityType.ARROW, l);
            a.setOwner(p);
            a.setPos(muzzle.x, muzzle.y, muzzle.z);
            a.setBaseDamage((e.engineLevel() >= 2 ? 1.9 : 1.5) * boost(l));   // 6 dégâts à pleine vitesse (7,6 consacrée)
            if (seraph) a.setPierceLevel((byte) 4);                 // carreau perforant
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            a.shoot(look.x, look.y, look.z, 4.0f, 0.0f);
            l.addFreshEntity(a);
        }
        e.setAmmo(e.ammo() - 1);
        e.markFired(now);
        l.playSound(null, BlockPos.containing(muzzle), SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 2.5f, 0.6f);
        l.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y, muzzle.z, 3, 0.1, 0.1, 0.1, 0.02);
        if (e.ammo() <= 0) {
            say(p, "§b" + com.wavesurvivor.i18n.WSLang.t("rift.e.empty"), now);
        } else {
            p.displayClientMessage(Component.literal("§b" + nameOf(e) + " §f" + e.ammo() + " / 16"), true);
        }
    }

    /** Tir instantané (rayon) : touche tout ce qui est sur la ligne ; {@code mark} : la cible est marquée (+25 % de dégâts subis, 8 s). */
    private static void beam(ServerPlayer p, ServerLevel l, Vec3 from, Vec3 dir, float dmg, boolean mark) {
        Vec3 to = from.add(dir.scale(70));
        net.minecraft.world.phys.BlockHitResult bh = l.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) to = bh.getLocation();
        RiftTitan.rockBeam(from, to);   // un rocher du Titan en vol sur la trajectoire est touché
        double len = from.distanceTo(to);
        for (double d = 0; d < len; d += 1.0) {
            Vec3 q = from.add(dir.scale(d));
            l.sendParticles(mark ? ParticleTypes.GLOW : ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        final Vec3 end = to;
        boolean titanHit = false;
        for (Entity t : l.getEntitiesOfClass(Entity.class, new AABB(from, end).inflate(2.0))) {
            if (t instanceof Player || t instanceof SiegeEngineEntity) continue;
            if (t instanceof com.wavesurvivor.entity.TitanHitboxEntity) {
                if (!titanHit && !t.getBoundingBox().inflate(0.3).clip(from, end).isEmpty()) {
                    titanHit = true;
                    RiftTitan.hurtTitan(l.damageSources().playerAttack(p), dmg);
                    if (mark && RiftTitan.entity() != null) RiftTitan.entity().getPersistentData().putLong("ws_rift_mark", l.getGameTime() + 160);
                }
                continue;
            }
            boolean target = RiftFortress.isTarget(t);
            if (!(t instanceof LivingEntity) && !target) continue;
            if (t.getBoundingBox().inflate(0.3).clip(from, end).isEmpty()) continue;
            if (target) RiftFortress.damageTarget(t, dmg);
            else {
                ((LivingEntity) t).hurt(l.damageSources().playerAttack(p), dmg);
                if (mark) t.getPersistentData().putLong("ws_rift_mark", l.getGameTime() + 160);
            }
        }
    }

    /** Dégâts supplémentaires sur une cible marquée par la baliste du Jugement. */
    static float markedDamage(LivingEntity e, float amount) {
        long until = e.getPersistentData().getLong("ws_rift_mark");
        return until > e.level().getGameTime() ? amount * 1.25f : amount;
    }

    // ─── Boulets de catapulte (trajectoire simulée) ───

    static void tick(ServerLevel l) {
        long now = l.getGameTime();
        if (!TOWERS.isEmpty()) {
            for (UUID id : new ArrayList<>(TOWERS)) {
                if (l.getEntity(id) instanceof SiegeEngineEntity e && !e.isRemoved()) {
                    // Bonus de PV (Bâtisseur connecté ou non, Pierre de Fondation portée ou non) : PV max ajustés, même proportion
                    if (now % 40 == 0 && Math.abs(e.baseMaxHp() - e.maxHp()) > 0.5f) e.applyMaxHp();
                    towerTick(l, e, now);
                }
                else TOWERS.remove(id);
            }
        }
        if (BOULDERS.isEmpty()) return;
        for (Boulder b : new ArrayList<>(BOULDERS)) {
            boolean boom = false;
            for (int s = 0; s < 4 && !boom; s++) {
                b.pos = b.pos.add(b.vel.scale(0.25));
                b.vel = b.vel.add(0, -0.0125, 0);
                BlockPos bp = BlockPos.containing(b.pos);
                if (!l.getBlockState(bp).isAir() && l.getBlockState(bp).getFluidState().isEmpty()) boom = true;
                else if (!l.getEntitiesOfClass(Entity.class, new AABB(b.pos, b.pos).inflate(1.3),
                        t -> (t instanceof LivingEntity && !(t instanceof Player)) || RiftFortress.isTarget(t) || t instanceof com.wavesurvivor.entity.TitanHitboxEntity).isEmpty()) boom = true;
                if (b.pos.y < RiftManager.CENTER.getY() - 120) { BOULDERS.remove(b); boom = false; break; }
            }
            if (!BOULDERS.contains(b)) continue;
            l.sendParticles(ParticleTypes.LARGE_SMOKE, b.pos.x, b.pos.y, b.pos.z, 2, 0.1, 0.1, 0.1, 0.0);
            l.sendParticles(ParticleTypes.FLAME, b.pos.x, b.pos.y, b.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (boom) {
                BOULDERS.remove(b);
                explode(l, b);
            }
        }
    }

    private static void explode(ServerLevel l, Boulder b) {
        Entity owner = l.getServer().getPlayerList().getPlayer(b.owner);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, b.pos.x, b.pos.y, b.pos.z, 1, 0, 0, 0, 0);
        l.playSound(null, BlockPos.containing(b.pos), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4f, 0.8f);
        blast(l, b.pos, 4.0, b.mul, owner, b.fire);
        if (b.frag) {   // fragmentation : 5 éclats autour de l'impact
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5 + Math.random();
                Vec3 q = b.pos.add(Math.cos(a) * 3.5, 0.5, Math.sin(a) * 3.5);
                l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                blast(l, q, 2.2, b.mul * 0.45f, owner, false);
            }
        }
    }

    private static void blast(ServerLevel l, Vec3 center, double r, float mul, Entity owner, boolean fire) {
        boolean titanDone = false;
        for (Entity t : l.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(r + 8))) {
            if (t instanceof Player || t instanceof SiegeEngineEntity) continue;
            if (t instanceof com.wavesurvivor.entity.TitanHitboxEntity || RiftTitan.isTitan(t)) {   // le Titan, via sa boîte de contact ou lui-même : une seule fois
                if (titanDone) continue;
                double d = t.getBoundingBox().getCenter().distanceTo(center);
                if (d > r + (t instanceof com.wavesurvivor.entity.TitanHitboxEntity ? 4 : t.getBbWidth())) continue;
                titanDone = true;
                float dmg = (float) (80 * mul * (1 - 0.7 * Math.min(1, d / r))) * 1.5f;
                if (fire && RiftTitan.entity() != null) RiftTitan.entity().setSecondsOnFire(6);
                RiftTitan.hurtTitan(l.damageSources().explosion(null, owner), dmg);
                continue;
            }
            boolean target = RiftFortress.isTarget(t);
            if (!(t instanceof LivingEntity) && !target) continue;
            double d = t.getBoundingBox().getCenter().distanceTo(center);
            if (d > r + t.getBbWidth()) continue;
            float dmg = (float) (80 * mul * (1 - 0.7 * Math.min(1, d / r)));
            if (fire && t instanceof LivingEntity) t.setSecondsOnFire(6);
            boolean big = target || RiftTitan.isTitan(t) || t.getPersistentData().getBoolean("ws_rift_avatar");
            if (big) dmg *= 1.5f;
            if (target) RiftFortress.damageTarget(t, dmg);
            else ((LivingEntity) t).hurt(l.damageSources().explosion(null, owner), dmg);
        }
    }
}
