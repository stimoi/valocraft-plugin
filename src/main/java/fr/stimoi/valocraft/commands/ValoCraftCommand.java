package fr.stimoi.valocraft.commands;

// ============================================================
// IMPORTS
// ============================================================
import fr.stimoi.valocraft.ValoCraft;
import fr.stimoi.valocraft.agents.Agent;
import fr.stimoi.valocraft.agents.AgentManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================
 * GESTIONNAIRE DE COMMANDES — /valocraft
 * ============================================================
 * Implémente CommandExecutor (exécution) + TabCompleter (auto-complétion).
 *
 * Commandes disponibles :
 *  /valocraft help           → Affiche l'aide
 *  /valocraft agent <nom>    → Sélectionne un agent
 *  /valocraft info           → Affiche les infos de l'agent actuel
 *
 * Syntaxe des couleurs Minecraft :
 *  §r = reset    §e = jaune   §c = rouge
 *  §a = vert     §7 = gris    §f = blanc
 *  §l = gras     §o = italique
 * ============================================================
 */
public class ValoCraftCommand implements CommandExecutor, TabCompleter {

    /** Instance du plugin */
    private final ValoCraft plugin;

    /** Gestionnaire d'agents (pour associer joueur ↔ agent) */
    private final AgentManager agentManager;

    // -------------------------------------------------------
    // CONSTRUCTEUR
    // -------------------------------------------------------
    public ValoCraftCommand(ValoCraft plugin, AgentManager agentManager) {
        this.plugin = plugin;
        this.agentManager = agentManager;
    }

    // -------------------------------------------------------
    // EXÉCUTION DE LA COMMANDE
    // -------------------------------------------------------

    /**
     * Appelé par Bukkit quand un joueur ou la console tape /valocraft.
     *
     * @param sender  Qui a tapé la commande (Player ou ConsoleCommandSender)
     * @param command L'objet Command (contient nom, alias, etc.)
     * @param label   L'alias utilisé (/vc, /valo, /valocraft...)
     * @param args    Tableau des arguments après la commande
     *                Ex: "/valocraft agent jett" → args = ["agent", "jett"]
     * @return true si la commande est valide, false pour afficher l'usage du plugin.yml
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // --- Vérification de la permission de base ---
        if (!sender.hasPermission("valocraft.use")) {
            sender.sendMessage("§cTu n'as pas la permission d'utiliser ValoCraft !");
            return true;
        }

        // --- Pas d'arguments → affiche l'aide ---
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        // --- Routage selon le premier argument (sous-commande) ---
        // switch sur args[0] en minuscules pour être insensible à la casse
        switch (args[0].toLowerCase()) {

            case "help", "aide", "?" -> {
                // /valocraft help
                sendHelp(sender, label);
            }

            case "agent", "select" -> {
                // /valocraft agent <nom>
                handleAgentSelect(sender, args);
            }

            case "info" -> {
                // /valocraft info
                handleInfo(sender);
            }

            default -> {
                // Sous-commande inconnue
                sender.sendMessage("§cSous-commande inconnue. Utilise §e/" + label + " help §cpour voir les commandes.");
            }
        }

        return true; // Retourner true = commande reconnue (n'affiche pas l'usage)
    }

    // -------------------------------------------------------
    // SOUS-COMMANDES
    // -------------------------------------------------------

    /**
     * Affiche le menu d'aide stylisé.
     */
    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§e§l┌────────────────────────────┐");
        sender.sendMessage("§e§l│  §fValo§cCraft §e§l— Commandes       │");
        sender.sendMessage("§e§l├────────────────────────────┤");
        sender.sendMessage("§e§l│ §e/" + label + " agent <nom>          §e§l│");
        sender.sendMessage("§e§l│  §7Sélectionne ton agent      §e§l│");
        sender.sendMessage("§e§l│                            │");
        sender.sendMessage("§e§l│ §e/" + label + " info                 §e§l│");
        sender.sendMessage("§e§l│  §7Infos sur ton agent actuel §e§l│");
        sender.sendMessage("§e§l│                            │");
        sender.sendMessage("§e§l│ §7Agents disponibles :       §e§l│");

