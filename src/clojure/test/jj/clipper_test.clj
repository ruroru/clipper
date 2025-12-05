(ns jj.clipper-test
  (:require
    [clojure.test :refer [are deftest]]
    [jj.clipper :as clipper]))


(deftest clipper-locate-test
  (are [ip expected-location]
    (= expected-location (clipper/locate ip))
    nil nil
    "254.255.255.255" nil
    "250.255.255.255" :sweden
    "251.0.0.0" :sweden
    "251.100.0.0" :sweden
    "251.127.0.0" :sweden
    "251.128.0.0" :lithuania
    "251.200.0.0" :lithuania
    "251.255.0.0" :lithuania
    "50.0.0.0" :norway
    "50.0.128.1" :united-states
    "50.128.0.0" :denmark
    "50.128.128.0" :taiwan
    "252.0.128.1" nil
    "253.0.0.0" :norway
    "253.0.0.100" :norway
    "253.0.0.127" :norway
    "253.0.0.128" :finland
    "253.0.0.200" :finland
    "253.0.0.227" :finland
    "253.0.0.228" :estonia
    "253.0.0.230" :estonia
    "253.0.0.255" :estonia
    "252.0.127.255" :norway
    "252.0.128.0" nil
    "252.0.128.255" nil
    "252.0.129.0" :united-states
    ))

