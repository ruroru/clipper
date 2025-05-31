(ns jj.ip-country-mapper
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [jj.iso.countries :as countries]))

(defn parse-csv-data [file-resource]
  (with-open [reader (io/reader file-resource)]
    (let [csv-data (csv/read-csv reader)]
      (into {}
            (doall
              (map (fn [row]
                     (let [network-str (first row)
                           alpha-2-name (nth row 4)]
                       [network-str (countries/alpha-2->name alpha-2-name)]))
                   (rest csv-data)))))))

(defn download-file
  [file-url download-location]
  (try
    (with-open [in (io/input-stream file-url)
                out (io/output-stream download-location)]
      (io/copy in out))
    (catch Exception e
      (println (str "Error downloading file: " (.getMessage e))))))

(defn create-file []
  (let [url "https://r2.datahub.io/clt98minh000ol708cfwakoxt/main/raw/data/geoip2-ipv4.csv"
        csv-file-location "./target/file.csv"]
    (download-file url csv-file-location)
    (let [parsed-data (parse-csv-data "./target/file.csv")]
      (spit "./src/resources/map.edn" (assoc parsed-data "127.0.0.1/32" :localhost)))))

(create-file)