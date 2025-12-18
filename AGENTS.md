AGENTS.md — maintenance notes for automation / AI agents

Purpose
-------
This repository-like folder is a self-contained diagnostic harness for macOS memory/pressure/freezes/reboots.
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
4) Avoid dependence on user’s shell configuration (PATH, aliases, functions).

Known pitfalls
--------------
- Tilde expansion:
  Babashka’s (shell "...") does not expand "~". Use absolute paths from (System/getProperty "user.home")
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

What “success” looks like
-------------------------
A good incident set contains:
- one baseline lite snap after reboot
- one lite snap near the dialog event (or just before)
- optionally one heavy snap during severe slowdown
- if rebooted: one reboot log snap
These allow comparisons: swap growth, memory_pressure state, wired growth, pageouts, and relevant OS events.

What to grep for (high-signal)
------------------------------
- Root cause of reboot:
  "Previous shutdown cause" | "panic" | "watchdog" | "Userspace watchdog" | "BSD process name"

- Memory kill / jetsam:
  "jetsam" | "memorystatus" | "killed process" | "kill cause" | "vm_pressure"

- GPU / display hangs:
  "GPU Restart" | "IOMFB" | "WindowServer" | "hang" | "stall"

Noise filters
-------------
Some logs are high-volume but low-signal (example: "Ignoring GPU update because this process is not GPU managed").
When summarizing reboot logs, it is acceptable to post-filter with `egrep -v` to drop these lines.

Change policy
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

If bb tasks “error” but files are created, suspect strict bash mode + pipeline exit status.
