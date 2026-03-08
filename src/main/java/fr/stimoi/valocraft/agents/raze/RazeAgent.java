package fr.stimoi.valocraft.agents.raze;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.Agent;
import fr.stimoi.valocraft.agents.AgentManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ============================================================
 * AGENT RAZE — Duelliste
 * ============================================================
 * Capacités :
 *  [1] Grenade Peintbomb (Paint Shells) : Lance une grenade
 *      explosive qui explose à l'impact et inflige des dégâts
 *      de zone avec particules colorées (jaune/orange).
 *
 *  [2] Pack Explosif (Blast Pack) : Se propulse en arrière
 *      avec une explosion sous ses pieds — permet des sauts
 *      de longue portée ou des repositionnements rapides.
 *      Dans Valorant, le Blast Pack peut aussi faire des dégâts
 *      aux ennemis proches.
 *
 * Raze est l'agent le plus explosif du jeu — toutes ses
 * capacités reposent sur des explosions et projections.
 * ============================================================
 */
public class RazeAgent extends Agent implements Listener {

    // -------------------------------------------------------
    // CONSTANTES
    // -------------------------------------------------------

    /** Cooldown de la Peintbomb (15 secondes) */
    private static final long PAINTBOMB_COOLDOWN_MS = 15_000L;

    /** Cooldown du Blast Pack (12 secondes) */
    private static final long BLASTPACK_COOLDOWN_MS = 12_000L;

    /** Rayon d'explosion de la Peintbomb (en blocs) */
    private static final double PAINTBOMB_RADIUS = 3.5;

    /** Dégâts de la Peintbomb au centre (réduit avec la distance) */
    private static final double PAINTBOMB_MAX_DAMAGE = 8.0;

    /** Force de propulsion du Blast Pack */
    private static final double BLASTPACK_FORCE = 1.8;

    /** Dégâts aux ennemis proches lors du Blast Pack */
    private static final double BLASTPACK_DAMAGE = 3.0;

    /** Rayon d'effet du Blast Pack */
    private static final double BLASTPACK_RADIUS = 2.5;

    private static final String ABILITY_PAINTBOMB = "paintbomb";
    private static final String ABILITY_BLASTPACK = "blastpack";

