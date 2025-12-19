(ns config
  "Shared configuration for Jarvis tasks.")

(def root (str (System/getProperty "user.home") "/jarvis"))
(def snaps (str root "/snaps"))
(def launch-agents (str (System/getProperty "user.home") "/Library/LaunchAgents"))

;; === User-situational defaults (adapt to your environment) ===

(def default-census-processes
  "Default processes to count in census."
  ["clojure-lsp" "java" "Brave" "Code.*Insiders"])

(def noise-patterns
  "Log noise patterns to filter out. Add patterns that clutter your logs."
  ["Ignoring GPU update because this process is not GPU managed"
   "Ignoring jetsam"
   "Ignoring memory limit"
   "not memory-managed"
   "WatchDogTimer"
   "corespeech"])

(def jvm-categories
  "JVM categorization patterns for breakdown analysis.
   Keys are category names, values are grep patterns."
  {:test-related "test-data|integration-test"
   :nrepl        "nrepl.cmdline"
   :shadow-cljs  "shadow-cljs"})
