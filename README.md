mem-debug toolkit (Babashka tasks + small shell scripts)

Goal
----
Capture “what the kernel thought was happening” when macOS shows “Your system has run out of application memory”,
when apps get paused/unresponsive, or when the machine freezes/reboots.

This is deliberately NOT a “tweak settings until it stops” kit.
It’s a “collect comparable evidence fast, with minimal cognitive load” kit.

Design
------
- Snapshots are written to ~/mem-debug/snaps/ as timestamped text files.
- Scripts live in ~/mem-debug/bin/ and are invoked via bb tasks in bb.edn.
- The default snapshot is small (lite). Heavy snapshots are for escalation.
- Log capture is bounded because log show can be slow and huge.

Why these signals
-----------------
- sysctl vm.swapusage + memory_pressure + vm_stat (key lines) tells whether the kernel is in memory distress
  (swap exhaustion, compressor pressure, wired growth, pageouts).
- df -h / tells whether swap/logging can even succeed (low disk can make “memory” symptoms worse).
- top/ps tell which processes were large at that moment (correlation, not proof).
- log show is the best shot at seeing “who killed whom” (jetsam/memorystatus), watchdog/panic, GPU resets, etc.

Recipes (what to run when)
--------------------------
Baseline (after reboot):
  bb snap

Dialog appears / system feels off:
  bb snap

Severe incident (system very slow, app paused and won’t recover):
  bb heavy

After unexpected reboot:
  bb reboot-log
  (Then grep for: panic/watchdog/Previous shutdown cause)

Understanding the output
------------------------
Start with summarize. If it’s still too big, use grep with a tighter pattern and/or filter out spammy lines.

Examples:
  bb summarize
  bb grep "panic|watchdog|Previous shutdown cause"
  bb grep "jetsam|memorystatus|killed process"
  bb grep "GPU Restart|IOMFB|WindowServer|hang|stall"

Operational gotchas (important)
------------------------------
- The scripts use strict bash settings (set -euo pipefail). Pipelines that can SIGPIPE are wrapped with “|| true”.
- log show can take time and produce large files; reboot-log uses a short window by default.

Layout
------
~/mem-debug/
  bb.edn
  README.txt
  AGENTS.md
  bin/
    memsnap-lite
    memsnap-heavy
    memsnap-reboot-log
    memsnap-grep
    memsnap-latest
  snaps/
    lite-*.txt
    heavy-*.txt
    reboot-*.txt
