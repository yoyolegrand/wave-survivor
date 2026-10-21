package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Random;

/**
 * BRASIER DES OFFRANDES (Kingdom) — on y sacrifie N'IMPORTE QUEL objet : seule la QUANTITÉ compte (1 objet = 1 point).
 *  - Chaque fois que la jauge est pleine (64 / 48 / 32 points selon le niveau) : un tirage de récompense.
 *  - Niveau 2 à 1 000 objets sacrifiés au total, niveau 3 à 3 000 (choix d'une spécialisation).
 *  - Uniquement pendant le Calme, 1 500 points maximum par Calme.
 *  - Refusés (restent dans la grille) : objets enchantés, objets du mod (reliques, Reliquaires…), clés de coffre,
 *    et les ressources du trésor (bois, pierre, fer, monnaie) pour éviter les boucles.
 * Spécialisations : Abondance (ressources ×1,5), Reliques (Reliquaire et bénédiction ×2),
 * Guerre (débloque Vague de réparation et Ferveur du royaume, chances doublées).
 */
public class OfferingBrazierBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOTS = 27;
    /** Paliers du niveau (COMMUN à tous les joueurs) : total d'objets sacrifiés au Brasier. */
    public static final int TIER2 = 4000, TIER3 = 12000;
    /** Limite PAR JOUEUR et par Calme : 750 au niveau 1, +250 par niveau. */
    public static final int CALM_CAP_BASE = 750, CALM_CAP_PER_LEVEL = 250;
    public static final String[] VARIANTS = {"", "abundance", "relics", "war"};
    private static final Random RNG = new Random();

    private final SimpleContainer storage = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            OfferingBrazierBlockEntity.this.setChanged();
        }
    };
    private int total, gauge, calmCycle = -1;
    /** Points sacrifiés pendant le Calme en cours, par joueur. */
    private final java.util.Map<java.util.UUID, Integer> calmUsed = new java.util.HashMap<>();
    private String variant = "";

    public int calmCap() { return CALM_CAP_BASE + CALM_CAP_PER_LEVEL * (level() - 1); }

    private int usedBy(java.util.UUID id) {
        return calmCycle == KingdomManager.cycle() ? calmUsed.getOrDefault(id, 0) : 0;
    }

    /** Valeurs synchronisées avec le menu de CE joueur : total, jauge, taille de jauge, ses points du Calme, sa limite, spécialisation. */
    private ContainerData dataFor(java.util.UUID player) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> Math.min(total, 32000); // valeurs du menu limitées à 32 767
                    case 1 -> gauge;
                    case 2 -> perRoll();
                    case 3 -> usedBy(player);
                    case 4 -> calmCap();
                    case 5 -> variantIndex();
                    default -> 0;
                };
            }
            @Override
            public void set(int i, int v) {}
            @Override
            public int getCount() { return 6; }
        };
    }

    public OfferingBrazierBlockEntity(BlockPos pos, BlockState state) {
        super(com.wavesurvivor.registry.ModBlockEntities.OFFERING_BRAZIER.get(), pos, state);
    }

    public SimpleContainer getStorage() { return storage; }

    public static int levelOf(int total) { return total >= TIER3 ? 3 : total >= TIER2 ? 2 : 1; }
    public int level() { return levelOf(total); }
    public int perRoll() { return switch (level()) { case 3 -> 32; case 2 -> 48; default -> 64; }; }
    public String variant() { return variant; }
    private int variantIndex() {
        for (int i = 0; i < VARIANTS.length; i++) if (VARIANTS[i].equals(variant)) return i;
        return 0;
    }

    // ─── Ce qui peut être sacrifié ───

    /** Objet accepté par le Brasier ? (sinon il reste dans la grille) */
    public static boolean accepts(ItemStack s) {
        if (s.isEmpty() || s.isEnchanted()) return false;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id != null && ("wavesurvivor".equals(id.getNamespace()) || "roulettechest".equals(id.getNamespace()))) return false;
        if (com.wavesurvivor.altar.AltarDefense.isCurrency(s)) return false;
        // Ressources du trésor : pas de boucle « ressource → offrande → ressource »
        if (s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS)) return false;
        return !(s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.DEEPSLATE)
                || s.is(Items.IRON_INGOT) || s.is(Items.RAW_IRON) || s.is(Items.IRON_NUGGET) || s.is(Items.IRON_BLOCK)
                || s.is(Items.RAW_IRON_BLOCK));
    }

    // ─── Sacrifice (bouton du menu) ───

    public void sacrifice(ServerPlayer p) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!KingdomManager.isCalmPhase()) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.brazier.calm_only"), true);
            return;
        }
        if (calmCycle != KingdomManager.cycle()) { calmCycle = KingdomManager.cycle(); calmUsed.clear(); }
        int cap = calmCap();
        int room = cap - calmUsed.getOrDefault(p.getUUID(), 0);
        if (room <= 0) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.brazier.cap", cap), true);
            return;
        }
        int points = 0, refused = 0;
        for (int i = 0; i < SLOTS && room > 0; i++) {
            ItemStack s = storage.getItem(i);
            if (s.isEmpty()) continue;
            if (!accepts(s)) { refused += s.getCount(); continue; }
            int take = Math.min(s.getCount(), room);
            s.shrink(take);
            room -= take;
            points += take;
        }
        if (points == 0) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c(refused > 0 ? "kingdom.brazier.refused" : "kingdom.brazier.empty"), true);
            return;
        }
        storage.setChanged();
        int before = level();
        total += points;
        calmUsed.merge(p.getUUID(), points, Integer::sum);
        gauge += points;
        int per = perRoll();
        int rolls = gauge / per;
        gauge %= per;
        // Tirages
        EnumMap<KingdomTreasury.Res, Integer> res = new EnumMap<>(KingdomTreasury.Res.class);
        int relics = 0, pr = 0;
        boolean blessing = false, repair = false, fervor = false;
        for (int r = 0; r < rolls; r++) {
            String reward = roll(p);
            switch (reward) {
                case "wood" -> res.merge(KingdomTreasury.Res.WOOD, qty(15, 30), Integer::sum);
                case "stone" -> res.merge(KingdomTreasury.Res.STONE, qty(15, 30), Integer::sum);
                case "money" -> res.merge(KingdomTreasury.Res.MONEY, qty(5, 15), Integer::sum);
                case "iron" -> res.merge(KingdomTreasury.Res.IRON, qty(3, 8), Integer::sum);
                case "essence" -> res.merge(KingdomTreasury.Res.ESSENCE, qty(1, 3), Integer::sum);
                case "reliquary" -> relics++;
                case "blessing" -> {
                    if (blessing) res.merge(KingdomTreasury.Res.ESSENCE, 3, Integer::sum); // une seule bénédiction par sacrifice
                    blessing = true;
                }
                case "repair" -> repair = true;
                case "fervor" -> fervor = true;
                case "pr" -> pr += 2 + RNG.nextInt(4);
                default -> {}
            }
        }
        // Abondance : ressources ×1,5
        if ("abundance".equals(variant)) res.replaceAll((k, v) -> (int) Math.round(v * 1.5));
        res.forEach((k, v) -> { if (v > 0 && KingdomTreasury.active()) KingdomTreasury.add(k, v); });
        for (int i = 0; i < relics; i++) {
            ItemStack rel = new ItemStack(com.wavesurvivor.registry.ModItems.RELIQUAIRE_SCELLE.get());
            if (!p.getInventory().add(rel)) p.drop(rel, false);
        }
        if (pr > 0) com.wavesurvivor.horde.renaissance.RenaissanceStore.addPoints(p, pr);
        if (repair) OfferingBrazier.repairWave(sl, worldPosition);
        if (fervor) OfferingBrazier.grantFervor();
        // Effets
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, worldPosition.getX() + 1.0, worldPosition.getY() + 1.6, worldPosition.getZ() + 1.0,
                20 + Math.min(60, points / 8), 0.4, 0.4, 0.4, 0.04);
        sl.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 1.0, worldPosition.getY() + 2.0, worldPosition.getZ() + 1.0, 6, 0.3, 0.3, 0.3, 0.02);
        sl.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.2f, 0.8f);
        // Récapitulatif
        StringBuilder sb = new StringBuilder();
        res.forEach((k, v) -> { if (v > 0) sb.append(" §f+").append(v).append(' ').append(com.wavesurvivor.i18n.WSLang.t("kingdom.brazier.res." + k.key)); });
        if (relics > 0) sb.append(" §d+").append(relics).append(' ').append(com.wavesurvivor.i18n.WSLang.t("kingdom.brazier.reliquary"));
        if (pr > 0) sb.append(" §b+").append(pr).append(" PR");
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.brazier.result", points, rolls, sb.toString()), false);
        if (relics > 0 || blessing || repair || fervor) {
            sl.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        if (repair) OfferingBrazier.announce(sl, "kingdom.brazier.repair_wave", p.getName().getString());
        if (fervor) OfferingBrazier.announce(sl, "kingdom.brazier.fervor", p.getName().getString());
        if (relics > 0) OfferingBrazier.announce(sl, "kingdom.brazier.reliquary_won", p.getName().getString());
        if (blessing) {
            OfferingBrazier.announce(sl, "kingdom.brazier.blessing_won", p.getName().getString());
            p.closeContainer();
            com.wavesurvivor.horde.benediction.RogueUpgradeManager.openMenu(p);
        }
        if (level() > before) {
            OfferingBrazier.announce(sl, level() == 3 ? "kingdom.brazier.level3" : "kingdom.brazier.level2", level());
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 1.0, worldPosition.getY() + 1.5, worldPosition.getZ() + 1.0, 40, 0.6, 0.6, 0.6, 0.3);
        }
        setChanged();
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static int qty(int a, int b) { return a + RNG.nextInt(b - a + 1); }

    /** Tirage pondéré selon le niveau et la spécialisation. */
    private String roll(ServerPlayer luckOwner) {
        int lvl = level();
        boolean relicsSpec = "relics".equals(variant), war = "war".equals(variant);
        // poids ×10 pour garder des décimales (0,7 % → 7)
        String[] ids = {"wood", "stone", "money", "iron", "essence", "reliquary", "blessing", "pr", "repair", "fervor"};
        int[] w = lvl >= 2
                ? new int[]{340, 260, 180, 140, 50, 9, 5, 20, 0, 0}
                : new int[]{400, 300, 160, 100, 30, 7, 4, 15, 0, 0};
        if (relicsSpec) { w[5] *= 2; w[6] *= 2; }
        if (war) { w[8] = 60; w[9] = 40; }
        // Chance de celui qui offre : les meilleures récompenses pèsent davantage
        for (int i = 0; i < w.length; i++) w[i] = (int) Math.round(w[i] * com.wavesurvivor.horde.benediction.BlessingLuck.brazierFactor(luckOwner, ids[i]));
        int sum = 0;
        for (int v : w) sum += v;
        int r = RNG.nextInt(Math.max(1, sum));
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return ids[i];
        }
        return "wood";
    }

    /** Choix de la spécialisation (niveau 3, une seule fois). */
    public void chooseVariant(ServerPlayer p, int idx) {
        if (level() < 3 || !variant.isEmpty() || idx < 1 || idx >= VARIANTS.length) return;
        variant = VARIANTS[idx];
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            OfferingBrazier.announce(sl, "kingdom.brazier.spec_chosen",
                    com.wavesurvivor.i18n.WSLang.t("kingdom.brazier.variant." + variant), p.getName().getString());
        }
    }

    // ─── Menu ───

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.wavesurvivor.offering_brazier");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new OfferingBrazierMenu(id, inv, storage, dataFor(player.getUUID()), worldPosition);
    }

    // ─── Sauvegarde ───

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("total", total);
        tag.putInt("gauge", gauge);
        tag.putInt("calmCycle", calmCycle);
        CompoundTag used = new CompoundTag();
        calmUsed.forEach((id, v) -> used.putInt(id.toString(), v));
        tag.put("calmUsedBy", used);
        tag.putString("variant", variant);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, itemsList());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        total = tag.getInt("total");
        gauge = tag.getInt("gauge");
        calmCycle = tag.contains("calmCycle") ? tag.getInt("calmCycle") : -1;
        calmUsed.clear();
        CompoundTag used = tag.getCompound("calmUsedBy");
        for (String k : used.getAllKeys()) {
            try { calmUsed.put(java.util.UUID.fromString(k), used.getInt(k)); } catch (IllegalArgumentException ignored) {}
        }
        variant = tag.getString("variant");
        net.minecraft.core.NonNullList<ItemStack> l = net.minecraft.core.NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, l);
        for (int i = 0; i < SLOTS; i++) storage.setItem(i, l.get(i));
    }

    private net.minecraft.core.NonNullList<ItemStack> itemsList() {
        net.minecraft.core.NonNullList<ItemStack> l = net.minecraft.core.NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) l.set(i, storage.getItem(i));
        return l;
    }

    /** Synchronisé au client (couleur des flammes selon la spécialisation). */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = super.getUpdateTag();
        t.putInt("total", total);
        t.putString("variant", variant);
        return t;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
