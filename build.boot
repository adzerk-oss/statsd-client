(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.7.0"]])

(def +version+ "1.0.0")

(task-options!
 pom  {:project     'adzerk/statsd-client
       :version     +version+
       :description "A sweet statsd client"
       :url         "https://github.com/adzerk-oss/statsd-client"
       :scm         {:url "https://github.com/adzerk-oss/statsd-client"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
