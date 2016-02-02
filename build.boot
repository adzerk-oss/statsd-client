(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]])

(def +version+ "0.1.0")

(require '[adzerk.bootlaces :refer :all])

(bootlaces! +version+)

(task-options!
 pom  {:project     'adzerk/statsd-client
       :version     +version+
       :description "A sweet extensible statsd client"
       :url         "https://github.com/adzerk-oss/statsd-client"
       :scm         {:url "https://github.com/adzerk-oss/statsd-client"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
