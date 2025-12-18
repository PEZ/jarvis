# mem-debug Copilot Instructions

macOS memory pressure diagnostic toolkit using Babashka tasks and bash scripts.

## Architecture

```
bb.edn          → Task runner (entry points)
bin/memsnap-*   → Bash scripts that capture system state
snaps/          → Timestamped snapshot files (lite-*.txt, heavy-*.txt, reboot-*.txt)
launchd/        → Scheduled job plists
```

**Data flow**: `bb <task>` → `bin/memsnap-*` → writes to `snaps/` → analyzed via `bb grep` or `bb menu:*`

## Key Commands

| Task | When to use |
|------|-------------|
| `bb snap` | System feels sluggish, "feels off" |
| `bb heavy` | Out of memory dialog appeared |
| `bb reboot-log` | After unexpected reboot (within 2h) |
| `bb menu:metrics` | Quick live + historical memory stats |
| `bb menu:clojure-lsp` | Check LSP instance count and RSS |
| `bb schedule:install` | Enable automatic snapshots |

## Code Patterns

### Babashka tasks in bb.edn
- Use `(System/getProperty "user.home")` for paths — tilde expansion doesn't work in `(shell ...)`
- Wrap truncated pipelines: `(cmd | head -N) || true` — SIGPIPE causes non-zero exit with `set -euo pipefail`

### Bash scripts in bin/
- Always use `set -euo pipefail`
- Section headers like `echo "=== swap ==="` enable grep-based analysis
- Suppress errors with `2>/dev/null || true` for optional commands
- Keep `log show` windows short (10m-30m) to avoid performance issues

### menu:* tasks pattern
All menu tasks show live state + historical comparison:
```clojure
(str "echo '=== CURRENT ===' && "
     "<live command> && "
     "echo && echo '=== HISTORICAL ===' && "
     "cd " snaps " && "
     "egrep -n '<pattern>' <glob> 2>/dev/null | head -N || echo '(no matches)'")
```

## Noise Filters

Exclude these high-volume/low-signal patterns:
- `AppleLOM.Watchdog` — hardware watchdog, not memory-related
- `Ignoring GPU update because this process is not GPU managed`
- `not memory-managed`, `is not RunningBoard jetsam`

## Testing Changes

After modifying tasks or scripts:
```bash
bb snap && bb latest && bb summarize
```
Verify exit code 0 and files created in `snaps/`.

## Current Investigation

Tracking "Your system has run out of application memory" on 64GB Mac. Primary suspects:
1. VS Code Insiders (multiple windows, restarts)
2. clojure-lsp (~500-900MB per instance, one per Calva window)
3. Brave browser renderer/GPU helpers

See [AGENTS.md](../AGENTS.md) for decision trees and metric interpretation.
