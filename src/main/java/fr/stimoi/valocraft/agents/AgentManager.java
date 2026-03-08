package fr.stimoi.valocraft.agents;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.jett.JettAgent;
import fr.stimoi.valocraft.agents.phoenix.PhoenixAgent;
import fr.stimoi.valocraft.agents.sage.SageAgent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import fr.stimoi.valocraft.agents.reyna.ReynaAgent;
import fr.stimoi.valocraft.agents.omen.OmenAgent;
import fr.stimoi.valocraft.agents.raze.RazeAgent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================
 * GESTIONNAIRE D'AGENTS — AgentManager
 * ============================================================
 * Rôle central du système :
 *  - Stocke tous les agents disponibles (Jett, Phoenix, Sage...)
 *  - Associe chaque joueur connecté à son agent choisi
 *  - Intercepte les clics droits pour déclencher les capacités
 *  - Nettoie les données quand un joueur se déconnecte
 *
 * Implémente Listener pour intercepter les événements Bukkit.
 * ============================================================
 */
public class AgentManager implements Listener {

    /** Instance du plugin (pour enregistrer les événements, le scheduler...) */
    private final ValoCraft plugin;

    /**
     * Registre des agents disponibles.
     * Clé   : nom de l'agent en minuscules ("jett", "phoenix", "sage")
     * Valeur: instance de l'agent (JettAgent, PhoenixAgent, SageAgent)
     */
    private final Map<String, Agent> registeredAgents = new HashMap<>();

    /**
     * Association joueur → agent sélectionné.
     * Clé   : UUID unique du joueur (ne change jamais, même si il renomme son compte)
     * Valeur: instance de l'agent choisi
     */
    private final Map<UUID, Agent> playerAgents = new HashMap<>();

