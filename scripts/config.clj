(ns config
  "Shared configuration for mem-debug tasks.")

(def root (str (System/getProperty "user.home") "/mem-debug"))
(def snaps (str root "/snaps"))
(def launch-agents (str (System/getProperty "user.home") "/Library/LaunchAgents"))
