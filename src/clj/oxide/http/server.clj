(ns oxide.http.server
  (:require [clojure.core.async :refer [thread <!!]]
            [oxide.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [org.httpkit.server :as http-kit-server]
            [clojure.java.io :as io]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.defaults]
            [ring.util.response :refer [resource-response response content-type]]
            [compojure.core :as comp :refer (defroutes GET POST)]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.system :refer [onyx-development-env]]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.plugin.datomic]
            [onyx.plugin.sql]
            [onyx.api]
            [oxide.onyx.datomic :as oxide-datomic]
            [oxide.onyx.impl]))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(def ring-defaults-config
  (assoc-in ring.middleware.defaults/site-defaults
            [:security :anti-forgery]
            {:read-token (fn [req] (-> req :params :csrf-token))}))

(defn workflow []
  [[:partition-keys :read-rows]
   [:read-rows :filter-by-city]
   [:filter-by-city :filter-by-rating]
   [:filter-by-rating :datomic-out]])

(defn catalog [datomic-uri]
  [{:onyx/name :partition-keys
    :onyx/ident :sql/partition-keys
    :onyx/type :input
    :onyx/medium :sql
    :onyx/consumption :concurrent
    :onyx/bootstrap? true
    :sql/classname "com.mysql.jdbc.Driver"
    :sql/subprotocol "mysql"
    :sql/subname "//127.0.0.1:3306/oxide"
    :sql/user "root"
    :sql/password ""
    :sql/table :yelp_data_set
    :sql/id :id
    :sql/rows-per-segment 1000
    :onyx/batch-size 1000
    :onyx/max-peers 1
    :onyx/doc "Partitions a range of primary keys into subranges"}

   {:onyx/name :read-rows
    :onyx/ident :sql/read-rows
    :onyx/fn :onyx.plugin.sql/read-rows
    :onyx/type :function
    :onyx/consumption :concurrent
    :sql/classname "com.mysql.jdbc.Driver"
    :sql/subprotocol "mysql"
    :sql/subname "//127.0.0.1:3306/oxide"
    :sql/user "root"
    :sql/password ""
    :sql/table :yelp_data_set
    :sql/id :id
    :onyx/batch-size 5
    :onyx/doc "Reads rows of a SQL table bounded by a key range"}

   {:onyx/name :filter-by-city
    :onyx/ident :oxide/filter-by-city
    :onyx/fn :oxide.onyx.impl/filter-by-city
    :onyx/type :function
    :onyx/consumption :concurrent
    :oxide/city "Phoenix"
    :oxide/state "AZ"
    :onyx/params [:oxide/city :oxide/state]
    :onyx/batch-size 1000
    :onyx/doc "Only emit entities that are in this city and state"}

   {:onyx/name :filter-by-rating
    :onyx/ident :oxide/filter-by-rating
    :onyx/fn :oxide.onyx.impl/filter-by-rating
    :onyx/type :function
    :onyx/consumption :concurrent
    :oxide/min-rating 4
    :onyx/params [:oxide/min-rating]
    :onyx/batch-size 1000
    :onyx/doc "Only emit entities that at least as good as this rating"}

   {:onyx/name :datomic-out
    :onyx/ident :datomic/commit-tx
    :onyx/type :output
    :onyx/medium :datomic
    :onyx/consumption :concurrent
    :datomic/uri datomic-uri
    :datomic/partition :oxide
    :onyx/batch-size 1000
    :onyx/doc "Transacts :datoms to storage"}])

(defn submit-onyx-job [peer-config]
  (let [datomic-uri (str "datomic:mem://" (java.util.UUID/randomUUID))]
    (oxide-datomic/set-up-output-database datomic-uri)
    {:datomic-uri datomic-uri
     :job-id
     (onyx.api/submit-job
      peer-config
      {:catalog (catalog datomic-uri)
       :workflow (workflow)
       :task-scheduler :onyx.task-scheduler/round-robin})}))

(defn process-submit-job [sente event peer-config]
  (let [expr (:expr (:?data event))
        n (:n (:?data event))
        {:keys [job-id datomic-uri]} (submit-onyx-job peer-config)
        uid (get-in event [:ring-req :cookies "ring-session" :value])]
    (onyx.api/await-job-completion peer-config job-id)
    ((:chsk-send! sente) uid [:job/complete {:job-id job-id :n n :datomic-uri datomic-uri}])))

(defn process-job-output [sente event]
  (try
    (let [db-uri (:datomic-uri (:?data event))
          conn (d/connect db-uri)
          db (d/db conn)
          query '[:find ?e :where [?e :id]]
          results (d/q query db)
          entities (map (partial into {}) (map (partial d/entity db) (map first results)))
          uid (get-in event [:ring-req :cookies "ring-session" :value])]
      ((:chsk-send! sente) uid [:job/output-payload {:payload entities
                                                     :n (:n (:?data event))}]))
    (catch Exception e
      (.printStacktrace e))))

(defn launch-event-handler [sente peer-config]
  (thread
   (loop []
     (when-let [event (<!! (:ch-chsk sente))]
       (prn "Got " (:id event))
       (cond (= (:id event) :job/submit)
             (thread (process-submit-job sente event peer-config))
             (= (:id event) :job/output)
             (thread (process-job-output sente event)))
       (recur)))))

(defrecord Httpserver [peer-config]
  component/Lifecycle
  (start [{:keys [sente] :as component}]
    (println "Starting HTTP Server")

    (defroutes my-routes
      (GET  "/" [] (page))
      (GET  "/chsk" req ((:ring-ajax-get-or-ws-handshake sente) req))
      (POST "/chsk" req ((:ring-ajax-post sente) req))
      (resources "/")
      (resources "/react" {:root "react"})
      (route/not-found "Page not found"))

    (launch-event-handler sente peer-config)

    (let [my-ring-handler (ring.middleware.defaults/wrap-defaults my-routes ring-defaults-config)
          server (http-kit-server/run-server my-ring-handler {:port 3000})
          uri (format "http://localhost:%s/" (:local-port (meta server)))]
      (println "Http-kit server is running at" uri)
      (assoc component :server server)))
  (stop [{:keys [server] :as component}]
    (println "Stopping HTTP Server")
    (server :timeout 100)
    (assoc component :server nil)))

(defn new-http-server [peer-config]
  (map->Httpserver {:peer-config peer-config}))

