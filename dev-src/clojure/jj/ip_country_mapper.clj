(ns jj.ip-country-mapper
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]
            [clojure.pprint :as pprint]
            [jj.iso.countries :as countries]))

(defn parse-csv-data [file-resource]
  (with-open [reader (io/reader file-resource)]
    (let [csv-data (csv/read-csv reader)]
      (doall
        (map (fn [row]
               (let [network-str (first row)
                     alpha-2-name (nth row 4)]
                 [network-str (countries/alpha-2->name alpha-2-name)]))
             (rest csv-data))))))

(defn download-file
  [file-url download-location]
  (try
    (with-open [in (io/input-stream file-url)
                out (io/output-stream download-location)]
      (io/copy in out))
    (catch Exception e
      (logger/error (str "Error downloading file: " (.getMessage e))))))

(defn get-first-octet [network-str]
  (-> network-str
      (clojure.string/split #"\.")
      first))

(defn group-by-first-octet [parsed-data]
  (reduce (fn [acc [network-str country]]
            (let [octet (get-first-octet network-str)]
              (update acc octet (fnil conj []) [network-str country])))
          {}
          parsed-data))

(defn ip->long [ip]
  (let [parts (mapv #(Long/parseLong %) (str/split ip #"\."))]
    (+ (bit-shift-left (parts 0) 24)
       (bit-shift-left (parts 1) 16)
       (bit-shift-left (parts 2) 8)
       (parts 3))))

(defn cidr->range [cidr]
  (let [[ip prefix] (str/split cidr #"/")
        prefix-len (Long/parseLong prefix)
        start (ip->long ip)
        mask (bit-and (bit-shift-left 0xFFFFFFFF (- 32 prefix-len)) 0xFFFFFFFF)
        network-start (bit-and start mask)
        network-end (bit-or network-start (bit-and (bit-not mask) 0xFFFFFFFF))]
    [network-start network-end]))

(defn ip-allocation-nested [cidr-map]
  (let [grouped (group-by val cidr-map)]
    (reduce-kv (fn [[countries counts] country cidrs]
                 (let [ip-counts (mapv (fn [[cidr _]]
                                         (let [[start end] (cidr->range cidr)]
                                           (inc (- end start))))
                                       cidrs)
                       count-val (if (= 1 (count ip-counts))
                                   (first ip-counts)
                                   ip-counts)]
                   [(conj countries country)
                    (conj counts count-val)]))
               [[] []]
               grouped)))

(defn create-map-edn-files []
  (let [url "https://r2.datahub.io/clt98minh000ol708cfwakoxt/main/raw/data/geoip2-ipv4.csv"
        csv-file-location "./target/file.csv"
        output-dir "./src/resources/ip-maps"]
    (download-file url csv-file-location)

    (.mkdirs (io/file output-dir))

    (let [parsed-data (parse-csv-data csv-file-location)
          grouped-data (group-by-first-octet parsed-data)]

      (doseq [[octet entries] grouped-data]
        (let [file-path (str output-dir "/" octet ".edn")
              data-map (into {} entries)]
          (with-open [w (io/writer file-path)]
            (binding [*out* w]
              (pprint/pprint (ip-allocation-nested data-map) )))
          (logger/info (str "Written " (count entries) " entries to " file-path))))

      (let [localhost-file (str output-dir "/127.edn")]
        (with-open [w (io/writer localhost-file)]
          (binding [*out* w]
            (pprint/pprint (merge (read-string (slurp localhost-file))
                                  {"127.0.0.1/32" :localhost}))))))))

(defn -main [& _]
  (create-map-edn-files))