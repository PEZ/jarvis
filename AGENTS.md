AGENTS.md — maintenance notes for automation / AI agents

Problem Statement
-----------------
**Primary symptom**: macOS dialog "Your system has run out of application memory" on a 64GB Mac.
**Suspected causes** (ranked by current evidence):
1. VS Code Insiders — multiple windows, frequent restarts may leak processes
2. clojure-lsp — one instance per VS Code window with Calva active (~500MB-900MB each)
3. Brave browser — GPU helpers, many renderer processes
4. Electron helper sprawl — orphaned GPU/Renderer processes

**Goal**: Correlate memory pressure events with process snapshots to identify the culprit(s).

Task Inventory
--------------
Core snapshots (write to ~/mem-debug/snaps/):
  bb snap          — lite snapshot, use for "feels off" moments
  bb heavy         — detailed snapshot with logs, use during severe slowdown
  bb reboot-log    — post-reboot panic/watchdog logs

Analysis:
  bb latest        — print newest snapshot path
  bb summarize     — key metrics from newest snapshot
  bb grep PATTERN  — search all snapshots

Quick diagnostics (menu:* tasks show CURRENT + HISTORICAL):
  bb menu:metrics      — swap, pressure, pageouts, wired memory
  bb menu:clojure-lsp  — all clojure-lsp processes and memory
  bb menu:insiders     — VS Code Insiders processes
  bb menu:vscode       — VS Code / JVM / Node processes
  bb menu:electron     — Electron/Chromium helpers (non-browser)
  bb menu:brave        — Brave browser processes
  bb menu:panic        — panic/watchdog/jetsam events
  bb menu:reboot       — shutdown cause analysis
  bb menu:jetsam       — kernel memory kill events

Scheduling:
  bb schedule:install    — enable automatic snapshots (lite 6h, heavy daily 3am)
  bb schedule:uninstall  — disable automatic snapshots
  bb schedule:status     — check if scheduled jobs are running

Diagnostic Decision Tree
------------------------
"System feels sluggish":
  → bb menu:metrics (check swap/pressure)
  → bb snap (capture state)
  → bb menu:clojure-lsp (count instances, check RSS)

"Out of memory dialog appeared":
  → bb heavy (immediate, before closing dialog if possible)
  → bb menu:jetsam (check what kernel killed)

"Mac rebooted unexpectedly":
  → bb reboot-log (within 2 hours of boot)
  → bb menu:reboot (check shutdown cause)
  → bb menu:panic (look for watchdog/panic)

"Investigating a suspect process":
  → bb menu:<suspect> for live + historical view
  → bb grep "<process-name>" to search all snapshots

Interpreting Key Metrics
------------------------
vm.swapusage:
  - used > 10GB with 64GB RAM = something is leaking or hoarding
  - rapid growth between snaps = active leak

memory_pressure:
  - "System-wide memory free percentage: X%"
  - Below 10% = danger zone, kernel will start killing

kern.memorystatus_level:
  - 0-100 scale, lower = more pressure
  - Below 20 = critical

Pageouts (from vm_stat):
  - High pageout rate = RAM exhausted, swapping actively
  - Compare between snapshots to see rate of change

Process RSS (from ps/top):
  - Resident Set Size = actual RAM used
  - clojure-lsp: expect 400-900MB each, 6 instances = 4GB+
  - Code Helper (Renderer): 100-500MB each, can accumulate

Purpose
-------
This folder is a self-contained diagnostic harness for macOS memory/pressure/freezes/reboots.
It prioritizes:
- repeatability (snapshots are comparable over time),
- low human attention cost (few tasks, clear recipes),
- safe execution under stress (write to file, avoid interactive tools),
- bounded output (cap grep output; cap log windows).

Invariants (do not break)
-------------------------
1) Output always goes to: ~/mem-debug/snaps/
2) Commands are stable entry points:
   - memsnap-lite (default)
   - memsnap-heavy (escalation)
   - memsnap-reboot-log (post-reboot)
   - memsnap-grep (analysis helper)
   - memsnap-latest
3) bb.edn tasks remain short aliases for those scripts.
4) Avoid dependence on user's shell configuration (PATH, aliases, functions).

Known Pitfalls
--------------
- Tilde expansion:
  Babashka's (shell "...") does not expand "~". Use absolute paths from (System/getProperty "user.home")
  or run via (shell "bash" "-lc" "...") when shell semantics are required.

- pipefail + head:
  macOS commands like `top | head` may return non-zero due to SIGPIPE when head closes early.
  With set -euo pipefail this aborts the script even if output is written.
  Therefore pipelines that are intentionally truncated MUST be wrapped:
    (cmd | head -N) || true

- Unified logging performance:
  `log show` can be slow and generate huge output.
  Keep default windows short (e.g. 10m for incident context, 30m for reboot context).
  Prefer tighter predicates rather than longer windows.

What "Success" Looks Like
-------------------------
A good incident set contains:
- one baseline lite snap after reboot
- one lite snap near the dialog event (or just before)
- optionally one heavy snap during severe slowdown
- if rebooted: one reboot log snap
These allow comparisons: swap growth, memory_pressure state, wired growth, pageouts, and relevant OS events.

High-Signal Grep Patterns
-------------------------
Root cause of reboot:
  "Previous shutdown cause" | "panic" | "watchdog" | "Userspace watchdog" | "BSD process name"

Memory kill / jetsam:
  "jetsam" | "memorystatus" | "killed process" | "kill cause" | "vm_pressure"

GPU / display hangs:
  "GPU Restart" | "IOMFB" | "WindowServer" | "hang" | "stall"

Noise Filters
-------------
Some logs are high-volume but low-signal (example: "Ignoring GPU update because this process is not GPU managed").
When summarizing reboot logs, it is acceptable to post-filter with `egrep -v` to drop these lines.

Change Policy
-------------
- Prefer adding new scripts over modifying existing ones if behavior changes materially.
- If modifying existing scripts, keep output format stable (headers like "=== swap ===") so greps keep working.
- Keep the default tasks stable; introduce new bb tasks only when there is a clear new recipe.

Testing (after edits)
---------------------
Run these and ensure they return exit code 0 and create files:
  bb snap
  bb heavy
  bb reboot-log
  bb latest
  bb summarize
  bb grep "panic|watchdog"

If bb tasks "error" but files are created, suspect strict bash mode + pipeline exit status.
