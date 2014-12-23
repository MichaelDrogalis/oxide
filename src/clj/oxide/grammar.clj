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

(defmulti compile-workflow
  (fn [[node body] workflow]
    node))

(defmethod compile-workflow :OxideForm
  [[node body] workflow]
  (compile-workflow body workflow))

(defmethod compile-workflow :FullExpr
  [[node visual-f more] workflow]
  (let [f (compile-workflow visual-f workflow)]
    (compile-workflow more (conj workflow [nil (keyword f)]))))

(defmethod compile-workflow :VisualFn
  [[node body] workflow]
  body)

(defmethod compile-workflow :Form
  [[node body] workflow]
  (compile-workflow body workflow))

(defmethod compile-workflow :PartialExpr
  [[node function & args] workflow]
  (let [f (compile-workflow function workflow)
        flow
        (if (nil? (first (last workflow)))
          (vec (conj (butlast workflow) [f (last (last workflow))]))
          (vec (conj workflow [f (first (last workflow))] [nil f])))]
    (reduce (fn [wf arg] (compile-workflow arg wf)) flow args)))

(defmethod compile-workflow :Function
  [[node body] workflow]
  (keyword body))

(defmethod compile-workflow :Arg
  [[node body] workflow]
  (compile-workflow body workflow))

(defmethod compile-workflow :DataSet
  [[node body ds-name] workflow]
  (let [dataset-name (compile-workflow ds-name workflow)]
    (vec (conj [[:input (ffirst workflow)]] workflow))))

(defmethod compile-workflow :String
  [[node & body] workflow]
  (apply str (map (fn [x] (compile-workflow x workflow)) body)))

(defmethod compile-workflow :Whitespace
  [[node & body] workflow]
  " ")

(defmethod compile-workflow :Constant
  [[node body] workflow]
  workflow)

(defmethod compile-workflow :default
  [leaf workflow]
  leaf)

(compile-workflow parsed [])


