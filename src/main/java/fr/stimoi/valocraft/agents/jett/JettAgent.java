package fr.stimoi.valocraft.agents.jett;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.Agent;
import fr.stimoi.valocraft.agents.AgentManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * ============================================================
 * AGENT JETT — Duelliste
 * ============================================================
 * Capacités :
 *  [1] Vent Arrière (Tailwind) : Dash dans la direction du regard
 *  [2] Courant Ascendant (Updraft) : Saut boosté vers le haut
 *
 * Jett est l'agent de mobilité par excellence dans Valorant.
 * Ses capacités reposent sur la manipulation de la vélocité du joueur.
 * ============================================================
 */
public class JettAgent extends Agent {

    // -------------------------------------------------------
    // CONSTANTES DE CONFIGURATION
    // Centralise les valeurs pour faciliter l'équilibrage
    // -------------------------------------------------------

    /** Durée du cooldown du dash en millisecondes (12 secondes) */
    private static final long DASH_COOLDOWN_MS = 12_000L;

    /** Durée du cooldown de l'updraft en millisecondes (20 secondes) */
    private static final long UPDRAFT_COOLDOWN_MS = 20_000L;

    /** Force du dash (distance parcourue) */
    private static final double DASH_FORCE = 1.2;

    /** Force du saut boosté (hauteur atteinte) */
    private static final double UPDRAFT_FORCE = 1.5;

    /** Nom interne des capacités (utilisé pour les cooldowns) */
    private static final String ABILITY_DASH    = "dash";
    private static final String ABILITY_UPDRAFT = "updraft";

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public JettAgent(ValoCraft plugin) {
        // Appelle le constructeur de la classe mère Agent
        super(plugin, "Jett", "Duelliste");
    }

    // -------------------------------------------------------
    // ITEMS DE CAPACITÉS
    // -------------------------------------------------------

    /**
     * [Capacité 1] Vent Arrière — item représentatif : FEATHER (plume)
     * L'item est tagué avec un NamespacedKey pour être identifiable.
     */
    @Override
    public ItemStack getAbility1Item() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();

        // Nom coloré affiché en jeu (§e = jaune, §r = reset, §7 = gris)
        meta.setDisplayName("§e⚡ Vent Arrière §7(Clic Droit)");

        // Lore = description sous le nom de l'item
        meta.setLore(List.of(
            "§7Fonce dans la direction",
            "§7de ton regard.",
            "§8Cooldown : §e" + (DASH_COOLDOWN_MS / 1000) + "s"
        ));

        // *** NamespacedKey : tag persistant sur l'item ***
        // PersistentDataContainer survit aux redémarrages serveur
        // Clé "valocraft:ability_id" → valeur "jett_ability1"
        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "jett_ability1"  // Convention : nomAgent_abilityN
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * [Capacité 2] Courant Ascendant — item représentatif : WIND_CHARGE
     */
    @Override
    public ItemStack getAbility2Item() {
        ItemStack item = new ItemStack(Material.WIND_CHARGE); // Disponible depuis 1.21
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§b↑ Courant Ascendant §7(Clic Droit)");
        meta.setLore(List.of(
            "§7Propulse-toi vers le haut.",
            "§8Cooldown : §e" + (UPDRAFT_COOLDOWN_MS / 1000) + "s"
        ));

        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "jett_ability2"
        );

        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------
    // ÉQUIPEMENT DU JOUEUR
    // -------------------------------------------------------

