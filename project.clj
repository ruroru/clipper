(defproject org.clojars.jj/clipper "1.0.0-SNAPSHOT"
  :description "clipper a high-performance, zero-dependency solution for determine country of origin of a  given IP."
  :url "https://github.com/ruroru/clipper"
  :license {:name "EPL-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojars.jj/iso-countries "1.2.3"]
                 [org.clojure/tools.logging "1.3.0"]]
  :java-source-paths ["src/java/main"]
  :source-paths ["src/clojure/main"]
  :test-paths ["src/clojure/test"]

  :resource-paths ["src/resources"]

  :profiles {:dev  {:aot          :all
                    :dependencies [[org.clojure/data.csv "1.1.0"]]
                    :source-paths ["dev-src/clojure"]
                    }
             :test {:resource-paths ["src/test-resources"]}}

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :plugins [[org.clojars.jj/bump-md "1.0.0"]
            [org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/strict-check "1.0.2"]])
