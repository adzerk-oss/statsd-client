(ns statsd-client.core
  (:import [java.util Random])
  (:import [java.net DatagramPacket DatagramSocket InetAddress]))

(def
  ^{:doc "Atom holding the socket configuration"}
  cfg
  (atom nil))

(def
  ^{:doc "Agent holding the datagram socket"}
  sockagt
  (agent nil))

(defn setup
  "Initialize configuration"
  [host port & opts]
  (send sockagt #(or % (DatagramSocket.)))
  (swap! cfg #(or % (merge {:random (Random.)
                            :host   (InetAddress/getByName host)
                            :port   (if (integer? port) port (Integer/parseInt port))}
                           (apply hash-map opts)))))

(defn- send-packet
  ""
  [^DatagramSocket socket ^DatagramPacket packet]
  (try
    (doto socket (.send packet))
    (catch Exception e
      socket)))

(defn send-stat 
  "Send a raw metric over the network."
  [^String content]
  (when-let [packet (try
                      (DatagramPacket.
                       ^"[B" (.getBytes content)
                       ^Integer (count content)
                       ^InetAddress (:host @cfg)
                       ^Integer (:port @cfg))
                      (catch Exception e
                        nil))]
    (send sockagt send-packet packet)))

(defn publish
  "Send a metric over the network, based on the provided sampling rate.
  This should be a fully formatted statsd metric line."
  [formatter {:keys [rate] :or {rate 1.0} :as metric}]
  (let [prefix (:prefix @cfg)
        content (->> metric formatter (str prefix))]
    (cond
      (nil? @cfg) nil
      (or (>= rate 1.0)
          (<= (.nextDouble ^Random (:random @cfg)) rate)) (send-stat content))))

;; value formatters
(def metric-types
  {:gauge "g"
   :counter "c"
   :histogram "h"
   :timer "ms"
   :set "s"})

(defn metric-name [x]
  (name (:k x)))

(defn value [f]
  (fn [x]
    (format "%s:%s" (f x) (:v x))))

(defn metric-type [f]
  (fn [x]
    (str (f x) "|" (metric-types (:mt x)))))

(defn rate [f]
  (fn [{:keys [rate] :as x}]
    (if rate
      (format "%s|@%f" (f x) rate)
      (f x))))

(def format-base (-> metric-name value metric-type rate))

;; metrics
(defn base-vals [mt k v r x] (merge {:mt mt :k k :v v :r r} x))

(defmacro defmetric
  "Captures commonalities among the metric functions"
  [name docstring metric-type & arities]
  `(defn ~name
     ~docstring
     ~@arities
     ([k# v#] (~name k# v# 1.0))
     ([k# v# rate# & {:as x#}]
      (base-vals ~metric-type k# v# rate# x#))))

(defmetric increment
  "Increment a counter at specified rate, defaults to a one increment
  with a 1.0 rate"
  :counter
  ([k]        (increment k 1 1.0)))

(defn decrement
  "Decrement a counter at specified rate, defaults to a one decrement
  with a 1.0 rate"
  ([k]        (increment k -1 1.0))
  ([k v]      (increment k (* -1 v) 1.0))
  ([k v rate] (increment k (* -1 v) rate)))

(defmetric timing
  "Time an event at specified rate, defaults to 1.0 rate"
  :timing)

(defmetric gauge
  "Send an arbitrary value."
  :gauge)

(defn unique
  "Send an event, unique occurences of which per flush interval
   will be counted by the statsd server. We have no rate call
   signature here because that wouldn't make much sense."
  [k v & {:as x}]      (base-vals :mt set k v 1.0 x))
