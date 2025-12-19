# Jarvis

Babashka toolkit for diagnosing macOS memory pressure on a 64GB Mac.

## Goal

Capture "what the kernel thought was happening" when macOS shows "Your system has run out of application memory", when apps get paused/unresponsive, or when the machine freezes/reboots.

This is deliberately NOT a "tweak settings until it stops" kit.
It's a "collect comparable evidence fast, with minimal cognitive load" kit.

## Quick Start

```bash
bb tasks              # See all available commands
bb recipe:metrics     # Check current memory health
bb snap:lite          # Capture lightweight snapshot
bb snap:heavy         # Capture comprehensive snapshot (during OOM)
```

## Design

- Snapshots are written to `~/jarvis/snaps/` as timestamped text files
- Scripts live in `~/jarvis/bin/` and are invoked via bb tasks
- The default snapshot is small (lite). Heavy snapshots are for escalation
- Log capture is bounded because `log show` can be slow and huge

## Why These Signals

- `sysctl vm.swapusage` + `memory_pressure` + `vm_stat` tells whether the kernel is in memory distress (swap exhaustion, compressor pressure, wired growth, pageouts)
- `df -h /` tells whether swap/logging can even succeed (low disk can make "memory" symptoms worse)
- `top`/`ps` tell which processes were large at that moment (correlation, not proof)
- `log show` is the best shot at seeing "who killed whom" (jetsam/memorystatus), watchdog/panic, GPU resets, etc.

## Recipes

**Baseline (after reboot):**
```bash
bb snap:lite
```

**Dialog appears / system feels off:**
```bash
bb snap:lite
```

**Severe incident (system very slow, OOM dialog):**
```bash
bb snap:heavy
```

**After unexpected reboot:**
```bash
bb reboot-log
bb recipe:panic   # Check for panic/watchdog events
```

## Analysis

```bash
bb summarize                                    # Key metrics from newest snapshot
bb grep "panic|watchdog|jetsam"                 # Search all snapshots
bb inspect-process clojure-lsp                  # Investigate specific process
bb recipe:metrics                               # Live + historical memory stats
```

## Layout

```
~/jarvis/
  bb.edn                    # Task definitions
  bin/
    memsnap-lite            # Lightweight capture
    memsnap-heavy           # Comprehensive capture
    memsnap-reboot-log      # Post-reboot logs
    memsnap-grep            # Search helper
    memsnap-latest          # Find newest snapshot
  scripts/
    tasks.clj               # Core task implementations
    recipes.clj             # Canned diagnostic recipes
    process_inspect.clj     # Process investigation
    config.clj              # Shared paths
  snaps/
    lite-*.txt              # Lightweight snapshots
    heavy-*.txt             # Comprehensive snapshots
    reboot-*.txt            # Post-reboot logs
  launchd/
    com.jarvis.memsnap-*.plist  # Scheduled job definitions
```

## Scheduling

```bash
bb schedule:install    # Enable auto-snapshots (lite every 6h, heavy daily 3am)
bb schedule:status     # Check if jobs are active
bb schedule:uninstall  # Disable auto-snapshots
```

## Gotchas

- Scripts use strict bash settings (`set -euo pipefail`). Pipelines that can SIGPIPE are wrapped with `|| true`
- `log show` can take time and produce large files; reboot-log uses a short window by default
- Babashka doesn't expand `~` in shell commands — use absolute paths
