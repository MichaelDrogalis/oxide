(ns oxide.core
  (:require [cljs.reader :refer [read-string]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.button :as b]
            [om-bootstrap.random :as r]
            [om-bootstrap.input :as i]
            [om-bootstrap.table :refer [table]]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs-uuid.core :as uuid]
            [taoensso.sente  :as sente :refer (cb-success?)])
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

(enable-console-print!)

(def default-expr "(histogram (group-by-popularity (minimum-popularity (within-location (data-set \"Yelp Businesses\") \"Phoenix\" \"AZ\") 3)))")

(defonce app-state (atom {:inputs []
                          :outputs {}
                          :current-expression default-expr}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn handle-job-complete [contents]
  (println (str (:job-id contents) "is done"))
  (chsk-send! [:job/output {:datomic-uri (:datomic-uri contents) :n (:n contents)}]))

(defn handle-output [contents]
  (swap! app-state assoc-in [:outputs (:n contents)] (:payload contents)))

(defn handle-payload [[event-type contents]]
  (cond (= event-type :job/complete)
        (handle-job-complete contents)
        (= event-type :job/output-payload)
        (handle-output contents)))

(defn event-handler [{:keys [event]}]
  (when (= (first event) :chsk/recv)
    (handle-payload (second event))))

(sente/start-chsk-router! ch-chsk event-handler)

(defcomponent expression-editor [data owner]
  (render-state [_ _] (d/pre {:id "editor"} ""))
  (did-mount [_]
             (let [editor (.edit js/ace "editor")]
               (.setOptions editor
                            (clj->js {:maxLines 15}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.insert editor default-expr)
               (.on (.getSession editor) "change"
                    (fn [e]
                      (om/transact! data (fn [a] (assoc a :current-expression (.getValue editor)))))))))

(defn abbreviate
  ([x] (abbreviate x 25))
  ([x n]
     (let [max-len n]
       (if (<= (count x) max-len)
         x
         (str (apply str (take max-len x)) "...")))))

(defn table-output [output]
  (table {:striped? true :bordered? true :condensed? true :responsive? true :hover? false}
         (d/thead
          (d/tr
           (d/th "Business Name")
           (d/th "Address")
           (d/th "Stars")))
         (d/tbody
          (for [row output]
            (do
              (d/tr
               (d/td (abbreviate (:business_name row) 35))
               (d/td (abbreviate (:address row) 45))
               (d/td (:stars row))))))))


;; ({:id 0, :star-counts "{5M 2162, 4M 3087}"})

(defcomponent histogram [data owner]
  (render-state [_ _] (d/div {:id "histogram"}))
  (did-mount [_]
             (let [stars (read-string (:star-counts (first (:output data))))
                   chart (Highcharts/Chart.
                          (clj->js {:chart {:renderTo "histogram"
                                            :type "column"}
                                    :title {:text "Histogram of Yelp Businesses Star Rating"}
                                    :xAxis {:categories ["1" "2" "3" "4" "5"]}
                                    :plotOptions {:column {:groupPadding 0
                                                           :pointPadding 0
                                                           :borderWidth 0}}
                                    :series [{:data [(get stars 1)
                                                     (get stars 2)
                                                     (get stars 3)
                                                     (get stars 4)
                                                     (get stars 5)]
                                              :name "Number of businesses"}]}))])))

(defcomponent exchange [data owner]
  (did-update [_ _ _]
              (.scrollTo js/window 0 (.-scrollHeight (.-body js/document))))
  (render-state [_ _]
                (d/div
                 (map-indexed
                  (fn [k input]
                    (r/well
                     {}
                     (d/div {:class "text-right"} (d/small (d/a {:href "#"} (str "#" k))))
                     (d/br)
                     (d/pre input)
                     (d/pre
                      (if-let [output (get-in data [:outputs k])]
                        (om/build histogram (assoc data :output output) {})
                        "Pending..."))))
                  (:inputs data)))))

(defcomponent submit [data owner]
  (render-state [_ _]
                (b/button {:bs-style "primary"
                           :on-click
                           (fn [e]
                             (let [expr (:current-expression @data)
                                   result (om/transact! data (fn [a] (assoc a :inputs (conj (:inputs a) expr))))
                                   n (dec (count (:inputs @result)))]
                               (chsk-send! [:job/submit {:expr expr :n n}])))}
                          "Go!")))

(defn main []
  (om/root
   (fn [app owner]
     (reify
       om/IRender
       (render [_]
         (d/div
          (g/grid
           {}
           (r/page-header
            {}
            (g/row
             {}
             (g/col
              {:xs 5 :md 2}
              (d/img {:id "logo" :src "img/logo-small.png"}))
             (g/col
              {:xs 1 :md 8}
              (g/row {} (d/h1 {:id "title"} "Oxide"))
              (g/row {} (d/small {:id "subtitle"} "Knowledge platform over Onyx")))))
           (g/row
            {}
            (g/row
             {}
             (g/col {:xs 10 :md 10} (p/panel {:header (d/h4 "interactive repl")}
                                             (g/row {}
                                                    (g/col {:md 12}
                                                           (om/build exchange app {})
                                                           (om/build expression-editor app {})
                                                           (om/build submit app {}))))))))))))
   app-state
   {:target (. js/document (getElementById "app"))}))
