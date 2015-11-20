(ns statsd-client.core-test
  (:require [statsd-client.core :refer :all]
            [clojure.test :refer :all]))

(def fincrement (comp base-formatter increment))
(def fdecrement (comp base-formatter decrement))
(def ftimer     (comp base-formatter timer))
(def fgauge     (comp base-formatter gauge))
(def funique    (comp base-formatter unique))

(deftest increment-format
  (is (= (fincrement :metric.name)             "metric.name:1|c"))
  (is (= (fincrement "metric.name")            "metric.name:1|c"))
  (is (= (fincrement :metric.name 2)           "metric.name:2|c"))
  (is (= (fincrement :metric.name 3.5)         "metric.name:3.5|c"))
  (is (= (fincrement :metric.name 1 :rate 0.3) "metric.name:1|c|@0.3")))

(deftest decrement-format
  (is (= (fdecrement :metric.name)             "metric.name:-1|c"))
  (is (= (fdecrement "metric.name")            "metric.name:-1|c"))
  (is (= (fdecrement :metric.name 2)           "metric.name:-2|c"))
  (is (= (fdecrement :metric.name 3.5)         "metric.name:-3.5|c"))
  (is (= (fdecrement :metric.name 1 :rate 0.3) "metric.name:-1|c|@0.3")))

(deftest timer-format
  (is (= (ftimer :metric.name 1)           "metric.name:1|ms"))
  (is (= (ftimer "metric.name" 1)          "metric.name:1|ms"))
  (is (= (ftimer :metric.name 3.5)         "metric.name:3.5|ms"))
  (is (= (ftimer :metric.name 1 :rate 0.3) "metric.name:1|ms|@0.3")))

(deftest gauge-format
  (is (= (fgauge :metric.name 1)           "metric.name:1|g"))
  (is (= (fgauge "metric.name" 1)          "metric.name:1|g"))
  (is (= (fgauge :metric.name 3.5)         "metric.name:3.5|g"))
  (is (= (fgauge :metric.name "xyz")       "metric.name:xyz|g"))
  (is (= (fgauge :metric.name 1 :rate 0.3) "metric.name:1|g|@0.3")))

(deftest unique-format
  (is (= (funique :metric.name 1)           "metric.name:1|s"))
  (is (= (funique "metric.name" 1)          "metric.name:1|s"))
  (is (= (funique :metric.name 3.5)         "metric.name:3.5|s"))
  (is (= (funique :metric.name "xyz")       "metric.name:xyz|s"))
  (is (= (funique :metric.name 1 :rate 0.3) "metric.name:1|s|@0.3")))

(use-fixtures :each (fn [f] (setup "localhost" 8125) (f)))

(defmacro should-send-expected-stat
  "Assert that the expected stat is passed to the send-stat method
   the expected number of times."
  [expected min-times max-times & body]
  `(let [counter# (atom 0)]
    (with-redefs
      [send-stat (fn [stat#]
                   (is (= ~expected stat#))
                   (swap! counter# inc))]
      ~@body)
    (is (and (>= @counter# ~min-times) (<= @counter# ~max-times)) (str "send-stat called " @counter# " times"))))

(deftest should-send-increment
  (should-send-expected-stat "gorets:1|c" 3 3
    (increment! "gorets")
    (increment! :gorets)
    (increment! "gorets", 1))
  (should-send-expected-stat "gorets:7|c" 1 1
    (increment! :gorets 7))
  (should-send-expected-stat "gorets:1.1|c" 1 1
    (increment! :gorets 1.1)))

(deftest should-send-decrement
  (should-send-expected-stat "gorets:-1|c" 3 3
    (decrement! "gorets")
    (decrement! :gorets)
    (decrement! "gorets", 1))
  (should-send-expected-stat "gorets:-7|c" 1 1
    (decrement! :gorets 7))
  (should-send-expected-stat "gorets:-1.1|c" 1 1
    (decrement! :gorets 1.1)))

(deftest should-send-gauge
  (should-send-expected-stat "gaugor:333|g" 3 3
    (gauge! "gaugor" 333)
    (gauge! :gaugor 333)
    (gauge! "gaugor" 333 :rate 1.0))
  (should-send-expected-stat "guagor:1.1|g" 1 1
    (gauge! :guagor 1.1)))

(deftest should-send-unique
  (should-send-expected-stat "unique:765|s" 2 2
    (unique! "unique" 765)
    (unique! :unique 765)))

(deftest should-send-timer-with-default-rate
  (should-send-expected-stat "glork:320|ms" 2 2
    (timer! "glork" 320)  
    (timer! :glork 320)))

(deftest should-send-timing-with-provided-rate
  (should-send-expected-stat "glork:320|ms|@0.99" 1 10
    (dotimes [n 10] (timer! "glork" 320 :rate 0.99))))

(deftest should-not-send-stat-without-cfg
  (with-redefs [cfg (atom nil)]
    (should-send-expected-stat "gorets:1|c" 0 0 (increment! "gorets"))))

(deftest should-time-code
  (let [cnt (atom 0)
        publisher (fn [& args])]
    (with-redefs [timer
                  (fn [metric-name value & {:keys [rate]}]
                    (is (= "test.time" metric-name))
                    (is (>= value 200))
                    (is (= 1.0 rate))
                    (swap! cnt inc))]
      (with-timing publisher "test.time"
        (Thread/sleep 200))
      (with-sampled-timing publisher "test.time" 1.0
        (Thread/sleep 200))
      (is (= @cnt 2)))))

(deftest should-prefix
  (with-redefs [cfg (atom nil)]
    (setup "localhost" 8125 :prefix "test.stats.")
    (should-send-expected-stat "test.stats.gorets:1|c" 2 2
      (increment! "gorets")
      (increment! :gorets))))
