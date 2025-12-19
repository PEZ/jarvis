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

(defn- census-one-process
  "Count and show memory for one process pattern.
   Uses pgrep without -f for simple names (avoids matching substrings in paths),
   and pgrep -f for regex patterns that need full cmdline matching."
  [pattern]
  (let [;; Detect if pattern needs regex matching (contains metacharacters)
        needs-regex? (re-find #"[.*|+?\[\](){}^$\\]" pattern)
        ;; Use pgrep -f only for regex patterns
        pgrep-cmd (if needs-regex?
                    (str "pgrep -f '" pattern "'")
                    (str "pgrep " pattern))
        ;; For ps grep, use '/java ' for java to avoid javascript matches
        ps-pattern (if (= pattern "java")
                     "/java "
                     pattern)]
    (p/shell "bash" "-c"
             (str "echo '--- " pattern " ---' && "
                  "count=$(" pgrep-cmd " | wc -l | tr -d ' ') && "
                  "echo \"Count: $count\" && "
                  "if [ \"$count\" -gt 0 ]; then "
                  "ps aux | grep -E '" ps-pattern "' | grep -v grep | "
                  "awk '{print int($6/1024) \" MB  PID \" $2}' | sort -rn | head -10; "
                  "fi"))))

(defn ^:export process-census!
  "Quick census of processes. Pass process names/patterns as args, or uses defaults:
   clojure-lsp, java, Brave, Code.*Insiders"
  [& args]
  (let [processes (if (seq args) args config/default-census-processes)]
    (p/shell "bash" "-c"
             (str "echo '=== Process Census ===' && "
                  "echo \"Timestamp: $(date +%Y%m%d-%H%M%S)\" && "
                  "echo && "
                  "echo '--- Disk Space ---' && "
                  "df -h / | tail -1 | awk '{print \"Available: \" $4 \" (\" $5 \" used)\"}' && "
                  "echo && "
                  "echo '--- Memory Pressure ---' && "
                  "memory_pressure | head -3 && "
                  "echo"))
    (doseq [proc processes]
      (census-one-process proc)
      (println))))

(defn ^:export jvm-breakdown!
  "Detailed JVM process analysis: memory totals, categorization, start times."
  []
  (let [{:keys [test-related nrepl shadow-cljs]} config/jvm-categories]
    (p/shell "bash" "-c"
             (str "echo '=== JVM Process Breakdown ===' && "
                  "echo \"Timestamp: $(date +%Y%m%d-%H%M%S)\" && "
                  "echo && "
                  "echo '--- Summary ---' && "
                  "echo \"Total JVMs: $(pgrep java | wc -l | tr -d ' ')\" && "
                  "ps aux | grep '/java ' | grep -v grep | awk '{sum+=$6} END {printf \"Total Memory: %d MB (%.1f GB)\\n\", sum/1024, sum/1024/1024}' && "
                  "echo && "
                  "echo '--- By Category ---' && "
                  "echo \"Test-related (" test-related "): $(ps aux | grep '/java ' | grep -E '" test-related "' | grep -v grep | wc -l | tr -d ' ')\" && "
                  "echo \"nREPL processes: $(ps aux | grep '/java ' | grep '" nrepl "' | grep -v grep | wc -l | tr -d ' ')\" && "
                  "echo \"Shadow-cljs: $(ps aux | grep '/java ' | grep " shadow-cljs " | grep -v grep | wc -l | tr -d ' ')\" && "
                  "echo && "
                  "echo '--- All JVMs (by memory) ---' && "
                  "ps aux | grep '/java ' | grep -v grep | awk '{print int($6/1024) \" MB  PID \" $2 \"  started \" $9}' | sort -rn | head -15"))))
