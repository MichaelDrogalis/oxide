(ns oxide.http.server
  (:require [clojure.core.async :refer [chan thread <!!]]
            [oxide.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [oxide.grammar :refer [compile-onyx-job get-entry parse-expr]]
            [org.httpkit.server :as http-kit-server]
            [clojure.java.io :as io]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.defaults]
            [ring.util.response :refer [resource-response response content-type]]
            [compojure.core :as comp :refer (defroutes GET POST)]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [onyx.system :as system]
            [onyx.extensions :as extensions]
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

(defn submit-onyx-job [peer-config catalog workflow]
  (let [datomic-uri (:datomic/uri (get-entry catalog :datomic-out))]
    (oxide-datomic/set-up-output-database datomic-uri)
    {:datomic-uri datomic-uri
     :job-id
     (onyx.api/submit-job
      peer-config
      {:catalog catalog
       :workflow workflow
       :task-scheduler :onyx.task-scheduler/round-robin})}))

(defn report-progress [sente peer-config job-id n uid catalog]
  (thread
   (let [ch (chan 100)
         client (component/start (system/onyx-client peer-config))]
     (extensions/subscribe-to-log (:log client) 0 ch)
     (loop [replica {:job-scheduler (:onyx.peer/job-scheduler peer-config)}]
       (let [position (<!! ch)
             entry (extensions/read-log-entry (:log client) position)
             new-replica (extensions/apply-log-entry entry replica)
             tasks (get (:tasks new-replica) job-id)
             complete-tasks (get (:completions new-replica) job-id)]
         (when (and (= (:fn entry) :complete-task)
                    (= (:job (:args entry)) job-id))
           (let [task (extensions/read-chunk (:log client) :task (:task (:args entry)))
                 task-name (:name task)
                 entry (get-entry catalog task-name)]
             ((:chsk-send! sente) uid [:job/completed-task {:job-id job-id :n n :description (:oxide/description entry)}])))
         (when (or (nil? tasks) (not= (into #{} tasks) (into #{} complete-tasks)))
          (recur new-replica)))))))

(defn process-submit-job [sente event peer-config]
  (let [expr (:expr (:?data event))
        {:keys [workflow catalog visualization]} (compile-onyx-job (parse-expr expr) {:catalog [] :workflow []})
        n (:n (:?data event))
        {:keys [job-id datomic-uri]} (submit-onyx-job peer-config catalog workflow)
        uid (get-in event [:ring-req :cookies "ring-session" :value])
        tasks (map :oxide/description catalog)]

    ((:chsk-send! sente) uid [:job/tasks {:job-id job-id :n n :tasks tasks}])

    (report-progress sente peer-config job-id n uid catalog)
    
    (onyx.api/await-job-completion peer-config job-id)
    ((:chsk-send! sente) uid [:job/complete {:job-id job-id :n n
                                             :datomic-uri datomic-uri
                                             :visualization visualization}])))

(defn process-job-output [sente event]
  (let [db-uri (:datomic-uri (:?data event))
        conn (d/connect db-uri)
        db (d/db conn)
        query '[:find ?e :where [?e :id]]
        results (d/q query db)
        entities (map (partial into {}) (map (partial d/entity db) (map first results)))
        uid (get-in event [:ring-req :cookies "ring-session" :value])]
    ((:chsk-send! sente) uid [:job/output-payload {:payload entities
                                                   :n (:n (:?data event))}])))

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

(defn get-job-output [req]
  (prn (:job-id (:params req)))
  "abc")

(defrecord Httpserver [peer-config]
  component/Lifecycle
  (start [{:keys [sente] :as component}]
    (println "Starting HTTP Server")

    (defroutes my-routes
      (GET  "/" [] (page))
      (GET  "/chsk" req ((:ring-ajax-get-or-ws-handshake sente) req))
      (POST "/chsk" req ((:ring-ajax-post sente) req))
      (GET "/job/:job-id" req (get-job-output req))
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

