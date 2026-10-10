package com.wavesurvivor;

import com.mojang.logging.LogUtils;
import com.wavesurvivor.config.ConfigLoader;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.horde.HordeCommand;
import com.wavesurvivor.horde.HordeTickHandler;
import com.wavesurvivor.horde.benediction.BenedictionEventHandler;
import com.wavesurvivor.horde.boss.BossDeathHandler;
import com.wavesurvivor.horde.loot.HordeLootHandler;
import com.wavesurvivor.horde.rewards.KillTracker;
import com.wavesurvivor.horde.spawn.CustomEntityRegistry;
import com.wavesurvivor.horde.spawn.CustomSkillRegistry;
import com.wavesurvivor.altar.AltarEventHandler;
import com.wavesurvivor.altar.AltarStore;
import com.wavesurvivor.altar.AltarTickHandler;
import com.wavesurvivor.horde.pact.BloodPactEventHandler;
import com.wavesurvivor.horde.roulette.CustomChestStore;
import com.wavesurvivor.horde.roulette.RouletteChestEventHandler;
import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteChestSpawnStore;
import com.wavesurvivor.horde.skill.impl.MortarExplosionHandler;
import com.wavesurvivor.horde.skill.impl.SupernovaTracker;
import com.wavesurvivor.horde.skill.impl.TotemHitHandler;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.PlayerLoginHandler;
import com.wavesurvivor.registry.ModBlocks;
import com.wavesurvivor.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(WaveSurvivorMod.MODID)
public class WaveSurvivorMod {
    public static final String MODID = "wavesurvivor";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ModConfig LOADED_CONFIG;

