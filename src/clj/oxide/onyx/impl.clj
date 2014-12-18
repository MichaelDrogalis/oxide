(ns oxide.onyx.impl
  (:require [clojure.string :refer [split]]))

(defn filter-by-city [city state {:keys [address] :as segment}]
  (prn segment)
  (let [this-city (last (split (first (split address #",")) #" "))
        this-state (second (split (last (split address #",")) #" "))]
    (when (and (= city this-city)
               (= state this-state))
      segment)))

(defn filter-by-rating [min-rating {:keys [stars] :as segment}]
  (prn segment)
  (let [rating (bigdec (str stars))]
    (when (>= rating min-rating)
      segment)))

