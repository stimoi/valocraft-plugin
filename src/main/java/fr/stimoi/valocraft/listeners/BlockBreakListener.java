package fr.stimoi.valocraft.listeners;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;

/**
 * ============================================================
 * LISTENER — Prévention du cassage de blocs
 * ============================================================
 * Dans Valorant, les joueurs ne peuvent pas modifier le terrain.
 * Ce Listener empêche tous les joueurs de casser des blocs,
 * sauf :
 *  - Les OPs (via la permission valocraft.bypass.blockbreak)
 *  - (Future extension) Des capacités spécifiques qui passent
 *    par un flag temporaire sur le joueur
 *
 * Comment fonctionne un Listener ?
 * → On implémente l'interface Listener (marker interface)
 * → On annote chaque méthode avec @EventHandler
 * → Bukkit appelle automatiquement ces méthodes quand l'événement se produit
 * ============================================================
 */
public class BlockBreakListener implements Listener {

    /** Instance du plugin (pour accéder à l'AgentManager si nécessaire) */
    private final ValoCraft plugin;

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public BlockBreakListener(ValoCraft plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------
    // ÉVÉNEMENT : CASSAGE DE BLOC
    // -------------------------------------------------------

    /**
     * Intercepte chaque tentative de cassage de bloc.
     *
     * @EventHandler(priority = EventPriority.HIGH)
     * → Traité après les autres plugins (priorité haute)
     *   mais avant HIGHEST et MONITOR.
     *   Utile pour ne pas entrer en conflit avec des plugins
     *   de protection de terrain (WorldGuard, GriefPrevention...).
     *
     * @param event L'événement de cassage de bloc
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // --- Bypass pour les admins ---
        // Les joueurs avec la permission "valocraft.bypass.blockbreak"
        // (par défaut : OP) peuvent toujours casser des blocs.
        if (player.hasPermission("valocraft.bypass.blockbreak")) {
            return; // On laisse passer l'événement
        }

        // --- Vérification du mode créatif ---
        // En mode créatif, les blocs se cassent instantanément.
        // On choisit d'autoriser le mode créatif (utile pour construire des arènes).
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }

        // --- Annulation du cassage ---
        // event.setCancelled(true) empêche le bloc d'être cassé
        // et annule tous les événements liés (drop d'item, XP...)
        event.setCancelled(true);

        // Message d'avertissement non-intrusif (actionbar = au-dessus de la vie)
        // On évite le chat pour ne pas spammer
        player.sendActionBar("§c✖ Impossible de casser des blocs en jeu ValoCraft !");

        // Note pour les développeurs : pour autoriser le cassage via une capacité,
        // utilise un HashSet<UUID> dans le plugin et vérifie ici :
        // if (plugin.getBlockBreakBypassPlayers().contains(player.getUniqueId())) return;
        // Puis retire le joueur du Set après le cassage.
    }
}
