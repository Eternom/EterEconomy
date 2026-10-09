package fr.eternom.eterEconomy.api;

import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;

/**
 * L'argent du réseau pour les plugins Eter : comme Vault, mais chaque mouvement dit POURQUOI (source), écrit tel quel
 * dans le journal et les totaux du jour (/ecostats). Les plugins tiers gardent Vault (leur source y est devinée).
 *
 * Bloquant (base) : à appeler hors du thread principal, comme Vault.
 * <pre>
 *     // compileOnly("com.github.Eternom:EterEconomy:&lt;tag&gt;") ; plugin.yml : softdepend: [EterEconomy]
 *     EconomyApi.get().ifPresent(eco -> eco.withdraw(uuid, 250, "EterMarket · boutique"));
 * </pre>
 */
public interface EconomyApi {

    /** L'économie d'EterEconomy si elle tourne sur ce serveur. */
    static Optional<EconomyApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(EconomyApi.class));
    }

    /** 1234.5 -> « 1 234 Heloks » (décimales et nom de la monnaie de la config). */
    String format(double amount);

    /** Nombre de décimales de la monnaie (0 : montants entiers). */
    int fractionalDigits();

    /** Arrondi aux décimales de la monnaie ; -1 si le montant est négatif ou invalide. */
    double round(double amount);

    double balance(UUID player);

    boolean has(UUID player, double amount);

    /** Retire amount (arrondi) si le joueur l'a : une seule requête, jamais de solde négatif. false sinon. */
    boolean withdraw(UUID player, double amount, String source);

    /** Ajoute amount (arrondi). false si le montant est invalide. */
    boolean deposit(UUID player, double amount, String source);

    /** De from à to : retiré puis versé ; si le versement échoue, from est remboursé. false si from n'a pas assez. */
    boolean transfer(UUID from, UUID to, double amount, String source);
}
