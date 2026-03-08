package fr.stimoi.valocraft.agents.reyna;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.Agent;
import fr.stimoi.valocraft.agents.AgentManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 * AGENT REYNA — Duelliste
 * ============================================================
 * Capacités :
 *  [1] Dévorer (Devour) : Consomme une "âme" pour se régénérer
 *      massivement sur 3 secondes. Simule le mécanisme d'âme
 *      de Valorant via un score de kills stocké par joueur.
 *
 *  [2] Impératrice (Empress) : Mode combat boosté — vitesse
 *      de déplacement augmentée + résistance temporaire (4s).
 *      Dans Valorant, chaque kill prolonge la durée.
 *
 * Particularité de Reyna :
 *  Elle est un agent SOLO très agressif. Ses capacités
 *  dépendent de sa performance au combat (kills).
 *  On simule les "âmes" en donnant des charges après chaque kill.
 * ============================================================
 */
public class ReynaAgent extends Agent {

    // -------------------------------------------------------
    // CONSTANTES
    // -------------------------------------------------------

    /** Cooldown de Dévorer (8 secondes) */
    private static final long DEVOUR_COOLDOWN_MS = 8_000L;

    /** Cooldown d'Impératrice (30 secondes) */
    private static final long EMPRESS_COOLDOWN_MS = 30_000L;

    /** Quantité de soin par tick de Dévorer (soin sur 3s = 15 ticks de 20 ticks chacun) */
    private static final double DEVOUR_HEAL_PER_TICK = 1.5;

    /** Durée de Dévorer en secondes */
    private static final int DEVOUR_DURATION_SECONDS = 4;

    /** Durée d'Impératrice en secondes */
    private static final int EMPRESS_DURATION_SECONDS = 6;

    /** Noms des capacités pour les cooldowns */
    private static final String ABILITY_DEVOUR  = "devour";
    private static final String ABILITY_EMPRESS = "empress";

    /**
     * Compteur d'âmes par joueur.
     * Dans Valorant, chaque kill donne 1 âme utilisable pour Dévorer.
     * Ici on simule : chaque utilisation consomme 1 âme, max 2 âmes stockées.
     * Les âmes se rechargent automatiquement toutes les 15 secondes.
     */
    private final Map<UUID, Integer> soulCharges = new HashMap<>();

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public ReynaAgent(ValoCraft plugin) {
        super(plugin, "Reyna", "Duelliste");

        // Tâche de recharge automatique des âmes (toutes les 15 secondes)
        // Simule le gain d'âmes au combat
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Integer> entry : soulCharges.entrySet()) {
                    // Maximum 2 âmes stockées
                    if (entry.getValue() < 2) {
                        soulCharges.put(entry.getKey(), entry.getValue() + 1);
                        // Notifie le joueur s'il est connecté
                        Player p = plugin.getServer().getPlayer(entry.getKey());
                        if (p != null) {
                            p.sendActionBar("§5👁 Âme chargée ! §7(" + (entry.getValue() + 1) + "/2)");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 300L, 300L); // Toutes les 300 ticks = 15 secondes
    }

    // -------------------------------------------------------
    // ITEMS DE CAPACITÉS
    // -------------------------------------------------------

    /**
     * [Capacité 1] Dévorer — item : AMETHYST_SHARD (âme violette)
     */
    @Override
    public ItemStack getAbility1Item() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§5👁 Dévorer §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Consomme une âme pour te",
            "§7régénérer massivement.",
            "§5Âmes requises : §f1",
            "§8Cooldown : §e" + (DEVOUR_COOLDOWN_MS / 1000) + "s",
            "§8Recharge âme : §e15s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "reyna_ability1"
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * [Capacité 2] Impératrice — item : NETHER_STAR (éclat d'énergie)
     */
    @Override
    public ItemStack getAbility2Item() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§d⚡ Impératrice §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Entre en mode combat :",
            "§e+ Vitesse §7de déplacement",
            "§a+ Résistance §7temporaire",
            "§8Durée : §e" + EMPRESS_DURATION_SECONDS + "s",
            "§8Cooldown : §e" + (EMPRESS_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "reyna_ability2"
        );

        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------
    // ÉQUIPEMENT
    // -------------------------------------------------------
    @Override
    public void equipPlayer(Player player) {
        player.getInventory().setItem(5, getAbility1Item());
        player.getInventory().setItem(6, getAbility2Item());

        // Initialise les âmes à 1 au départ
        soulCharges.put(player.getUniqueId(), 1);

        player.sendMessage("§5╔══════════════════════════════╗");
        player.sendMessage("§5║  Agent sélectionné : §fReyna  §5║");
        player.sendMessage("§5║  Rôle : §7Duelliste            §5║");
        player.sendMessage("§5║  [E] §fDévorer §8(âmes: 1/2)   §5║");
        player.sendMessage("§5║  [Q] §fImpératrice             §5║");
        player.sendMessage("§5╚══════════════════════════════╝");

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.6f);
    }

    // -------------------------------------------------------
    // CAPACITÉ 1 : DÉVORER
    // -------------------------------------------------------

    /**
     * Consomme une âme et régénère Reyna sur plusieurs secondes.
     *
     * Mécanisme :
     * 1. Vérifie le cooldown + le nombre d'âmes disponibles
     * 2. Consomme 1 âme
     * 3. Lance un BukkitRunnable qui soigne chaque seconde pendant DEVOUR_DURATION_SECONDS
     * 4. Applique un effet de régénération visuel (particules violettes)
     */
    @Override
    public void useAbility1(Player player) {
        // --- Vérification cooldown ---
        if (isOnCooldown(player, ABILITY_DEVOUR, DEVOUR_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_DEVOUR, DEVOUR_COOLDOWN_MS);
            player.sendMessage("§c⏱ Dévorer disponible dans §e" + remaining + "s");
            return;
        }

        // --- Vérification des âmes ---
        int souls = soulCharges.getOrDefault(player.getUniqueId(), 0);
        if (souls <= 0) {
            player.sendMessage("§c👁 Pas d'âme disponible ! Attends la recharge (§e15s§c).");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.0f, 0.5f);
            return;
        }

        // --- Consomme 1 âme et démarre le cooldown ---
        soulCharges.put(player.getUniqueId(), souls - 1);
        setCooldown(player, ABILITY_DEVOUR);

        // --- Effet d'activation ---
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 0.7f);
        player.sendActionBar("§5👁 Dévorer actif ! §7Régénération en cours...");