        // Affiche dynamiquement les agents enregistrés
        for (Agent agent : agentManager.getAllAgents()) {
            sender.sendMessage("§e§l│  §f• §e" + agent.getName() +
                " §7(" + agent.getRole() + ")         §e§l│");
        }

        sender.sendMessage("§e§l└────────────────────────────┘");
    }

    /**
     * Gère la sélection d'un agent par un joueur.
     * Réservé aux vrais joueurs (pas la console).
     *
     * @param sender La personne qui a tapé la commande
     * @param args   Arguments de la commande (args[1] = nom de l'agent)
     */
    private void handleAgentSelect(CommandSender sender, String[] args) {

        // --- La console ne peut pas sélectionner d'agent ---
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur !");
            return;
        }

        // --- Vérification de la permission de sélection ---
        if (!player.hasPermission("valocraft.agent.select")) {
            player.sendMessage("§cTu n'as pas la permission de sélectionner un agent !");
            return;
        }

        // --- Vérification de l'argument ---
        if (args.length < 2) {
            player.sendMessage("§cUsage : §e/valocraft agent §7<" +
                agentManager.getAllAgents().stream()
                    .map(a -> a.getName().toLowerCase())
                    .collect(Collectors.joining("|")) + ">");
            return;
        }

        String agentName = args[1]; // Nom de l'agent choisi (ex: "jett")

        // --- Tentative de sélection via l'AgentManager ---
        boolean success = agentManager.selectAgent(player, agentName);

        if (!success) {
            // L'agent n'existe pas dans le registre
            player.sendMessage("§cAgent §e" + agentName + " §cinconnu !");
            player.sendMessage("§7Agents disponibles : §e" +
                agentManager.getAllAgents().stream()
                    .map(Agent::getName)
                    .collect(Collectors.joining("§7, §e")));
        }
        // Si success = true, equipPlayer() a déjà envoyé les messages de confirmation
    }

    /**
     * Affiche les informations sur l'agent actuel du joueur.
     */
    private void handleInfo(CommandSender sender) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return;
        }

        Agent agent = agentManager.getPlayerAgent(player);

        if (agent == null) {
            player.sendMessage("§cTu n'as pas encore sélectionné d'agent !");
            player.sendMessage("§7Utilise §e/valocraft agent <nom> §7pour en choisir un.");
            return;
        }

        player.sendMessage("§e╔═══════════════════════════╗");
        player.sendMessage("§e║  Ton agent actuel :        ║");
        player.sendMessage("§e║  §fNom  : §e" + agent.getName() + "             §e║");
        player.sendMessage("§e║  §fRôle : §7" + agent.getRole() + "        §e║");
        player.sendMessage("§e╚═══════════════════════════╝");
    }

    // -------------------------------------------------------
    // AUTO-COMPLÉTION (TAB)
    // -------------------------------------------------------

    /**
     * Appelé quand le joueur appuie sur Tab pendant qu'il tape /valocraft.
     * Retourne une liste de suggestions affichées dans le chat.
     *
     * @param sender  Qui tape la commande
     * @param command La commande
     * @param alias   L'alias utilisé
     * @param args    Arguments actuellement saisis
     * @return Liste des complétions possibles (filtrée automatiquement par Bukkit)
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Premier argument → sous-commandes disponibles
            List<String> subCommands = List.of("agent", "help", "info");
            // Filtre les suggestions selon ce que le joueur a déjà tapé
            completions.addAll(
                subCommands.stream()
                    .filter(sc -> sc.startsWith(args[0].toLowerCase()))
                    .toList()
            );

        } else if (args.length == 2 && args[0].equalsIgnoreCase("agent")) {
            // Deuxième argument après "agent" → noms d'agents
            completions.addAll(
                agentManager.getAllAgents().stream()
                    .map(a -> a.getName().toLowerCase())
                    .filter(n -> n.startsWith(args[1].toLowerCase()))
                    .toList()
            );
        }

        return completions;
    }
}
