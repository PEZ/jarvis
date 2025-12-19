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

Quick diagnostics:
  bb menu:metrics  — swap, pressure, pageouts, wired memory (live + historical)
  bb menu:panic    — panic/watchdog/jetsam events (live logs + historical)
  bb menu:reboot   — shutdown cause analysis (live logs + historical)
  bb menu:jetsam   — kernel memory kill events (live logs + historical)

Process inspection:
  bb inspect-process <name> [flags]  — flexible process diagnostics (live + historical)

  Flags:
    --patterns <regex>   Override grep pattern (default: process name)
    --exclude <regex>    Exclude matches from output
    --events <regex>     Additional patterns for historical search
    --snapshots <type>   lite, heavy, reboot, or all (default: all)
    --limit <n>          Max lines of output (default: 150)

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
  → bb inspect-process <name> for live + historical view
  → bb grep "<process-name>" to search all snapshots

Known Process Patterns for Agents
---------------------------------
When investigating processes, use `bb inspect-process` with these recommended patterns:

**brave** (Brave browser):
```bash
bb inspect-process brave --events "GPU|jetsam|coalition|killed|videoencoder|suspend|resume|stalled|hang|memoryLimit|RunningBoard" --exclude "not memory-managed"
```
- Captures: Brave browser and all helpers (GPU, renderer, etc.)
- Typical RSS: main ~800MB, renderers 100-500MB each
- Look for: GPU helper accumulation, renderer sprawl

**clojure-lsp**:
```bash
bb inspect-process clojure-lsp --patterns "clojure-lsp|clj-kondo"
```
- Captures: language server and linter
- Typical RSS: 400-900MB each, multiple instances with VS Code windows
- Look for: instance count (should match VS Code window count)

**electron** (non-browser Electron helpers):
```bash
bb inspect-process electron --patterns "Helper.*(Renderer|GPU|Plugin)|utility-network|crashpad" --exclude "Brave|Chrome" --events "gpu-process"
```
- Captures: VS Code electron helpers without browser noise
- Look for: orphaned helpers, accumulating renderers after window closes

**insiders** (VS Code Insiders):
```bash
bb inspect-process insiders --patterns "Code.*Insiders|extensionHost|shared-process|ptyHost"
```
- Captures: VS Code Insiders specific processes
- Look for: multiple extensionHost instances, leaking helpers

**java / JVM**:
```bash
bb inspect-process java --events "GC|OutOfMemory|JIT|SIGKILL|heap|killed" --snapshots heavy
```
- Captures: java processes, JVM events, memory kills
- Typical RSS: varies widely by application
- Use --snapshots heavy: JVM details captured in heavy snapshots

**node**:
```bash
bb inspect-process node --events "heap|SIGKILL|killed"
```
- Captures: Node.js processes
- Common in: VS Code extensions, build tools

**vscode** (full ecosystem):
```bash
bb inspect-process vscode --patterns "Code Helper|Electron|java|clojure|node" --events "GC|OutOfMemory|JIT|SIGKILL|killed" --snapshots heavy
```
- Captures: full VS Code ecosystem including JVM
- Use when: investigating VS Code memory as a whole

Agent Interaction Model
-----------------------
**Philosophy**: The agent is an interactive memory diagnostics expert. Users talk to the agent in natural language, not to a command reference guide. The agent runs diagnostics, interprets results, and provides expert analysis.

**Critical**: DO NOT present CLI commands as the primary interface. Instead, offer expert assistance with numbered investigation options.

Greeting Response Pattern
-------------------------
When user greets the agent (e.g., "Hi", "Hello"), respond with:

1. **Quick health check**: Run bb menu:metrics to get current memory status
2. **Brief status summary**: Interpret current health (swap, pressure, pageouts)
3. **Numbered investigation menu**: Present common scenarios as a numbered list

**Example Greeting Response:**

```
Hello there, Mr Clojurian! 👋

I'm your memory diagnostics expert. I just checked your system:

**Current Status**: Looking healthy! ✅
- Zero swap usage (excellent)
- 62G/64GB in use with active compression
- ~44K pageouts (moderate, manageable)

**What can I help you investigate?**

1. **General health check** - Analyze memory pressure and trends
2. **System feels sluggish** - Find what's consuming RAM
3. **Process investigation** - Check clojure-lsp, VS Code, Brave, or other suspects
4. **Out of memory dialog** - Investigate what triggered it
5. **Unexpected reboot** - Analyze crash/panic logs
6. **Compare trends** - See how memory has changed over time

Just tell me a number or describe what you need!
```

User Response Handling
----------------------
Users can respond with:
- **A number**: "3" or "Check 2" → Run corresponding investigation
- **Process name**: "clojure-lsp" or "Brave" → Investigate that process
- **Natural language**: "system is slow" → Map to appropriate investigation
- **Custom request**: "compare last 6 hours" → Analyze specific timeframe

Common User Requests & Agent Actions
------------------------------------
**"Hello"** / **"Hi"** / **General greeting**
  → Run bb menu:metrics for current status
  → Present numbered investigation menu
  → Good for: Getting oriented, understanding available help

**"How's memory doing?"** / **"Check memory trends"**
  → Agent will run bb menu:metrics and interpret current vs. historical state
  → Shows: swap usage, pageouts, memory level trends
  → Good for: Daily check-ins, general curiosity

