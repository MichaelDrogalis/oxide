(ns oxide.system
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [oxide.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [oxide.onyx.peers :refer [peers]]
            [oxide.http.sente :refer [sente]]
            [oxide.http.server :refer [new-http-server]]
            [onyx.system :refer [onyx-development-env]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]))

(def id (java.util.UUID/randomUUID))

(def zk-addr "127.0.0.1:2185")

(def zk-port 2185)

(def scheduler :onyx.job-scheduler/round-robin)

(def env-config
  {:hornetq/mode :vm
   :hornetq/server? true
   :hornetq.server/type :vm
   :zookeeper/address zk-addr
   :zookeeper/server? true
   :zookeeper.server/port zk-port
   :onyx.peer/job-scheduler scheduler
   :onyx/id id})

(def peer-config
  {:hornetq/mode :vm
   :zookeeper/address zk-addr
   :onyx/id id
   :onyx.peer/inbox-capacity 1000
   :onyx.peer/outbox-capacity 1000
   :onyx.peer/job-scheduler scheduler})

(def n-peers 4)

(defn get-system []
  (component/system-map
   :onyx-env (onyx-development-env env-config)
   :onyx-peers (component/using (peers peer-config n-peers) [:onyx-env])
   :sente (component/using (sente) [:onyx-peers])
   :http (component/using (new-http-server peer-config) [:sente])))

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (get-system))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))

(defn -main [& [port]]
  (go))

