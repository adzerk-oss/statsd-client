(ns statsd-client.core-test
  (:require [statsd-client.core :as sc]
            [clojure.test :refer :all]))

(def fincrement (comp sc/base-formatter sc/increment))
(def fdecrement (comp sc/base-formatter sc/decrement))
(def ftiming    (comp sc/base-formatter sc/timing))
(def fgauge     (comp sc/base-formatter sc/gauge))
(def funique    (comp sc/base-formatter sc/unique))

(deftest increment-format
  (is (= (fincrement :metric.name)       "metric.name:1|c"))
  (is (= (fincrement "metric.name")      "metric.name:1|c"))
  (is (= (fincrement :metric.name 2)     "metric.name:2|c"))
  (is (= (fincrement :metric.name 3.5)   "metric.name:3.5|c"))
  (is (= (fincrement :metric.name 1 0.3) "metric.name:1|c|@0.3")))

