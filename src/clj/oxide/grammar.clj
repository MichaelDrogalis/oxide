(ns oxide.grammar
  (:require [instaparse.core :as insta]))

(def oxide-grammar
  (insta/parser
   "OxideForm = '(' FullExpr ')'
    FullExpr = VisualFn Form
    Form = Whitespace+ '(' (PartialExpr | DataSet) ')'
    PartialExpr = Function Arg+
    Arg = Whitespace+ Constant Whitespace* | Form
    VisualFn = 'histogram' | 'locate-on-map' | 'table' | 'blurb'
    Function = 'within-location' | 'minimum-popularity' | 'group-by-popularity'
    DataSet = 'data-set' Whitespace+ String
    String = '\"' (#'[a-zA-Z]' | Whitespace+)+ '\"'
    Constant = 'x'
    Whitespace = #'\\s+'"))

(oxide-grammar "(histogram (group-by-popularity (minimum-popularity (within-location (data-set \"Yelp Businesses\") x))))")

