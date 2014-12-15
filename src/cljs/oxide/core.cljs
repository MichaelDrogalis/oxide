(ns oxide.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.button :as b]
            [om-bootstrap.random :as r]
            [om-bootstrap.input :as i]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]))

(enable-console-print!)

(def default-expr "LocateOnMap [Within [DataSet(\"restaurants\"), \"Baltimore, MD\"]]")

(defonce app-state (atom {:inputs []
                          :current-expression default-expr}))

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

(defcomponent exchange [data owner]
  (did-update [_ _ _]
             (.scrollTo js/window 0 (.-scrollHeight (.-body js/document))))
  (render-state [_ _]
                (d/div
                 (map-indexed
                  (fn [k input]
                    (r/well
                     {}
                     (d/div {:class "text-right"} (d/small (str "#" k)))
                     (d/br)
                     (d/pre input)
                     (d/pre "Your output here...")))
                  (:inputs data)))))

(defcomponent submit [data owner]
  (render-state [_ _]
                (b/button {:bs-style "primary"
                           :on-click
                           (fn [e]
                             (om/transact! data (fn [a] (assoc a :inputs (conj (:inputs a) (:current-expression @data))))))} 
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
           (g/row
            {}
            (g/col
             {:xs 10 :md 10}
             (r/page-header
              {}
              (g/row {} "Oxide")
              (g/row {} (d/small "Knowledge platform over Onyx")))))
           (g/row
            {}
            (g/row
             {}
             (g/col {:xs 10 :md 10} (p/panel {:header (d/h4 "interactive repl")}
                                             (g/row {}
                                                    (g/col {:md 10}
                                                           (om/build exchange app {})
                                                           (om/build expression-editor app {})
                                                           (om/build submit app {}))))))))))))
   app-state
   {:target (. js/document (getElementById "app"))}))
