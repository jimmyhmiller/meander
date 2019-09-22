(ns meander.match.gamma-test
  (:require [clojure.spec.alpha :as s :include-macros true]
            [clojure.test :as t]
            [clojure.test.check.clojure-test :as tc.t :include-macros true]
            [clojure.test.check.generators :as tc.gen :include-macros true]
            [clojure.test.check.properties :as tc.prop :include-macros true]
            [meander.match.gamma :as r.match :include-macros true]
            [meander.syntax.gamma :as r.syntax :include-macros true]))

(def gen-scalar
  (tc.gen/one-of [tc.gen/int
                  tc.gen/string
                  tc.gen/keyword
                  tc.gen/symbol]))

;; ---------------------------------------------------------------------
;; match macro tests

;; Top level

(t/deftest quote-pattern-test
  (t/is (r.match/match '?x
          '?x true))

  (t/is (r.match/match '!x
          '!x true))

  (t/is (r.match/match '(a . b . c .. d ..2)
          '(a . b . c .. d ..2)
          true))

  (t/is (r.match/match '[a . b . c .. d ..2]
          '[a . b . c .. d ..2]
          true)))


(tc.t/defspec unquote-any-x-matches-any-x
  (tc.prop/for-all [x gen-scalar]
    (r.match/match x
      ~x
      true

      _
      false)))


(tc.t/defspec _-matches-any-x
  (tc.prop/for-all [x gen-scalar]
    (r.match/match x
      _
      true)))


(tc.t/defspec ?x-matches-any-x
  (tc.prop/for-all [x gen-scalar]
    (r.match/match x
      ?x
      true)))


(tc.t/defspec !xs-collects-any-x
  (tc.prop/for-all [x gen-scalar]
    (r.match/match x
      !xs
      (= [x] !xs)

      _
      false)))


(tc.t/defspec pred-succeeds
  (tc.prop/for-all [x tc.gen/nat]
    (r.match/match x
      (pred nat-int?)
      true)))


(tc.t/defspec pred-fails
  (tc.prop/for-all [x tc.gen/nat]
    (r.match/match x
      (pred string?)
      false

      _
      true)))


(tc.t/defspec guard-succeeds
  (tc.prop/for-all [x tc.gen/nat]
    (r.match/match x
      (guard (= 1 1))
      true)))


(tc.t/defspec guard-fails
  (tc.prop/for-all [x tc.gen/nat]
    (r.match/match x
      (guard (= 1 2))
      false

      _
      true)))


(tc.t/defspec and-succeeds
  (tc.prop/for-all [x tc.gen/nat]
    (r.match/match x
      (and ~x ?x)
      (= x ?x))))


(tc.t/defspec and-fails
  (tc.prop/for-all [x gen-scalar]
    (r.match/match x
      (and ~x ?x (guard false))
      false

      _
      true)))


(tc.t/defspec or-succeeds
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar
                    z gen-scalar]
    (r.match/match x
      (or ~z ~y ~x)
      true

      _
      false)))


(tc.t/defspec or-fails
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar
                    z gen-scalar]
    (r.match/match [x y z]
      (or ~z ~y ~x)
      false

      _
      true)))

#?(:clj
   (t/deftest or-compilation-fails
     (t/is (try
             (macroexpand '(meander.match.gamma/match 1 (or ?x ?y ?z) false))
             false
             (catch clojure.lang.ExceptionInfo _
               true)))))
 
(tc.t/defspec let-succeeds
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match y
      (and ?y (let ?x x))
      (and (= y ?y)
           (= x ?x)))))


(tc.t/defspec let-fails
  (tc.prop/for-all [x gen-scalar]
    (r.match/match x
      (and ?x (let ?x [x]))
      false

      _
      true)))


(t/deftest let-test
  (t/is (= [1 2]
           (r.match/match 42
             (or [?x ?y] (let [?x ?y] [1 2]))
             [?x ?y]))))

;; Seqs

(tc.t/defspec seq-unquote-patterns-match
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match (list x (list y x) y)
      (~x (~y ~x) ~y)
      true

      _
      false)))


(tc.t/defspec seq-lvrs-bind-the-values-they-match
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match (list x (list y x) y)
      (?x (?y ?x) ?y)
      (and (= ?x x)
           (= ?y y))

      _
      false)))