    /**
     * Place les items de capacités dans l'inventaire du joueur.
     * Convention Valorant : capacités dans les slots 6 et 7 (index 5 et 6).
     */
    @Override
    public void equipPlayer(Player player) {
        // Vide d'abord les slots de capacités
        player.getInventory().setItem(5, getAbility1Item());
        player.getInventory().setItem(6, getAbility2Item());

        // Message de confirmation avec les couleurs de Jett
        player.sendMessage("§e╔══════════════════════════╗");
        player.sendMessage("§e║  Agent sélectionné : §fJett  §e║");
        player.sendMessage("§e║  Rôle : §7Duelliste          §e║");
        player.sendMessage("§e║  [E] §fVent Arrière         §e║");
        player.sendMessage("§e║  [Q] §fCourant Ascendant    §e║");
        player.sendMessage("§e╚══════════════════════════╝");

        // Son de sélection d'agent
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    // -------------------------------------------------------
    // CAPACITÉ 1 : VENT ARRIÈRE (DASH)
    // -------------------------------------------------------

    /**
     * Propulse Jett dans la direction de son regard.
     *
     * Logique :
     * 1. Vérifie le cooldown
     * 2. Récupère le vecteur directionnel du regard (getDirection())
     * 3. Multiplie ce vecteur par la force du dash
     * 4. Applique la vélocité au joueur (setVelocity)
     * 5. Joue son et particules
     */
    @Override
    public void useAbility1(Player player) {
        // --- Vérification du cooldown ---
        if (isOnCooldown(player, ABILITY_DASH, DASH_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_DASH, DASH_COOLDOWN_MS);
            player.sendMessage("§c⏱ Vent Arrière disponible dans §e" + remaining + "s");
            return;
        }

        // --- Démarre le cooldown ---
        setCooldown(player, ABILITY_DASH);

        // --- Calcul de la direction ---
        // getLocation().getDirection() retourne un vecteur unitaire
        // dans la direction où regarde le joueur (normalisé : longueur = 1)
        Vector direction = player.getLocation().getDirection();

        // On ne veut pas propulser vers le bas si le joueur regarde le sol
        // On fixe la composante verticale à 0.15 (légèrement vers le haut)
        direction.setY(Math.max(direction.getY(), 0.15));

        // Multiplie le vecteur unitaire par la force du dash
        Vector velocity = direction.multiply(DASH_FORCE);

        // --- Application de la vélocité ---
        // setVelocity() est instantané et respecte la physique du jeu
        player.setVelocity(velocity);

        // --- Effets visuels et sonores ---
        // Particules de vent au point de départ du dash
        player.getWorld().spawnParticle(
            Particle.CLOUD,          // Type de particule
            player.getLocation(),    // Position
            20,                      // Nombre de particules
            0.3, 0.3, 0.3,           // Dispersion X, Y, Z
            0.05                     // Vitesse des particules
        );

        // Son de dash (le plus proche de Valorant disponible dans Minecraft)
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.8f);

        // Actionbar : message au centre de l'écran (moins intrusif que le chat)
        player.sendActionBar("§e⚡ Vent Arrière activé !");
    }

    // -------------------------------------------------------
    // CAPACITÉ 2 : COURANT ASCENDANT (UPDRAFT)
    // -------------------------------------------------------

    /**
     * Propulse Jett vers le haut.
     *
     * Note technique : on utilise Vector(0, force, 0) pour un mouvement
     * purement vertical. On additionne la vélocité actuelle horizontale
     * pour ne pas stopper le mouvement en cours.
     */
    @Override
    public void useAbility2(Player player) {
        // --- Vérification du cooldown ---
        if (isOnCooldown(player, ABILITY_UPDRAFT, UPDRAFT_COOLDOWN_MS)) {
            double remaining = getRemainingCooldown(player, ABILITY_UPDRAFT, UPDRAFT_COOLDOWN_MS);
            player.sendMessage("§c⏱ Courant Ascendant disponible dans §e" + remaining + "s");
            return;
        }

        // --- Démarre le cooldown ---
        setCooldown(player, ABILITY_UPDRAFT);

        // --- Vélocité verticale ---
        // On conserve la vélocité horizontale actuelle pour ne pas briser l'élan
        Vector currentVelocity = player.getVelocity();
        Vector upwardBoost = new Vector(
            currentVelocity.getX() * 0.5, // Réduit légèrement le mouvement horizontal
            UPDRAFT_FORCE,                  // Fort boost vertical
            currentVelocity.getZ() * 0.5
        );

        player.setVelocity(upwardBoost);

        // --- Effets ---
        player.getWorld().spawnParticle(
            Particle.GUST,              // Particule de vent (disponible 1.21)
            player.getLocation(),
            10,
            0.2, 0.0, 0.2,
            0.1
        );

        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1.0f, 1.2f);
        player.sendActionBar("§b↑ Courant Ascendant activé !");
    }
}
