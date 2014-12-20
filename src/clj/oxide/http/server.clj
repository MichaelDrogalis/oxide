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
    :onyx/batch-size 1000
    :sql/classname "com.mysql.jdbc.Driver"
    :sql/subprotocol "mysql"
    :sql/subname "//127.0.0.1:3306/oxide"
    :sql/user "root"
    :sql/password ""
    :sql/table :yelp_data_set
    :sql/id :id
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
    (onyx.api/submit-job
     peer-config
     {:catalog (catalog datomic-uri)
      :workflow (workflow)
      :task-scheduler :onyx.task-scheduler/round-robin})))

(defn launch-event-handler [sente peer-config]
  (thread
   (loop []
     (when-let [x (<!! (:ch-chsk sente))]
       (when (= (:id x) :oxide.client/repl)
         (let [expr (:expr (:?data x))
               n (:n (:?data x))
               job-id (submit-onyx-job peer-config)
               uid (get-in x [:ring-req :cookies "ring-session" :value])]
           (onyx.api/await-job-completion peer-config job-id)
           ((:chsk-send! sente) uid [:job/complete {:job-id job-id :n n}])))
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

