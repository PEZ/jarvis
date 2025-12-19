(ns tasks
  "Task implementations for Jarvis diagnostic toolkit.
   Loaded via bb.edn :paths and called from task definitions."
  (:require [babashka.process :as p]
            [config]))

;; --- Core snapshots ---

(defn ^:export snap! []
  (p/shell (str config/root "/bin/memsnap-lite")))

(defn ^:export heavy! []
  (p/shell (str config/root "/bin/memsnap-heavy")))

(defn ^:export reboot-log! []
  (p/shell (str config/root "/bin/memsnap-reboot-log")))

(defn ^:export latest []
  (p/shell (str config/root "/bin/memsnap-latest")))

(defn ^:export summarize! []
  (p/shell "bash" "-lc"
           (str "f=$(" config/root "/bin/memsnap-latest 2>/dev/null || true); "
                "if [ -n \"$f\" ]; then "
                config/root "/bin/memsnap-grep "
                "\"swapusage|memory_pressure|Filesystem|/dev|pageouts|compress|VM|GPU|panic|watchdog|jetsam\" "
                "\"$f\"; "
                "else echo \"No snaps found in " config/root "/snaps\"; fi")))

(defn ^:export grep-snaps!
  ([] (grep-snaps! nil nil))
  ([pattern] (grep-snaps! pattern nil))
  ([pattern glob]
   (let [pattern (or pattern "swapusage|memory_pressure|Filesystem|/dev|pageouts|compress|VM|GPU|panic|watchdog|jetsam|memorystatus")
         glob (or glob "*.txt")]
     (p/shell "bash" "-c"
              (str "cd " config/snaps " && "
                   "egrep -n '" pattern "' " glob " 2>/dev/null | "
                   "egrep -v 'Ignoring GPU update because this process is not GPU managed' | "
                   "head -200")))))

;; --- Schedule management ---

(defn ^:export schedule-install! []
  (p/shell "bash" "-c"
           (str "mkdir -p " config/launch-agents " && "
                "cp " config/root "/launchd/com.jarvis.memsnap-lite.plist " config/launch-agents "/ && "
                "cp " config/root "/launchd/com.jarvis.memsnap-heavy.plist " config/launch-agents "/ && "
                "launchctl unload " config/launch-agents "/com.jarvis.memsnap-lite.plist 2>/dev/null || true && "
                "launchctl unload " config/launch-agents "/com.jarvis.memsnap-heavy.plist 2>/dev/null || true && "
                "launchctl load " config/launch-agents "/com.jarvis.memsnap-lite.plist && "
                "launchctl load " config/launch-agents "/com.jarvis.memsnap-heavy.plist && "
                "echo 'Installed and loaded:' && "
                "launchctl list | grep memsnap")))

(defn ^:export schedule-uninstall! []
  (p/shell "bash" "-c"
           (str "launchctl unload " config/launch-agents "/com.jarvis.memsnap-lite.plist 2>/dev/null || true && "
                "launchctl unload " config/launch-agents "/com.jarvis.memsnap-heavy.plist 2>/dev/null || true && "
                "rm -f " config/launch-agents "/com.jarvis.memsnap-lite.plist " config/launch-agents "/com.jarvis.memsnap-heavy.plist && "
                "echo 'Uninstalled memsnap launchd jobs'")))

(defn ^:export schedule-status! []
  (p/shell "bash" "-c"
           "echo '=== Loaded jobs ===' && (launchctl list | grep memsnap || echo '(none loaded)') && echo && echo '=== Next lite run ===' && (launchctl print gui/$(id -u)/com.jarvis.memsnap-lite 2>/dev/null | grep -E 'state|interval|last exit' || echo '(not loaded)')"))
