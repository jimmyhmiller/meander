# Meander<sup>ε</sup>

Meander is a Clojure/ClojureScript data transformation library which combines higher order functional programming with concepts from *term rewriting* and *logic programmin*g. It does so with a trifold union of syntactic pattern matching, syntactic pattern substitution, and a suite of transform combinators known as _strategies_ that run the gamut from purely functional to purely declarative.

[![Clojars Project](https://img.shields.io/clojars/v/meander/delta.svg)](https://clojars.org/meander/delta)

## What can Meander do?

Meander allows you to work with your data in a transparent way. Enabling you to directly see the input and output of your code.

```clojure
(require '[meander.epsilon :as m])

(defn favorite-food-info [user foods-by-name]
  (m/match {:user user
            :foods-by-name foods-by-name}
    {:user
     {:name ?name
      :favorite-food {:name ?food}}
     :foods-by-name {?food {:popularity ?popularity
                            :calories ?calories}}}
    {:name ?name
     :favorite {:food ?food
                :popularity ?popularity
                :calories ?calories}}))
```

Here we can see Meander's `match` function at work. Match allows us to pattern match on a data structure and return the answer that matches our pattern. We use logic variables (symbols that start with `?`) to extract values from our input and return them in our output. Logic variable also let us join across values. Here we do that using the `?food` variable to lookup our users favorite foods in out `foods-by-name` collection. To learn more about this checkout the [Types of Variables]() article.

### Finding More than One Answer

What if instead of a user having one favorite food, they had multiple and we wanted to return the information for all of them? This is where `search` comes in handy.

```clojure
(defn favorite-foods-info [user foods-by-name]
  (m/search {:user user
             :foods-by-name foods-by-name}
    {:user
     {:name ?name
      :favorite-foods (m/scan {:name ?food}})
     :foods-by-name {?food {:popularity ?popularity
                            :calories ?calories}}}
    {:name ?name
     :favorite {:food ?food
                :popularity ?popularity
                :calories ?calories}}))
```

There is actually very little that is different here. I pluralized some names, change line 2 to use search instead of match and on line 6 added a scan. That is all we need to find all of a users favorite foods and look up the information about them. Checkout [Understanding Meander's Pattern Matching Functions]() for more information.

### Remembering Values

Let's shift gears. What if a user has all sorts of different foods scattered through out their information and we want to collect them all? Here we can use `memory variables`.

```clojure
(defn grab-all-foods [user]
  {:favorite-foods [{:name !foods} ...]
   :special-food !food
   :recipes [{:title !foods} ...]
   :meal-plan {:breakfast [{:food !foods} ...]
               :lunch [{:food !foods} ...]
               :dinner [{:food !foods} ...]}}
  !foods)
```

This code example is a little contrived, but it does immediately show you how you can grab values from all sorts of places in your data structure and collect them together. This combination of a `!memory-variable` and the zero or more operator `...` is a fairly common one. Using them together allows you to gather up many values quickly. If you want to enforce that a certain number of elements exist you can also use the n or more operator (..1 ..2 ..3 etc). For more information check out the [Repeats and Collections]() article for more information.

### Conditional Matches

Stepping away from food examples, we can see a few of Meanders more traditional pattern matching abilities. Imagine that we have coordinates that can either include be [x y] or [x y z] and we want a pattern to that extracts y.

```clojure
(def point [1 2])

(m/match point
  [?x ?y] ?y
  [?x ?y ?z] ?y)
;; => 2
```

Here we used match to check against multiple patterns. Meander checks this in a top to bottom ordering. One thing to note is that since we didn't use `?x` or `?z` we could have replaced them with the a wildcard match (`_`). The above pattern accomplishes the task, but imagine that for some reason people keep passing things that aren't numbers into our match, so we want to restrict our matches to only numbers.

```clojure
(m/match point
  [(m/pred number?) (m/pred number? ?y)] ?y
  [(m/pred number?) (m/pred number? ?y) (m/pred number?)] ?y)
```

This ensures things that aren't number fail to match, but is a little verbose. Honestly, that isn't a problem. Length is not the measure of good code, clarity is. But just to see how Meander allows us to build our own extensions let's look at how we can shorten things up.

```clojure
(m/defsyntax num?
  ([] `(m/pred number?))
  ([pattern] `(m/pred number? ~pattern)))

(m/match point
  [(num?) (num? ?y)] ?y
  [(num?) (num? ?y) (num?)] ?y)

```

Here we use defsyntax to essential build our own macros for Meander. To learn more check out the [Extending Meander]() article.

### Gaining Control

Sometimes we have a multistep process where we want to transform nested data in place. This is an area that Meander continues to explore, but one powerful way of solving this problem is using Meander's strategies.

```clojure
(require '[meander.strategies.epsilon :as strat])

(def eliminate-zeros
  (strat/rewrite
   (+ ?x 0) ?x
   (+ 0 ?x) ?x))

(def eliminate-all-zeros
  (strat/bottom-up
   (strat/attempt eliminate-zeros)))

(eliminate-all-zeros '(+ (+ 0 (+ 0 (+ 3 (+ 2 0)))) 0)))
;; => (+ 3 2)
```

Using our strategies we can make rewrite rules and then say how they ought to be applied. Here we use the attempt strategy, which just says if the match fails, then return whatever was passed in. And the bottom up strategies which applies our match to the most deep value, and replaces values that match all the way up the tree. To learn more checkout [Applying Meander Strategies]()

## Going Further

To explore Meander further checkout all of our [docs](), visit the [#meander room]() on slack, or read the [contributing guide](). You can also checkout things made by the community. Below we have a list of blogs posts and talks about Meander as well as libraries that are using or build on to Meander. If you have any questions don't hesitate to ask them in slack or file an issue. We are happy to help.

### Blog Posts

* [Meander: The answer to map fatigue](http://timothypratley.blogspot.com/2019/01/meander-answer-to-map-fatigue.html)
* [Meander for Practical Data Transformation](https://jimmyhmiller.github.io/meander-practical/)

### Talks

* [Strangeloop 2019 (Video available after conference)](https://thestrangeloop.com/2019/meander-declarative-explorations-at-the-limits-of-fp.html)

### Libraries

* Add yours here