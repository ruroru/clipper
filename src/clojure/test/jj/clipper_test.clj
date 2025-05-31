(ns jj.clipper-test
  (:require
    [clojure.test :refer [are deftest]]
    [jj.clipper :as clipper]))


(deftest clipper-locate-test
  (are [ip expected-location]
    (= expected-location (clipper/locate ip))
    "127.0.0.1" :localhost))


