(ns oxide.grammar
  (:require [instaparse.core :as insta]))

(def oxide-grammar
  (insta/parser
   "OxideForm = <'('> FullExpr <')'>
    FullExpr = VisualFn Form
    Form = <Whitespace+> <'('> (PartialExpr | DataSet) <')'>
    PartialExpr = Function Arg+
    Arg = <Whitespace+> Constant <Whitespace*> | Form
    VisualFn = 'histogram' | 'locate-on-map' | 'table' | 'blurb'
    Function = 'within-location' | 'minimum-popularity' | 'group-by-popularity'
    DataSet = 'data-set' <Whitespace+> String
    String = <'\"'> (#'[a-zA-Z]' | Whitespace+)+ <'\"'>
    Constant = String | #'[0-9]'+
    Whitespace = #'\\s+'"))

(def parsed (oxide-grammar "(histogram (group-by-popularity (minimum-popularity (within-location (data-set \"Yelp Businesses\")) 3)))"))

(def base-catalog
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

   {:onyx/name :within-location
    :onyx/ident :oxide/filter-by-city
    :onyx/fn :oxide.onyx.impl/filter-by-city
    :onyx/type :function
    :onyx/consumption :concurrent
    :oxide/city "Phoenix"
    :oxide/state "AZ"
    :onyx/params [:oxide/city :oxide/state]
    :onyx/batch-size 1000
    :onyx/doc "Only emit entities that are in this city and state"}

   {:onyx/name :minimum-popularity
    :onyx/ident :oxide/filter-by-rating
    :onyx/fn :oxide.onyx.impl/filter-by-rating
    :onyx/type :function
    :onyx/consumption :concurrent
    :oxide/min-rating 4
    :onyx/params [:oxide/min-rating]
    :onyx/batch-size 1000
    :onyx/doc "Only emit entities that at least as good as this rating"}

   {:onyx/name :group-by-popularity
    :onyx/ident :oxide/group-by-stars
    :onyx/fn :oxide.onyx.impl/group-by-stars
    :onyx/type :function
    :onyx/consumption :concurrent
    :onyx/max-peers 1
    :onyx/batch-size 1000
    :onyx/doc "Maintain local state, summing the number of businesses with each star level"}

   {:onyx/name :datomic-out
    :onyx/ident :datomic/commit-tx
    :onyx/type :output
    :onyx/medium :datomic
    :onyx/consumption :concurrent
;;    :datomic/uri datomic-uri
    :datomic/partition :oxide
    :onyx/batch-size 1000
    :onyx/doc "Transacts :datoms to storage"}])

(defn get-entry [catalog name]
  (first (filter #(= (:onyx/name %) name) catalog)))

(defmulti compile-onyx-job
  (fn [[node body] job]
    node))

(defmethod compile-onyx-job :OxideForm
  [[node body] {:keys [workflow] :as job}]
  (compile-onyx-job body job))

(defmethod compile-onyx-job :FullExpr
  [[node visual-f more] {:keys [workflow] :as job}]
  (let [f (compile-onyx-job visual-f job)
        j (-> job
              (assoc :workflow [[nil (keyword f)]])
              (assoc :catalog [(get-entry base-catalog :partition-keys)
                               (get-entry base-catalog :read-rows)
                               (get-entry base-catalog :datomic-out)]))]
    (compile-onyx-job more j)))

(defmethod compile-onyx-job :VisualFn
  [[node body] {:keys [workflow] :as job}]
  "datomic-out")

(defmethod compile-onyx-job :Form
  [[node body] {:keys [workflow] :as job}]
  (compile-onyx-job body job))

(defmethod compile-onyx-job :PartialExpr
  [[node function & args] {:keys [workflow] :as job}]
  (let [compiled (compile-onyx-job function job)]
    (reduce (fn [j arg] (compile-onyx-job arg j)) compiled args)))

(defmethod compile-onyx-job :Function
  [[node body] {:keys [workflow catalog] :as job}]
  (let [f (keyword body)]
    (merge job
           {:workflow
            (if (nil? (ffirst workflow))
              (vec (concat [[f (last (first workflow))]] (rest workflow)))
              (vec (concat [[nil f] [f (ffirst workflow)]] workflow)))
            :catalog (conj catalog (get-entry base-catalog f))})))

(defmethod compile-onyx-job :Arg
  [[node body] {:keys [workflow] :as job}]
  (compile-onyx-job body job))

(defmethod compile-onyx-job :DataSet
  [[node body ds-name] {:keys [workflow] :as job}]
  (let [dataset-name (compile-onyx-job ds-name job)]
    (merge job
           {:workflow
            (if (ffirst workflow)
              (vec (concat [[:partition-keys :read-rows] [:read-rows (ffirst workflow)]] workflow))
              (vec (concat [[:partition-keys :read-rows] [:read-rows (last (first workflow))]] (rest workflow))))})))

(defmethod compile-onyx-job :String
  [[node & body] {:keys [workflow] :as job}]
  (apply str (map (fn [x] (compile-onyx-job x job)) body)))

(defmethod compile-onyx-job :Whitespace
  [[node & body] {:keys [workflow] :as job}]
  " ")

(defmethod compile-onyx-job :Constant
  [[node body] {:keys [workflow] :as job}]
  job)

(defmethod compile-onyx-job :default
  [leaf {:keys [workflow] :as job}]
  leaf)

(clojure.pprint/pprint (compile-onyx-job parsed {:workflow [] :catalog []}))

