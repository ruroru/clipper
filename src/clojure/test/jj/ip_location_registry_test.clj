(ns jj.ip-location-registry-test
  (:require
    [clojure.test :refer [are deftest]])
  (:import (jj IpLocationRegistry)))



(deftest tree-test
  (let [ip-map {"5.0.0.0/7"          :de
                "6.0.0.0/8"          :us
                "127.0.0.1/32"       :localhost
                "255.255.255.255/32" :biggest-ip
                "0.0.0.0/32"         :smallest-ip}
        ip-tree ^IpLocationRegistry (IpLocationRegistry. ip-map)]
    (are [ip expected-location] (= expected-location (.locate ip-tree ip))
                                "4.0.0.0" :de
                                "5.255.255.255" :de
                                "127.0.0.1" :localhost
                                "6.0.0.0" :us
                                "6.255.255.255" :us
                                "6.0.2.1" :us
                                "255.255.255.255" :biggest-ip
                                "0.0.0.0" :smallest-ip)
    (are [ip] (nil? (.locate ip-tree ip))
              "3.255.255.255"
              "127.0.0.2"
              "127.0.0.0")))