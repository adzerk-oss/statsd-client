(ns statsd-client.core-test
  (:require [statsd-client.core :as sc]
            [clojure.test :refer :all]))

(def fincrement (comp sc/base-formatter sc/increment))
(def fdecrement (comp sc/base-formatter sc/decrement))
(def ftimer     (comp sc/base-formatter sc/timer))
(def fgauge     (comp sc/base-formatter sc/gauge))
(def funique    (comp sc/base-formatter sc/unique))

(deftest increment-format
  (is (= (fincrement :metric.name)       "metric.name:1|c"))
  (is (= (fincrement "metric.name")      "metric.name:1|c"))
  (is (= (fincrement :metric.name 2)     "metric.name:2|c"))
  (is (= (fincrement :metric.name 3.5)   "metric.name:3.5|c"))
  (is (= (fincrement :metric.name 1 :rate 0.3) "metric.name:1|c|@0.3")))

(deftest decrement-format
  (is (= (fdecrement :metric.name)       "metric.name:-1|c"))
  (is (= (fdecrement "metric.name")      "metric.name:-1|c"))
  (is (= (fdecrement :metric.name 2)     "metric.name:-2|c"))
  (is (= (fdecrement :metric.name 3.5)   "metric.name:-3.5|c"))
  (is (= (fdecrement :metric.name 1 :rate 0.3) "metric.name:-1|c|@0.3")))

(deftest timer-format
  (is (= (ftimer :metric.name 1)     "metric.name:1|ms"))
  (is (= (ftimer "metric.name" 1)    "metric.name:1|ms"))
  (is (= (ftimer :metric.name 3.5)   "metric.name:3.5|ms"))
  (is (= (ftimer :metric.name 1 :rate 0.3) "metric.name:1|ms|@0.3")))

(deftest gauge-format
  (is (= (fgauge :metric.name 1)     "metric.name:1|g"))
  (is (= (fgauge "metric.name" 1)    "metric.name:1|g"))
  (is (= (fgauge :metric.name 3.5)   "metric.name:3.5|g"))
  (is (= (fgauge :metric.name "xyz") "metric.name:xyz|g"))
  (is (= (fgauge :metric.name 1 :rate 0.3) "metric.name:1|g|@0.3")))

(deftest unique-format
  (is (= (funique :metric.name 1)     "metric.name:1|s"))
  (is (= (funique "metric.name" 1)    "metric.name:1|s"))
  (is (= (funique :metric.name 3.5)   "metric.name:3.5|s"))
  (is (= (funique :metric.name "xyz") "metric.name:xyz|s"))
  (is (= (funique :metric.name 1 :rate 0.3) "metric.name:1|s|@0.3")))

