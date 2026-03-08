package fr.stimoi.valocraft.agents.sage;

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

import java.util.List;

/**
 * ============================================================
 * AGENT SAGE — Sentinelle
 * ============================================================
 * Capacités :
 *  [1] Orbe de Soin (Healing Orb) : Cliquer sur un allié pour le soigner.
 *      Sage peut aussi se soigner elle-même avec un soin réduit.
 *
 * Particularité de Sage :
 *  La capacité 1 fonctionne différemment des autres agents :
 *  au lieu d'un clic droit dans l'air, on clique droit SUR un joueur.
 *  C'est l'AgentManager qui détecte PlayerInteractEntityEvent
 *  et appelle directement healTarget(soigneur, cible).
 * ============================================================
 */
public class SageAgent extends Agent {

    // -------------------------------------------------------
    // CONSTANTES DE CONFIGURATION
    // -------------------------------------------------------

    /** Cooldown de l'Orbe de Soin (40 secondes) */
    private static final long HEAL_COOLDOWN_MS = 40_000L;

    /** Cooldown de l'auto-soin (60 secondes — pénalité pour se soigner soi-même) */
    private static final long SELF_HEAL_COOLDOWN_MS = 60_000L;

    /** Points de vie rendus à un allié */
    private static final double ALLY_HEAL_AMOUNT = 10.0;

    /** Points de vie rendus à Sage si elle se soigne (réduit dans Valorant) */
    private static final double SELF_HEAL_AMOUNT = 6.0;

    /** Portée maximale pour soigner un allié (en blocs) */
    private static final double HEAL_RANGE = 5.0;

    /** Noms des capacités pour les cooldowns */
    private static final String ABILITY_HEAL      = "heal";
    private static final String ABILITY_SELF_HEAL = "self_heal";

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public SageAgent(ValoCraft plugin) {
        super(plugin, "Sage", "Sentinelle");
    }

    // -------------------------------------------------------
    // ITEMS DE CAPACITÉS
    // -------------------------------------------------------

    /**
     * [Capacité 1] Orbe de Soin — item : EMERALD (vert = soin)
     * Pour utiliser : tenir cet item et faire clic droit SUR un joueur.
     */
    @Override
    public ItemStack getAbility1Item() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a✚ Orbe de Soin §7(Clic Droit sur joueur)");
        meta.setLore(List.of(
            "§7Soigne un allié (§a+" + ALLY_HEAL_AMOUNT + " HP§7).",
            "§7Peut aussi se soigner (§a+" + SELF_HEAL_AMOUNT + " HP§7).",
            "§8Portée : §e" + HEAL_RANGE + " blocs",
            "§8Cooldown allié : §e" + (HEAL_COOLDOWN_MS / 1000) + "s",
            "§8Cooldown auto-soin : §e" + (SELF_HEAL_COOLDOWN_MS / 1000) + "s"
        ));

