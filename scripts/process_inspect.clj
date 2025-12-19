(ns process-inspect
  "Process inspection: live ps + historical snapshot grep."
  (:require [babashka.cli :as cli]
            [babashka.process :as p]
            [config]))

(def ^:private process-spec
  "CLI spec for inspect-process command."
  {:patterns  {:desc "Override grep pattern (default: process name)"}
   :exclude   {:desc "Exclude matches from output"}
   :events    {:desc "Additional patterns for historical search"}
   :snapshots {:desc "lite, heavy, reboot, or all" :default "all"}
   :limit     {:desc "Max lines of output" :coerce :int :default 150}})

(defn- snapshot-glob
  "Return glob pattern for snapshot type."
  [snapshot-type]
  (case snapshot-type
    "lite"   "lite-*.txt"
    "heavy"  "heavy-*.txt"
    "reboot" "reboot-*.txt"
    "all"    "*.txt"
    "*.txt"))

(defn- parse-process-args
  "Parse CLI args into config map. Pure function."
  [args]
  (let [{[process] :args :keys [opts]} (cli/parse-args args {:spec process-spec})]
    (assoc opts :process process)))

(defn- build-process-cmd
  "Build shell command string for process inspection. Pure function."
  [{:keys [process patterns exclude events snapshots limit]}]
  (let [pattern (or patterns process)
        glob (snapshot-glob snapshots)
        ps-cmd (str "ps aux | head -1 && "
                    "(ps aux | egrep -i '" pattern "' | grep -v grep"
                    (when exclude (str " | egrep -v '" exclude "'"))
                    " || echo '(none running)')")
        hist-pattern (if events
                       (str pattern "|" events)
                       pattern)
        hist-cmd (str "cd " config/snaps " && "
                      "egrep -in '" hist-pattern "' " glob " 2>/dev/null"
                      (when exclude (str " | egrep -v '" exclude "'"))
                      " | head -" limit
                      " || echo '(no matches)'")]
    (str "echo '=== CURRENT ===' && "
         ps-cmd " && "
         "echo && echo '=== HISTORICAL ===' && "
         hist-cmd)))

(defn ^:export inspect!
  "Generic process inspection. Usage: bb inspect-process <name> [flags]

   Examples:
     bb inspect-process java
     bb inspect-process brave --exclude Chrome
     bb inspect-process electron --patterns 'Helper.*(Renderer|GPU)' --exclude 'Brave|Chrome'

   Flags:
     --patterns <regex>   - Override grep pattern (default: process name)
     --exclude <regex>    - Exclude matches from output
     --events <regex>     - Additional patterns for historical search
     --snapshots <type>   - lite, heavy, reboot, or all (default: all)
     --limit <n>          - Max lines of output (default: 150)"
  [args]
  (let [{:keys [process patterns] :as config} (parse-process-args args)]
    (if-not (or patterns process)
      (do (println "Usage: bb inspect-process <name> [--patterns <regex>] [--exclude <regex>] [--events <regex>] [--snapshots <type>] [--limit <n>]")
          (System/exit 1))
      (p/shell "bash" "-c" (build-process-cmd config)))))

^:rct/test
(comment
  ;; parse-process-args tests

  ;; Basic process name with defaults
  (parse-process-args ["java" "--limit" "10"])
  ;=> {:snapshots "all", :limit 10, :process "java"}

  ;; Multiple flags parsed correctly
  (parse-process-args ["electron" "--patterns" "Helper.*(Renderer|GPU)"
                       "--exclude" "Brave|Chrome" "--limit" "20"])
  ;=> {:snapshots "all", :limit 20, :patterns "Helper.*(Renderer|GPU)",
  ;    :exclude "Brave|Chrome", :process "electron"}

  ;; Snapshot filtering
  (parse-process-args ["clojure-lsp" "--snapshots" "heavy"])
  ;=> {:snapshots "heavy", :limit 150, :process "clojure-lsp"}

  ;; snapshot-glob tests
  (snapshot-glob "lite")   ;=> "lite-*.txt"
  (snapshot-glob "heavy")  ;=> "heavy-*.txt"
  (snapshot-glob "reboot") ;=> "reboot-*.txt"
  (snapshot-glob "all")    ;=> "*.txt"
  (snapshot-glob "bogus")  ;=> "*.txt"

  ;; build-process-cmd tests

  ;; Basic command structure
  (build-process-cmd {:process "clojure-lsp" :snapshots "heavy" :limit 50})
  ;=>> #"echo '=== CURRENT ===' && ps aux.*clojure-lsp.*=== HISTORICAL ===.*heavy-\*\.txt.*head -50"

  ;; Events pattern extends historical search
  (build-process-cmd {:process "java" :events "GC|OutOfMemory" :snapshots "heavy" :limit 30})
  ;=>> #"java\|GC\|OutOfMemory.*heavy-\*\.txt.*head -30"

  ;; Exclude filter applied to both ps and grep
  (build-process-cmd {:process "brave" :exclude "Chrome" :limit 20 :snapshots "all"})
  ;=>> #"egrep -v 'Chrome'.*egrep -v 'Chrome'"

  ;; End-to-end: parse -> build
  (-> ["brave" "--exclude" "Chrome" "--events" "GPU|jetsam" "--snapshots" "lite"]
      parse-process-args
      build-process-cmd))
  ;=>> #"brave.*egrep -v 'Chrome'.*brave\|GPU\|jetsam.*lite-\*\.txt"