    /**
     * Set des projectiles Peintbomb en vol.
     * Même mécanisme que Phoenix pour identifier nos Snowballs.
     */
    private final Set<UUID> paintbombProjectiles = new HashSet<>();

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public RazeAgent(ValoCraft plugin) {
        super(plugin, "Raze", "Duelliste");
        // Listener pour détecter l'impact des Peintbombs
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------
    // ITEMS DE CAPACITÉS
    // -------------------------------------------------------

    /**
     * [Capacité 1] Grenade Peintbomb — item : FIRE_CHARGE orange
     * On utilise TNT comme représentation visuelle plus impactante
     */
    @Override
    public ItemStack getAbility1Item() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6💥 Grenade Peintbomb §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Lance une grenade explosive",
            "§7qui inflige des dégâts de zone.",
            "§8Rayon : §e" + PAINTBOMB_RADIUS + " blocs",
            "§8Dégâts max : §e" + PAINTBOMB_MAX_DAMAGE + " HP",
            "§8Cooldown : §e" + (PAINTBOMB_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "raze_ability1"
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * [Capacité 2] Pack Explosif — item : GUNPOWDER (poudre à canon)
     */
    @Override
    public ItemStack getAbility2Item() {
        ItemStack item = new ItemStack(Material.GUNPOWDER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6🚀 Pack Explosif §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Explose sous tes pieds et",
            "§7te propulse vers l'avant/haut.",
            "§cBlesse §7les ennemis proches",
            "§8Cooldown : §e" + (BLASTPACK_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "raze_ability2"
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

        player.sendMessage("§6╔══════════════════════════════╗");
        player.sendMessage("§6║  Agent sélectionné : §fRaze   §6║");
        player.sendMessage("§6║  Rôle : §7Duelliste            §6║");
        player.sendMessage("§6║  [E] §fGrenade Peintbomb       §6║");
        player.sendMessage("§6║  [Q] §fPack Explosif           §6║");
        player.sendMessage("§6╚══════════════════════════════╝");

        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.6f, 1.5f);
    }

    // -------------------------------------------------------
    // CAPACITÉ 1 : GRENADE PEINTBOMB
    // -------------------------------------------------------

    /**
     * Lance une grenade explosive dans la direction du regard.
     *
     * À l'impact :
     * - Explosion de particules jaunes/oranges (la peinture de Raze !)
     * - Dégâts calculés selon la distance au centre
     *   (dégâts complets au centre, réduits aux bords)
     */
    @Override
    public void useAbility1(Player player) {
        if (isOnCooldown(player, ABILITY_PAINTBOMB, PAINTBOMB_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_PAINTBOMB, PAINTBOMB_COOLDOWN_MS);
            player.sendMessage("§c⏱ Peintbomb disponible dans §e" + remaining + "s");
            return;
        }

        setCooldown(player, ABILITY_PAINTBOMB);

        // Lance le projectile avec une vitesse plus élevée qu'une balle normale
        Snowball grenade = player.launchProjectile(Snowball.class);
        grenade.setVelocity(player.getLocation().getDirection().multiply(2.5));

        // Enregistre le projectile et son lanceur
        paintbombProjectiles.add(grenade.getUniqueId());
        grenade.setMetadata("raze_shooter",
            new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));

        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.8f, 1.8f);
        player.sendActionBar("§6💥 Peintbomb lancée !");

        // Trail de particules colorées (peinture jaune) pendant le vol
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!grenade.isValid() || grenade.isDead()) {
                    cancel();
                    return;
                }
                grenade.getWorld().spawnParticle(
                    Particle.DUST,
                    grenade.getLocation(), 5,
                    0.1, 0.1, 0.1,
                    // DustOptions : couleur jaune-orange, taille 1.2
                    new Particle.DustOptions(Color.fromRGB(255, 160, 0), 1.2f)
                );
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Détecte l'impact de la Peintbomb et déclenche l'explosion.
     */
    @EventHandler
    public void onPaintbombHit(ProjectileHitEvent event) {
        if (!paintbombProjectiles.contains(event.getEntity().getUniqueId())) return;

        paintbombProjectiles.remove(event.getEntity().getUniqueId());

        if (!event.getEntity().hasMetadata("raze_shooter")) return;
        String shooterUuidStr = event.getEntity()
            .getMetadata("raze_shooter").get(0).asString();
        Player shooter = plugin.getServer().getPlayer(UUID.fromString(shooterUuidStr));

        Location impact = event.getEntity().getLocation();

        // Déclenche l'explosion de peinture
        explodePaintbomb(impact, shooter);
    }

    /**
     * Crée l'explosion visuelle et inflige les dégâts.
     *
     * Les dégâts sont proportionnels à la PROXIMITÉ du centre :
     * - Au centre exact → PAINTBOMB_MAX_DAMAGE
     * - Au bord du rayon → 0 dégâts
     * Formule : dégâts = maxDmg * (1 - distance/rayon)
     *
     * @param center  Position d'impact
     * @param shooter Joueur qui a lancé la grenade (peut être null)
     */
    private void explodePaintbomb(Location center, Player shooter) {
        // --- Effets d'explosion ---
        center.getWorld().spawnParticle(
            Particle.EXPLOSION, center, 3, 0.5, 0.5, 0.5, 0.2
        );
        // Nuage de peinture jaune-orange
        center.getWorld().spawnParticle(
            Particle.DUST, center, 80, 1.5, 1.0, 1.5,
            new Particle.DustOptions(Color.fromRGB(255, 200, 0), 2.0f)
        );
        center.getWorld().spawnParticle(
            Particle.DUST, center, 40, 2.0, 0.5, 2.0,
            new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.5f)
        );
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);