(tc.t/defspec seq-mvrs-collect-the-values-they-match
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match (list x (list y x) y)
      (!xs (!ys !xs) !ys)
      (and (= [x x] !xs)
           (= [y y] !ys))

      _
      false)))


(tc.t/defspec seq-zero-or-more-any-in-head
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `(~@(mapcat identity (repeat n [x1 x2])) ~y1 ~y2)
      (~x1 ~x2 ... ~y1 ~y2)
      true

      _
      false)))


(tc.t/defspec seq-zero-or-more-any-in-tail
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `(~x1 ~x2 ~@(mapcat identity (repeat n [y1 y2])))
      (~x1 ~x2 . ~y1 ~y2 ...)
      true

      _
      false)))


(tc.t/defspec seq-zero-or-more-mvr-in-head
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `(~@(mapcat identity (repeat n [x1 x2])) ~y1 ~y2)
      (!xs1 !xs2 ... ~y1 ~y2)
      (and (= (repeat n x1) !xs1)
           (= (repeat n x2) !xs2))

      _
      false)))


(tc.t/defspec seq-zero-or-more-mvr-in-tail
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `(~x1 ~x2 ~@(mapcat identity (repeat n [y1 y2])))
      (~x1 ~x2 . !ys1 !ys2 ...)
      (and (= (repeat n y1) !ys1)
           (= (repeat n y2) !ys2))

      _
      false)))

(tc.t/defspec seq-zero-or-more-lvr-previously-bound
  (tc.prop/for-all [n (tc.gen/such-that (complement zero?) tc.gen/nat)
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `(~@(mapcat identity (repeat n [x1 x2])) ~y1 ~y2)
      (?x1 ?x2 . ?x1 ?x2 ... ~y1 ~y2)
      (and (= x1 ?x1)
           (= x2 ?x2))

      _
      false)))


(tc.t/defspec seq-zero-or-more-nested-mvr
  (tc.prop/for-all [n (tc.gen/such-that (complement zero?) tc.gen/nat)
                    b (s/gen simple-symbol?)
                    v gen-scalar]
    (r.match/match `(let (~@(mapcat identity (repeat n [b v])))
                      ~@(repeat n b))
      (`let (!bs !vs ...) . !body ...)
      (and (= !bs (repeat n b))
           (= !vs (repeat n v))
           (= !body (repeat n b)))

      _
      false)))


(tc.t/defspec seq-drop-in-head
  (tc.prop/for-all [n tc.gen/nat
                    x gen-scalar]
    (r.match/match `(~@(map identity (repeat n x)) ~x ~x)
      (_ ... ~x ~x)
      true)))


(tc.t/defspec seq-drop-in-tail
  (tc.prop/for-all [n tc.gen/nat
                    x gen-scalar]
    (r.match/match `(~@(map identity (repeat n x)) ~x ~x)
      (~x ~x . _ ...)
      true

      _
      false)))


(tc.t/defspec seq-?x-guard-true-?x-succeeds
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match (list x y x)
      (?x (guard (= ?x ?x)) ?x)
      true

      _
      false)))

(t/deftest seq-as-pattern
  (let [xs '(1 2 3)]
    (t/is (r.match/match xs
            (?x ?y ?z :as ?xs)
            (= (list ?x ?y ?z) ?xs xs)))))

;; ---------------------------------------------------------------------
;; Vectors

(tc.t/defspec vec-unquote-patterns-match
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match [x [y x] y]
      [~x [~y ~x] ~y]
      true

      _
      false)))


(tc.t/defspec vec-lvrs-bind-the-values-they-match
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match [x [y x] y]
      [?x [?y ?x] ?y]
      (and (= ?x x)
           (= ?y y))

      _
      false)))


(tc.t/defspec vec-mvrs-collect-the-values-they-match
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match [x [y x] y]
      [!xs [!ys !xs] !ys]
      (and (= [x x] !xs)
           (= [y y] !ys))

      _
      false)))


(tc.t/defspec vec-zero-or-more-any-in-head
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `[~@(mapcat identity (repeat n [x1 x2])) ~y1 ~y2]
      [~x1 ~x2 ... ~y1 ~y2]
      true

      _
      false)))


(tc.t/defspec vec-zero-or-more-any-in-tail
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `[~x1 ~x2 ~@(mapcat identity (repeat n [y1 y2]))]
      [~x1 ~x2 . ~y1 ~y2 ...]
      true

      _
      false)))


(tc.t/defspec vec-zero-or-more-mvr-in-head
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `[~@(mapcat identity (repeat n [x1 x2])) ~y1 ~y2]
      [!xs1 !xs2 ... ~y1 ~y2]
      (and (= (repeat n x1) !xs1)
           (= (repeat n x2) !xs2))

      _
      false)))