        // Tag NamespacedKey pour identification
        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY,
            PersistentDataType.STRING,
            "sage_ability1"
        );

        item.setItemMeta(meta);
        return item;
    }

    /** [Capacité 2] Orbe de Lenteur — placeholder */
    @Override
    public ItemStack getAbility2Item() {
        ItemStack item = new ItemStack(Material.ICE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b❄ Orbe de Lenteur §8(À venir)");
        meta.setLore(List.of("§8Non disponible dans cette version."));
        meta.getPersistentDataContainer().set(
            AgentManager.ABILITY_KEY, PersistentDataType.STRING, "sage_ability2"
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

        player.sendMessage("§a╔══════════════════════════════╗");
        player.sendMessage("§a║  Agent sélectionné : §fSage   §a║");
        player.sendMessage("§a║  Rôle : §7Sentinelle           §a║");
        player.sendMessage("§a║  [E] §fOrbe de Soin            §a║");
        player.sendMessage("§a║  §8Clic droit §7sur un joueur   §a║");
        player.sendMessage("§a╚══════════════════════════════╝");

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    // -------------------------------------------------------
    // CAPACITÉ 1 : ORBE DE SOIN (via clic sur un joueur)
    // Cette méthode est appelée depuis AgentManager.onPlayerInteractEntity()
    // -------------------------------------------------------

    /**
     * Soigne une cible (allié ou Sage elle-même).
     * Appelée par AgentManager quand Sage clique droit sur un joueur avec l'Emerald.
     *
     * @param healer Sage (le joueur qui utilise la capacité)
     * @param target Le joueur à soigner
     */
    public void healTarget(Player healer, Player target) {

        boolean isSelfHeal = healer.getUniqueId().equals(target.getUniqueId());

        // --- Vérification du cooldown approprié ---
        String cooldownKey = isSelfHeal ? ABILITY_SELF_HEAL : ABILITY_HEAL;
        long cooldownDuration = isSelfHeal ? SELF_HEAL_COOLDOWN_MS : HEAL_COOLDOWN_MS;

        if (isOnCooldown(healer, cooldownKey, cooldownDuration)) {
            double remaining = getRemainingCooldown(healer, cooldownKey, cooldownDuration);
            healer.sendMessage("§c⏱ Orbe de Soin disponible dans §e" + remaining + "s");
            return;
        }

        // --- Vérification de la portée (distance entre healer et cible) ---
        if (!isSelfHeal) {
            double distance = healer.getLocation().distance(target.getLocation());
            if (distance > HEAL_RANGE) {
                healer.sendMessage("§c⚠ " + target.getName() + " est trop loin ! (§e" +
                    String.format("%.1f", distance) + " / " + HEAL_RANGE + " blocs§c)");
                return;
            }
        }

        // --- Application du soin ---
        double healAmount = isSelfHeal ? SELF_HEAL_AMOUNT : ALLY_HEAL_AMOUNT;

        // Math.min garantit qu'on ne dépasse pas le maximum de HP
        double newHealth = Math.min(target.getHealth() + healAmount, target.getMaxHealth());
        target.setHealth(newHealth);

        // --- Démarre le cooldown ---
        setCooldown(healer, cooldownKey);

        // --- Effets visuels et sonores ---
        // Particules de soin autour de la cible
        target.getWorld().spawnParticle(
            Particle.HEART,
            target.getLocation().add(0, 1.5, 0),
            8, 0.4, 0.4, 0.4, 0.05
        );
        // Particules vertes de régénération
        target.getWorld().spawnParticle(
            Particle.COMPOSTER,   // Petites particules vertes
            target.getLocation().add(0, 1.0, 0),
            20, 0.3, 0.5, 0.3, 0.1
        );

        // Son de soin pour les deux joueurs
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
        healer.playSound(healer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);

        // --- Messages ---
        if (isSelfHeal) {
            healer.sendActionBar("§a✚ Auto-soin : §f+" + SELF_HEAL_AMOUNT + " HP §8(§f" +
                String.format("%.1f", newHealth) + "§8/§f" + String.format("%.0f", healer.getMaxHealth()) + "§8)");
        } else {
            // Message pour Sage (le soigneur)
            healer.sendActionBar("§a✚ Tu as soigné §f" + target.getName() +
                " §a(§f+" + ALLY_HEAL_AMOUNT + " HP§a)");

            // Message pour la cible
            target.sendActionBar("§a✚ Tu as été soigné par §f" + healer.getName() +
                " §a(§f+" + ALLY_HEAL_AMOUNT + " HP§a → §f" + String.format("%.1f", newHealth) + " HP§a)");
        }
    }

    // -------------------------------------------------------
    // useAbility1 et useAbility2
    // -------------------------------------------------------

    /**
     * Appelée quand Sage clique droit dans l'AIR avec l'Emerald
     * (pas sur un joueur). On lui rappelle comment soigner.
     */
    @Override
    public void useAbility1(Player player) {
        player.sendMessage("§a✚ §7Fais §eclic droit sur un joueur §7pour le soigner !");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f);
    }

    /** Capacité 2 non implémentée */
    @Override
    public void useAbility2(Player player) {
        player.sendMessage("§8Orbe de Lenteur n'est pas encore implémentée.");
    }
}
