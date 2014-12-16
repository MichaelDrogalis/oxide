(ns oxide.onyx
  (:require [onyx.api]))

(def workflow
  [[:partition-keys :read-rows]
   [:read-rows :filter-by-city]
   [:filter-by-city :filter-by-rating]
   [:filter-by-rating :out]])

(def catalog
  [{:onyx/name :partition-keys
    :onyx/ident :sql/partition-keys
    :onyx/type :input
    :onyx/medium :sql
    :onyx/consumption :sequential
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
    :onyx/fn :oxide.onyx.impl/filter-by-city
    :onyx/type :function
    :onyx/consumption :concurrent
    :oxide/city "Phoenix"
    :oxide/state "AZ"
    :onyx/batch-size 1000
    :onyx/doc "Only emit entities that are in this city and state"}

   {:onyx/name :filter-by-rating
    :onyx/fn :oxide.onyx.impl/filter-by-rating
    :onyx/type :function
    :onyx/consumption :concurrent
    :oxide/rating 4
    :onyx/batch-size 1000
    :onyx/doc "Only emit entities that at least as good as this rating"}

   {:onyx/name :out
    :onyx/ident :core.async/write-to-chan
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/consumption :sequential
    :onyx/batch-size 1000
    :onyx/doc "Writes segments to a core.async channel"}])

