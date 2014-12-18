(ns oxide.onyx.impl
  (:require [clojure.string :refer [split]]))

(defn strip-nil-keys [datom]
  (into {} (filter (fn [[a b]] (identity b)) datom)))

(defn filter-by-city [city state {:keys [address] :as segment}]
  (let [this-city (last (split (first (split address #",")) #" "))
        this-state (second (split (last (split address #",")) #" "))]
    (if (and (= city this-city) (= state this-state))
      (strip-nil-keys segment)
      [])))

(defn filter-by-rating [min-rating {:keys [stars] :as segment}]
  (let [rating (bigdec (str stars))]
    (if (>= rating min-rating)
      (strip-nil-keys segment)
      [])))