(tc.t/defspec vec-zero-or-more-mvr-in-tail
  (tc.prop/for-all [n tc.gen/nat
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `[~x1 ~x2 ~@(mapcat identity (repeat n [y1 y2]))]
      [~x1 ~x2 . !ys1 !ys2 ...]
      (and (= (repeat n y1) !ys1)
           (= (repeat n y2) !ys2))

      _
      false)))


(tc.t/defspec vec-zero-or-more-lvr-previously-bound
  (tc.prop/for-all [n (tc.gen/such-that (complement zero?) tc.gen/nat)
                    x1 gen-scalar
                    x2 gen-scalar
                    y1 gen-scalar
                    y2 gen-scalar]
    (r.match/match `[~@(mapcat identity (repeat n [x1 x2])) ~y1 ~y2]
      [?x1 ?x2 . ?x1 ?x2 ... ~y1 ~y2]
      (and (= x1 ?x1)
           (= x2 ?x2))

      _
      false)))


(tc.t/defspec vec-zero-or-more-nested-mvr
  (tc.prop/for-all [n (tc.gen/such-that (complement zero?) tc.gen/nat)
                    b (s/gen simple-symbol?)
                    v gen-scalar]
    (r.match/match `(let [~@(mapcat identity (repeat n [b v]))]
                      ~@(repeat n b))
      (`let [!bs !vs ...] . !body ...)
      (and (= !bs (repeat n b))
           (= !vs (repeat n v))
           (= !body (repeat n b)))

      _
      false)))


(tc.t/defspec vec-drop-in-head
  (tc.prop/for-all [n tc.gen/nat
                    x gen-scalar]
    (r.match/match `[~@(map identity (repeat n x)) ~x ~x]
      [_ ... ~x ~x]
      true)))


(tc.t/defspec vec-drop-in-tail
  (tc.prop/for-all [n tc.gen/nat
                    x gen-scalar]
    (r.match/match `[~@(map identity (repeat n x)) ~x ~x]
      [~x ~x . _ ...]
      true)))


(tc.t/defspec vec-?x-guard-true-?x-succeeds
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match [x y x]
      [?x (guard (= ?x ?x)) ?x]
      true

      _
      false)))


(t/deftest vec-compile-inside-seq
  (t/is (= 1 (r.match/match '(let [] 1)
               ('let [] ?x)
               ?x))))

(t/deftest vec-as-pattern
  (let [xs [1 2 3]]
    (t/is (r.match/match xs
            [?x ?y ?z :as ?xs]
            (= [?x ?y ?z] ?xs xs)))))

;; ---------------------------------------------------------------------
;; Maps


(tc.t/defspec map-unq-succeeds
  (tc.prop/for-all [x gen-scalar]
    (r.match/match {:x x, x :x}
      {:x ~x, ~x :x}
      true

      _
      false)))


(tc.t/defspec map-lvr-succeeds
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match {:x x, :y y}
      {:x ?x, :y ?y}
      (= [x y] [?x ?y])

      _
      false)))


(tc.t/defspec map-vec-lvr
  (tc.prop/for-all [x gen-scalar]
    (let [y [x]
          z [[x]]]
      (r.match/match {:x [x y], :y [y x], :z [z]}
        {:x [?x ?y] :y [?y ?x] :z [?z]}
        (= [x y z] [?x ?y ?z])

        _
        false))))


(tc.t/defspec map-seq-lvr
  (tc.prop/for-all [x gen-scalar]
    (let [y [x]
          z [[x]]]
      (r.match/match {:x (list x y), :y (list y x), :z (list z)}
        {:x (?x ?y) :y (?y ?x) :z (?z)}
        (= (list x y z) (list ?x ?y ?z))

        _
        false))))


