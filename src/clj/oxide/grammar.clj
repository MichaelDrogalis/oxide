(ns oxide.grammar
  (:require [instaparse.core :as insta]))

(def oxide-grammar
  (insta/parser
   "OxideForm = <'('> FullExpr <')'>
    FullExpr = VisualFn Form
    Form = <Whitespace+> <'('> (PartialExpr | DataSet) <')'>
    PartialExpr = Function Arg+
    Arg = <Whitespace+> Constant <Whitespace*> | Form
    VisualFn = 'histogram' | 'locate-on-map' | 'table' | 'blurb'
    Function = 'within-location' | 'minimum-popularity' | 'group-by-popularity'
    DataSet = 'data-set' <Whitespace+> String
    String = <'\"'> (#'[a-zA-Z]' | Whitespace+)+ <'\"'>
    Constant = String | #'[0-9]'+
    Whitespace = #'\\s+'"))

(def parsed (oxide-grammar "(histogram (group-by-popularity (minimum-popularity (within-location (data-set \"Yelp Businesses\") 3))))"))

(def visual-fn (second (second (second parsed))))

(def tree-1 (nth (second parsed) 2))

(def group-by-popularity (second (second (second tree-1))))

(def tree-2 (second (nth (second tree-1) 2)))

(def min-popularity (second (second (second tree-2))))

(def tree-3 (second (nth (second tree-2) 2)))

(def within-location (second (second (second tree-3))))

(def tree-4 (second (nth (second tree-3) 2)))

(def data-set (rest (nth (second tree-4) 2)))

(clojure.pprint/pprint data-set)

