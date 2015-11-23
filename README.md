statsd-client is an extensible client for the
[statsd](https://github.com/etsy/statsd).

## Installing

Add the following to your Leiningen or Boot deps:

```clojure
[adzerk/statsd-client "1.0.0"]
```

## Usage

To send a metric to statsd, call `setup` and then use `increment!`,
`decrement!`, `timer!`, `gauge!`, and `unique!`:

```clojure
(ns example
  (:require [adzerk.statsd-client :as s]))

(s/setup      "127.0.0.1" 8125)          ; statsd server details
(s/increment! :some.counter)             ; add 1 to a counter
(s/decrement! :some.counter)             ; subtract 1 from a counter
(s/increment! "some.counter")            ; metric names can be keys or strings
(s/increment! :some.counter 2)           ; add 2 to a counter
(s/increment! :some.counter 2 :rate 0.1) ; specify sample rate

(s/timing! :metric.name 300)             ; record 300ms for "metric.name"
(s/gauge!  :metric.name 43)              ; record an arbitrary value
(s/unique! :metric.name "whatevs")       ; see comment in source for details
```

## Extension

Hey, wouldn't it be cool to send your own custom extended metrics to
statsd? The answer is yes! Yes it would be! You can do this easily
because the following three steps are **DECOMPLECTED**

1. Creating a metric using `increment`, `decrement`, `timer`, `gauge`,
   or `unique`.
2. Formatting it using `base-formatter` or your own custom formatter
3. Publishing it using `publish`

To use your own custom format, you just need to create your own
formatter and, optionally, write your own functions that combine the
steps as `increment!`, `decrement!` et al. do.

Let's write a custom formatter:

```clojure
(ns example
  (:require [adzerk.statsd-client :as s]
            [clojure.string :as str]))

(defn categories [f]
  (fn [metric-attrs]
    (str (f metric-attrs) "|#" (str/join "," (:categories metric-attrs)))))

(def custom-formatter (categories base-formatter))
```

Formatters follow the middleware pattern. When you call `(categories
base-formatter)`, you get a new function that expects metric
attributes and returns a formatted string. `base-formatter` handles
the metric attributes `:metric-name`, `:metric-type`, `:value`, and
`:rate`. The `categories` formatter handles a new attribute,
`:categories`. Here's how you would use it:

```clojure
;; formatters return a string - this is just for demonstration purposes
(def cfincrement (comp custom-formatter s/increment))
(def cfdecrement (comp custom-formatter s/decrement))

(cfincrement :metric.name 1 :categories ["moop" "bloop"])
; => "metric.name:1|c|#moop,bloop"

;; value of -1 instead of 1 when decrementing
(cfdecrement :metric.name 1 :categories ["moop" "bloop"])
; => "metric.name:-1|c|#moop,bloop"
```

Here's how you could actually combine everything for convenience:

```clojure
(def publish-custom (partial s/publish custom-formatter))
(def increment!     (comp publish-custom s/increment))
(def decrement!     (comp publish-custom s/decrement))
(def timer!         (comp publish-custom s/timer))
(def gauge!         (comp publish-custom s/gauge))
(def unique!        (comp publish-custom s/unique))
```
