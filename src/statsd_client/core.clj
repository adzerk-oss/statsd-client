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

;; value formatters - metric-name, value, metric-type, rate follow
;; middleware pattern with metric-name as the base case
(defn metric-name [opts]
  (name (:metric-name opts)))

(defn value [f]
  (fn [opts]
    (format "%s:%s" (f opts) (:value opts))))

(def metric-types
  {:gauge "g"
   :counter "c"
   :histogram "h"
   :timer "ms"
   :set "s"})
(defn metric-type [f]
  (fn [opts]
    (str (f opts) "|" (metric-types (:metric-type opts)))))

(defn rate [f]
  (fn [{:keys [rate] :as opts}]
    (if (and rate (not= rate 1.0))
      (format "%s|@%s" (f opts) rate)
      (f opts))))

(def base-formatter (-> metric-name value metric-type rate))

;; metrics
(defn base-vals
  [metric-type metric-name value opts]
  (merge {:metric-type metric-type
          :metric-name metric-name
          :value value}
         opts))

(defmacro defmetric
  "Captures commonalities among the metric functions. Provides default
  handling of metric name, value, and optional metric components"
  [name docstring metric-type & arities]
  `(defn ~name
     ~docstring
     ~@arities
     ([metric-name# value# & {:as opts#}]
      (base-vals ~metric-type metric-name# value# opts#))))

(defmetric increment
  "Increment a counter at specified rate, defaults to a one increment
  with a 1.0 rate"
  :counter
  ([metric-name] (increment metric-name 1)))

(defn decrement
  "Decrement a counter at specified rate, defaults to a one decrement
  with a 1.0 rate"
  ([metric-name]                    (increment metric-name -1))
  ([metric-name value]              (increment metric-name (* -1 value)))
  ([metric-name value & opts] (apply increment metric-name (* -1 value) opts)))

(defmetric timer
  "Time an event at specified rate, defaults to 1.0 rate"
  :timer)

(defmetric gauge
  "Send an arbitrary value."
  :gauge)

(defn unique
  "Send an event, unique occurences of which per flush interval
   will be counted by the statsd server. We have no rate call
   signature here because that wouldn't make much sense."
  [metric-name value & {:as opts}]
  (base-vals :set metric-name value opts))

;; timing
(defmacro with-sampled-timing
  "Time the execution of the provided code, with sampling."
  [publish metric-name rate & body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)]
     (~publish (timer ~metric-name (- (System/currentTimeMillis) start#) :rate ~rate))
    result#))

(defmacro with-timing
  "Time the execution of the provided code."
  [publish metric-name & body]
  `(with-sampled-timing ~publish ~metric-name 1.0 ~@body))

;; conveniently packaged defaults
(def publish-base (partial publish base-formatter))

(def increment!   (comp publish-base increment))
(def decrement!   (comp publish-base decrement))
(def timer!       (comp publish-base timer))
(def gauge!       (comp publish-base gauge))
(def unique!      (comp publish-base unique))