(tc.t/defspec map-mvr
  (tc.prop/for-all [x gen-scalar
                    y gen-scalar]
    (r.match/match {:x x, :y y}
      {:x !xs, :y !xs}
      (or (= [x y] !xs)
          (= [y x] !xs))

      _
      false)))


(tc.t/defspec map-map
  (tc.prop/for-all [x gen-scalar]
    (r.match/match {:x {:y x}, :z x}
      {:x {:y ~x}, :z ~x}
      true

      _
      false)))


(t/deftest map-rest-map
  (= [2 {:_1 1, :_3 3}]
     (r.match/match {:_1 1, :_2 2, :_3 3}
       {:_2 ?2 & ?rest-map}
       [?2 ?rest-map]))

  (= [2 3 {:_1 1}]
     (r.match/match {:_1 1, :_2 2, :_3 3}
       {:_2 ?2 & {:_3 ?3 & ?rest-map}}
       [?2 ?3 ?rest-map]))

  (= '([2 :_1 1] [2 :_3 3])
     (r.match/search {:_1 1, :_2 2, :_3 3}
       {:_2 ?2 & (vscan [?k ?v])}
       [?2 ?k ?v])

     ))

;; ---------------------------------------------------------------------
;; Sets

(t/deftest set-unq
  (let [k1 :k1
        k2 :k2]
    (r.match/match #{k1 k2}
      #{~(keyword (name k1)) ~k2}
      true)))


#_
;; TODO: This should pass because ?x in the nested map can be bound
;; previously.
(t/deftest map-can-have-variable-bound-lvr-keys
  (r.match/match {:k1 "v1"
                  :k2 {"v1" "v2"}}
    {:k1 ?x
     :k2 {?x ?y}}
    (t/is (and (= ?x "v1")
               (= ?y "v2")))

    _
    false))

#_
;; TODO: This should pass because ?x and ?y can be matched before
;; attempting to match the subsequence.
(t/deftest zero-or-more-may-have-lvrs-if-lvrs-can-be-bound
  (t/is  (= [1 2]
            (r.match/match [[1 2 1 2] 1 2]
              [[?x ?y ...] ?x ?y]
              [?x ?y]))))

#_
;; TODO: This should pass because there is only one possible solution.
(t/deftest sequences-of-zero-or-more
  (t/is (r.match/match [1 2 3 4 3 4]
          [1 2 ... 3 4 ...]
          true

          _
          false)))

;; ---------------------------------------------------------------------
;; re form

(t/deftest re-test
  (t/is (r.match/match "foo foo foo"
          (re #"(?:foo ?)+")
          true
          _
          false))

  (t/is (r.match/match "Harry Hacker"
          (re #"([^ ]+) *([^ ]+)" [_ ?Harry ?Hacker])
          (and (= ?Harry "Harry")
               (= ?Hacker "Hacker"))
          _
          false)))

;; ---------------------------------------------------------------------
;; search macro tests


(t/deftest search-map-1-test
  (t/is (= #{["v1" "v2"]}
           (set (r.match/search {:k1 "v1", :k2 {"v1" "v2"}}
                  {:k1 ?x, :k2 {?x ?y}}
                  [?x ?y])))))

(t/deftest search-map-2-test
  (t/is (= #{[:k1 :k2 "v1" "v2"]
             [:k1 :k2 :k3 "v1" "v2"]}
           (set (r.match/search {:k1 "v1"
                                 :k2 {"v1" "v2"}
                                 :k3 {"v2" "v1"}}

                  {?k1 ?x
                   ?k2 {?x ?y}}
                  [?k1 ?k2 ?x ?y]

                  {?k1 ?x
                   ?k2 {?x ?y}
                   ?k3 {?y ?x}}
                  [?k1 ?k2 ?k3 ?x ?y])))))


(t/deftest search-or-1-test
  (t/is (= #{[1 2] [2 1]}
           (set (r.match/search [1 2]
                  (or [?x ?y] [?y ?x])
                  [?x ?y])))))


(t/deftest search-subsequence-1-test
  (t/is (= (set (r.match/search [1 2 1 2 1 2 3 5 6 7 8 9 1 2]
                  [?x ?y . ?x ?y ... !zs ... ?x ?y]
                  {:?x ?x, :?y ?y, :!zs !zs}))
           #{{:?x 1, :?y 2, :!zs [1 2 1 2 3 5 6 7 8 9]}
             {:?x 1, :?y 2, :!zs [1 2 3 5 6 7 8 9]}
             {:?x 1, :?y 2, :!zs [3 5 6 7 8 9]}})))


