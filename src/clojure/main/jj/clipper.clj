(ns jj.clipper
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)
           (jj IpTree))
  (:gen-class))

(def ^:private  ipv4-tree (IpTree. (edn/read (PushbackReader. (io/reader (io/resource "map.edn"))))))

(defn locate [ip]
  (if ipv4-tree
    (.locate ^IpTree ipv4-tree ^String ip)
    (println "Unable to find CSV file")))

