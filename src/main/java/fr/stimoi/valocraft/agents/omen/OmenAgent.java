package fr.stimoi.valocraft.agents.omen;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.Agent;
import fr.stimoi.valocraft.agents.AgentManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================
 * AGENT OMEN — Contrôleur
 * ============================================================
 * Capacités :
 *  [1] Pas dans l'Ombre (Shrouded Step) : Téléportation courte
 *      dans la direction du regard (portée limitée, ignore les blocs).
 *      Dans Valorant, Omen se téléporte après un bref délai visible.
 *      Ici : délai de 0.5s avec effets visuels, puis téléport.
 *
 *  [2] Depuis les Ombres (From the Shadows) : Téléportation
 *      longue distance vers le point visé jusqu'à 30 blocs.
 *      Omen devient invisible pendant la "traversée" (1.5s)
 *      avant d'apparaître à destination.
 *
 * ============================================================
 */
public class OmenAgent extends Agent {

    // -------------------------------------------------------
    // CONSTANTES
    // -------------------------------------------------------

    /** Cooldown de Pas dans l'Ombre (7 secondes) */
    private static final long STEP_COOLDOWN_MS = 7_000L;

    /** Cooldown de Depuis les Ombres (25 secondes) */
    private static final long SHADOWS_COOLDOWN_MS = 25_000L;

    /** Distance maximale du téléport court (en blocs) */
    private static final double STEP_MAX_DISTANCE = 8.0;

    /** Distance maximale du téléport long (en blocs) */
    private static final double SHADOWS_MAX_DISTANCE = 30.0;

    /** Délai avant téléportation (en ticks) — simule le cast de Valorant */
    private static final long TELEPORT_DELAY_TICKS = 10L; // 0.5 secondes

    /** Durée d'invisibilité pendant Depuis les Ombres (en ticks) */
    private static final long SHADOWS_INVIS_TICKS = 30L; // 1.5 secondes

    private static final String ABILITY_STEP    = "step";
    private static final String ABILITY_SHADOWS = "shadows";

    /**
     * Stocke la destination en attente de téléportation.
     * Pendant le délai entre le clic et le téléport, on mémorise où aller.
     */
    private final Map<UUID, Location> pendingTeleports = new HashMap<>();

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public OmenAgent(ValoCraft plugin) {
        super(plugin, "Omen", "Contrôleur");
    }

    // -------------------------------------------------------
    // ITEMS DE CAPACITÉS
    // -------------------------------------------------------

