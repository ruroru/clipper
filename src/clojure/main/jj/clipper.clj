(ns jj.clipper
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.tools.logging :as logger]))

(def ^:private octet-cache
  "Cache for loaded octet range data"
  (atom {}))

(defn- load-octet-data [octet]
  "Load and cache octet data from EDN file"
  (if-let [cached (get @octet-cache octet)]
    cached
    (if-let [resource (io/resource (str "jj/clipper/" octet ".edn"))]
      (let [content (-> resource
                        slurp
                        str/trim
                        edn/read-string)]
        (swap! octet-cache assoc octet content)
        content)
      (do
        (logger/error "Unable to find " (str "jj/clipper/" octet ".edn"))
        nil))))

(defn- parse-octets [ip]
  (when ip
    (mapv #(Integer/parseInt %) (str/split ip #"\."))))

(defn- get-first-octet [ip]
  (when ip
    (-> ip
        (str/split #"\.")
        first)))

(defn- find-location [ranges octets]
  (when (and (seq ranges) (seq octets))
    (let [octet-value (first octets)
          remaining-octets (rest octets)]
      (loop [remaining ranges
             accumulated 0]
        (when (seq remaining)
          (let [[range-size location] (take 2 remaining)
                new-accumulated (+ accumulated range-size)]
            (if (< octet-value new-accumulated)
              (cond
                (vector? location)
                (find-location location remaining-octets)

                (= location :not-assigned)
                nil

                :else
                location)
              (recur (drop 2 remaining) new-accumulated))))))))

(defn locate [ip]
  (when (some? ip)
    (when-let [octet (get-first-octet ip)]
      (when-let [content (load-octet-data octet)]
        (if (keyword? content)
          content
          (when-let [octets (parse-octets ip)]
            (find-location content (rest octets))))))))
