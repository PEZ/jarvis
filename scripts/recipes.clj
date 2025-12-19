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
