(ns oxide.onyx
  (:require [clojure.core.async :refer [chan <!!]]
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

(def id (java.util.UUID/randomUUID))

(def output-ch (chan 1000))

(def datomic-uri (str "datomic:mem://" (java.util.UUID/randomUUID)))

(oxide-datomic/set-up-output-database datomic-uri)

(def env-config
  {:hornetq/mode :vm
   :hornetq/server? true
   :hornetq.server/type :vm
   :zookeeper/address "127.0.0.1:2185"
   :zookeeper/server? true
   :zookeeper.server/port 2185
   :onyx/id id})

(def peer-config
  {:hornetq/mode :vm
   :zookeeper/address "127.0.0.1:2185"   
   :onyx/id id
   :onyx.peer/inbox-capacity 1000
   :onyx.peer/outbox-capacity 1000
   :onyx.peer/job-scheduler :onyx.job-scheduler/round-robin})

(def workflow
  [[:partition-keys :read-rows]
   [:read-rows :filter-by-city]
   [:filter-by-city :filter-by-rating]
   [:filter-by-rating :datomic-out]])

(def catalog
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

   {:onyx/name :core-async-out
    :onyx/ident :core.async/write-to-chan
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/consumption :concurrent
    :onyx/batch-size 1000
    :onyx/max-peers 1
    :onyx/doc "Writes segments to a core.async channel"}

   {:onyx/name :datomic-out
    :onyx/ident :datomic/commit-tx
    :onyx/type :output
    :onyx/medium :datomic
    :onyx/consumption :concurrent
    :datomic/uri datomic-uri
    :datomic/partition :oxide
    :onyx/batch-size 1000
    :onyx/doc "Transacts :datoms to storage"}])

(defmethod l-ext/inject-lifecycle-resources :out
  [_ _] {:core-async/out-chan output-ch})

(def dev (onyx-development-env env-config))

(def env (component/start dev))

(def v-peers (onyx.api/start-peers! 1 peer-config))

(onyx.api/submit-job
 peer-config {:catalog catalog :workflow workflow
              :task-scheduler :onyx.task-scheduler/round-robin})

(doseq [peer v-peers]
  ((:shutdown-fn peer)))

(component/stop env)