    /**
     * [Capacité 1] Pas dans l'Ombre — item : ENDER_EYE (œil des ombres)
     */
    @Override
    public ItemStack getAbility1Item() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§8👁 Pas dans l'Ombre §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Téléporte-toi dans la direction",
            "§7de ton regard (§e" + (int) STEP_MAX_DISTANCE + " blocs§7).",
            "§8Délai : §e0.5s §8avant téléport",
            "§8Cooldown : §e" + (STEP_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "omen_ability1"
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * [Capacité 2] Depuis les Ombres — item : CHORUS_FRUIT (téléportation longue)
     */
    @Override
    public ItemStack getAbility2Item() {
        ItemStack item = new ItemStack(Material.CHORUS_FRUIT);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§8🌑 Depuis les Ombres §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Téléportation longue distance",
            "§7jusqu'à §e" + (int) SHADOWS_MAX_DISTANCE + " blocs.",
            "§8Invisible §7pendant la traversée",
            "§8Cooldown : §e" + (SHADOWS_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "omen_ability2"
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

        player.sendMessage("§8╔══════════════════════════════╗");
        player.sendMessage("§8║  Agent sélectionné : §fOmen   §8║");
        player.sendMessage("§8║  Rôle : §7Contrôleur           §8║");
        player.sendMessage("§8║  [E] §fPas dans l'Ombre        §8║");
        player.sendMessage("§8║  [Q] §fDepuis les Ombres       §8║");
        player.sendMessage("§8╚══════════════════════════════╝");

        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.5f);
    }

    // -------------------------------------------------------
    // CAPACITÉ 1 : PAS DANS L'OMBRE (téléport court)
    // -------------------------------------------------------

    /**
     * Téléporte Omen à courte distance dans la direction du regard.
     *
     * Algorithme :
     * 1. Lance un rayon (RayTrace) depuis le joueur dans sa direction
     * 2. Trouve le premier bloc solide dans les 8 blocs devant lui
     * 3. Se téléporte juste avant ce bloc (ou à 8 blocs si rien devant)
     * 4. Délai de 0.5s avec particules d'ombres pendant l'attente
     */
    @Override
    public void useAbility1(Player player) {
        if (isOnCooldown(player, ABILITY_STEP, STEP_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_STEP, STEP_COOLDOWN_MS);
            player.sendMessage("§c⏱ Pas dans l'Ombre disponible dans §e" + remaining + "s");
            return;
        }

        setCooldown(player, ABILITY_STEP);

        // --- Calcul de la destination via RayTrace ---
        // RayTrace = "lancer un rayon" dans une direction pour trouver le premier obstacle
        RayTraceResult result = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(),    // Départ : hauteur des yeux du joueur
            player.getLocation().getDirection(), // Direction du regard
            STEP_MAX_DISTANCE,           // Distance maximale
            org.bukkit.FluidCollisionMode.NEVER, // Ignore l'eau/lave
            true                         // Ignore les blocs passables (herbe, etc.)
        );

        // Destination finale
        Location destination;

        if (result != null && result.getHitPosition() != null) {
            // Un bloc a été trouvé : on se téléporte juste avant
            // hitPosition = point exact d'impact du rayon sur le bloc
            Vector hitPos = result.getHitPosition();
            destination = new Location(
                player.getWorld(),
                hitPos.getX() - player.getLocation().getDirection().getX() * 0.5,
                hitPos.getY(),
                hitPos.getZ() - player.getLocation().getDirection().getZ() * 0.5,
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
            );
        } else {
            // Aucun bloc devant : on va au maximum (8 blocs)
            destination = player.getLocation().add(
                player.getLocation().getDirection().multiply(STEP_MAX_DISTANCE)
            );
        }

        // Assure que la destination est au sol (pas dans un bloc)
        destination.setY(Math.floor(destination.getY()));
        final Location finalDest = destination;

        // --- Effets visuels au départ ---
        player.getWorld().spawnParticle(
            Particle.PORTAL, player.getLocation(), 30, 0.3, 0.5, 0.3, 0.5
        );
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        player.sendActionBar("§8👁 Téléportation...");

        // --- Délai avant téléport (simule le cast time de Valorant) ---
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                // Téléporte le joueur
                player.teleport(finalDest);

                // Effets à l'arrivée
                player.getWorld().spawnParticle(
                    Particle.PORTAL, finalDest, 25, 0.3, 0.5, 0.3, 0.3
                );
                player.playSound(finalDest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                player.sendActionBar("§8👁 Téléportation réussie !");
            }
        }.runTaskLater(plugin, TELEPORT_DELAY_TICKS);
    }

    // -------------------------------------------------------
    // CAPACITÉ 2 : DEPUIS LES OMBRES (téléport long + invisibilité)
    // -------------------------------------------------------

    /**
     * Téléportation longue distance avec phase d'invisibilité.
     *
     * Déroulement :
     * 1. Calcule la destination (jusqu'à 30 blocs dans la direction du regard)
     * 2. Rend le joueur invisible pendant SHADOWS_INVIS_TICKS (1.5s)
     * 3. Téléporte après le délai
     * 4. Retire l'invisibilité à l'arrivée
     */
    @Override
    public void useAbility2(Player player) {
        if (isOnCooldown(player, ABILITY_SHADOWS, SHADOWS_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_SHADOWS, SHADOWS_COOLDOWN_MS);
            player.sendMessage("§c⏱ Depuis les Ombres disponible dans §e" + remaining + "s");
            return;
        }

        setCooldown(player, ABILITY_SHADOWS);

        // --- Calcul de la destination ---
        RayTraceResult result = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(),
            player.getLocation().getDirection(),
            SHADOWS_MAX_DISTANCE,
            org.bukkit.FluidCollisionMode.NEVER,
            true
        );

        Location destination;
        if (result != null && result.getHitPosition() != null) {
            Vector hitPos = result.getHitPosition();
            destination = new Location(
                player.getWorld(),
                hitPos.getX() - player.getLocation().getDirection().getX() * 0.5,
                hitPos.getY(),
                hitPos.getZ() - player.getLocation().getDirection().getZ() * 0.5,
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
            );
        } else {
            destination = player.getLocation().add(
                player.getLocation().getDirection().multiply(SHADOWS_MAX_DISTANCE)
            );
        }
        destination.setY(Math.floor(destination.getY()));
        final Location finalDest = destination;

        // --- Phase 1 : Invisibilité (simule la traversée des ombres) ---
        // INVISIBILITY : le joueur disparaît des autres joueurs
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.INVISIBILITY,
            (int) (SHADOWS_INVIS_TICKS + 10), // Un peu plus long pour éviter le flash
            0, false, false, false
        ));

        // Particules d'ombres au départ
        player.getWorld().spawnParticle(
            Particle.SQUID_INK,
            player.getLocation().add(0, 1, 0),
            20, 0.3, 0.5, 0.3, 0.05
        );
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 1.0f, 0.5f);
        player.sendActionBar("§8🌑 Traversée des ombres...");

        // --- Phase 2 : Téléportation après délai ---
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                player.teleport(finalDest);

                // Retire l'invisibilité immédiatement à l'arrivée
                player.removePotionEffect(PotionEffectType.INVISIBILITY);

                // Explosion de particules à l'arrivée
                finalDest.getWorld().spawnParticle(
                    Particle.SQUID_INK, finalDest.clone().add(0, 1, 0),
                    40, 0.5, 0.8, 0.5, 0.1
                );
                finalDest.getWorld().spawnParticle(
                    Particle.PORTAL, finalDest.clone().add(0, 1, 0),
                    30, 0.5, 0.8, 0.5, 0.3
                );

                player.playSound(finalDest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
                player.sendActionBar("§8🌑 Tu surgis des ombres !");
            }
        }.runTaskLater(plugin, SHADOWS_INVIS_TICKS);
    }
}
