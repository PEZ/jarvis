(ns tasks
  "Task implementations for Jarvis diagnostic toolkit.
   Loaded via bb.edn :paths and called from task definitions."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as string]
            [config]))

;; --- Capture helpers ---

(defn- timestamp []
  (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")
           (java.time.LocalDateTime/now)))

(defn- run-cmd
  "Run command, return stdout as string. Returns empty string on failure."
  [& args]
  (try
    (-> (apply p/process {:out :string :err :inherit} args)
        deref
        :out
        (or ""))
    (catch Exception _ "")))

(defn- section [label & cmd-args]
  (str "=== " label " ===\n" (string/trim (apply run-cmd cmd-args)) "\n"))

;; --- Noise filters ---

(def ^:private noise-patterns
  ["Ignoring GPU update because this process is not GPU managed"
   "Ignoring jetsam"
   "Ignoring memory limit"
   "not memory-managed"
   "WatchDogTimer"
   "corespeech"])

(defn- filter-noise [text]
  (let [lines (string/split-lines text)]
    (->> lines
         (remove (fn [line]
                   (some #(string/includes? line %) noise-patterns)))
         (string/join "\n"))))

;; --- Core snapshots ---

(defn- write-snapshot!
  "Write snapshot content to file, return the file path."
  [prefix content]
  (fs/create-dirs config/snaps)
  (let [path (str config/snaps "/" prefix "-" (timestamp) ".txt")]
    (spit path content)
    (println path)
    path))

(defn ^:export snap!
  "Capture lightweight memory snapshot."
  []
  (let [content (str (section "date" "date")
                     (section "uptime" "uptime")
                     (section "swap" "sysctl" "vm.swapusage")
                     (section "memory_pressure" "memory_pressure")
                     (section "vm_stat (key)" "bash" "-c"
                              "vm_stat | egrep 'wired|compress|free|pageouts|faults' || true")
                     (section "kern memory" "bash" "-c"
                              "sysctl kern.memorystatus_level vm.page_free_count vm.page_speculative_count 2>/dev/null || true")
                     (section "shutdown cause" "bash" "-c"
                              "log show --last 5m --predicate 'eventMessage CONTAINS \"Previous shutdown cause\"' --style compact 2>/dev/null | head -5 || true")
                     (section "disk /" "df" "-h" "/")
                     (section "top mem (head)" "bash" "-c"
                              "(top -l 1 -o mem | head -25) || true"))]
    (write-snapshot! "lite" content)))

(defn ^:export heavy!
  "Capture comprehensive memory snapshot (use during OOM)."
  []
  (let [memory-log (-> (run-cmd "bash" "-c"
                                "log show --last 10m --predicate 'eventMessage CONTAINS[c] \"memory\" AND NOT eventMessage CONTAINS \"not memory-managed\"' --style compact 2>/dev/null | head -200 || true")
                       filter-noise)
        jetsam-log (-> (run-cmd "bash" "-c"
                                "log show --last 10m --predicate 'eventMessage CONTAINS[c] \"jetsam\" AND eventMessage CONTAINS \"kill\"' --style compact 2>/dev/null | head -100 || true")
                       filter-noise)
        content (str (section "date" "date")
                     (section "uptime" "uptime")
                     (section "swap" "sysctl" "vm.swapusage")
                     (section "vm_stat" "vm_stat")
                     (section "memory_pressure" "memory_pressure")
                     (section "disk /" "df" "-h" "/")
                     (section "top mem" "bash" "-c" "(top -l 1 -o mem | head -60) || true")
                     (section "ps rss/vsz" "bash" "-c"
                              "(ps -axo pid,rss,vsz,comm | sort -nrk2 | head -60) || true")
                     (section "kern memory" "bash" "-c"
                              "sysctl kern.memorystatus_level vm.page_free_count vm.page_speculative_count 2>/dev/null || true")
                     "=== log memory last 10m ===\n" memory-log "\n"
                     "=== log jetsam last 10m ===\n" jetsam-log "\n")]
    (write-snapshot! "heavy" content)))

(defn ^:export reboot-log!
  "Capture post-reboot diagnostic logs (run within 2h of boot)."
  []
  (let [shutdown-cause (run-cmd "bash" "-c"
                                "log show --last 30m --predicate 'eventMessage CONTAINS \"Previous shutdown cause\"' --style compact 2>/dev/null | head -10 || true")
        panic-log (-> (run-cmd "bash" "-c"
                               (str "log show --last 30m --style compact --predicate '"
                                    "(eventMessage CONTAINS \"panic\") OR "
                                    "(eventMessage CONTAINS \"kernel_panic\") OR "
                                    "(eventMessage CONTAINS \"Userspace watchdog timeout\") OR "
                                    "(eventMessage CONTAINS \"jetsam\" AND eventMessage CONTAINS \"killed\") OR "
                                    "(eventMessage CONTAINS \"memorystatus\" AND eventMessage CONTAINS \"kill\") OR "
                                    "(eventMessage CONTAINS \"GPU Restart\") OR "
                                    "(process == \"ReportCrash\")' 2>/dev/null | head -500 || true"))
                      filter-noise)
        content (str "=== Previous shutdown cause ===\n" shutdown-cause "\n\n"
                     "=== Panic/crash/jetsam events ===\n" panic-log "\n")]
    (write-snapshot! "reboot" content)))

(defn ^:export latest
  "Print path to newest snapshot."
  []
  (let [files (fs/glob config/snaps "*.txt")]
    (when (seq files)
      (let [newest (->> files
                        (sort-by #(.toMillis (fs/last-modified-time %)))
                        last
                        str)]
        (println newest)
        newest))))

(defn ^:export summarize!
  "Print key metrics from newest snapshot."
  []
  (if-let [f (latest)]
    (let [content (slurp f)
          pattern #"(?i)swapusage|memory_pressure|Filesystem|/dev|pageouts|compress|VM|GPU|panic|watchdog|jetsam"]
      (println "====" f "====")
      (doseq [line (string/split-lines content)
              :when (re-find pattern line)]
        (println line)))
    (println "No snaps found in" config/snaps)))

(defn ^:export grep-snaps!
  "Search snapshots for pattern."
  ([] (grep-snaps! nil nil))
  ([pattern] (grep-snaps! pattern nil))
  ([pattern glob-pattern]
   (let [pattern (or pattern "swapusage|memory_pressure|Filesystem|/dev|pageouts|compress|VM|GPU|panic|watchdog|jetsam|memorystatus")
         re (re-pattern (str "(?i)" pattern))
         files (fs/glob config/snaps (or glob-pattern "*.txt"))]
     (doseq [f (sort files)]
       (let [content (slurp (str f))
             lines (string/split-lines content)
             matches (->> lines
                          (map-indexed (fn [i line] [(inc i) line]))
                          (filter (fn [[_ line]]
                                    (and (re-find re line)
                                         (not (some #(string/includes? line %) noise-patterns)))))
                          (take 200))]
         (when (seq matches)
           (println "====" (str f) "====")
           (doseq [[n line] matches]
             (println (str n ":") line))
           (println)))))))

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