        // --- Régénération sur la durée via BukkitRunnable ---
        new BukkitRunnable() {
            int secondsElapsed = 0;

            @Override
            public void run() {
                secondsElapsed++;

                // Arrêt si la durée est dépassée ou si le joueur est mort/déconnecté
                if (secondsElapsed > DEVOUR_DURATION_SECONDS || !player.isOnline()) {
                    cancel();
                    if (player.isOnline()) {
                        player.sendActionBar("§5👁 Dévorer terminé.");
                    }
                    return;
                }

                // Soin par tick
                double newHealth = Math.min(
                    player.getHealth() + DEVOUR_HEAL_PER_TICK,
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()
                );
                player.setHealth(newHealth);

                // Particules de soin violettes autour du joueur
                player.getWorld().spawnParticle(
                    Particle.WITCH,                             // Particules violettes
                    player.getLocation().add(0, 1, 0),
                    10, 0.4, 0.5, 0.4, 0.05
                );

                player.sendActionBar("§5👁 Dévorer §a+" + DEVOUR_HEAL_PER_TICK +
                    " HP §7(" + secondsElapsed + "/" + DEVOUR_DURATION_SECONDS + "s)");
            }
        }.runTaskTimer(plugin, 0L, 20L); // Toutes les secondes
    }

    // -------------------------------------------------------
    // CAPACITÉ 2 : IMPÉRATRICE
    // -------------------------------------------------------

    /**
     * Active le mode Impératrice : boost de vitesse + résistance.
     *
     * Utilise les PotionEffect de Bukkit pour appliquer les buffs.
     * PotionEffect(type, durée en ticks, amplificateur)
     * amplificateur 0 = niveau 1, 1 = niveau 2, etc.
     */
    @Override
    public void useAbility2(Player player) {
        // --- Vérification cooldown ---
        if (isOnCooldown(player, ABILITY_EMPRESS, EMPRESS_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_EMPRESS, EMPRESS_COOLDOWN_MS);
            player.sendMessage("§c⏱ Impératrice disponible dans §e" + remaining + "s");
            return;
        }

        setCooldown(player, ABILITY_EMPRESS);

        int durationTicks = EMPRESS_DURATION_SECONDS * 20; // Conversion secondes → ticks

        // --- Application des effets de potion ---
        // SPEED niveau 2 (amplificateur 1) = boost de vitesse significatif
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SPEED, durationTicks, 1, false, true, true
        ));
        // RESISTANCE niveau 1 = réduit les dégâts reçus de 20%
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.RESISTANCE, durationTicks, 0, false, true, true
        ));
        // HASTE niveau 1 = vitesse d'attaque augmentée (simule le tir rapide de Valorant)
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.HASTE, durationTicks, 1, false, true, true
        ));

        // --- Effets visuels ---
        player.getWorld().spawnParticle(
            Particle.DUST,
            player.getLocation().add(0, 1, 0),
            30,
            0.5, 1.0, 0.5,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 255), 1.5f) // Violet
        );

        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1.0f, 1.5f);
        player.sendActionBar("§d⚡ IMPÉRATRICE ! §fVitesse + Résistance + Hâte §7(" + EMPRESS_DURATION_SECONDS + "s)");

        // Timer de fin pour notifier le joueur
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.sendActionBar("§8⚡ Impératrice terminée.");
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 0.8f);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    // -------------------------------------------------------
    // NETTOYAGE
    // -------------------------------------------------------
    @Override
    public void cleanupPlayer(Player player) {
        super.cleanupPlayer(player);
        soulCharges.remove(player.getUniqueId());
    }

    /**
     * Méthode publique pour ajouter une âme (appelable depuis un EventListener de kill).
     * À connecter à PlayerDeathEvent dans le futur pour le système de kills.
     *
     * @param player Le joueur qui reçoit l'âme
     */
    public void addSoulCharge(Player player) {
        int current = soulCharges.getOrDefault(player.getUniqueId(), 0);
        if (current < 2) {
            soulCharges.put(player.getUniqueId(), current + 1);
            player.sendActionBar("§5👁 Âme obtenue ! §7(" + (current + 1) + "/2)");
        }
    }
}