        // --- Application des dégâts aux joueurs dans la zone ---
        for (Entity entity : center.getWorld().getNearbyEntities(
                center, PAINTBOMB_RADIUS, PAINTBOMB_RADIUS, PAINTBOMB_RADIUS)) {

            if (!(entity instanceof Player target)) continue;

            // Pas de dégâts sur le lanceur (comme dans Valorant — self-damage désactivé ici)
            if (shooter != null && target.getUniqueId().equals(shooter.getUniqueId())) continue;

            double distance = target.getLocation().distance(center);
            if (distance > PAINTBOMB_RADIUS) continue;

            // Calcul des dégâts proportionnels à la distance
            // Plus on est proche du centre, plus les dégâts sont élevés
            double damageMultiplier = 1.0 - (distance / PAINTBOMB_RADIUS);
            double damage = PAINTBOMB_MAX_DAMAGE * damageMultiplier;

            if (damage > 0.5) { // Seuil minimum pour éviter les micro-dégâts
                if (shooter != null) {
                    target.damage(damage, shooter);
                } else {
                    target.damage(damage);
                }
                target.sendActionBar("§6💥 Touché par la Peintbomb de Raze ! (§c-" +
                    String.format("%.1f", damage) + " HP§6)");
            }
        }
    }

    // -------------------------------------------------------
    // CAPACITÉ 2 : PACK EXPLOSIF (BLAST PACK)
    // -------------------------------------------------------

    /**
     * Crée une explosion sous les pieds de Raze qui la propulse.
     *
     * Logique de propulsion :
     * - Récupère la direction du regard du joueur
     * - Applique une forte vélocité vers l'avant + légèrement vers le haut
     * - Effet "rocket jump" : combo direction regard + boost vertical
     *
     * Dégâts aux ennemis proches :
     * - Simule le souffle de l'explosion sur les adversaires voisins
     */
    @Override
    public void useAbility2(Player player) {
        if (isOnCooldown(player, ABILITY_BLASTPACK, BLASTPACK_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_BLASTPACK, BLASTPACK_COOLDOWN_MS);
            player.sendMessage("§c⏱ Pack Explosif disponible dans §e" + remaining + "s");
            return;
        }

        setCooldown(player, ABILITY_BLASTPACK);

        // --- Calcul de la direction de propulsion ---
        Vector direction = player.getLocation().getDirection().normalize();

        // On combine la direction horizontale du regard avec un boost vertical
        // Cela permet à Raze de s'envoler vers l'avant-haut comme dans Valorant
        Vector launchVector = new Vector(
            direction.getX() * BLASTPACK_FORCE,
            Math.max(direction.getY(), 0.3) * BLASTPACK_FORCE + 0.5, // Minimum 0.5 vers le haut
            direction.getZ() * BLASTPACK_FORCE
        );

        player.setVelocity(launchVector);

        // --- Effets visuels au sol ---
        Location groundLocation = player.getLocation();
        groundLocation.getWorld().spawnParticle(
            Particle.EXPLOSION, groundLocation, 2, 0.2, 0.0, 0.2, 0.1
        );
        groundLocation.getWorld().spawnParticle(
            Particle.DUST, groundLocation, 30, 1.0, 0.2, 1.0,
            new Particle.DustOptions(Color.fromRGB(255, 120, 0), 1.8f)
        );
        groundLocation.getWorld().spawnParticle(
            Particle.SMOKE, groundLocation, 15, 0.5, 0.1, 0.5, 0.05
        );

        player.playSound(groundLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        player.sendActionBar("§6🚀 Pack Explosif ! Propulsion activée !");

        // --- Dégâts aux ennemis proches (souffle de l'explosion) ---
        for (Entity entity : player.getWorld().getNearbyEntities(
                groundLocation, BLASTPACK_RADIUS, BLASTPACK_RADIUS, BLASTPACK_RADIUS)) {

            if (!(entity instanceof Player target)) continue;
            if (target.getUniqueId().equals(player.getUniqueId())) continue; // Pas d'auto-dégâts

            double distance = target.getLocation().distance(groundLocation);
            if (distance > BLASTPACK_RADIUS) continue;

            // Dégâts réduits selon la distance
            double dmgMultiplier = 1.0 - (distance / BLASTPACK_RADIUS);
            double damage = BLASTPACK_DAMAGE * dmgMultiplier;

            if (damage > 0.5) {
                target.damage(damage, player);
                // Petit recul pour les ennemis proches (effet de souffle)
                Vector knockback = target.getLocation().subtract(groundLocation).toVector()
                    .normalize().multiply(0.8).setY(0.4);
                target.setVelocity(knockback);
                target.sendActionBar("§6💨 Projeté par le Pack Explosif de Raze !");
            }
        }
    }
}
