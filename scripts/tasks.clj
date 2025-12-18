(ns tasks
  "Task implementations for mem-debug diagnostic toolkit.
   Loaded via bb.edn :paths and called from task definitions."
  (:require [babashka.process :as p]))

;; --- Paths ---

(def root (str (System/getProperty "user.home") "/mem-debug"))
(def snaps (str root "/snaps"))
(def launch-agents (str (System/getProperty "user.home") "/Library/LaunchAgents"))

;; --- Core snapshots ---

(defn ^:export snap! []
  (p/shell (str root "/bin/memsnap-lite")))

(defn ^:export heavy! []
  (p/shell (str root "/bin/memsnap-heavy")))

(defn ^:export reboot-log! []
  (p/shell (str root "/bin/memsnap-reboot-log")))

(defn ^:export latest []
  (p/shell (str root "/bin/memsnap-latest")))

(defn ^:export summarize! []
  (p/shell "bash" "-lc"
         (str "f=$(" root "/bin/memsnap-latest 2>/dev/null || true); "
              "if [ -n \"$f\" ]; then "
              root "/bin/memsnap-grep "
              "\"swapusage|memory_pressure|Filesystem|/dev|pageouts|compress|VM|GPU|panic|watchdog|jetsam\" "
              "\"$f\"; "
              "else echo \"No snaps found in " root "/snaps\"; fi")))

(defn ^:export grep-snaps!
  ([] (grep-snaps! nil nil))
  ([pattern] (grep-snaps! pattern nil))
  ([pattern glob]
   (let [pattern (or pattern "swapusage|memory_pressure|Filesystem|/dev|pageouts|compress|VM|GPU|panic|watchdog|jetsam|memorystatus")
         glob (or glob "*.txt")]
     (p/shell "bash" "-c"
            (str "cd " snaps " && "
                 "egrep -n '" pattern "' " glob " 2>/dev/null | "
                 "egrep -v 'Ignoring GPU update because this process is not GPU managed' | "
                 "head -200")))))

;; --- Menu tasks: live + historical diagnostics ---

(defn ^:export menu:panic! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT (last 10m) ===' && "
              "(log show --last 10m --predicate 'eventMessage CONTAINS \"panic\" OR eventMessage CONTAINS \"watchdog\" OR eventMessage CONTAINS \"jetsam\" OR eventMessage CONTAINS \"GPU Restart\" OR eventMessage CONTAINS \"hang\"' 2>/dev/null | "
              "egrep -v 'AppleLOM.Watchdog|corespeech' | head -50 || echo '(no recent events)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'panic|watchdog|jetsam|memorystatus|vm_pageout|GPU Restart|IOMFB|WindowServer|hang|stall|killed' reboot-*.txt 2>/dev/null | "
              "egrep -v 'Ignoring GPU update|not memory-managed|AppleLOM.Watchdog|corespeech' | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:reboot! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT (shutdown cause) ===' && "
              "(log show --last 30m --predicate 'eventMessage CONTAINS \"Previous shutdown cause\" OR eventMessage CONTAINS \"panic\" OR eventMessage CONTAINS \"BSD process name\"' 2>/dev/null | head -20 || echo '(no recent events)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'panic|Userspace watchdog|watchdogd|Previous shutdown cause|BSD process name' reboot-*.txt 2>/dev/null | "
              "egrep -v 'AppleLOM.Watchdog' | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:jetsam! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT (last 10m) ===' && "
              "(log show --last 10m --predicate 'eventMessage CONTAINS \"jetsam\" OR eventMessage CONTAINS \"memorystatus\" OR eventMessage CONTAINS \"killed\" OR eventMessage CONTAINS \"vm_pressure\"' 2>/dev/null | "
              "egrep -v 'not memory-managed|is not RunningBoard jetsam' | head -50 || echo '(no recent events)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'jetsam|memorystatus|killed process|kill cause|vm_pressure|low memory|purgeable|coalition' reboot-*.txt 2>/dev/null | "
              "egrep -v 'not memory-managed|is not RunningBoard jetsam' | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:brave! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT ===' && "
              "ps aux | head -1 && "
              "(ps aux | grep -i 'Brave' | grep -v grep || echo '(Brave not running)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'Brave.*(GPU|videoencoder|jetsam|killed|suspend|resume|stalled|hang|coalition|memoryLimit|RunningBoard)' *.txt 2>/dev/null | "
              "egrep -v 'not memory-managed' | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:vscode! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT ===' && "
              "ps aux | head -1 && "
              "(ps aux | egrep -i 'Code Helper|Electron|java|clojure|node' | grep -v grep | head -30 || echo '(none running)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'Code Helper|Electron|java|clojure|node|JIT|GC|OutOfMemory|SIGKILL|killed' heavy-*.txt 2>/dev/null | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:clojure-lsp! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT ===' && "
              "ps aux | head -1 && "
              "(ps aux | egrep -i 'clojure-lsp|clj-kondo' | grep -v grep || echo '(none running)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -in 'clojure-lsp|clj-kondo' *.txt 2>/dev/null | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:insiders! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT ===' && "
              "ps aux | head -1 && "
              "ps aux | egrep -i 'Code.*Insiders|extensionHost|shared-process|ptyHost' | grep -v grep && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -in 'Code - Insiders|Code Helper.*Insiders|extensionHost|shared-process|ptyHost' *.txt 2>/dev/null | "
              "head -200 || echo '(no matches)'")))

