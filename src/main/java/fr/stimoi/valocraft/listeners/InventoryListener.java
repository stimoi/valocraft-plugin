package fr.stimoi.valocraft.listeners;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.AgentManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * ============================================================
 * LISTENER — Gestion de l'inventaire ValoCraft
 * ============================================================
 * Protège les items de capacités contre :
 *  1. Le drop (touche Q ou glisser hors de l'inventaire)
 *  2. Le déplacement dans l'inventaire (clic dans le menu inventaire)
 *  3. La collecte d'items du sol par les joueurs ayant des agents
 *
 * Pourquoi ?
 * → Un joueur ne doit pas pouvoir perdre ses capacités en jeu
 * → Empêche le glitch d'échanger des items entre joueurs
 * ============================================================
 */
public class InventoryListener implements Listener {

    private final ValoCraft plugin;

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public InventoryListener(ValoCraft plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------
    // ÉVÉNEMENT 1 : DROP D'ITEM (touche Q ou drag hors fenêtre)
    // -------------------------------------------------------

    /**
     * Intercepte la tentative de lâcher un item.
     * Si l'item lâché est un item de capacité ValoCraft → annulation.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Récupère l'ItemStack qui va être droppé
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Vérifie si c'est un item de capacité ValoCraft
        if (isAbilityItem(droppedItem)) {
            event.setCancelled(true); // Annule le drop
            player.sendActionBar("§c✖ Tu ne peux pas lâcher tes capacités d'agent !");

            // Sécurité : remet l'item dans l'inventaire si nécessaire
            // (dans les rares cas où l'item a déjà été retiré de l'inventaire)
            if (!player.getInventory().contains(droppedItem)) {
                player.getInventory().addItem(droppedItem);
            }
        }
    }

    // -------------------------------------------------------
    // ÉVÉNEMENT 2 : CLIC DANS L'INVENTAIRE
    // -------------------------------------------------------

    /**
     * Intercepte les clics dans l'inventaire (Shift+Clic, glisser, etc.)
     * Empêche de déplacer les items de capacités.
     *
     * Cas couverts :
     *  - Clic gauche / droit sur un item de capacité dans l'inventaire
     *  - Shift+Clic pour déplacer rapidement
     *  - Touche Q dans l'inventaire pour dropper
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Ne traite que les clics de vrais joueurs
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // --- Vérifie l'item sur le curseur (item "porté" par la souris) ---
        ItemStack cursorItem = event.getCursor();
        if (isAbilityItem(cursorItem)) {
            event.setCancelled(true);
            player.sendActionBar("§c✖ Tu ne peux pas déplacer tes capacités d'agent !");
            return;
        }

        // --- Vérifie l'item sur lequel on clique ---
        ItemStack clickedItem = event.getCurrentItem();
        if (isAbilityItem(clickedItem)) {
            event.setCancelled(true);
            player.sendActionBar("§c✖ Tu ne peux pas déplacer tes capacités d'agent !");
        }
    }

    // -------------------------------------------------------
    // ÉVÉNEMENT 3 : DRAG (glisser-déposer dans l'inventaire)
    // -------------------------------------------------------

    /**
     * Intercepte les opérations de drag (maintenir le clic et glisser
     * pour répartir les items sur plusieurs slots).
     * Si l'item déplacé est une capacité → annulation.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack draggedItem = event.getOldCursor();
        if (isAbilityItem(draggedItem)) {
            event.setCancelled(true);
            player.sendActionBar("§c✖ Tu ne peux pas déplacer tes capacités d'agent !");
        }
    }

    // -------------------------------------------------------
    // MÉTHODE UTILITAIRE
    // -------------------------------------------------------

    /**
     * Vérifie si un ItemStack est un item de capacité ValoCraft.
     *
     * Utilise AgentManager.getAbilityId() qui lit le PersistentDataContainer
     * de l'item pour trouver la NamespacedKey "valocraft:ability_id".
     *
     * C'est BEAUCOUP plus fiable que de comparer les noms ou lores,
     * car un joueur pourrait avoir un item avec le même nom manuellement.
     *
     * @param item L'item à tester (peut être null)
     * @return true si l'item est une capacité ValoCraft
     */
    private boolean isAbilityItem(ItemStack item) {
        // AgentManager.getAbilityId() retourne null si l'item n'est pas une capacité
        return AgentManager.getAbilityId(item) != null;
    }
}
