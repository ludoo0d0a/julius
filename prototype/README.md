# Julius — prototype de refonte des écrans

Prototype HTML de la refonte des écrans Julius (orchestration d'agents IA).
Le **voice screen** n'est pas inclus (écran déjà finalisé).

## Ouvrir

Ouvrir **`index.html`** dans un navigateur — c'est le launcher.

- `index.html` — carte des écrans + direction visuelle + entrées iOS / Android.
- `app.html` — le prototype navigable (cascade complète).
  - `?p=ios` ou `?p=android` — variante de plateforme.
  - `?screen=<id>` — démarrer sur un écran (`queue`, `featlist`, `projects`,
    `features`, `feature`, `conversation`, `gitci`, `settings`).
  - `?board=1` — cadre épuré (utilisé par la planche).
- `screens.html` — planche : tous les écrans sur une page, iOS / Android.

## Écrans

Bottom bar : **Scheduler · Features · Projets · Réglages**.

- **Scheduler** — file/séquenceur : sessions en parallèle, file d'attente,
  quotas par compte, dernières features.
- **Features** — liste globale tous projets, filtres (En cours / À merger /
  En file / Mergé / Échec) + recherche.
- **Projets** — dépôts, switch rapide → features du projet.
- **Détail feature** — lancer N conversations / N agents (stratégie : coût,
  dispo, quota, multi-comptes).
- **Conversation** — chat agent + actions PR/git (merge, conflits, retry) et
  statut PR / CI GitHub.
- **Réglages** — comptes agents (parallelLimit + limite quotidienne par
  compte), stratégie de file, GitHub.

Vocabulaire repris du code : `IN_PROGRESS / QUEUED / PENDING / FAILED`,
`prState open/merged`, backends `JULES` / `CLAUDE_CODE`, `autoMergeOnCiSuccess`.