(defn ^:export menu:electron! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT ===' && "
              "ps aux | head -1 && "
              "(ps aux | egrep -i 'Helper.*(Renderer|GPU|Plugin)|utility-network|crashpad' | grep -v 'Brave\\|Chrome' | grep -v grep | head -30 || echo '(none)') && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'Helper.*Renderer|Helper.*GPU|Helper.*Plugin|utility-network|gpu-process|crashpad' *.txt 2>/dev/null | "
              "egrep -v 'Brave|Chrome' | "
              "head -150 || echo '(no matches)'")))

(defn ^:export menu:metrics! []
  (p/shell "bash" "-c"
         (str "echo '=== CURRENT ===' && "
              "sysctl vm.swapusage && "
              "echo '---' && memory_pressure | head -5 && "
              "echo '---' && vm_stat | egrep -i 'pageout|compress|wired' && "
              "echo '---' && top -l 1 -n 0 -s 0 | grep PhysMem && "
              "echo && echo '=== HISTORICAL ===' && "
              "cd " snaps " && "
              "egrep -n 'swapusage|memory_pressure|pageouts|compress|wired|PhysMem|kern.memorystatus' lite-*.txt 2>/dev/null | "
              "head -200 || echo '(no matches)'")))

;; --- Schedule management ---

(defn ^:export schedule:install! []
  (p/shell "bash" "-c"
         (str "mkdir -p " launch-agents " && "
              "cp " root "/launchd/com.pez.memsnap-lite.plist " launch-agents "/ && "
              "cp " root "/launchd/com.pez.memsnap-heavy.plist " launch-agents "/ && "
              "launchctl unload " launch-agents "/com.pez.memsnap-lite.plist 2>/dev/null || true && "
              "launchctl unload " launch-agents "/com.pez.memsnap-heavy.plist 2>/dev/null || true && "
              "launchctl load " launch-agents "/com.pez.memsnap-lite.plist && "
              "launchctl load " launch-agents "/com.pez.memsnap-heavy.plist && "
              "echo 'Installed and loaded:' && "
              "launchctl list | grep memsnap")))

(defn ^:export schedule:uninstall! []
  (p/shell "bash" "-c"
         (str "launchctl unload " launch-agents "/com.pez.memsnap-lite.plist 2>/dev/null || true && "
              "launchctl unload " launch-agents "/com.pez.memsnap-heavy.plist 2>/dev/null || true && "
              "rm -f " launch-agents "/com.pez.memsnap-lite.plist " launch-agents "/com.pez.memsnap-heavy.plist && "
              "echo 'Uninstalled memsnap launchd jobs'")))

(defn ^:export schedule:status! []
  (p/shell "bash" "-c"
         "echo '=== Loaded jobs ===' && (launchctl list | grep memsnap || echo '(none loaded)') && echo && echo '=== Next lite run ===' && (launchctl print gui/$(id -u)/com.pez.memsnap-lite 2>/dev/null | grep -E 'state|interval|last exit' || echo '(not loaded)')"))
