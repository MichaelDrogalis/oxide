(ns oxide.onyx.impl
  (:require [clojure.string :refer [split]]))

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