    /**
     * NamespacedKey pour identifier les items de capacités.
     * Stocké dans les PersistentDataContainer des ItemMeta.
     * Clé : "valocraft:ability_id" → valeur : "jett_dash", "phoenix_fireball"...
     */
    public static NamespacedKey ABILITY_KEY;

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public AgentManager(ValoCraft plugin) {
        this.plugin = plugin;
        // Crée la NamespacedKey une seule fois (réutilisée partout)
        ABILITY_KEY = new NamespacedKey(plugin, "ability_id");

        // Enregistre ce gestionnaire comme Listener d'événements Bukkit
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------
    // ENREGISTREMENT DES AGENTS
    // -------------------------------------------------------

    /**
     * Instancie et enregistre tous les agents disponibles.
     * Pour ajouter un nouvel agent (ex: Reyna), crée sa classe
     * et ajoute-la ici.
     */
    public void registerAllAgents() {
    // Agents existants
    registerAgent(new JettAgent(plugin));
    registerAgent(new PhoenixAgent(plugin));
    registerAgent(new SageAgent(plugin));

    // Nouveaux agents
    registerAgent(new ReynaAgent(plugin));
    registerAgent(new OmenAgent(plugin));
    registerAgent(new RazeAgent(plugin));

    plugin.getLogger().info("Agents enregistrés : " + registeredAgents.keySet());
    }

    /**
     * Ajoute un agent au registre.
     *
     * @param agent Instance de l'agent à enregistrer
     */
    private void registerAgent(Agent agent) {
        registeredAgents.put(agent.getName().toLowerCase(), agent);
    }

    // -------------------------------------------------------
    // SÉLECTION D'UN AGENT PAR UN JOUEUR
    // -------------------------------------------------------

    /**
     * Assigne un agent à un joueur et équipe son inventaire.
     *
     * @param player    Le joueur qui sélectionne
     * @param agentName Nom de l'agent ("jett", "Phoenix"... insensible à la casse)
     * @return true si l'agent existe et a été assigné, false sinon
     */
    public boolean selectAgent(Player player, String agentName) {
        Agent agent = registeredAgents.get(agentName.toLowerCase());

        if (agent == null) {
            // Agent introuvable dans le registre
            return false;
        }

        // Vérifie la permission spécifique à l'agent
        String permission = "valocraft.agent." + agentName.toLowerCase();
        if (!player.hasPermission(permission)) {
            player.sendMessage("§cTu n'as pas la permission de jouer " + agent.getName() + " !");
            return false;
        }

        // Retire l'ancien agent (nettoyage des cooldowns)
        Agent previousAgent = playerAgents.get(player.getUniqueId());
        if (previousAgent != null) {
            previousAgent.cleanupPlayer(player);
        }

        // Assigne le nouvel agent et équipe le joueur
        playerAgents.put(player.getUniqueId(), agent);
        agent.equipPlayer(player);

        return true;
    }

    /**
     * Retourne l'agent actuellement sélectionné par un joueur.
     *
     * @param player Le joueur
     * @return L'agent du joueur, ou null s'il n'en a pas
     */
    public Agent getPlayerAgent(Player player) {
        return playerAgents.get(player.getUniqueId());
    }

    // -------------------------------------------------------
    // DÉTECTION DES CLICS DROITS POUR DÉCLENCHER LES CAPACITÉS
    // -------------------------------------------------------

    /**
     * Intercepte les interactions avec des blocs/l'air.
     * Vérifie si l'item en main est un item de capacité ValoCraft.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // On ne traite que les clics droits (Action.RIGHT_CLICK_AIR / RIGHT_CLICK_BLOCK)
        if (!event.getAction().name().startsWith("RIGHT_CLICK")) return;

        // Évite de traiter deux fois l'événement (main + main secondaire)
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Récupère l'ID de capacité depuis les métadonnées persistantes de l'item
        String abilityId = getAbilityId(itemInHand);
        if (abilityId == null) return; // L'item tenu n'est pas une capacité ValoCraft

        // Récupère l'agent du joueur
        Agent agent = playerAgents.get(player.getUniqueId());
        if (agent == null) {
            player.sendMessage("§cTu n'as pas d'agent sélectionné ! Utilise §e/valocraft agent <nom>");
            return;
        }

        // Déclenche la capacité correspondant au slot de l'item
        // Logique : si l'abilityId contient "ability1", c'est la capacité 1
        if (abilityId.endsWith("_ability1")) {
            event.setCancelled(true); // Empêche l'interaction bloc/entité normale
            agent.useAbility1(player);
        } else if (abilityId.endsWith("_ability2")) {
            event.setCancelled(true);
            agent.useAbility2(player);
        }
    }

    /**
     * Intercepte les clics droits sur des entités (joueurs).
     * Utilisé par Sage pour soigner un autre joueur.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Évite le double-traitement main/main secondaire
        if (event.getHand() != EquipmentSlot.HAND) return;

        // On ne s'intéresse qu'aux interactions avec des joueurs
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        String abilityId = getAbilityId(itemInHand);
        if (abilityId == null) return;

        Agent agent = playerAgents.get(player.getUniqueId());
        if (agent == null) return;

        // Si l'item est la capacité 1 de Sage (orbe de soin), on soigne la cible
        if (abilityId.equals("sage_ability1")) {
            event.setCancelled(true);
            // On passe la cible au gestionnaire de Sage via un cast
            if (agent instanceof SageAgent sageAgent) {
                sageAgent.healTarget(player, target);
            }
        }
    }

    // -------------------------------------------------------
    // NETTOYAGE AU DÉPART D'UN JOUEUR
    // -------------------------------------------------------

    /**
     * Quand un joueur se déconnecte, on supprime ses données
     * pour éviter les fuites mémoire.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Agent agent = playerAgents.remove(uuid);
        if (agent != null) {
            agent.cleanupPlayer(event.getPlayer());
        }
    }

    // -------------------------------------------------------
    // MÉTHODE UTILITAIRE
    // -------------------------------------------------------

    /**
     * Extrait l'ID de capacité depuis les métadonnées persistantes d'un ItemStack.
     *
     * PersistentDataContainer = stockage de données arbitraires dans un item,
     * qui persiste même après redémarrage du serveur (contrairement aux lore/noms).
     *
     * @param item L'item à analyser (peut être null ou AIR)
     * @return L'ID de capacité (ex: "jett_ability1"), ou null si ce n'est pas un item ValoCraft
     */
    public static String getAbilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        // Vérifie si le PersistentDataContainer contient notre clé "valocraft:ability_id"
        if (!meta.getPersistentDataContainer().has(ABILITY_KEY, PersistentDataType.STRING)) return null;

        return meta.getPersistentDataContainer().get(ABILITY_KEY, PersistentDataType.STRING);
    }

    // -------------------------------------------------------
    // GETTERS UTILITAIRES
    // -------------------------------------------------------

    /** @return Tous les agents enregistrés (lecture seule) */
    public Collection<Agent> getAllAgents() {
        return registeredAgents.values();
    }

    /** @return Nombre d'agents enregistrés */
    public int getAgentCount() {
        return registeredAgents.size();
    }

    /**
     * Nettoyage global (appelé dans onDisable).
     * Vide toutes les maps pour libérer la mémoire.
     */
    public void cleanup() {
        playerAgents.clear();
        registeredAgents.clear();
    }
}
