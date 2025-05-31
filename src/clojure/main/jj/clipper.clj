(ns jj.clipper
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as logger]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)
           (jj IpLocationRegistry)))


(def ip-mapper (atom nil))

(defn locate [ip]
  (when (nil? @ip-mapper)
    (reset! ip-mapper
            (IpLocationRegistry. (edn/read (PushbackReader. (io/reader (io/resource "map.edn")))))))

  (when (instance? String ip)
    (if @ip-mapper
      (.locate ^IpLocationRegistry @ip-mapper ^String ip)
      (logger/error "Unable to find CSV file"))))