(t/deftest vscan-test
  (t/is (= '([:_1 "_1"] [:_2 "_2"] [:_3 "_3"] [:_4 "_4"])
           (r.match/search [{:_1 "_1"} {:_2 "_2", :_3 "_3"} {:_4 "_4"}]
             (vscan {?k ?v})
             [?k ?v]))))


(t/deftest scan-test
  (t/is (= '([:_1 "_1"] [:_2 "_2"] [:_3 "_3"] [:_4 "_4"])
           (r.match/search [{:_1 "_1"} {:_2 "_2", :_3 "_3"} {:_4 "_4"}]
             (scan {?k ?v})
             [?k ?v]))))

;; ---------------------------------------------------------------------
;; check

(t/deftest no-value-before-zero-or-more
  (t/testing "match"
    (let [error (r.match/check (r.syntax/parse '[... ?x]) false)]
      (t/is (= "Zero or more (...) is a postfix operator. It must have some value in front of it. (i.e. [1 ... ?x])"
               #?(:clj (.getMessage error)
                  :cljs (.-message error))))))

  (t/testing "search"
    (let [error (r.match/check (r.syntax/parse '[... ?x]) true)]
      (t/is (= "Zero or more (...) is a postfix operator. It must have some value in front of it. (i.e. [1 ... ?x])"
               #?(:clj (.getMessage error)
                  :cljs (.-message error)))))))


(t/deftest no-value-before-one-or-more
  (t/testing "match"
    (let [error (r.match/check (r.syntax/parse '[..2 ?x]) false)]
      (t/is (= "N or more (..N) is a postfix operator. It must have some value in front of it. (i.e. [1 ..2 ?x])"
               #?(:clj (.getMessage error)
                  :cljs (.-message error))))))

  (t/testing "search"
    (let [error (r.match/check (r.syntax/parse '[..2 ?x]) true)]
      (t/is (= "N or more (..N) is a postfix operator. It must have some value in front of it. (i.e. [1 ..2 ?x])"
               #?(:clj (.getMessage error)
                  :cljs (.-message error)))))))


(t/deftest no-value-after-one-or-more
  (t/testing "match"
    (let [error (r.match/check (r.syntax/parse '[1 .. ?x]) false)]
      (t/is (=  "Ambiguous ellipsis. Perhaps you meant the n or more operator (..N) or the zero or more operator (...)?"
                #?(:clj (.getMessage error)
                   :cljs (.-message error))))))

  (t/testing "search"
    (let [error (r.match/check (r.syntax/parse '[1 .. ?x]) true)]
      (t/is (= "Ambiguous ellipsis. Perhaps you meant the n or more operator (..N) or the zero or more operator (...)?"
               #?(:clj (.getMessage error)
                  :cljs (.-message error)))))))
 
;; ---------------------------------------------------------------------
;; Misc

(t/deftest memory-variables-in-nested-zero-or-more
  (t/is (= [[:aa :bb :cc] [1 2 3]]
           (r.match/match '[([:aa 1] [:bb 2]) ([:cc 3])]
             [([!ks !vs] ...) ...]
             [!ks !vs]

             _
             nil))))


(t/deftest pred
  (t/is (= [1 2]
           (r.match/match 1
             (pred odd? ?x (app inc ?y))
             [?x ?y]))))


(t/deftest app
  (t/is (r.match/match {:foo 1}
          (app (fn [m] (assoc m :baz 2))
               ?x
               (app (fn [m] (assoc m :quux 3))
                    ?y)
               (let ?z [?x ?y]))
          (= ?z
             [?x ?y]
             [{:foo 1, :baz 2}
              {:foo 1, :baz 2, :quux 3}]))))


(t/deftest ground-map-pattern-expresses-submap-match
  (t/is (r.match/match {:foo 1, :bar 2, :baz 3}
          {:foo 1}
          true))

  (t/is (r.match/match {:foo 1, :bar 2, :baz 3}
          {:bar 2}
          true))

  (t/is (r.match/match {:foo 1, :bar 2, :baz 3}
          {:baz 3}
          true))

  (t/is (r.match/match {:foo 1, :bar 2, :baz 3}
          {:foo 1, :bar 2}
          true))

  (t/is (r.match/match {:foo 1, :bar 2, :baz 3}
          {:bar 2, :baz 3}
          true))

  (t/is (r.match/match {:foo 1, :bar 2, :baz 3}
          {:foo 1, :baz 3}
          true))

  (t/is (r.match/match '({:foo 1, :bar 2, :baz 3}
                         {:foo 1, :bar 2, :baz 3}
                         {:foo 1, :bar 2, :baz 3})
          ({:foo 1, :bar 2}
           {:bar 2, :baz 3}
           {:foo 1, :baz 3})
          true))

  (t/is (r.match/match [{:foo 1, :bar 2, :baz 3}
                        {:foo 1, :bar 2, :baz 3}
                        {:foo 1, :bar 2, :baz 3}]
          [{:foo 1, :bar 2}
           {:bar 2, :baz 3}
           {:foo 1, :baz 3}]
          true)))

(t/deftest ground-set-pattern-expresses-subset-match
  (t/is (r.match/match #{1 2 3}
          #{1}
          true))

  (t/is (r.match/match #{1 2 3}
          #{2}
          true))

  (t/is (r.match/match #{1 2 3}
          #{3}
          true))

  (t/is (r.match/match #{1 2 3}
          #{1 2}
          true))

  (t/is (r.match/match #{1 2 3}
          #{2 3}
          true))

  (t/is (r.match/match #{1 2 3}
          #{1 3}
          true))

  (t/is (r.match/match '(#{1 2 3}
                         #{1 2 3}
                         #{1 2 3})
          (#{1 2}
           #{2 3}
           #{1 3})
          true))

  (t/is (r.match/match [#{1 2 3}
                        #{1 2 3}
                        #{1 2 3}]
          [#{1 2}
           #{2 3}
           #{1 3}]
          true))

  (t/is (r.match/match #{[{:foo "bar", :baz "foo"}]
                         [{:foo "baz", :baz "quux"}]}
          #{[{:foo "bar"}]
            [{:baz "quux"}]}
          true))

  (t/is (r.match/match #{[{:foo "bar", :baz "foo"}]
                         [{:foo "baz", :baz "quux"}]}
          #{[{:foo "baz"}]
            [{:baz "foo"}]}
          true)))

(tc.t/defspec find-results-are-elements-of-search-results
  (tc.prop/for-all [v (tc.gen/vector tc.gen/nat 3 5)]
    (contains? (set (r.match/search v
                      [!xs ..1 !ys ...]
                      {'!xs !xs, '!ys !ys}))
               (r.match/find v
                 [!xs ..1 !ys ...]
                 {'!xs !xs, '!ys !ys}))))

(t/deftest find-mvrs-are-collected-properly
  (let [data [{:name "George"
               :species "Parakeet"
               :age 3
               :owners ["Frege" "Peirce"]}
              {:name "Francis"
               :species "Dog"
               :age 8
               :owners ["De Morgan"]}
              {:name "Bob"
               :species "Goldfish"
               :age 1
               :owners ["Peirce"]}]

        expected-search-results
        #{["Peirce" ["George" "Bob"]]
          ["Peirce" ["Bob"]]
          ["Peirce" ["George"]]
          ["Frege" ["George"]]
          ["De Morgan" ["Francis"]]}]
    (t/is (= expected-search-results
             (set (r.match/search data
                    [_ ...
                     {:name !names
                      :owners [_ ... ?owner . _ ...]}
                     .
                     (or (and {:name !names
                               :owners [_ ... ?owner . _ ...]})
                         _)
                     ...]
                    [?owner !names]))))
    (t/is (contains? expected-search-results
                     (r.match/find data
                       [_ ...
                        {:name !names
                         :owners [_ ... ?owner . _ ...]}
                        .
                        (or (and {:name !names
                                  :owners [_ ... ?owner . _ ...]})
                            _)
                        ...]
                       [?owner !names])))))

(t/deftest find-separated-items 
  (t/is (= [:a :v '[[:any _] [:lvr ?a] [:lit "Bill"]]]
           (r.match/find '([:a [[:any _] [:lvr ?a] [:lit "Bill"]]]
                           [:e [[:lvr ?e] [:any _] [:lit "Alice"]]]
                           [:v [[:any _] [:lvr ?a] [:lit "Bill"]]])
             (_ ... [?i1 ?tuple] . _ ... [?i2 ?tuple] . _ ...)
             [?i1 ?i2 ?tuple]))))

(t/deftest set-negation
  (t/is (= '(1 3)
           (r.match/search [#{1 2 3} #{[2 2]}]
             [#{?x} (not #{[?x ?x]})]
             ?x)))

  (let [x (r.match/find [#{1 2 3} #{[2 2]}]
            [#{?x} (not #{[?x ?x]})]
            ?x)]
    (t/is (or (= x 1)
              (= x 3)))))

(t/deftest unbound-mvrs-in-dsj
  (t/is (= [[0 2 4 6 8] [1 3 5 7 9]]
           (r.match/match (range 10)
             ((or (pred even? !evens) (pred odd? !odds)) ...)
             [!evens !odds])))

  (t/is (= [[2 1 3] []]
           (r.match/match [2 1 3]
             [(or (pred even? !xs) (pred odd? !ys)) !xs !xs]
             [!xs !ys])))

  (t/is (= [[2 4 6 8] [1 3 5 7 9]]
           (r.match/match [1 2 3 4 5 6 7 8 9]
             [(or (pred even? !xs) (pred odd? !ys)) ...]
             [!xs !ys]))))

(t/deftest seq-pattern-matching-with-infinite-sequences
  (t/is (r.match/match (iterate inc 1)
          (_1)
          false

          _n
          true))

  (t/is (r.match/match (iterate inc 1)
          (_1 _2)
          false

          _n
          true)))

;; ---------------------------------------------------------------------
;; JS Array match tests

#?(:cljs
   (tc.t/defspec jsa-unquote-patterns-match
     (tc.prop/for-all [x gen-scalar
                       y gen-scalar]
       (r.match/match #js [x #js [y x] y]
         #js [~x #js [~y ~x] ~y]
         true

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-lvrs-bind-the-values-they-match
     (tc.prop/for-all [x gen-scalar
                       y gen-scalar]
       (r.match/match #js [x #js [y x] y]
         #js [?x #js [?y ?x] ?y]
         (and (= ?x x)
              (= ?y y))

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-mvrs-collect-the-values-they-match
     (tc.prop/for-all [x gen-scalar
                       y gen-scalar]
       (r.match/match #js [x #js [y x] y]
         #js [!xs #js [!ys !xs] !ys]
         (and (= [x x] !xs)
              (= [y y] !ys))

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-zero-or-more-any-in-head
     (tc.prop/for-all [n tc.gen/int
                       x1 gen-scalar
                       x2 gen-scalar
                       y1 gen-scalar
                       y2 gen-scalar]
       (r.match/match (into-array (conj (into [] (mapcat identity) (repeat n [x1 x2])) y1 y2))
         #js [~x1 ~x2 ... ~y1 ~y2]
         true

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-zero-or-more-any-in-tail
     (tc.prop/for-all [n tc.gen/nat
                       x1 gen-scalar
                       x2 gen-scalar
                       y1 gen-scalar
                       y2 gen-scalar]
       (r.match/match (into-array (into [x1 x2] (mapcat identity) (repeat n [y1 y2])))
         #js [~x1 ~x2 . ~y1 ~y2 ...]
         true

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-zero-or-more-mvr-in-head
     (tc.prop/for-all [n tc.gen/nat
                       x1 gen-scalar
                       x2 gen-scalar
                       y1 gen-scalar
                       y2 gen-scalar]
       (r.match/match (into-array (conj (into [] (mapcat identity) (repeat n [x1 x2])) y1 y2))
         #js [!xs1 !xs2 ... ~y1 ~y2]
         (and (= (repeat n x1) !xs1)
              (= (repeat n x2) !xs2))

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-zero-or-more-mvr-in-tail
     (tc.prop/for-all [n tc.gen/nat
                       x1 gen-scalar
                       x2 gen-scalar
                       y1 gen-scalar
                       y2 gen-scalar]
       (r.match/match (into-array (into [x1 x2] (mapcat identity) (repeat n [y1 y2])))
         #js [~x1 ~x2 . !ys1 !ys2 ...]
         (and (= (repeat n y1) !ys1)
              (= (repeat n y2) !ys2))

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-zero-or-more-lvr-previously-bound
     (tc.prop/for-all [n (tc.gen/such-that (complement zero?) tc.gen/nat)
                       x1 gen-scalar
                       x2 gen-scalar
                       y1 gen-scalar
                       y2 gen-scalar]
       (r.match/match (into-array (conj (into [] (mapcat identity) (repeat n [x1 x2])) y1 y2))
         #js [?x1 ?x2 . ?x1 ?x2 ... ~y1 ~y2]
         (and (= x1 ?x1)
              (= x2 ?x2))

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-zero-or-more-nested-mvr
     (tc.prop/for-all [n (tc.gen/such-that (complement zero?) tc.gen/nat)
                       b (s/gen simple-symbol?)
                       v gen-scalar]
       (r.match/match (list* `let (into-array (into [] (mapcat identity) (repeat n [b v])))
                             (repeat n b))
         (`let #js [!bs !vs ...] . !body ...)
         (and (= !bs (repeat n b))
              (= !vs (repeat n v))
              (= !body (repeat n b)))

         _
         false))))


#?(:cljs
   (tc.t/defspec jsa-drop-in-head
     (tc.prop/for-all [n tc.gen/nat
                       x gen-scalar]
       (r.match/match (into-array (conj (into [] (map identity) (repeat n x)) x x))
         #js [_ ... ~x ~x]
         true))))


#?(:cljs
   (tc.t/defspec jsa-drop-in-tail
     (tc.prop/for-all [n tc.gen/nat
                       x gen-scalar]
       (r.match/match (into-array (conj (into [] (map identity) (repeat n x)) x x))
         #js [~x ~x . _ ...]
         true))))


#?(:cljs
   (tc.t/defspec jsa-?x-guard-true-?x-succeeds
     (tc.prop/for-all [x gen-scalar
                       y gen-scalar]
       (r.match/match #js [x y x]
         #js [?x (guard (= ?x ?x)) ?x]
         true

         _
         false))))


#?(:cljs
   (t/deftest jsa-compile-inside-seq
     (t/is (= 1 (r.match/match '(let #js [] 1)
                  ('let #js [] ?x)
                  ?x)))))

;; ---------------------------------------------------------------------
;; JS Object match tests

#?(:cljs
   (tc.t/defspec jso-unq-succeeds
     (tc.prop/for-all [x gen-scalar]
       (r.match/match #js {:x x}
         #js {:x ~x}
         true

         _
         false))))

#?(:cljs
   (tc.t/defspec jso-lvr-succeeds
     (tc.prop/for-all [x gen-scalar
                       y gen-scalar]
       (r.match/match #js {:x x, :y y}
         #js {:x ?x, :y ?y}
         (= [x y] [?x ?y])

         _
         false))))


#?(:cljs
   (tc.t/defspec jso-vec-lvr
     (tc.prop/for-all [x gen-scalar]
       (let [y [x]
             z [[x]]]
         (r.match/match #js {:x [x y], :y [y x], :z [z]}
           #js {:x [?x ?y] :y [?y ?x] :z [?z]}
           (= [x y z] [?x ?y ?z])

           _
           false)))))

#?(:cljs
   (tc.t/defspec jso-seq-lvr
     (tc.prop/for-all [x gen-scalar]
       (let [y [x]
             z [[x]]]
         (r.match/match #js {:x (list x y), :y (list y x), :z (list z)}
           #js {:x (?x ?y) :y (?y ?x) :z (?z)}
           (= (list x y z) (list ?x ?y ?z))

           _
           false)))))

#?(:cljs
   (tc.t/defspec jso-mvr
     (tc.prop/for-all [x gen-scalar
                       y gen-scalar]
       (r.match/match #js {:x x, :y y}
         #js {:x !xs, :y !xs}
         (or (= [x y] !xs)
             (= [y x] !xs))

         _
         false))))


#?(:cljs
   (tc.t/defspec jso-jso
     (tc.prop/for-all [x gen-scalar]
       (r.match/match #js {:x #js {:y x}, :z x}
         #js {:x #js {:y ~x}, :z ~x}
         true

         _
         false))))
