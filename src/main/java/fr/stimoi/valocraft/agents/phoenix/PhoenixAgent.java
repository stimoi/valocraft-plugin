package fr.stimoi.valocraft.agents.phoenix;

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

import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/**
 * ============================================================
 * AGENT PHOENIX — Duelliste
 * ============================================================
 * Capacités :
 *  [1] Mains Brûlantes (Blaze) : Lance une boule de feu qui crée
 *      au sol une zone de flammes : soigne Phoenix, blesse les ennemis.
 *  [2] (Bonus) Sphère Flash — non implémentée dans cette démo
 *
 * Fonctionnement de Mains Brûlantes :
 *  1. Le joueur clique → lance un projectile (Snowball réutilisé comme vecteur)
 *  2. Le Snowball est marqué avec des métadonnées pour être identifié
 *  3. Quand le Snowball touche quelque chose → on crée la zone de feu
 *  4. Une BukkitRunnable (timer) tourne chaque seconde pendant 5s
 *     pour appliquer les effets de soin/dégâts
 * ============================================================
 */
public class PhoenixAgent extends Agent implements Listener {

    // -------------------------------------------------------
    // CONSTANTES DE CONFIGURATION
    // -------------------------------------------------------

    /** Cooldown de Mains Brûlantes (18 secondes) */
    private static final long FIREBALL_COOLDOWN_MS = 18_000L;

    /** Rayon de la zone de feu (en blocs) */
    private static final double FIRE_ZONE_RADIUS = 2.5;

    /** Durée de la zone de feu (en secondes) */
    private static final int FIRE_ZONE_DURATION_TICKS = 5; // secondes (5 cycles d'1s)

    /** Dégâts infligés par seconde aux ennemis dans la zone */
    private static final double DAMAGE_PER_SECOND = 3.0;

    /** Soin accordé par seconde à Phoenix dans la zone */
    private static final double HEAL_PER_SECOND = 2.0;

    /** Nom interne de la capacité */
    private static final String ABILITY_FIREBALL = "fireball";

    /**
     * Set des UUIDs de projectiles lancés par PhoenixAgent.
     * Permet d'identifier nos Snowballs parmi tous les projectiles du serveur.
     */
    private final Set<UUID> trackedProjectiles = new HashSet<>();

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public PhoenixAgent(ValoCraft plugin) {
        super(plugin, "Phoenix", "Duelliste");
        // Enregistre Phoenix comme Listener pour détecter l'impact du projectile
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------
    // ITEMS DE CAPACITÉS
    // -------------------------------------------------------

    /**
     * [Capacité 1] Mains Brûlantes — item : FIRE_CHARGE
     */
    @Override
    public ItemStack getAbility1Item() {
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c🔥 Mains Brûlantes §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Lance une boule de feu qui crée",
            "§7une zone de flammes au sol.",
            "§a✚ §7Soigne Phoenix",
            "§c✖ §7Blesse les ennemis",
            "§8Cooldown : §e" + (FIREBALL_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "phoenix_ability1"
        );

        item.setItemMeta(meta);
        return item;
    }

    /** [Capacité 2] Non implémentée — retourne un item placeholder */
    @Override
    public ItemStack getAbility2Item() {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c🌀 Sphère Flash §8(À venir)");
        meta.setLore(List.of("§8Non disponible dans cette version."));
        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY, PersistentDataType.STRING, "phoenix_ability2"
        );
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------
    // ÉQUIPEMENT DU JOUEUR
    // -------------------------------------------------------
    @Override
    public void equipPlayer(Player player) {
        player.getInventory().setItem(5, getAbility1Item());
        player.getInventory().setItem(6, getAbility2Item());

        player.sendMessage("§c╔══════════════════════════════╗");
        player.sendMessage("§c║  Agent sélectionné : §fPhoenix §c║");
        player.sendMessage("§c║  Rôle : §7Duelliste             §c║");
        player.sendMessage("§c║  [E] §fMains Brûlantes          §c║");
        player.sendMessage("§c╚══════════════════════════════╝");

        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);
    }

