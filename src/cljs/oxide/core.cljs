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

(defonce app-state (atom {}))

(defcomponent expression-editor [data owner]
  (init-state [_] {})
  (will-mount [_] {})
  (render-state [_ _] (d/pre {:id "editor"} ""))
  (did-mount [_]
             (let [editor (.edit js/ace "editor")]
               (.setOptions editor
                            (clj->js {:maxLines 15}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.insert editor "LocateOnMap [Within [DataSet(\"restaurants\"), \"Baltimore, MD\"]]"))))

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
                                                           (om/build expression-editor {} {})
                                                           (b/button {:bs-style "primary"} "Go!"))))))))))))
   app-state
   {:target (. js/document (getElementById "app"))}))