    public WaveSurvivorMod() {
        LOGGER.info("[WaveSurvivor] === Bootstrap du mod ===");

        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // Register des blocks/items custom (DeferredRegister sur mod event bus)
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        // Langue du mod (anglais par défaut, /ws lang fr)
        com.wavesurvivor.i18n.WSLang.loadSettings();
        // Configuration d'origine du mod : copie des fichiers manquants (premier lancement uniquement)
        com.wavesurvivor.config.DefaultConfigInstaller.installMissing();
        com.wavesurvivor.registry.ModEntities.register(modEventBus);
        com.wavesurvivor.registry.ModBlockEntities.register(modEventBus);
        com.wavesurvivor.registry.ModMenus.register(modEventBus); // menus personnalisés (Atelier de Réparation)

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new HordeTickHandler());
        MinecraftForge.EVENT_BUS.register(new HordeLootHandler());
        MinecraftForge.EVENT_BUS.register(new BossDeathHandler());
        MinecraftForge.EVENT_BUS.register(new KillTracker());
        MinecraftForge.EVENT_BUS.register(new PlayerLoginHandler());
        MinecraftForge.EVENT_BUS.register(new TotemHitHandler());
        MinecraftForge.EVENT_BUS.register(new SupernovaTracker());
        MinecraftForge.EVENT_BUS.register(new MortarExplosionHandler());
        MinecraftForge.EVENT_BUS.register(new BenedictionEventHandler());
        MinecraftForge.EVENT_BUS.register(new RouletteChestManager());
        MinecraftForge.EVENT_BUS.register(new RouletteChestEventHandler());
        MinecraftForge.EVENT_BUS.register(new BloodPactEventHandler());
        MinecraftForge.EVENT_BUS.register(new AltarEventHandler());
        MinecraftForge.EVENT_BUS.register(new AltarTickHandler());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.altar.AltarDefense());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.skill.NecroTracker());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.boss.LicheController());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.skill.BoneProjectiles());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.skill.MagicBolts());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.breach.BreachManager());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.boss.CavalierController());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.spawn.HordePiglinControl());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.spawn.HordeInfightingGuard());
        // Filet de sécurité : arrêt propre de la horde à la fermeture du monde + nettoyage des restes au chargement
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.HordeSafety());
        // « La Chute du Monolithe » : séquence de défaite stylisée
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.altar.MonolithFall());
        // Mutateurs : effets en jeu (mort, dégâts, réapparition, faim, anomalies…)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.mutator.MutatorEffects());
        // Arène du Roi : joueurs restés dans l'arène renvoyés chez eux à la connexion
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.RaidManager());
        // Sauvegarde de partie : message « Reprendre / Abandonner » à la connexion
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.HordeSession());
        // Explosions des unités de la horde : dégâts au Monolithe, aux murs et aux défenses
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.BuildingAttack());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.anomaly.AnomalyManager());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.spawn.HordeEndermanControl());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.boss.XaltorController());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.chaos.TotemAuraManager());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.chaos.GisementManager());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.spawn.HordeAggro());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.item.RelicEffects());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.item.RelicSets()); // 1.5 : bonus de set des reliques
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.item.RelicEquip()); // 1.5 : reliques équipées (sans Curios)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.renaissance.Heritage()); // 1.5 : Héritage et Renaissance
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.bossrush.BossRush()); // 1.6 : Boss Rush (vies, barre d'action)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.frost.StormManager()); // 1.6 : tempêtes (chaos et boss)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.renaissance.RenaissanceRewards()); // 1.5 : PR gagnés en jouant (boss)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.benediction.BenedictionEvents()); // bénédictions conservées à la mort
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomReport()); // bilan de fin d'Assaut (Kingdom)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.skill.IceFx()); // effets de glace en blocs (nettoyage)
        // Iron's Spells (optionnel) : fait avancer les sorts chargés / canalisés des créatures
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.compat.IronsSpells());
        // Difficulté des boss (phases, anti-exploitation, bouclier, plafond de dégâts)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.boss.BossDirector());
        // Explosions : pas de dégâts au terrain pendant une horde, blocs du mod toujours protégés
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.ExplosionGuard());
        // Flèches plantées nettoyées pendant les hordes (monstres/tours : 3 s, joueurs : 20 s)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.ArrowCleaner());
        // Mode Kingdom : règles de construction du claim + PV des murs
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomClaim.Events());
        // Mode Kingdom : les flèches des tours traversent les soldats et les joueurs
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomDefenses.ArrowEvents());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.renaissance.RenaissanceStats.Events());
        // Mode Kingdom : trésor commun (émeraudes ramassées, blocs naturels cassés, anti-farm des blocs posés)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomTreasury.Events());
        // Mode Kingdom : rôles des joueurs et mort en multijoueur (spectateur 10 s)
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomRoles.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomLives.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomRoleEffects.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomDemolition.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomRoleExtras.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomAlchemy.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.kingdom.KingdomLeftovers.Events());
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.editor.HordeEditSessions.Events());
        // Hordes : les poissons d'argent n'infestent plus la pierre
        MinecraftForge.EVENT_BUS.register(new com.wavesurvivor.horde.SilverfishGuard());

        LOADED_CONFIG = ConfigLoader.loadOrCreateDefault();
        CustomEntityRegistry.init(LOADED_CONFIG);
        CustomSkillRegistry.init(LOADED_CONFIG);
        RouletteChestSpawnStore.load();
        AltarStore.load();
        com.wavesurvivor.altar.AltarRecipes.load();
        CustomChestStore.load();
        com.wavesurvivor.horde.renaissance.RenaissanceShopStore.load();
        logConfigSummary();
    }

    private void logConfigSummary() {
        int hordes = LOADED_CONFIG.hordeConfigMulti != null ? LOADED_CONFIG.hordeConfigMulti.size() : 0;
        int entities = LOADED_CONFIG.customEntity != null ? LOADED_CONFIG.customEntity.size() : 0;
        int skills = LOADED_CONFIG.customSkill != null ? LOADED_CONFIG.customSkill.size() : 0;
        int items = LOADED_CONFIG.customItem != null ? LOADED_CONFIG.customItem.size() : 0;

        LOGGER.info("[WaveSurvivor] === Config OK ===");
        LOGGER.info("[WaveSurvivor]   Hordes multi : {}", hordes);
        LOGGER.info("[WaveSurvivor]   Custom entities : {}", entities);
        LOGGER.info("[WaveSurvivor]   Custom skills : {}", skills);
        LOGGER.info("[WaveSurvivor]   Custom items : {}", items);
        LOGGER.info("[WaveSurvivor]   Autels : {}", LOADED_CONFIG.altarConfig != null ? LOADED_CONFIG.altarConfig.size() : 0);
        LOGGER.info("[WaveSurvivor]   Renaissance : {}", LOADED_CONFIG.renaissanceConfig != null ? LOADED_CONFIG.renaissanceConfig.size() : 0);
        LOGGER.info("[WaveSurvivor]   Bénédictions : {}", LOADED_CONFIG.benedictionConfig != null ? LOADED_CONFIG.benedictionConfig.size() : 0);
        LOGGER.info("[WaveSurvivor]   Blood pacts : {}", LOADED_CONFIG.bloodPactConfig != null ? LOADED_CONFIG.bloodPactConfig.size() : 0);
        LOGGER.info("[WaveSurvivor]   Roulette chests : {}", LOADED_CONFIG.rouletteChestConfig != null ? LOADED_CONFIG.rouletteChestConfig.size() : 0);
        LOGGER.info("[WaveSurvivor]   Totems : {}", LOADED_CONFIG.totemConfig != null ? LOADED_CONFIG.totemConfig.size() : 0);
        LOGGER.info("[WaveSurvivor]   Scripts : {}", LOADED_CONFIG.script != null ? LOADED_CONFIG.script.size() : 0);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
        LOGGER.info("[WaveSurvivor] commonSetup terminé.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HordeCommand.register(event.getDispatcher());
        com.wavesurvivor.horde.renaissance.RenaissanceCommand.register(event.getDispatcher());
        LOGGER.info("[WaveSurvivor] Commandes /ws et /renaissance enregistrées.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        int nb = LOADED_CONFIG.hordeConfigMulti != null ? LOADED_CONFIG.hordeConfigMulti.size() : 0;
        LOGGER.info("[WaveSurvivor] Serveur démarré — {} hordes disponibles.", nb);
    }

    public static ModConfig getConfig() {
        return LOADED_CONFIG;
    }

    /**
     * Recharge le config.json depuis le disque et ré-init les registres CustomEntity/CustomSkill.
     * NE redémarre PAS la horde en cours (elle continue avec l'ancien template).
     * @return summary string pour le retour de la commande.
     */
    public static String reloadConfig() {
        LOGGER.info("[WaveSurvivor] === Reload config demandé ===");
        ModConfig newCfg = ConfigLoader.loadOrCreateDefault();
        LOADED_CONFIG = newCfg;
        CustomEntityRegistry.init(newCfg);
        CustomSkillRegistry.init(newCfg);

        // Recharge aussi les custom chests (fichier séparé)
        com.wavesurvivor.horde.roulette.RouletteChestRegistry.clearAllCustom();
        CustomChestStore.load();
        com.wavesurvivor.horde.renaissance.RenaissanceShopStore.load();
        com.wavesurvivor.altar.AltarRecipes.load();

        int hordes = newCfg.hordeConfigMulti != null ? newCfg.hordeConfigMulti.size() : 0;
        int entities = newCfg.customEntity != null ? newCfg.customEntity.size() : 0;
        int skills = newCfg.customSkill != null ? newCfg.customSkill.size() : 0;
        String summary = String.format("%d hordes, %d entities, %d skills, %d custom chests",
                hordes, entities, skills, CustomChestStore.count());
        LOGGER.info("[WaveSurvivor] Reload OK : {}", summary);
        return summary;
    }
}
