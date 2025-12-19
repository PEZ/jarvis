(ns config
  "Shared configuration for Jarvis tasks.")

(def root (str (System/getProperty "user.home") "/jarvis"))
(def snaps (str root "/snaps"))
(def launch-agents (str (System/getProperty "user.home") "/Library/LaunchAgents"))
