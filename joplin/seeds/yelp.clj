(ns seeds.yelp
  (:require [clojure.java.jdbc :as jdbc]
            [cheshire.core :refer [parse-string]]))

(defn db-spec [subname]
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname subname
   :user "root"
   :password ""})

(defn run [target & args]
  (let [spec (db-spec (:subname (:db target)))
        xs (slurp (clojure.java.io/resource "data-sets/yelp/yelp_academic_dataset_business.json"))]
    (doseq [x (clojure.string/split xs #"\n")]
      (let [parsed (parse-string x true)]
        (jdbc/insert! spec :yelp_data_set
                      {:business_name (:name parsed)
                       :address (:full_address parsed)
                       :stars (:stars parsed)
                                           
                       :sunday_open_hour  (:open (:Sunday (:hours parsed)))
                       :sunday_close_hour (:close (:Sunday (:hours parsed)))
                                           
                       :monday_open_hour  (:open (:Monday (:hours parsed)))
                       :monday_close_hour (:close (:Monday (:hours parsed)))
                                           
                       :tuesday_open_hour  (:open (:Tuesday (:hours parsed)))
                       :tuesday_close_hour (:close (:Tuesday (:hours parsed)))

                       :wednesday_open_hour  (:open (:Wednesday (:hours parsed)))
                       :wednesday_close_hour (:close (:Wednesday (:hours parsed)))

                       :thursday_open_hour  (:open (:Thursday (:hours parsed)))
                       :thursday_close_hour (:close (:Thursday (:hours parsed)))
                                           
                       :friday_open_hour  (:open (:Friday (:hours parsed)))
                       :friday_close_hour (:close (:Friday (:hours parsed)))
                                           
                       :saturday_open_hour  (:open (:Saturday (:hours parsed)))
                       :saturday_close_hour (:close (:Saturday (:hours parsed)))})))))

