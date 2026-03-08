package fr.stimoi.valocraft;

// ============================================================
// IMPORTS BUKKIT / PAPER
// JavaPlugin = classe mère de tout plugin Bukkit/Paper
// ============================================================
import fr.stimoi.valocraft.agents.AgentManager;
import fr.stimoi.valocraft.commands.ValoCraftCommand;
import fr.stimoi.valocraft.listeners.BlockBreakListener;
import fr.stimoi.valocraft.listeners.InventoryListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ============================================================
 * CLASSE PRINCIPALE — ValoCraft
 * ============================================================
 * Point d'entrée du plugin. Paper/Bukkit instancie cette classe
 * automatiquement au chargement grâce à la ligne "main:" dans plugin.yml.
 *
 * Cycle de vie :
 *  onEnable()  → appelé quand le plugin démarre (serveur start ou /reload)
 *  onDisable() → appelé quand le plugin s'arrête (serveur stop ou /reload)
 * ============================================================
 */
public final class ValoCraft extends JavaPlugin {

    // -------------------------------------------------------
    // Singleton : permet d'accéder à l'instance du plugin
    // depuis n'importe quelle classe avec ValoCraft.getInstance()
    // -------------------------------------------------------
    private static ValoCraft instance;

    // Gestionnaire centralisé des agents (Jett, Phoenix, Sage...)
    private AgentManager agentManager;

    // -------------------------------------------------------
    // onEnable — Démarrage du plugin
    // -------------------------------------------------------
    @Override
    public void onEnable() {
        // Enregistre le singleton AVANT toute autre opération
        instance = this;

        // Affiche un message dans la console du serveur
        getLogger().info("╔═══════════════════════════════════╗");
        getLogger().info("║  ValoCraft v" + getDescription().getVersion() + " — by sti_moi    ║");
        getLogger().info("║  Initialisation en cours...       ║");
        getLogger().info("╚═══════════════════════════════════╝");

        // 1) Crée et initialise le gestionnaire d'agents
        //    (enregistre toutes les capacités de Jett, Phoenix, Sage...)
        this.agentManager = new AgentManager(this);
        agentManager.registerAllAgents();

        // 2) Enregistre les commandes
        //    getCommand("valocraft") correspond à l'entrée dans plugin.yml
        ValoCraftCommand commandExecutor = new ValoCraftCommand(this, agentManager);
        getCommand("valocraft").setExecutor(commandExecutor);
        // TabCompleter = auto-complétion de la commande avec Tab
        getCommand("valocraft").setTabCompleter(commandExecutor);

        // 3) Enregistre les listeners (événements Bukkit)
        //    PluginManager gère l'abonnement aux événements du serveur
        getServer().getPluginManager().registerEvents(
                new BlockBreakListener(this), this
        );
        getServer().getPluginManager().registerEvents(
                new InventoryListener(this), this
        );

        getLogger().info("ValoCraft activé avec succès ! " + agentManager.getAgentCount() + " agents chargés.");
    }

    // -------------------------------------------------------
    // onDisable — Arrêt du plugin
    // -------------------------------------------------------
    @Override
    public void onDisable() {
        // Nettoie les données des joueurs pour éviter les fuites mémoire
        if (agentManager != null) {
            agentManager.cleanup();
        }
        getLogger().info("ValoCraft désactivé. À bientôt !");
    }

    // -------------------------------------------------------
    // ACCESSEURS STATIQUES
    // Utilisés par les autres classes : ValoCraft.getInstance()
    // -------------------------------------------------------

    /**
     * Retourne l'instance unique du plugin.
     * Utilisation : ValoCraft plugin = ValoCraft.getInstance();
     */
    public static ValoCraft getInstance() {
        return instance;
    }

    /**
     * Retourne le gestionnaire d'agents.
     * Utile pour récupérer l'agent d'un joueur depuis n'importe quelle classe.
     */
    public AgentManager getAgentManager() {
        return agentManager;
    }
}