    // -------------------------------------------------------
    // CAPACITÉ 1 : MAINS BRÛLANTES
    // -------------------------------------------------------

    /**
     * Lance un projectile (Snowball) depuis la position du joueur.
     *
     * Pourquoi un Snowball ?
     * → Projectile physique disponible en Bukkit API
     * → Suit une trajectoire balistique réaliste (gravité)
     * → On peut intercepter son impact avec ProjectileHitEvent
     */
    @Override
    public void useAbility1(Player player) {
        // --- Vérification cooldown ---
        if (isOnCooldown(player, ABILITY_FIREBALL, FIREBALL_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_FIREBALL, FIREBALL_COOLDOWN_MS);
            player.sendMessage("§c⏱ Mains Brûlantes disponible dans §e" + remaining + "s");
            return;
        }

        setCooldown(player, ABILITY_FIREBALL);

        // --- Lancement du projectile ---
        // launchProjectile() crée le Snowball dans la direction du regard du joueur
        Snowball fireball = player.launchProjectile(Snowball.class);

        // Vitesse (multiplicateur sur le vecteur direction du regard)
        // 2.0 = vitesse modérée, fidèle à Valorant
        fireball.setVelocity(player.getLocation().getDirection().multiply(2.0));

        // --- Enregistrement du projectile ---
        // On ajoute l'UUID à notre Set pour pouvoir l'identifier plus tard dans onProjectileHit
        trackedProjectiles.add(fireball.getUniqueId());

        // On stocke l'UUID du lanceur dans les métadonnées du projectile
        // pour savoir qui a lancé la boule de feu lors de l'impact
        fireball.setMetadata("phoenix_shooter",
            new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // --- Effets de lancement ---
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
        player.sendActionBar("§c🔥 Mains Brûlantes lancées !");

        // Trail de particules de feu pendant le vol (toutes les 2 ticks = 0.1s)
        BukkitRunnable trailTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Arrête si le projectile a disparu ou a touché quelque chose
                if (!fireball.isValid() || fireball.isDead()) {
                    cancel();
                    return;
                }
                // Particules de flamme qui suivent le projectile
                fireball.getWorld().spawnParticle(
                    Particle.FLAME, fireball.getLocation(), 5, 0.1, 0.1, 0.1, 0.05
                );
            }
        };
        trailTask.runTaskTimer(plugin, 0L, 2L); // 0L = commence immédiatement, 2L = toutes les 2 ticks
    }

    // -------------------------------------------------------
    // DÉTECTION DE L'IMPACT DU PROJECTILE
    // -------------------------------------------------------

    /**
     * Appelé quand N'IMPORTE quel projectile touche quelque chose.
     * On filtre uniquement nos Snowballs grâce au Set trackedProjectiles.
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Ce n'est pas un de nos projectiles → on ignore
        if (!trackedProjectiles.contains(event.getEntity().getUniqueId())) return;

        // Retire le projectile du Set (il va être détruit)
        trackedProjectiles.remove(event.getEntity().getUniqueId());

        // Récupère l'UUID du lanceur depuis les métadonnées
        if (!event.getEntity().hasMetadata("phoenix_shooter")) return;
        String shooterUuidStr = event.getEntity()
            .getMetadata("phoenix_shooter").get(0).asString();
        UUID shooterUuid = UUID.fromString(shooterUuidStr);

        // Récupère l'objet Player du lanceur
        Player shooter = plugin.getServer().getPlayer(shooterUuid);

        // Position d'impact du projectile
        Location impactLocation = event.getEntity().getLocation();

        // --- Création de la zone de feu ---
        createFireZone(impactLocation, shooter);
    }

    // -------------------------------------------------------
    // ZONE DE FEU
    // -------------------------------------------------------

    /**
     * Crée une zone de flammes qui dure dans le temps.
     * Utilise un BukkitRunnable pour appliquer les effets périodiquement.
     *
     * @param center  Centre de la zone (point d'impact)
     * @param shooter Joueur qui a lancé la boule de feu (Phoenix)
     */
    private void createFireZone(Location center, Player shooter) {

        // Effet d'explosion visuelle à l'impact
        center.getWorld().spawnParticle(
            Particle.EXPLOSION, center, 3, 0.5, 0.5, 0.5, 0.1
        );
        center.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);

        // -------------------------------------------------------
        // BukkitRunnable = tâche répétitive (comme un Thread simplifié)
        // runTaskTimer(plugin, delayTicks, periodTicks)
        //  → délai initial : 0 (immédiat)
        //  → période : 20 ticks = 1 seconde
        // -------------------------------------------------------
        new BukkitRunnable() {
            // Compteur d'itérations pour stopper après FIRE_ZONE_DURATION_TICKS secondes
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed++;

                // --- Arrêt de la zone après la durée maximale ---
                if (ticksElapsed > FIRE_ZONE_DURATION_TICKS) {
                    cancel(); // Arrête ce BukkitRunnable
                    return;
                }

                // --- Effets de particules de la zone (rendu visuel) ---
                // On dessine un cercle de particules au sol
                for (double angle = 0; angle < 2 * Math.PI; angle += 0.3) {
                    double x = center.getX() + FIRE_ZONE_RADIUS * Math.cos(angle);
                    double z = center.getZ() + FIRE_ZONE_RADIUS * Math.sin(angle);
                    Location borderParticle = new Location(center.getWorld(), x, center.getY(), z);

                    center.getWorld().spawnParticle(
                        Particle.FLAME, borderParticle, 1, 0.0, 0.1, 0.0, 0.02
                    );
                }
                // Particules de feu au centre
                center.getWorld().spawnParticle(
                    Particle.LARGE_SMOKE, center, 8, 0.8, 0.3, 0.8, 0.02
                );
                center.getWorld().spawnParticle(
                    Particle.FLAME, center, 15, 1.0, 0.2, 1.0, 0.03
                );

                // --- Application des effets sur les joueurs dans la zone ---
                for (Entity entity : center.getWorld().getNearbyEntities(center, FIRE_ZONE_RADIUS, 2.0, FIRE_ZONE_RADIUS)) {
                    if (!(entity instanceof Player target)) continue; // Ignore les non-joueurs

                    double distanceToCenter = entity.getLocation().distance(center);
                    if (distanceToCenter > FIRE_ZONE_RADIUS) continue; // Hors de la zone exacte

                    if (shooter != null && target.getUniqueId().equals(shooter.getUniqueId())) {
                        // --- PHOENIX : soigne son propre utilisateur ---
                        double newHealth = Math.min(
                            target.getHealth() + HEAL_PER_SECOND,
                            target.getMaxHealth()
                        );
                        target.setHealth(newHealth);
                        // Particules vertes de soin
                        target.getWorld().spawnParticle(
                            Particle.HEART, target.getLocation().add(0, 1, 0),
                            3, 0.3, 0.3, 0.3, 0
                        );
                        target.sendActionBar("§a✚ Régénération de Phoenix (+§f" + HEAL_PER_SECOND + "§a HP)");

                    } else {
                        // --- ENNEMIS : inflige des dégâts ---
                        // damage() déclenche les événements EntityDamageEvent
                        // (compatible avec les plugins de protection/anti-cheat)
                        if (shooter != null) {
                            target.damage(DAMAGE_PER_SECOND, shooter);
                        } else {
                            target.damage(DAMAGE_PER_SECOND);
                        }
                        target.sendActionBar("§c🔥 Tu brûles dans le feu de Phoenix ! (-§f" + DAMAGE_PER_SECOND + "§c HP)");
                    }
                }

                // Son de feu ambiant dans la zone
                center.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.0f);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Répète toutes les 20 ticks (1 seconde)
    }

    /** Capacité 2 non implémentée dans cette démo */
    @Override
    public void useAbility2(Player player) {
        player.sendMessage("§8Sphère Flash n'est pas encore implémentée.");
    }
}
