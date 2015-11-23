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
statsd? The answer is yes! Yes it would be!

### Why?

Some services, like Datadog, can understand custom metric attributes
that aren't included in the original statsd implementation. This
library lets you handle those custom attributes. The rest of this
section covers how statsd metrics are formatted.

The original implementation handles the following metric attributes:

* metric name
* value
* metric type (counter, timer, gauge, histogram)
* sample rate

Metrics are sent to a server as simple lines of text. For example,
here's a string for a counter metric named "googly.moogly" with a
value of 1:

```
googly.moogly:1|c
```

Datadog lets you add tags to a metric, like so:

```
googly.moogly:1|c|#country:china,aws
```

This adds two tags to the metric: the key-value pair `country:china`
and the value `aws`.

This library provides everything you need to start using a basic
statsd system, and it allows you to roll your own custom format
handlers.

### How to Extend

You can create extensions easily because the following three steps are
**DECOMPLECTED**:

1. Creating a metric using `increment`, `decrement`, `timer`, `gauge`,
   or `unique`.
2. Formatting it using `base-formatter` or your own custom formatter
3. Publishing it using `publish`

In step 1, you create a map which represents a metric:

```clojure
(s/increment :googly.moogly)
; => {:metric-type :counter, :metric-name :googly.moogly, :value 1}
```

The functions `increment`, `decrement`, etc really only vary in that
they provide different values for the `:metric-type`.

If you want to add custom metric attributes, just include key-value
pairs as arguments to the built-in metric functions:

```clojure
(s/increment :googly.moogly 1 :tags ["aws" "country:china"])
; => {:metric-type :counter, :metric-name :googly.moogly, :value 1, :tags ["aws" "country:china"]}
```

In the above example, we added the custom `:tags` attribute. In order
for this to actually get sent to the statsd server, you have to format
it and add it to the statsd string. I'll cover that soon.

In step 2, you format the metric as a statsd-compliant string:

```clojure
(s/base-formatter (s/increment :googly.moogly))
; => "googly.moogly:1|c"
```

Notice that the base formatter can't handle `:tags`:

```clojure
(s/base-formatter (s/increment :googly.moogly 1 :tags ["aws" "country:china"]))
; => "googly.moogly:1|c"
```

In step 3, you publish with `publish`. This actually takes the
formatter and metric as arguments:

```clojure
(s/publish s/base-formatter (s/increment :googly.moogly))
; => *magically sends data to server*
```

To use your own custom format, you need to create your own formatter
and, optionally, write your own functions that combine the steps as
`increment!`, `decrement!` et al. do.

Let's write a custom formatter:

```clojure
(ns example
  (:require [adzerk.statsd-client :as s]
            [clojure.string :as str]))

(defn tags [f]
  (fn [metric-attrs]
    (str (f metric-attrs) "|#" (str/join "," (:tags metric-attrs)))))

(def custom-formatter (tags base-formatter))
```

Formatters follow the middleware pattern. You call `tags` with another
formatter, `f`, as its argument. The retun value is a new function
that expects metric attributes and returns a formatted string.

The anonymous function that `tags` return calls `str` with 3
arguments. The first argument is `(f metric-attrs)`, which builds a
statsd string. If `f` is `base-formatter`, the value of the first
argument will be something like `"googly.moogly:1|c"`. The second
argument is `"|#"` - the pound sign indicates that tags are going to
follow. The third argument formats the tags. So the overall pattern
is, "format a string using `base-formatter`, then append tags." Here
are some examples the formatter in action:

```clojure
;; formatters return a string - this is just for demonstration purposes
(def cfincrement (comp custom-formatter s/increment))
(def cfdecrement (comp custom-formatter s/decrement))

(cfincrement :metric.name 1 :tags ["moop" "bloop"])
; => "metric.name:1|c|#moop,bloop"

;; value of -1 instead of 1 when decrementing
(cfdecrement :metric.name 1 :tags ["moop" "bloop"])
; => "metric.name:-1|c|#moop,bloop"
```

Aaaand here's how you could actually combine everything for
convenience:

```clojure
(def publish-custom (partial s/publish custom-formatter))
(def increment!     (comp publish-custom s/increment))
(def decrement!     (comp publish-custom s/decrement))
(def timer!         (comp publish-custom s/timer))
(def gauge!         (comp publish-custom s/gauge))
(def unique!        (comp publish-custom s/unique))
```
