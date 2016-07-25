(ns adzerk.statsd-client.datadog
  "Add tags middleware for datadog happiness"
  (:require [adzerk.statsd-client :as c]
            [clojure.string :as str]))

(defprotocol FormatTags
  (format-tags [tags] "format the tags"))

(extend-protocol FormatTags
  clojure.lang.PersistentVector
  (format-tags [tags] (str/join "," tags))

  clojure.lang.PersistentArrayMap
  (format-tags [tags] (->> tags
                           (mapv (fn [[k v]]
                                   (if (nil? v)
                                     (name k)
                                     (str (name k) ":" v))))
                           format-tags))

  java.lang.Object
  (format-tags [_] ""))

(defn tags
  "Tags can be a map or vector:
   {:env \"production\" :chat nil} ; => |#env:production,chat
   [\"env:production\", \"chat\"]  ; => |#env:production,chat"
  [f]
  (fn [metric-attrs]
    (str (f metric-attrs) "|#" (format-tags (:tags metric-attrs)))))

(def tag-formatter (tags c/base-formatter))

(def publish-tag (partial c/publish tag-formatter))
(def increment!  (comp publish-tag c/increment))
(def decrement!  (comp publish-tag c/decrement))
(def timer!      (comp publish-tag c/timer))
(def gauge!      (comp publish-tag c/gauge))
(def unique!     (comp publish-tag c/unique))
