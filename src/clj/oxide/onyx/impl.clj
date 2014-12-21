(ns oxide.onyx.impl
  (:require [clojure.data.fressian :as fressian]
            [clojure.string :refer [split]]
            [onyx.peer.task-lifecycle-extensions :as l-ext]
            [onyx.extensions :as extensions]))

(defn strip-nil-keys [x]
  (into {} (filter (fn [[a b]] (identity b)) x)))

(defn normalize-address [x]
  (update-in x [:address] clojure.string/replace "\n" " "))

(defn filter-by-city [city state segment]
  (let [{:keys [address] :as segment} (normalize-address segment)
        this-city (last (split (first (split address #",")) #" "))
        this-state (second (split (last (split address #",")) #" "))]
    (if (and (= city this-city) (= state this-state))
      (strip-nil-keys segment)
      [])))

(defn filter-by-rating [min-rating {:keys [stars] :as segment}]
  (let [rating (bigdec (str stars))]
    (if (>= rating min-rating)
      (strip-nil-keys segment)
      [])))

(defn group-by-stars [local-state {:keys [stars]}]
  (swap! local-state (fn [state] (assoc state stars (inc (get state stars 0)))))
  [])

(defmethod l-ext/inject-lifecycle-resources :group-by-stars
  [_ {:keys [onyx.core/queue] :as event}]
  (let [local-state (atom {})]
    {:onyx.core/params [local-state]
     :oxide/state local-state}))

(defmethod l-ext/close-lifecycle-resources :group-by-stars
  [_ {:keys [onyx.core/queue oxide/state] :as event}]
  (let [session (extensions/create-tx-session queue)
        compressed-state (fressian/write {:id 0 :star-counts (pr-str @state)})]
    (doseq [queue-name (vals (:onyx.core/egress-queues event))]
      (let [producer (extensions/create-producer queue session queue-name)]
        (extensions/produce-message queue producer session compressed-state)
        (extensions/close-resource queue producer)))
    (extensions/commit-tx queue session)
    (extensions/close-resource queue session))
  {})

