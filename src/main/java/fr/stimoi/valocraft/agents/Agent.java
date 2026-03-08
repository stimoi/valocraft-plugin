package fr.stimoi.valocraft.agents;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================
 * CLASSE ABSTRAITE — Agent
 * ============================================================
 * Modèle de base pour TOUS les agents ValoCraft.
 * Chaque agent (Jett, Phoenix, Sage...) doit hériter de cette classe
 * et implémenter ses propres capacités.
 *
 * Pourquoi "abstract" ?
 * → On ne peut pas instancier Agent directement ("new Agent()" = impossible)
 * → Force chaque sous-classe à implémenter les méthodes abstraites
 * ============================================================
 */
public abstract class Agent {

    // -------------------------------------------------------
    // CHAMPS COMMUNS À TOUS LES AGENTS
    // -------------------------------------------------------

    /** Instance du plugin principal (accès aux logs, scheduler, etc.) */
    protected final ValoCraft plugin;

    /** Nom de l'agent affiché en jeu (ex: "Jett", "Phoenix") */
    protected final String name;

    /** Rôle de l'agent (ex: "Duelliste", "Contrôleur") */
    protected final String role;

    /**
     * Cooldowns par capacité.
     * Structure : Map<UUID du joueur, Map<nom de la capacité, timestamp (ms) de la dernière utilisation>>
     * Exemple : cooldowns.get(player.getUniqueId()).get("dash") = 1700000000000L
     */
    protected final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------

    /**
     * @param plugin Instance du plugin (pour le scheduler et les logs)
     * @param name   Nom de l'agent ("Jett", "Phoenix", etc.)
     * @param role   Rôle Valorant ("Duelliste", "Sentinelle", etc.)
     */
    public Agent(ValoCraft plugin, String name, String role) {
        this.plugin = plugin;
        this.name = name;
        this.role = role;
    }

    // -------------------------------------------------------
    // MÉTHODES ABSTRAITES — À implémenter dans chaque agent
    // -------------------------------------------------------

    /**
     * Retourne l'ItemStack représentant la capacité 1 de l'agent.
     * Cet item est placé dans l'inventaire du joueur lors de la sélection.
     *
     * @return ItemStack de la capacité 1 (avec NamespacedKey dans les métadonnées)
     */
    public abstract ItemStack getAbility1Item();

    /**
     * Retourne l'ItemStack représentant la capacité 2 de l'agent.
     *
     * @return ItemStack de la capacité 2
     */
    public abstract ItemStack getAbility2Item();

    /**
     * Exécute la capacité 1 de l'agent.
     * Appelée quand le joueur fait un clic droit avec l'item de capacité 1.
     *
     * @param player Le joueur qui utilise la capacité
     */
    public abstract void useAbility1(Player player);

    /**
     * Exécute la capacité 2 de l'agent.
     * Appelée quand le joueur fait un clic droit avec l'item de capacité 2.
     *
     * @param player Le joueur qui utilise la capacité
     */
    public abstract void useAbility2(Player player);

    /**
     * Donne au joueur les items de capacités dans son inventaire.
     * Appelée lors de la sélection de l'agent.
     *
     * @param player Le joueur qui sélectionne cet agent
     */
    public abstract void equipPlayer(Player player);

    // -------------------------------------------------------
    // GESTION DES COOLDOWNS — Méthodes utilitaires communes
    // Disponibles pour tous les agents qui héritent de cette classe
    // -------------------------------------------------------

    /**
     * Vérifie si le joueur est encore en cooldown pour une capacité donnée.
     *
     * @param player       Le joueur à vérifier
     * @param abilityName  Nom interne de la capacité (ex: "dash", "fireball")
     * @param cooldownMs   Durée du cooldown en millisecondes
     * @return true si le joueur EST en cooldown (ne peut pas utiliser), false sinon
     */
    protected boolean isOnCooldown(Player player, String abilityName, long cooldownMs) {
        UUID uuid = player.getUniqueId();

        // Si le joueur n'a jamais utilisé cette capacité, pas de cooldown
        if (!cooldowns.containsKey(uuid)) return false;
        if (!cooldowns.get(uuid).containsKey(abilityName)) return false;

        // Calcule le temps écoulé depuis la dernière utilisation
        long lastUsed = cooldowns.get(uuid).get(abilityName);
        long elapsed = System.currentTimeMillis() - lastUsed;

        return elapsed < cooldownMs;
    }

    /**
     * Calcule le temps restant (en secondes) avant que la capacité soit disponible.
     * Utilisé pour afficher un message au joueur.
     *
     * @param player       Le joueur concerné
     * @param abilityName  Nom de la capacité
     * @param cooldownMs   Durée totale du cooldown
     * @return Secondes restantes (arrondi au supérieur), 0 si pas en cooldown
     */
    protected double getRemainingCooldown(Player player, String abilityName, long cooldownMs) {
        UUID uuid = player.getUniqueId();
        if (!cooldowns.containsKey(uuid) || !cooldowns.get(uuid).containsKey(abilityName)) return 0;

        long lastUsed = cooldowns.get(uuid).get(abilityName);
        long elapsed = System.currentTimeMillis() - lastUsed;
        long remaining = cooldownMs - elapsed;

        // Arrondi à 1 décimale pour l'affichage
        return remaining > 0 ? Math.round(remaining / 100.0) / 10.0 : 0;
    }

    /**
     * Enregistre l'utilisation d'une capacité (démarre le cooldown).
     * Appelée AU DÉBUT de l'exécution d'une capacité.
     *
     * @param player       Le joueur qui a utilisé la capacité
     * @param abilityName  Nom de la capacité
     */
    protected void setCooldown(Player player, String abilityName) {
        UUID uuid = player.getUniqueId();
        // computeIfAbsent crée la map intérieure si elle n'existe pas encore
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                 .put(abilityName, System.currentTimeMillis());
    }

    /**
     * Nettoie les données d'un joueur (appelé quand il quitte le serveur).
     * IMPORTANT : évite les fuites mémoire sur les serveurs actifs.
     *
     * @param player Le joueur qui se déconnecte
     */
    public void cleanupPlayer(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    // -------------------------------------------------------
    // GETTERS
    // -------------------------------------------------------

    /** @return Nom de l'agent ("Jett", "Phoenix", "Sage"...) */
    public String getName() { return name; }

    /** @return Rôle de l'agent ("Duelliste", "Sentinelle"...) */
    public String getRole() { return role; }
}
