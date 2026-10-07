package com.wavesurvivor.altar;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.loot.LootItems;
import com.wavesurvivor.network.MonolithShopPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DÉFENSE DU MONOLITHE — le monolithe devient une cible à protéger pendant la horde.
 *
 *   - Activée par horde (altar_recipes.json → defense.enabled), seulement pour une horde lancée depuis un autel.
 *   - Barre "Intégrité du Monolithe" affichée aux joueurs à moins de 64 blocs.
 *   - Profanateurs (CustomEntity targetsAltar=true) : ignorent les joueurs et frappent l'autel.
 *   - Réparation : clic droit avec l'item de réparation (poudre d'os par défaut).
 *   - Boutique (clic droit sans l'item de réparation) : 3 améliorations en émeraudes, communes à l'équipe,
 *     perdues en fin de horde — Renfort (+PV max), Régénération (PV/s), Blindage (-% dégâts).
 *   - 0 PV : horde échouée + autel profané. Victoire au-dessus du seuil : bonus à tous les défenseurs.
 */
public class AltarDefense {

    private static final double VIEW_RANGE = 64.0;
    private static final double ATTACK_REACH = 2.3;
    private static final int ATTACK_INTERVAL = 20;

    public static final int UP_HP = 0, UP_REGEN = 1, UP_ARMOR = 2;
    public static final String[] UP_NAMES = {"Renfort", "Régénération", "Blindage"};

    private static class Session {
        String hordeName;
        String dim;
        BlockPos pos;
        AltarRecipes.Defense cfg;
        float hp;
        float maxHp;
        final int[] levels = new int[3];
        float regenCarry;
        ServerBossEvent bar;
        boolean warned50, warned25;
        MinecraftServer server;
    }

    private static Session SESSION = null;
    private static final Map<UUID, Long> PROFANERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REPAIR = new ConcurrentHashMap<>();

    public static boolean isActive() { return SESSION != null; }

    public static boolean isActiveAt(String dim, BlockPos pos) {
        return SESSION != null && SESSION.pos.equals(pos) && SESSION.dim.equals(dim);
    }

    public static void registerProfaner(Entity e) {
        if (e != null) PROFANERS.put(e.getUUID(), 0L);
    }

    /** Profanateur suivi (utilisé pour ne pas lui imposer d'autre cible, ex : piglins de horde). */
    public static boolean isProfaner(UUID id) {
        return PROFANERS.containsKey(id);
    }

    /** Position du monolithe en défense (null si aucune). */
    public static BlockPos activePos() {
        Session s = SESSION;
        return s != null ? s.pos : null;
    }

    /** Retire jusqu'à `amount` d'intégrité (siphon de boss, avant Blindage). @return quantité réellement retirée. */
    public static float siphon(ServerLevel level, float amount) {
        Session s = SESSION;
        if (s == null || amount <= 0) return 0;
        float before = s.hp;
        damage(level, s, amount);
        return Math.max(0, before - s.hp);
    }

    // ─── Cycle de vie (appelé par HordeManager) ───

    public static void onHordeStart(MinecraftServer server, String hordeName, String dim, BlockPos pos) {
        SESSION = null;
        PROFANERS.clear();
        if (pos == null || dim == null) return;
        AltarRecipes.Defense cfg = AltarRecipes.getDefense(hordeName);
        if (cfg == null) return;

        Session s = new Session();
        s.hordeName = hordeName;
        s.dim = dim;
        s.pos = pos;
        s.cfg = cfg;
        s.maxHp = Math.max(1, cfg.maxHealth);
        // Héritage — Seigneur 4 : Monolithe +20 % de PV
        boolean lord4 = com.wavesurvivor.horde.renaissance.Heritage.anyone(server, com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 4);
        if (lord4) s.maxHp *= 1.20f;
        s.hp = s.maxHp;
        s.server = server;
        s.bar = new ServerBossEvent(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.integrite_du_monolithe_2901")).withStyle(ChatFormatting.GREEN),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
        s.bar.setProgress(1f);
        SESSION = s;
        refreshBar(s);

        broadcast(server, com.wavesurvivor.i18n.WSLang.t("srv.defense_du_monolithe_protegez_l_autel_re")
                + prettyItem(cfg.repairItem) + com.wavesurvivor.i18n.WSLang.t("srv.ameliorez_le_avec_des") + prettyItem(cfg.upgradeCurrency)
                + com.wavesurvivor.i18n.WSLang.t("srv.clic_droit"));
        if (lord4) broadcast(server, com.wavesurvivor.i18n.WSLang.t("heritage.monolith_bonus"));
        WaveSurvivorMod.LOGGER.info("[AltarDefense] Session démarrée ({} PV) @ {}", cfg.maxHealth, pos);
    }

    /** victory = horde terminée normalement (pas stop / échec). */
    public static void onHordeEnd(MinecraftServer server, boolean victory) {
        Session s = SESSION;
        SESSION = null;
        if (server != null) {
            for (UUID id : PROFANERS.keySet()) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    Entity e = lvl.getEntity(id);
                    if (e != null) { e.discard(); break; }
                }
            }
        }
        PROFANERS.clear();
        if (s == null) return;

        float pct = 100f * s.hp / Math.max(1, s.maxHp);
        if (victory && pct >= s.cfg.bonusThreshold) {
            ItemStack proto = LootItems.resolve(s.cfg.bonusItem, s.cfg.bonusCount);
            for (ServerPlayer p : s.bar.getPlayers()) {
                if (!proto.isEmpty()) {
                    ItemStack give = proto.copy();
                    if (!p.getInventory().add(give)) p.drop(give, false);
                }
                p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.monolithe_intact_a") + Math.round(pct) + com.wavesurvivor.i18n.WSLang.t("srv.bonus_de_defense")
                        + (proto.isEmpty() ? s.cfg.bonusItem : proto.getHoverName().getString())));
            }
        } else if (victory) {
            broadcast(server, com.wavesurvivor.i18n.WSLang.t("srv.monolithe_a") + Math.round(pct) + com.wavesurvivor.i18n.WSLang.t("srv.pas_de_bonus_seuil") + s.cfg.bonusThreshold + "%).");
        }
        s.bar.removeAllPlayers();
    }

    // ─── Tick ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Session s = SESSION;
        if (s == null) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        ServerLevel level = resolveLevel(server, s.dim);
        if (level == null) return;

        if (now % 20 == 0) {
            updateBarPlayers(level, s);
            tickRegen(level, s);
        }
        if (now % 5 == 0) tickProfaners(level, s, now);
    }

    /** Régénération : levels[REGEN] × regenPerLevel PV par seconde. */
    private static void tickRegen(ServerLevel level, Session s) {
        int lvl = s.levels[UP_REGEN];
        if (lvl <= 0 || s.hp >= s.maxHp) return;
        s.regenCarry += (float) (lvl * s.cfg.regenPerLevel);
        int heal = (int) s.regenCarry;
        if (heal <= 0) return;
        s.regenCarry -= heal;
        s.hp = Math.min(s.maxHp, s.hp + heal);
        refreshBar(s);
        if (s.hp > s.maxHp * 0.5f) s.warned50 = false;
        if (s.hp > s.maxHp * 0.25f) s.warned25 = false;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, s.pos.getX() + 0.5, s.pos.getY() + 1.1, s.pos.getZ() + 0.5,
                2, 0.35, 0.2, 0.35, 0.01);
    }

    private static void updateBarPlayers(ServerLevel level, Session s) {
        double cx = s.pos.getX() + 0.5, cz = s.pos.getZ() + 0.5;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            boolean near = p.level() == level && p.distanceToSqr(cx, s.pos.getY(), cz) <= VIEW_RANGE * VIEW_RANGE;
            if (near && !s.bar.getPlayers().contains(p)) s.bar.addPlayer(p);
            else if (!near && s.bar.getPlayers().contains(p)) s.bar.removePlayer(p);
        }
    }

    private static void tickProfaners(ServerLevel level, Session s, long now) {
        double tx = s.pos.getX() + 0.5, ty = s.pos.getY(), tz = s.pos.getZ() + 0.5;
        for (Map.Entry<UUID, Long> e : PROFANERS.entrySet()) {
            Entity ent = level.getEntity(e.getKey());
            if (!(ent instanceof Mob mob) || !mob.isAlive()) {
                PROFANERS.remove(e.getKey());
                continue;
            }
            mob.setTarget(null);
            // Mode Kingdom : tant qu'il suit un couloir / chemin tracé, la marche le guide ; cette IA reprend au Monolithe
            if (com.wavesurvivor.horde.kingdom.KingdomManager.marching(e.getKey())) continue;
            // Mobs à « cerveau » (piglins) : on efface leurs cibles et on leur donne l'autel comme destination
            boolean brainMob = mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin;
            if (brainMob) {
                var brain = mob.getBrain();
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
            }
            double dx = mob.getX() - tx, dz = mob.getZ() - tz;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz > ATTACK_REACH || Math.abs(mob.getY() - ty) > 2.5) {
                if (brainMob) {
                    mob.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                            new net.minecraft.world.entity.ai.memory.WalkTarget(s.pos, 1.0f, 1));
                } else if (now % 20 == 0 || mob.getNavigation().isDone()) {
                    mob.getNavigation().moveTo(tx, ty, tz, 1.0);
                }
                continue;
            }
            if (brainMob) mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(tx, ty + 0.5, tz);
            if (now - e.getValue() >= ATTACK_INTERVAL) {
                e.setValue(now);
                mob.swing(InteractionHand.MAIN_HAND);
                AttributeInstance atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                float dmg = (atk != null ? (float) Math.max(1.0, atk.getValue()) : 2f)
                        * (float) Math.max(0, s.cfg.profanerDamageMultiplier);
                level.playSound(null, s.pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8f, 0.6f);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, tx, ty + 1.1, tz, 3, 0.3, 0.2, 0.3, 0.05);
                damage(level, s, dmg);
                if (SESSION == null) return;
            }
        }
    }

    // ─── Intégrité ───

    private static float armorFactor(Session s) {
        float f = Math.max(0f, 1f - s.levels[UP_ARMOR] * s.cfg.armorPerLevel / 100f);
        // Sceau du Gardien : un porteur à ≤ 8 blocs du Monolithe → −30 % de dégâts (−45 / −60 % aux niveaux II / III)
        f *= com.wavesurvivor.item.RelicEffects.guardianFactor(s.server, s.dim, s.pos);
        return f;
    }

    private static void damage(ServerLevel level, Session s, float rawAmount) {
        float amount = rawAmount * armorFactor(s);
        s.hp = Math.max(0, s.hp - amount);
        refreshBar(s);
        float pct = 100f * s.hp / s.maxHp;
        if (!s.warned50 && pct <= 50) {
            s.warned50 = true;
            broadcast(level.getServer(), com.wavesurvivor.i18n.WSLang.t("srv.le_monolithe_faiblit_50_reparez_le"));
        }
        if (!s.warned25 && pct <= 25) {
            s.warned25 = true;
            broadcast(level.getServer(), com.wavesurvivor.i18n.WSLang.t("srv.le_monolithe_va_tomber_25"));
            level.playSound(null, s.pos, SoundEvents.WITHER_AMBIENT, SoundSource.BLOCKS, 1f, 0.7f);
        }
        if (s.hp <= 0) fail(level, s);
    }

    private static void refreshBar(Session s) {
        float p = s.hp / s.maxHp;
        s.bar.setProgress(Math.max(0f, Math.min(1f, p)));
        s.bar.setColor(p > 0.5f ? BossEvent.BossBarColor.GREEN : p > 0.25f ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        StringBuilder ups = new StringBuilder();
        if (s.levels[UP_HP] > 0) ups.append(" §7[R").append(s.levels[UP_HP]).append("]");
        if (s.levels[UP_REGEN] > 0) ups.append(" §a[+").append(s.levels[UP_REGEN]).append("]");
        if (s.levels[UP_ARMOR] > 0) ups.append(" §b[B").append(s.levels[UP_ARMOR]).append("]");
        ChatFormatting col = p > 0.5f ? ChatFormatting.GREEN : p > 0.25f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        s.bar.setName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.integrite_du_monolithe") + Math.round(s.hp) + "/" + Math.round(s.maxHp) + " ⛨")
                .withStyle(col).append(Component.literal(ups.toString())));
    }

    private static void fail(ServerLevel level, Session s) {
        SESSION = null;
        s.bar.removeAllPlayers();

        AltarStore.AltarEntry entry = AltarStore.get(s.dim, s.pos);
        if (entry != null) {
            entry.hordeName = null;
            entry.zoneRadius = 0;
            AltarManager.applyStyle(level, s.pos, entry, net.minecraft.world.item.DyeColor.RED, AltarParticles.AMES.id);
            AltarStore.register(entry);
        }
        WaveSurvivorMod.LOGGER.info("[AltarDefense] Monolithe détruit @ {} → horde échouée", s.pos);
        // « La Chute du Monolithe » : séquence de défaite (~8 s), puis arrêt propre de la horde et bilan
        com.wavesurvivor.horde.renaissance.RenaissanceRewards.markDefeat(); // PR des vagues acquises versés à l'arrêt
        MonolithFall.start(level, s.pos, s.dim, s.hordeName);
    }

    /** Clic droit avec l'item de réparation. @return true si l'interaction a été consommée. */
    /**
     * Mode Kingdom : dépense {@code cost} unités de la monnaie de la horde (émeraudes…). Créatif : gratuit.
     * @return faux si pas de Monolithe actif ou pas assez de monnaie (message affiché).
     */
    public static boolean trySpend(ServerPlayer player, int cost) {
        Session s = SESSION;
        if (s == null) {
            player.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.defense_only_kingdom"), true);
            return false;
        }
        // Partie Kingdom : la monnaie est dans le trésor commun
        if (com.wavesurvivor.horde.kingdom.KingdomTreasury.active()) {
            if (player.isCreative()) return true;
            var money = com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.MONEY;
            if (!com.wavesurvivor.horde.kingdom.KingdomTreasury.has(money, cost)) {
                player.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.treasury.need_money", cost,
                        com.wavesurvivor.horde.kingdom.KingdomTreasury.get(money)), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
                return false;
            }
            com.wavesurvivor.horde.kingdom.KingdomTreasury.spend(money, cost);
            com.wavesurvivor.horde.kingdom.KingdomTreasury.sync(player.getServer());
            return true;
        }
        ItemStack currency = LootItems.resolve(s.cfg.upgradeCurrency, 1);
        if (currency.isEmpty()) return false;
        Item cur = currency.getItem();
        if (player.isCreative()) return true;
        if (count(player, cur) < cost) {
            player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.il_te_faut") + cost + " " + currency.getHoverName().getString() + "."), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
            return false;
        }
        consume(player, cur, cost);
        return true;
    }

    /** Mode Kingdom (sanctuaire de réparation) : soigne le Monolithe actif. */
    public static void healMonolith(float amount) {
        Session s = SESSION;
        if (s == null || s.hp >= s.maxHp) return;
        s.hp = Math.min(s.maxHp, s.hp + amount);
        if (s.hp > s.maxHp * 0.5f) s.warned50 = false;
        if (s.hp > s.maxHp * 0.25f) s.warned25 = false;
        refreshBar(s);
    }

    /** Mode Kingdom (Mairie) : +PV max au Monolithe (et autant de PV actuels), barre mise à jour. */
    public static void addMaxHp(float v) {
        Session s = SESSION;
        if (s == null) return;
        s.maxHp += v;
        s.hp = Math.min(s.maxHp, s.hp + v);
        refreshBar(s);
    }

    // ─── Sauvegarde de partie (HordeSession) ───

    /** Position de l'autel de la horde en cours (null sans session). */
    public static BlockPos sessionPos() { Session s = SESSION; return s == null ? null : s.pos; }

    public static String sessionDim() { Session s = SESSION; return s == null ? null : s.dim; }

    /** {PV, PV max, niveau PV, niveau régénération, niveau armure} du Monolithe, ou null sans session. */
    public static float[] snapshot() {
        Session s = SESSION;
        return s == null ? null : new float[]{s.hp, s.maxHp, s.levels[0], s.levels[1], s.levels[2]};
    }

    /** Reprise d'une partie : PV et niveaux d'amélioration du Monolithe restaurés. */
    public static void restore(float hp, float maxHp, int[] levels) {
        Session s = SESSION;
        if (s == null) return;
        for (int i = 0; i < Math.min(3, levels.length); i++) s.levels[i] = levels[i];
        s.maxHp = Math.max(1f, maxHp);
        s.hp = Math.max(1f, Math.min(s.maxHp, hp));
        refreshBar(s);
    }

    // ─── Coups extérieurs (unités « attaque les bâtiments », explosions) ───

    /** Le Monolithe en défense est-il à portée de cette position (même dimension, horizontal ≤ reach, vertical ≤ 3) ? */
    public static boolean inReach(ServerLevel level, net.minecraft.world.phys.Vec3 at, double reach) {
        Session s = SESSION;
        if (s == null || !s.dim.equals(level.dimension().location().toString())) return false;
        double dx = at.x - (s.pos.getX() + 0.5), dz = at.z - (s.pos.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach && Math.abs(at.y - s.pos.getY()) <= 3;
    }

    /** Coup porté au Monolithe par une unité ou une explosion (armure et reliques appliquées). */
    public static void externalHit(ServerLevel level, float rawDamage) {
        Session s = SESSION;
        if (s == null || rawDamage <= 0 || !s.dim.equals(level.dimension().location().toString())) return;
        double tx = s.pos.getX() + 0.5, ty = s.pos.getY(), tz = s.pos.getZ() + 0.5;
        level.playSound(null, s.pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8f, 0.6f);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, tx, ty + 1.1, tz, 3, 0.3, 0.2, 0.3, 0.05);
        damage(level, s, rawDamage);
    }

    /** Mutateur « Monolithe fragile » : multiplie l'intégrité max (et actuelle) du Monolithe. */
    public static void scaleMaxHp(double f) {
        Session s = SESSION;
        if (s == null || f <= 0) return;
        s.maxHp = Math.max(1f, (float) (s.maxHp * f));
        s.hp = Math.min(s.maxHp, (float) (s.hp * f));
        refreshBar(s);
    }

    /** Mode Kingdom : objet servant de monnaie à la horde en cours (émeraude par défaut). */
    public static net.minecraft.world.item.Item currencyItem() {
        Session s = SESSION;
        if (s == null) return net.minecraft.world.item.Items.EMERALD;
        ItemStack c = LootItems.resolve(s.cfg.upgradeCurrency, 1);
        return c.isEmpty() ? net.minecraft.world.item.Items.EMERALD : c.getItem();
    }

    /** Mode Kingdom : vrai si l'objet est la monnaie de la horde en cours (émeraude…). */
    public static boolean isCurrency(ItemStack st) {
        Session s = SESSION;
        if (s == null || st == null || st.isEmpty()) return false;
        ItemStack currency = LootItems.resolve(s.cfg.upgradeCurrency, 1);
        return !currency.isEmpty() && st.is(currency.getItem());
    }

    public static boolean tryRepair(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        Session s = SESSION;
        if (s == null || !isActiveAt(level.dimension().location().toString(), pos)) return false;
        ItemStack repair = LootItems.resolve(s.cfg.repairItem, 1);
        if (repair.isEmpty() || !held.is(repair.getItem())) return false;

        long now = level.getGameTime();
        Long last = LAST_REPAIR.get(player.getUUID());
        if (last != null && now - last < 10) return true;
        LAST_REPAIR.put(player.getUUID(), now);

        if (s.hp >= s.maxHp) {
            player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.le_monolithe_est_intact")), true);
            return true;
        }
        if (!player.isCreative()) held.shrink(1);
        s.hp = Math.min(s.maxHp, s.hp + s.cfg.repairAmount);
        refreshBar(s);
        if (s.hp > s.maxHp * 0.5f) s.warned50 = false;
        if (s.hp > s.maxHp * 0.25f) s.warned25 = false;
        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1f, 0.8f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.4, 0.3, 0.4, 0.02);
        player.displayClientMessage(Component.literal("§a⛨ +" + s.cfg.repairAmount + " §7(" + Math.round(s.hp) + "/" + Math.round(s.maxHp) + ")"), true);
        return true;
    }

    // ─── Boutique d'améliorations ───

    /** Clic droit (sans item de réparation) pendant la défense : ouvre la boutique. @return true si ouverte. */
    public static boolean tryOpenShop(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!isActiveAt(level.dimension().location().toString(), pos)) return false;
        sendShopState(player, true);
        return true;
    }

    public static void sendShopState(ServerPlayer p, boolean open) {
        Session s = SESSION;
        if (s == null) return;
        int[] costs = new int[3];
        for (int i = 0; i < 3; i++) costs[i] = s.cfg.cost(i, s.levels[i]);
        MonolithShopPackets.State st = new MonolithShopPackets.State(open, s.pos, Math.round(s.hp), Math.round(s.maxHp),
                s.levels.clone(), costs, s.cfg.upgradeMaxLevel, s.cfg.upgradeCurrency,
                s.cfg.hpPerLevel, s.cfg.regenPerLevel, s.cfg.armorPerLevel);
        // Mode Kingdom : niveau de la Mairie et rayon du claim (onglet Mairie de la boutique)
        if (com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) {
            st.townTier = com.wavesurvivor.horde.kingdom.KingdomTownHall.tier();
            st.claimRadius = com.wavesurvivor.horde.kingdom.KingdomTownHall.claimRadius();
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), st);
    }

    /** Achat d'un niveau d'amélioration (commun à l'équipe). */
    public static void upgrade(ServerPlayer player, int type) {
        Session s = SESSION;
        // Mode Kingdom : types ≥ 10 = achats du royaume (défenses 10-13, rempart 20, porte 21, pièges 30-34)
        if (s != null && type >= 10) {
            if (player.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5) > 64) {
                player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.trop_loin_du_monolithe")), true);
                return;
            }
            if (type == 40) com.wavesurvivor.horde.kingdom.KingdomTownHall.upgrade(player);   // Mairie
            else if (type == 41) com.wavesurvivor.horde.kingdom.KingdomDefenses.repairAll(player); // Mairie : tout réparer
            else if (type == 42) com.wavesurvivor.horde.kingdom.KingdomManager.scoutRepeat(player); // Mairie : rapport de l'Éclaireur
            else if (type == 50) com.wavesurvivor.horde.kingdom.KingdomTreasury.withdraw(player, 10); // retrait d'émeraudes
            else if (type >= 60 && type < 70) com.wavesurvivor.horde.kingdom.KingdomRoles.choose(player, type - 60); // rôle
            else if (type >= 70 && type < 75) com.wavesurvivor.horde.kingdom.KingdomAlchemy.brew(player, type - 70); // fioles
            else if (type >= 75 && type < 80) com.wavesurvivor.horde.kingdom.KingdomAlchemy.upgrade(player, type - 75); // amélioration des fioles
            else if (type >= 80 && type < 83) com.wavesurvivor.horde.kingdom.KingdomOmens.choose(player, type - 80); // présages
            else if (type == 90) com.wavesurvivor.horde.kingdom.KingdomRoleExtras.recoverHorn(player); // cor du Commandant
            else com.wavesurvivor.horde.kingdom.KingdomDefenses.buy(player, type);
            sendShopState(player, false);
            return;
        }
        if (s == null || type < 0 || type > 2) return;
        if (!(player.level() instanceof ServerLevel level) || !isActiveAt(level.dimension().location().toString(), s.pos)) return;
        if (player.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5) > 64) {
            player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.trop_loin_du_monolithe")), true);
            return;
        }
        int lvl = s.levels[type];
        if (lvl >= s.cfg.upgradeMaxLevel) {
            player.displayClientMessage(Component.literal("§7" + com.wavesurvivor.i18n.WSLang.t(UP_NAMES[type]) + com.wavesurvivor.i18n.WSLang.t("srv.est_deja_au_niveau_max")), true);
            sendShopState(player, false);
            return;
        }
        int cost = s.cfg.cost(type, lvl);
        ItemStack currency = LootItems.resolve(s.cfg.upgradeCurrency, 1);
        if (currency.isEmpty()) return;
        Item cur = currency.getItem();
        if (com.wavesurvivor.horde.kingdom.KingdomTreasury.active()) {
            // Partie Kingdom : payé avec le trésor commun
            if (!trySpend(player, cost)) return;
        } else if (!player.isCreative()) {
            if (count(player, cur) < cost) {
                player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.il_te_faut") + cost + " " + currency.getHoverName().getString() + "."), true);
                level.playSound(null, s.pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
                return;
            }
            consume(player, cur, cost);
        }

        s.levels[type] = lvl + 1;
        if (type == UP_HP) {
            s.maxHp += s.cfg.hpPerLevel;
            s.hp = Math.min(s.maxHp, s.hp + s.cfg.hpPerLevel);
        }
        refreshBar(s);

        level.playSound(null, s.pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.3f);
        level.playSound(null, s.pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, s.pos.getX() + 0.5, s.pos.getY() + 1.2, s.pos.getZ() + 0.5,
                30, 0.4, 0.6, 0.4, 0.2);
        broadcast(level.getServer(), "§a⛨ §f" + player.getGameProfile().getName() + com.wavesurvivor.i18n.WSLang.t("srv.a_ameliore_le_monolithe")
                + com.wavesurvivor.i18n.WSLang.t(UP_NAMES[type]) + com.wavesurvivor.i18n.WSLang.t("srv.niv") + s.levels[type] + " §8(-" + cost + " " + currency.getHoverName().getString() + ")");

        // Rafraîchit l'écran de tous les joueurs proches qui l'ont ouvert (le client ignore s'il est fermé)
        for (ServerPlayer p : s.bar.getPlayers()) sendShopState(p, false);
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(item)) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(item)) n += st.getCount();
        return n;
    }

    private static void consume(ServerPlayer p, Item item, int amount) {
        int left = amount;
        java.util.List<ItemStack> all = new java.util.ArrayList<>(p.getInventory().items);
        all.addAll(p.getInventory().offhand);
        for (ItemStack st : all) {
            if (left <= 0) break;
            if (!st.is(item)) continue;
            int take = Math.min(left, st.getCount());
            st.shrink(take);
            left -= take;
        }
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
    }

    // ─── Test admin ───

    public static int spawnTestProfaners(MinecraftServer server, int n) {
        Session s = SESSION;
        if (s == null) return 0;
        ServerLevel level = resolveLevel(server, s.dim);
        if (level == null) return 0;
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            int x = s.pos.getX() + (int) Math.round(Math.cos(a) * 12);
            int z = s.pos.getZ() + (int) Math.round(Math.sin(a) * 12);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) continue;
            zombie.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            zombie.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.profanateur_test")));
            zombie.setCustomNameVisible(true);
            zombie.setPersistenceRequired();
            if (level.addFreshEntity(zombie)) {
                registerProfaner(zombie);
                spawned++;
            }
        }
        return spawned;
    }

    // ─── Utils ───

    private static void broadcast(MinecraftServer server, String msg) {
        Component c = Component.literal(msg);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static String prettyItem(String id) {
        ItemStack st = LootItems.resolve(id, 1);
        return st.isEmpty() ? id : st.getHoverName().getString();
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dim) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
            ServerLevel lvl = server.getLevel(key);
            return lvl != null ? lvl : server.overworld();
        } catch (Exception e) {
            return server.overworld();
        }
    }
}
