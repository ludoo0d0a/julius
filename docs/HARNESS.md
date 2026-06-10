# Julius Coding Harness

Julius is a conversational voice agent that also acts as a **mobile harness** for remote coding agents (Jules, Claude Code). The harness orchestrates feature building: backlog → queued sessions → branch/PR → CI → merge/conflict resolution.

## Queue engine

`CodingAgentQueueEngine.tick()` is the single orchestration entry point (WorkManager `FeatureSchedulerWorker`, ~2 min interval).

1. Poll active features (session + PR state)
2. Run GitHub PR lifecycle (mergeable + CI check → auto-merge when enabled)
3. Auto-dequeue `PENDING` features when capacity allows

### Limits (defaults for Jules)

| Setting | Default | Description |
|---------|---------|-------------|
| `parallelLimit` | 3 | Max concurrent `QUEUED` + `IN_PROGRESS` features |
| `dailyLimitPerAccount` | 15 | Max sessions started per agent account per UTC day |
| `queuePaused` | false | Global pause toggle |
| `autoMergeOnCiSuccess` | true | Merge PR when mergeable and CI green |

### Agent accounts

Multiple accounts per backend (`AgentAccount`: id, label, backend, apiKey, enabled). `AccountAllocator` picks the enabled account with lowest active load and available daily quota.

## Navigation (harness)

All harness screens are **full-screen routes** (`HarnessRoute`):

- `QueueDashboard` — pending/active queue, pause toggle
- `Projects` → `Features` (per repo) → `Conversations` (per feature) → `Chat` (per session)
- `GitCi`, `PrConflict`, `ActivitiesDebug`, `AddFeature`

Entry: **Harness** button on the voice home (replaces separate Jules/Features buttons).

## Voice commands

| Command | Action |
|---------|--------|
| `CREATE_FEATURE` | Add feature to backlog + trigger queue |
| `REPLAY_FEATURE` | Replay feature prompts via allocated account |
| `MERGE_PR` | Merge first open PR found |

## Feature statuses

| Status | Meaning |
|--------|---------|
| `PENDING` | Waiting for queue slot |
| `QUEUED` | Session created, starting |
| `IN_PROGRESS` | Session active or PR awaiting CI/merge |
| `COMPLETED` | PR merged or session done |
| `FAILED` | Session or start failed |
