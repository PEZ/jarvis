# Jarvis — Your Memory Diagnostics Partner

An AI-agent-centric toolkit for diagnosing macOS memory pressure. You talk to Jarvis, Jarvis runs diagnostics, interprets results, and provides expert analysis.

## Prerequisites

- **[Babashka](https://babashka.org/)** — The `bb` command runs all Jarvis tasks
  ```sh
  brew install borkdude/brew/babashka
  ```
- **VS Code** (or Cursor) — With an AI agent that has terminal access
- **GitHub Copilot** (or similar) — The AI that becomes Jarvis

The toolkit is currently macOS-centric (memory_pressure, vm_stat, log show). If you're on Linux, tell Jarvis — it can adapt the diagnostic scripts to use `/proc/meminfo`, `dmesg`, and other Linux equivalents.

## Working with Jarvis

**Just say hi.** Open a chat with your AI agent in the Jarvis workspace and greet it. Jarvis will check your system health, interpret what it finds, and offer investigation options.

**Describe your situation.** Tell Jarvis what you're experiencing — OOM dialogs, sluggishness, unexpected reboots. Jarvis will choose the right diagnostics and explain what it finds.

**Ask questions.** "What's eating my memory?" "Is clojure-lsp leaking?" "What happened before the last reboot?" Jarvis has tools for all of these.

**Let Jarvis evolve the toolkit.** When Jarvis notices patterns or gaps, it will propose new recipes or improvements. The toolkit adapts to your specific needs.

## Background

This toolkit was born from a specific frustration: macOS "Your system has run out of application memory" dialogs on a 64GB Mac that shouldn't run out of memory.

The current focus reflects the original author's context — VS Code extension development with heavy Clojure/JVM usage. That's why you'll see process patterns for VS Code, clojure-lsp, Electron helpers, and JVM processes baked in.

**If you're picking up this toolkit**: Start by telling Jarvis about *your* environment. The agent will help adapt the investigation patterns, process baselines, and suspects list to match your workload. The toolkit is designed to evolve with each user's specific situation.

**Customizing for your environment**: Edit `scripts/config.clj` to adapt Jarvis to your situation:
- `default-census-processes` — Which processes to monitor by default
- `noise-patterns` — Log noise to filter out
- `jvm-categories` — Patterns for categorizing JVM processes

**Current suspects** (for the original author's context):
1. **VS Code Insiders** — multiple windows, process leaks on restart
2. **clojure-lsp** — ~500-900MB per instance, one per Calva window
3. **Brave browser** — GPU helpers, renderer sprawl
4. **Electron orphans** — helpers that outlive their windows

## Philosophy

This is deliberately NOT a "tweak settings until it stops" kit.
It's a "collect comparable evidence fast, with minimal cognitive load" kit.

The goal: **Correlate memory events with snapshots to find the culprit(s).**

## What to Tell Jarvis

**After a reboot**: "I just rebooted, capture a baseline."

**System feels off**: "Things feel sluggish, check memory health."

**OOM dialog appeared**: "I'm seeing the out of memory dialog — capture everything before I dismiss it."

**After unexpected reboot**: "My Mac rebooted unexpectedly, what happened?"

**Investigating a suspect**: "Is Brave hogging memory?" or "How many clojure-lsp instances are running?"

**Setting up monitoring**: "Enable automatic snapshots so we have history."

## Design

- Snapshots are written to `~/jarvis/snaps/` as timestamped text files
- Task implementations live in `scripts/*.clj`
- The default snapshot is small (lite). Heavy snapshots are for escalation
- Log capture is bounded because `log show` can be slow and huge

## Interpreting Results

**Healthy signs**:
- Zero swap usage
- `memorystatus_level` > 50
- Moderate pageouts (growth rate matters, not absolute)

**Warning signs**:
- Swap > 10GB on 64GB Mac → something hoarding/leaking
- `memorystatus_level` < 20 → critical pressure
- Rapid pageout growth between snapshots → active pressure

**Process baselines**:
- clojure-lsp: 400-900MB each (count should match VS Code windows)
- Code Helper (Renderer): 100-500MB each (watch for accumulation)
- Brave main: ~800MB, renderers: 100-500MB each

## Why These Signals

- `sysctl vm.swapusage` + `memory_pressure` + `vm_stat` tells whether the kernel is in memory distress (swap exhaustion, compressor pressure, wired growth, pageouts)
- `df -h /` tells whether swap/logging can even succeed (low disk can make "memory" symptoms worse)
- `top`/`ps` tell which processes were large at that moment (correlation, not proof)
- `log show` is the best shot at seeing "who killed whom" (jetsam/memorystatus), watchdog/panic, GPU resets, etc.

## What Jarvis Can Do

**Health checks**: Jarvis monitors swap usage, memory pressure, compressor activity, and pageout rates — and explains what they mean.

**Snapshots**: Jarvis captures system state for later analysis. Lightweight snapshots for quick checks, heavy snapshots during incidents.

**Process investigation**: Jarvis can dig into specific processes — memory usage, instance counts, kernel events, crash history.

**Historical analysis**: Jarvis searches across all captured snapshots to find patterns and correlate events.

**Post-mortem**: After crashes or reboots, Jarvis examines system logs for panic events, jetsam kills, and watchdog timeouts.

## Tasks

Jarvis' toolkit is built around [Babashka Tasks](https://book.babashka.org/#tasks). You can use them yourself too, of course 😀. List available tasks with:

```sh
bb tasks
```

## Automatic Monitoring

Ask Jarvis to enable scheduled snapshots. This gives you historical data to analyze when incidents occur — invaluable for catching slow leaks or correlating events across days.
