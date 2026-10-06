# EterEconomy

L'économie du réseau, fournie à **Vault** : soldes des joueurs et comptes partagés (banques de Vault). **Aucune
commande** : `/money`, `/pay` et `/eco` sont dans EterEssential, la sidebar d'EterTab lit Vault ; tout autre plugin
compatible Vault (boutiques...) fonctionne aussi. Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.5.0+** (`depend`) : base de données, Redis (facultatif) et joueurs du réseau.
- **Vault** (`depend`). EterEconomy s'enregistre avec la priorité haute : il l'emporte sur l'économie d'un autre
  plugin (Essentials...) installé en même temps.

## Fonctionnement

**La base est la seule source de vérité** (`module/account/AccountRepository`, table `etereconomy_balances`) :
- chaque mouvement est **une requête SQL relative**, atomique : `balance = ROUND(balance + ?, décimales)` pour un dépôt,
  `... balance - ? WHERE balance >= ?` pour un retrait. Deux serveurs ne peuvent pas s'écraser, un retrait ne passe
  jamais en négatif, avec ou sans Redis ;
- un compte inexistant vaut `currency.starting-balance` ; il est créé au premier mouvement (ou par `createPlayerAccount`) ;
- **Redis (facultatif)** garde une copie des soldes lus (`economy:balance:<uuid>`, 1 min), **effacée à chaque
  mouvement** : la lecture suivante repart de la base. Sans Redis, chaque lecture va en base.

**Les appels sont bloquants** (base, Redis) : les plugins appellent Vault hors du thread principal (c'est le cas de tous
les plugins Eter). Les montants sont arrondis aux décimales de la monnaie ; un montant négatif ou invalide est refusé.
Une seule économie pour tout le réseau : le monde passé par Vault est ignoré. Les variantes de Vault par pseudo
retrouvent le joueur dans `eter_players`.

**Banques** (`module/bank/BankRepository`, table `etereconomy_banks`) : mêmes mouvements atomiques. Vault ne sait pas
ajouter de membres : le propriétaire est le seul membre. `banks.enabled: false` les désactive (`hasBankSupport()`).

`module/vault/VaultEconomy` traduit l'API de Vault vers ces deux dépôts ; `format()` donne « 1 234 Heloks ».

## Configuration

`config.yml` : `currency` (noms singulier et pluriel, décimales de 0 à 4, solde de départ) et `banks`
(activées, solde de départ). Ne pas changer les décimales une fois le serveur ouvert.

## Version 2.0.0

Réécriture sur EterLib (Gradle, base et Redis communs). Abandonnés : les stockages YAML, SQLite et PostgreSQL, la
copie « vivante » en mémoire et ses sauvegardes périodiques (inutiles quand la base est la source de vérité), les
commandes `/eco` et `/bank` (les commandes vont dans EterEssential) et les variables PlaceholderAPI (l'extension
Vault de PlaceholderAPI donne `%vault_eco_balance%`). Les anciennes tables `eter_balances`, `eter_banks`,
`eter_bank_members` et les clés Redis `balances`, `banks`, `bank:*` ne sont plus utilisées.
