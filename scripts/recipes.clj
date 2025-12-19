(ns recipes
  "Canned diagnostic recipes: live + historical queries."
  (:require [babashka.process :as p]
            [config]))

(defn ^:export panic! []
  (p/shell "bash" "-c"
           (str "echo '=== CURRENT (last 10m) ===' && "
                "(log show --last 10m --predicate 'eventMessage CONTAINS \"panic\" OR eventMessage CONTAINS \"watchdog\" OR eventMessage CONTAINS \"jetsam\" OR eventMessage CONTAINS \"GPU Restart\" OR eventMessage CONTAINS \"hang\"' 2>/dev/null | "
                "egrep -v 'AppleLOM.Watchdog|corespeech' | head -50 || echo '(no recent events)') && "
                "echo && echo '=== HISTORICAL ===' && "
                "cd " config/snaps " && "
                "egrep -n 'panic|watchdog|jetsam|memorystatus|vm_pageout|GPU Restart|IOMFB|WindowServer|hang|stall|killed' reboot-*.txt 2>/dev/null | "
                "egrep -v 'Ignoring GPU update|not memory-managed|AppleLOM.Watchdog|corespeech' | "
                "head -150 || echo '(no matches)'")))

(defn ^:export reboot! []
  (p/shell "bash" "-c"
           (str "echo '=== CURRENT (shutdown cause) ===' && "
                "(log show --last 30m --predicate 'eventMessage CONTAINS \"Previous shutdown cause\" OR eventMessage CONTAINS \"panic\" OR eventMessage CONTAINS \"BSD process name\"' 2>/dev/null | head -20 || echo '(no recent events)') && "
                "echo && echo '=== HISTORICAL ===' && "
                "cd " config/snaps " && "
                "egrep -n 'panic|Userspace watchdog|watchdogd|Previous shutdown cause|BSD process name' reboot-*.txt 2>/dev/null | "
                "egrep -v 'AppleLOM.Watchdog' | "
                "head -150 || echo '(no matches)'")))

(defn ^:export jetsam! []
  (p/shell "bash" "-c"
           (str "echo '=== CURRENT (last 10m) ===' && "
                "(log show --last 10m --predicate 'eventMessage CONTAINS \"jetsam\" OR eventMessage CONTAINS \"memorystatus\" OR eventMessage CONTAINS \"killed\" OR eventMessage CONTAINS \"vm_pressure\"' 2>/dev/null | "
                "egrep -v 'not memory-managed|is not RunningBoard jetsam' | head -50 || echo '(no recent events)') && "
                "echo && echo '=== HISTORICAL ===' && "
                "cd " config/snaps " && "
                "egrep -n 'jetsam|memorystatus|killed process|kill cause|vm_pressure|low memory|purgeable|coalition' reboot-*.txt 2>/dev/null | "
                "egrep -v 'not memory-managed|is not RunningBoard jetsam' | "
                "head -150 || echo '(no matches)'")))

(defn ^:export metrics! []
  (p/shell "bash" "-c"
           (str "echo '=== CURRENT ===' && "
                "sysctl vm.swapusage && "
                "echo '---' && memory_pressure | head -5 && "
                "echo '---' && vm_stat | egrep -i 'pageout|compress|wired' && "
                "echo '---' && top -l 1 -n 0 -s 0 | grep PhysMem && "
                "echo && echo '=== HISTORICAL ===' && "
                "cd " config/snaps " && "
                "egrep -n 'swapusage|memory_pressure|pageouts|compress|wired|PhysMem|kern.memorystatus' lite-*.txt 2>/dev/null | "
                "head -200 || echo '(no matches)'")))

(defn ^:export process-census!
  "Quick census of suspected memory-hoarding processes."
  []
  (p/shell "bash" "-c"
           (str "echo '=== Process Census ===' && "
                "echo \"Timestamp: $(date +%Y%m%d-%H%M%S)\" && "
                "echo && "
                "echo '--- Disk Space ---' && "
                "df -h / | tail -1 | awk '{print \"Available: \" $4 \" (\" $5 \" used)\"}' && "
                "echo && "
                "echo '--- clojure-lsp (count + memory) ---' && "
                "echo \"Count: $(pgrep -f clojure-lsp | wc -l | tr -d ' ')\" && "
                "ps aux | grep clojure-lsp | grep -v grep | awk '{sum+=$6; print int($6/1024) \" MB  PID \" $2}' | sort -rn && "
                "echo && "
                "echo '--- Java/JVM (count + memory) ---' && "
                "echo \"Count: $(pgrep -f java | wc -l | tr -d ' ')\" && "
                "ps aux | grep java | grep -v grep | awk '{sum+=$6; print int($6/1024) \" MB  PID \" $2}' | sort -rn | head -10 && "
                "echo && "
                "echo '--- VS Code Helpers (count) ---' && "
                "echo \"Renderer: $(pgrep -f 'Code.*Helper.*Renderer' | wc -l | tr -d ' ')\" && "
                "echo \"GPU: $(pgrep -f 'Code.*Helper.*GPU' | wc -l | tr -d ' ')\" && "
                "echo \"Plugin: $(pgrep -f 'Code.*Helper.*Plugin' | wc -l | tr -d ' ')\" && "
                "echo \"ExtensionHost: $(pgrep -f extensionHost | wc -l | tr -d ' ')\" && "
                "echo && "
                "echo '--- Memory Pressure ---' && "
                "memory_pressure | head -3")))

(defn ^:export jvm-breakdown!
  "Detailed JVM process analysis: memory totals, categorization, start times."
  []
  (p/shell "bash" "-c"
           (str "echo '=== JVM Process Breakdown ===' && "
                "echo \"Timestamp: $(date +%Y%m%d-%H%M%S)\" && "
                "echo && "
                "echo '--- Summary ---' && "
                "echo \"Total JVMs: $(pgrep -f java | wc -l | tr -d ' ')\" && "
                "ps aux | grep java | grep -v grep | awk '{sum+=$6} END {printf \"Total Memory: %d MB (%.1f GB)\\n\", sum/1024, sum/1024/1024}' && "
                "echo && "
                "echo '--- By Category ---' && "
                "echo \"Test-related (test-data|integration-test): $(ps aux | grep java | grep -E 'test-data|integration-test' | grep -v grep | wc -l | tr -d ' ')\" && "
                "echo \"nREPL processes: $(ps aux | grep 'nrepl.cmdline' | grep -v grep | wc -l | tr -d ' ')\" && "
                "echo \"Shadow-cljs: $(ps aux | grep java | grep shadow-cljs | grep -v grep | wc -l | tr -d ' ')\" && "
                "echo && "
                "echo '--- All JVMs (by memory) ---' && "
                "ps aux | grep java | grep -v grep | awk '{print int($6/1024) \" MB  PID \" $2 \"  started \" $9}' | sort -rn | head -15")))
