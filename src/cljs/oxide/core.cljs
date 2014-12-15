(ns oxide.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.random :as r]
            [om-bootstrap.input :as i]
            [om-tools.dom :as d :include-macros true]))

(defonce app-state (atom {:text "Hello Chestnut!"}))

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
                                                    (g/col {:xs 10 :md 10}
                                                           (d/code
                                                            {}
                                                            (i/input
                                                             {:feedback? true
                                                              :type "text"
                                                              :placeholder "Enter text"
                                                              :group-classname "group-class"
                                                              :wrapper-classname "wrapper-class"
                                                              :label-classname "label-class"})))))))))))))
   app-state
   {:target (. js/document (getElementById "app"))}))
