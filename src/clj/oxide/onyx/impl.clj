(ns oxide.onyx.impl
  (:require [clojure.string :refer [split]]))

(defn filter-by-city [city state {:keys [address] :as segment}]
  (let [this-city (last (split (first (split x #",")) #" "))
        this-state (second (split (last (split x #",")) #" "))]
    (when (and (= city this-city)
               (= state this-state))
      segment)))

(defn filter-by-rating [min-rating {:keys [stars] :as segment}]
  (let [rating (bigdec (str stars))]
    (when (>= rating min-rating)
      segment)))

