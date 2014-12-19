(ns oxide.http.server
  (:require
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
    [compojure.core     :as comp :refer (defroutes GET POST)]
    [compojure.route    :as route]
    [com.stuartsierra.component :as component]))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defrecord Httpserver [get-or-ws-fn post-fn]
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

    (let [my-ring-handler (ring.middleware.defaults/wrap-defaults my-routes ring-defaults-config)
          server (http-kit-server/run-server my-ring-handler {:port 3000})
          uri (format "http://localhost:%s/" (:local-port (meta server)))]
      (println "Http-kit server is running at" uri)
      (assoc component :server server)))
  (stop [{:keys [server] :as component}]
    (println "Stopping HTTP Server")
    (server :timeout 100)
    (assoc component :server nil)))

(defn new-http-server []
  (map->Httpserver {}))

