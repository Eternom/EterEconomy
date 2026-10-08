# EterEconomy

L'économie du réseau, fournie à **Vault** : soldes des joueurs et comptes partagés (banques de Vault), plus un
**journal de chaque mouvement** et un tableau de bord admin (`/ecostats`) pour suivre l'équilibre. **Aucune commande
pour les joueurs** : `/money`, `/pay` et `/eco` sont dans EterEssential, la sidebar d'EterTab lit Vault ; tout autre
plugin compatible Vault (boutiques...) fonctionne aussi. Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.8.0+** (`depend`, textes communs, cadre des menus, bus réseau, `Money`) : base de données, Redis (obligatoire) et joueurs du réseau.
- **Vault** (`depend`). EterEconomy s'enregistre avec la priorité haute : il l'emporte sur l'économie d'un autre
  plugin (Essentials...) installé en même temps.

## Fonctionnement

**La base est la seule source de vérité** (`module/account/AccountRepository`, table `etereconomy_balances`) :
- chaque mouvement est **une requête SQL relative**, atomique : `balance = ROUND(balance + ?, décimales)` pour un dépôt,
  `... balance - ? WHERE balance >= ?` pour un retrait. Deux serveurs ne peuvent pas s'écraser, un retrait ne passe
  jamais en négatif ;
- un compte inexistant vaut `currency.starting-balance` ; il est créé au premier mouvement (ou par `createPlayerAccount`) ;
- **Redis** garde une copie des soldes lus (`economy:balance:<uuid>`, 1 min), **effacée à chaque
  mouvement** : la lecture suivante repart de la base.

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

## Journal et statistiques

**Journal** (`module/history/TransactionLog`, table `etereconomy_transactions`) : chaque mouvement réussi (joueur ou
banque) avec montant signé, solde après, **source** et serveur. La source est le plugin qui a appelé Vault, trouvé
dans la pile d'appels (`JavaPlugin#getProvidingPlugin`, mis en cache par classe) : EterReward, EterEssential... ;
« Serveur » si aucun plugin. Chaque mouvement s'ajoute aussi aux totaux du jour par source (`etereconomy_daily` :
créé, détruit, opérations ; ajout atomique). Un `/pay` est un retrait puis un dépôt : net nul pour sa source.

**Entretien** (`HistoryMaintenance`, toutes les heures en tâche de fond) : relevé de la masse monétaire du jour
(`etereconomy_supply`, joueurs + banques) ; une fois par jour, sur le seul serveur qui réserve la tâche
(`etereconomy_jobs`, `INSERT IGNORE`), les transactions de plus de `history.retention-days` (365) sont **archivées**
dans `plugins/EterEconomy/archives/*.csv.gz` puis supprimées. Archive impossible = rien n'est supprimé. Totaux par
jour et relevés sont gardés pour toujours (quelques lignes par jour).

**`/ecostats`** (`etereconomy.stats`, op) : masse monétaire et ses variations sur 24 h et 7 jours, totaux créés et
détruits sur la période (aujourd'hui, 7 ou 30 jours), chaque source (émeraude : elle crée de l'argent, redstone : elle
en détruit), la masse jour par jour sur 28 jours, les 28 plus riches (fortune anormale = faille ou duplication).

## Repères économiques

Grille pour régler tous les prix et récompenses (EterMarket, EterReward). À ajuster avec `/ecostats`.

**Principe** : l'argent entre par des **robinets** et sort par des **éviers**. Si les robinets l'emportent
durablement, la masse monétaire gonfle : les prix entre joueurs s'envolent et les récompenses ne valent plus rien
(inflation). Objectif : une masse qui ne monte que doucement, au rythme des nouveaux joueurs.

**Le robinet principal : les quêtes de la guilde des métiers** (EterMarket). Les boutiques du serveur **ne rachètent
rien** : on ne gagne de l'argent qu'en livrant ses quêtes du jour au PNJ de son métier. Le robinet est donc fermé et
mesurable : un nombre fixe de quêtes par joueur et par jour.

| Repère | Valeur |
|---|---|
| Une quête | **110 à 320 Heloks** selon le niveau (facile, normale, difficile), 10 à 30 minutes de jeu |
| Par jour | 3 quêtes + 1 bonus (× 1,3) : **environ 850 Heloks** |
| Par semaine, joueur très assidu | environ **6 000 Heloks** |
| Changer de métier | **2 000 Heloks** (évier), une fois par semaine au plus |

**Les éviers** :
- **les boutiques des PNJ**, qui ne font que vendre ;
- **l'hôtel des ventes** : 5 % de taxe sur chaque vente et 1 % de frais de mise en vente, non remboursés ;
- **le changement de métier**.
- **les clans** (EterClan) : création (1 000), chunks au-delà des 9 gratuits (250, puis +7 % par chunk, plafond × 15) et
  leur entretien hebdomadaire (20, même courbe) : ~275/semaine pour 10 chunks en plus, ~4 000 pour 40 ;
- **la mort** (EterEssential) : 5 % de l'argent sur soi. La banque d'un clan protège, contre un intérêt versé à la
  réserve du clan (pas un évier : l'argent reste dans le clan).

**La règle d'or des prix en boutique** : le prix **à l'unité** d'un objet en boutique doit toujours être **supérieur**
à ce que sa livraison rapporte à l'unité. Sinon, on l'achète pour le livrer, et on imprime de l'argent sans fin.
L'éditeur de boutique d'EterMarket l'affiche en rouge (⚠) quand ce n'est pas le cas. Exemple : une quête de 64 charbons à
150 Heloks rapporte 2,34 par charbon, donc la boutique doit le vendre plus cher que 2,34 l'unité.

**Les récompenses gratuites** (`/daily`, votes…) : au plus **environ 10 % du revenu** d'un joueur assidu, soit un cycle
de `/daily` d'environ **600 à 750 Heloks**, objets compris.

**Signaux d'alerte dans `/ecostats`** :
- une masse monétaire qui monte de plus de **5 % par jour** pendant plusieurs jours : il faut réduire les robinets ou
  ajouter des éviers (taxes, coût de services comme `/rtp`, réparations) ;
- une source qui crée beaucoup plus que prévu : prix mal réglé ou faille ;
- un joueur qui pèse une part démesurée du classement des plus riches : à vérifier.
