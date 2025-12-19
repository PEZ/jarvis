(ns config
  "Shared configuration for Jarvis tasks.")

(def root (str (System/getProperty "user.home") "/jarvis"))
(def snaps (str root "/snaps"))
(def launch-agents (str (System/getProperty "user.home") "/Library/LaunchAgents"))

(def default-census-processes
  "Default processes to count in census."
  ["clojure-lsp" "java" "Brave" "Code.*Insiders"])
