# Jarvis Battle Log

Victories in the battle against "Your system has run out of application memory" on a 64GB Mac.

---

## Battle #1: The Phantom JVMs

**Date**: December 19, 2025
**Status**: ✅ VICTORY — Root cause identified

### Symptoms
- Sporadic macOS OOM dialogs despite 64GB RAM
- No obvious memory hog in Activity Monitor
- Problem disappeared when stopping Calva integration test runs

### Investigation

**Hypothesis formation**:
1. User noticed OOM stopped when they freed disk space (80GB → 100GB free)
2. User noticed OOM stopped when they stopped running Calva integration tests many times a day

**Controlled experiment**:

| Phase | JVM Count | Notes |
|-------|-----------|-------|
| Baseline | 10 | Normal working state |
| After 1 test run | 13 | +3 JVMs |
| After 4-5 test runs | 25 | +15 JVMs, 5.4GB total |
| 10 minutes later | 4 | Orphans eventually cleaned up |

**Key insight**: JVMs spawned by Calva jack-in tests linger for ~5-10 minutes before cleanup. Rapid test iterations accumulate JVMs faster than cleanup occurs.

### Root Cause

**Primary**: Calva integration tests spawn JVMs (for jack-in testing) that don't terminate immediately. During intensive test sessions, these accumulate.

**Contributing factor**: Low disk space (~80GB free on 64GB Mac) left no room for swap files. When memory pressure hit, macOS had no escape valve → OOM dialog.

### Resolution

1. **Immediate**: Keep disk space above 100GB free
2. **Pending**: Investigate Calva test teardown to improve JVM cleanup timing
3. **Monitoring**: Use `bb recipe:jvms` during test runs to watch for accumulation

### Tools Used
- `bb recipe:census` — quick process counts
- `bb recipe:jvms` — JVM memory breakdown and categorization
- Manual `ps aux` analysis for start times and classpaths

### Lessons Learned

1. **Delayed cleanup ≠ leak** — processes that clean up eventually can still cause OOM if creation outpaces cleanup
2. **Disk space matters** — on high-RAM Macs, swap is the pressure release valve; no disk = no valve
3. **Test infrastructure** — integration tests that spawn external processes need aggressive cleanup
4. **Pre-existing condition** — verified same orphan behavior in released Calva; not a regression from feature branch. More tests = more churn = faster accumulation.

---

*"The first rule of debugging is to actually look at what's happening, not what you think is happening."*
