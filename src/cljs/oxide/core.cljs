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

(def default-expr "(histogram\n\t(group-by-popularity\n\t\t(minimum-popularity\n\t\t\t(within-location\n\t\t\t\t(data-set \"Yelp Businesses\")\n\t\t\t\t\"Phoenix\" \"AZ\")\n\t\t\t3)))")

(defonce app-state (atom {:inputs []
                          :outputs {}
                          :datomic-uris {}
                          :visualizations {}
                          :current-expression default-expr}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn handle-job-complete [contents]
  (println (str (:job-id contents) "is done"))
  (swap! app-state
         (fn [state]
           (-> state
               (assoc-in [:visualizations (:n contents)] (:visualization contents))
               (assoc-in [:datomic-uris (:n contents)] (:datomic-uri contents)))))
  (chsk-send! [:job/output {:datomic-uri (:datomic-uri contents) :n (:n contents)}]))

(defn handle-job-tasks [contents]
  (swap! app-state assoc-in [:tasks (:n contents)] (:tasks contents)))

(defn handle-started-task [contents]
  (swap! app-state assoc-in [:in-progress (:n contents) (:description contents)] true))

(defn handle-completed-task [contents]
  (swap! app-state assoc-in [:completions (:n contents) (:description contents)] true))

(defn handle-output [contents]
  (swap! app-state assoc-in [:outputs (:n contents)] (:payload contents)))

(defn handle-payload [[event-type contents]]
  (cond (= event-type :job/complete)
        (handle-job-complete contents)
        (= event-type :job/tasks)
        (handle-job-tasks contents)
        (= event-type :job/started-task)
        (handle-started-task contents)
        (= event-type :job/completed-task)
        (handle-completed-task contents)
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
               (.require js/ace "ace/ext/language_tools")
               (.setOptions editor
                            (clj->js {:maxLines 15
                                      :enableBasicAutocompletion true}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.insert editor default-expr)
               (.on (.getSession editor) "change"
                    (fn [e]
                      (om/transact! data (fn [a] (assoc a :current-expression (.getValue editor)))))))))

(defcomponent frozen-input [data owner]
  (render-state [_ _] (d/pre (:input data)))
  (did-mount [_]
             (let [editor (.edit js/ace (om/get-node owner))]
               (.setOptions editor
                            (clj->js {:maxLines 15}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.setHighlightActiveLine editor false)
               (.setHighlightGutterLine editor false)
               (.setReadOnly editor true)
               (set! (.-opacity (.-style (.-element (.-$cursorLayer (.-renderer editor))))) 0))))

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

(defcomponent histogram [data owner]
  (render-state [_ _] (d/div))
  (did-mount [this]
             (let [stars (read-string (:star-counts (first (:output data))))
                   chart (Highcharts/Chart.
                          (clj->js {:chart {:renderTo (om/get-node owner)
                                            :type "column"}
                                    :title {:text "Histogram of Yelp Businesses by Star Rating"}
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

(defcomponent map-location [data owner]
  (render-state [_ _] (d/div))
  (did-mount [this]
             (let [markers (map (fn [x]
                                  {:title (:business_name x)
                                   :lat (js/parseFloat (:latitude x))
                                   :lng (js/parseFloat (:longitude x))})
                                (:output data))
                   gmap
                   (js/GMaps. (clj->js {:div (om/get-node owner)
                                        :height "300px"
                                        :width "100%"
                                        :zoom 10
                                        :lat -12.043333
                                        :lng -77.028333}))
                   bounds (google.maps/LatLngBounds.)]
               (doseq [m markers]
                 (.addMarker gmap (clj->js m))
                 (.extend bounds (google.maps/LatLng. (js/parseFloat (:lat m))
                                                      (js/parseFloat (:lng m)))))
               (.fitBounds gmap bounds))))

(defcomponent exchange [data owner]
  (did-update [_ _ _]
              (.scrollTo js/window 0 (.-scrollHeight (.-body js/document))))
  (render-state [_ _]
                (d/div
                 (map-indexed
                  (fn [k input]
                    (let [v (get-in data [:visualizations k])]
                      (r/well
                       {}
                       (d/div {:class "text-right"}
                              (d/small (d/a {:href
                                             (str "/job/"
                                                  (get-in data [:datomic-uris k]) "/"
                                                  (get-in data [:visualizations k]))}
                                            (str "#" k))))
                       (d/br)
                       (om/build frozen-input (assoc data :input input))
                       (d/pre
                        (if-let [output (get-in data [:outputs k])]
                          (cond (= v "histogram")
                                (om/build histogram (assoc data :output output) {})
                                (= v "table")
                                (table-output output)
                                (= v "locate-on-map")
                                (om/build map-location (assoc data :output output) {})
                                :else "Well this is broken")
                          (if-let [tasks (get-in data [:tasks k])]
                            (d/div
                             (d/h4 "Onyx workflow plan")
                             (d/ul {:class "fa-ul"}
                              (for [task tasks]
                                (d/li
                                 (d/span
                                  (let [done? (get-in data [:completions k task])
                                        progress? (get-in data [:in-progress k task])
                                        status (cond done? "fa-li fa fa-check-square"
                                                     progress? "fa-li fa fa-spinner fa-spin"
                                                     :else "fa-li fa fa-square" )]
                                    (d/i {:class status}))
                                  task)))))
                            "Pending..."))))))
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
             (g/col {:xs 10 :md 8} (p/panel {:header (d/h4 "interactive repl")}
                                             (g/row {}
                                                    (g/col {:md 12}
                                                           (om/build exchange app {})
                                                           (om/build expression-editor app {})
                                                           (om/build submit app {}))))))))))))
   app-state
   {:target (. js/document (getElementById "app"))}))

