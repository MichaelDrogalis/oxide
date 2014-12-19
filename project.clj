(defproject com.mdrogalis/oxide "0.1.0-SNAPSHOT"
  :description "Knowledge platform over Onyx"
  :url "https://github.com/MichaelDrogalis/oxide"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs" "joplin"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [com.mdrogalis/onyx "0.5.0-SNAPSHOT"]
                 [com.mdrogalis/onyx-core-async "0.5.0-SNAPSHOT"]
                 [com.mdrogalis/onyx-datomic "0.5.0-SNAPSHOT"]
                 [com.mdrogalis/onyx-sql "0.5.0-SNAPSHOT"]
                 [com.taoensso/sente "1.2.0"]
                 [http-kit "2.1.18"]
                 [instaparse "1.3.5"]
                 [cheshire "5.4.0"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [racehub/om-bootstrap "0.3.2"]
                 [honeysql "0.4.3"]
                 [ring "1.3.1"]
                 [compojure "1.2.0"]
                 [enlive "1.1.5"]
                 [om "0.7.3"]
                 [prismatic/om-tools "0.3.6"]
                 [figwheel "0.1.4-SNAPSHOT"]
                 [environ "1.0.0"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]
                 [leiningen "2.5.0"]
                 [joplin.core "0.2.4"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-environ "1.0.0"]
            [joplin.lein "0.2.4"]]
  :min-lein-version "2.5.0"
  :uberjar-name "oxide.jar"
  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    "resources/public/js/out.js.map"
                                        :preamble      ["react/react.min.js"]
                                        :externs       ["react/externs/react.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}
  :profiles {:dev {:repl-options {:init-ns oxide.server
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :plugins [[lein-figwheel "0.1.4-SNAPSHOT"]]
                   :figwheel {:http-server-root "public"
                              :port 3449
                              :css-dirs ["resources/public/css"]}
                   :env {:is-dev true}
                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}}}
             :uberjar {:hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}}
  :joplin {:migrators {:yelp "joplin/migrators/yelp"}
           :seeds {:yelp "seeds.yelp/run"}
           :databases {:yelp-dev {:type :sql
                                  :url "jdbc:mysql://localhost:3306/oxide?user=root"
                                  :subname "//localhost:3306/oxide?user=root"}}
           :environments {:dev [{:db :yelp-dev :migrator :yelp :seed :yelp}]}})

