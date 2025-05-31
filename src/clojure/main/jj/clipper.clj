(ns jj.clipper
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as logger]
            [clojure.java.io :as io])
  (:import (java.io InputStreamReader PushbackReader)
           (java.util.zip GZIPInputStream)
           (jj IpLocationRegistry)))


(def ip-mapper (atom nil))

(defn locate [ip]
  (when (nil? @ip-mapper)
    (reset! ip-mapper
            (-> (io/resource "map.edn.gz")
                io/input-stream
                (GZIPInputStream.)
                (InputStreamReader.)
                (PushbackReader.)
                edn/read
                (IpLocationRegistry.))))

  (when (instance? String ip)
    (if @ip-mapper
      (.locate ^IpLocationRegistry @ip-mapper ^String ip)
      (logger/error "Unable to find CSV file"))))

