(ns oxide.onyx.peers
  (:require [com.stuartsierra.component :as component]
            [onyx.api]))

(defrecord Peers [config n]
  component/Lifecycle

  (start [component]
    (println "Starting Virtual Peers")
    (assoc component :peers (onyx.api/start-peers! n config)))

  (stop [component]
    (println "Stopping Virtual Peers")

    (doseq [peer (:peers component)]
      ((:shutdown-fn peer)))
    
    component))

(defn peers [config n]
  (map->Peers {:config config :n n}))

