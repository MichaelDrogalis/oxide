(ns oxide.http.sente
  (:require [clojure.core.async :refer [close!]]
            [com.stuartsierra.component :as component]
            [taoensso.sente :refer [make-channel-socket!]]))

(defrecord Sente []
  component/Lifecycle
  (start [component]
    (println "Starting Sente")
    (let [x (make-channel-socket! {})]
      (assoc component
        :ring-ajax-post (:ajax-post-fn x)
        :ring-ajax-get-or-ws-handshake (:ajax-get-or-ws-handshake-fn x)
        :ch-chsk (:ch-recv x)
        :chsk-send! (:send-fn x)
        :connected-uids (:connected-uids x))))

  (stop [component]
    (println "Stopping Sente")
    (close! (:ch-chsk component))
    (assoc component :server nil)))

(defn sente []
  (map->Sente {}))