**"Check trends over last [timeframe]"** (e.g., "last half day", "overnight")
  → Agent will grep snapshots from that period and analyze progression
  → Shows: pageout growth rate, memory level changes, swap activity
  → Good for: Understanding if situation is improving/degrading

**"System is forcing me to quit apps"** / **"Got out of memory dialog"**
  → Agent will capture heavy snapshot and check recent jetsam events
  → Shows: what kernel killed, current memory state, top consumers
  → Good for: Active OOM incidents (do this BEFORE closing dialog if possible)

**"System feels sluggish"** / **"Everything is slow"**
  → Agent will check current metrics, capture snapshot, analyze top processes
  → Shows: whether it's memory pressure, which processes consuming most
  → Good for: Diagnosing performance issues

**"Mac rebooted unexpectedly"** / **"Just had a crash"**
  → Agent will capture reboot log (within 2h) and check panic/watchdog events
  → Shows: shutdown cause, panic logs, what triggered the reboot
  → Good for: Post-mortem after unexpected restarts

**"How's [process] doing?"** (e.g., "How's clojure-lsp doing?", "Check Brave")
  → Agent will run bb inspect-process with appropriate patterns (see Known Process Patterns)
  → Shows: current instances, RSS usage, historical patterns
  → Good for: Tracking specific suspects

**"What changed since yesterday?"** / **"Compare with baseline"**
  → Agent will compare latest snapshot with baseline/earlier snapshots
  → Shows: process count changes, memory consumption deltas
  → Good for: Identifying what's different after updates/restarts

**"Take a snapshot"** / **"Capture current state"**
  → Agent will run bb snap (or bb heavy if situation is severe)
  → Creates timestamped snapshot for future comparison
  → Good for: Preserving state at specific moments

**"Is this normal?"** (with context about pageouts/swap/etc.)
  → Agent will interpret metrics against expected baselines
  → Shows: whether values indicate healthy vs. problematic state
  → Good for: Understanding if you should be concerned

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

Architecture Overview
---------------------
The toolkit uses a three-layer architecture:

**1. Data Capture Layer** (bash scripts in bin/)
- memsnap-lite: Minimal overhead baseline (~6KB, automated every 6h)
- memsnap-heavy: Comprehensive diagnostic (~50-500KB, daily 3am + manual)
- memsnap-reboot-log: Post-mortem analysis (~70-700KB, manual within 2h of boot)

**2. Task Orchestration Layer** (Babashka)
- Thin wrappers around bash scripts (tasks.clj)
- Nice CLI interface without impedance mismatch
- Absolute path handling for portability

**3. Analysis Layer** (menu tasks)
- Pattern: CURRENT (live system state) + HISTORICAL (grep snapshots)
- Bounded output (head -150/-200) prevents overflow
- No snapshot modification (read-only analysis)

Key Strategies
--------------
- **Strict error handling**: `set -euo pipefail` in all bash scripts
- **Pipeline truncation safety**: `(cmd | head -N) || true` for SIGPIPE handling
- **Bounded output**: head limits everywhere prevent runaway output under stress
- **Noise filtering**: Exclude high-volume/low-signal patterns (AppleLOM.Watchdog, etc.)
- **Short time windows**: 5m/10m/30m for log show queries (prevents slow queries)
- **Structured headers**: `=== keyword ===` enables grep-based analysis
- **Timestamped filenames**: Self-documenting chronology, no database needed
- **Absolute paths**: Babashka doesn't expand `~`, use (System/getProperty "user.home")

Agent Usage Guidelines
----------------------
**Understanding the toolkit**:
- menu:* tasks are READ-ONLY — they show live state + grep historical snapshots
- menu:* tasks do NOT capture new snapshots
- Only bb snap/heavy/reboot-log (and scheduled jobs) create snapshots
- All snapshots land in ~/mem-debug/snaps/ with timestamp filenames

**Effective workflow for agents**:
1. Use bb menu:metrics first for quick live+historical overview
2. Use bb grep "pattern" to search across all snapshots efficiently
3. Use bb summarize to extract key metrics from newest snapshot
4. When in doubt about current state, run live commands (sysctl, vm_stat, ps aux)
5. Don't manually snapshot unless user requests or incident occurs

**Common mistakes to avoid**:
- Don't expect menu tasks to create snapshots (they're analysis only)
- Don't run `bb snap` unnecessarily (scheduled jobs handle baselines)
- Don't forget about the scheduled automation (check bb schedule:status)
- Don't ignore section headers in snapshots (they enable targeted grepping)
- Don't use relative paths in Babashka shell commands (always absolute)

**Pattern recognition**:
- Zero swap + moderate pageouts = healthy memory management
- Swap > 10GB on 64GB Mac = leak or memory hog
- memorystatus_level < 20 = critical pressure
- Pageout rate matters more than absolute count (compare snapshots)
- Multiple clojure-lsp instances × ~600MB each = significant contributor

**Interpreting trends**:
- Compare snapshots chronologically to see memory pressure evolution
- Look for process accumulation (e.g., 6 clojure-lsp vs. 1)
- Pageout growth rate indicates active pressure vs. normal paging
- Memory level trending down + pageouts increasing = pressure building
- Memory level trending up = system successfully freeing memory

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
