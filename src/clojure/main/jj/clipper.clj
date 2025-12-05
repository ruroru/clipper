(ns jj.clipper
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.tools.logging :as logger]))

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
      (if-let [resource (io/resource (str "jj/clipper/" octet ".edn"))]
        (let [content (-> resource
                          slurp
                          str/trim
                          edn/read-string)]
          (if (keyword? content)
            content
            (when-let [octets (parse-octets ip)]
              (find-location content (rest octets)))))
        (logger/error "Unable to find " (str "jj/clipper/" octet ".edn"))))))