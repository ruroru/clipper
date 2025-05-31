(ns jj.clipper-test
  (:require
    [clojure.test :refer [are is deftest]]
    [jj.clipper :as clipper])
  (:import (java.io File)))


(deftest clipper-locate-test
  (are [ip expected-location]
    (= expected-location (clipper/locate ip))
    "127.0.0.1" :localhost
    "1.1.1.1" :australia
    "8.8.8.8" :united-states))


(deftest negative-locate-tests
  (are [ip] (nil? (clipper/locate ip))
            :keyword
            nil
            1
            "1.0.0.256"
            " 1.0.0.252"
            "1.0.0.252 "
            "1.0.0 .252"
            "1.0. 0.252"
            "1.0.0"
            "1"
            ""
            "256.0.0.0"
            "256.-1.0.0"
            "251.0.0.0.2"
            ))

(deftest validate-map-edn-exists
  (is (.exists (File. "./src/resources/map.edn"))))

