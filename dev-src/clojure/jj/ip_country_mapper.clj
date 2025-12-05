(ns jj.ip-country-mapper
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jj.iso.countries :as countries]))
(defn download [url dest]
  (log/info "Starting download from" url)
  (io/copy (io/input-stream url) (io/file dest))
  (log/info "Finished downloading CSV to" dest))
(defn parse-line [line]
  (let [[cidr _ _ _ cc] (str/split line #",")]
    (when-let [country (countries/alpha-2->name cc)]
      (let [[ip plen] (str/split cidr #"/")
            [a b c _] (map #(Integer/parseInt %) (str/split ip #"\."))
            plen (Integer/parseInt plen)
            n (bit-shift-left 1 (max 0 (- 24 plen)))]
        [a b c n country]))))
(defn rle [arr]
  (loop [i 0 res (transient [])]
    (if (>= i 256)
      (persistent! res)
      (let [v (aget arr i)
            end (loop [j (inc i)] (if (and (< j 256) (= (aget arr j) v)) (recur (inc j)) j))]
        (recur end (conj! (conj! res (- end i)) v))))))
(defn simplify [v]
  (if (and (= 2 (count v)) (= 256 (first v))) (second v) v))
(defn process-octet [entries]
  (let [^"[[Ljava.lang.Object;" arr (into-array (repeatedly 256 #(object-array (repeat 256 :not-assigned))))]
    (doseq [[_ b c n country] entries]
      (loop [i 0 bb b cc c]
        (when (and (< i n) (< bb 256))
          (aset (aget arr bb) cc country)
          (let [nc (inc cc)]
            (if (< nc 256)
              (recur (inc i) bb nc)
              (recur (inc i) (inc bb) 0))))))
    (let [l2 (mapv (fn [b] (simplify (rle (aget arr b)))) (range 256))]
      (simplify (vec (rle (object-array l2)))))))

(defn create-map-edn-files []
  (let [url "https://r2.datahub.io/clt98minh000ol708cfwakoxt/main/raw/data/geoip2-ipv4.csv"
        csv-file "./target/file.csv"
        out-dir "./src/resources/jj/clipper"]
    (log/info "Starting IP country mapper generation")
    (when-not (.exists (io/file csv-file))
      (download url csv-file))
    (.mkdirs (io/file out-dir))
    (log/info "Created output directory" out-dir)
    (log/info "Parsing CSV file...")
    (let [by-a (with-open [r (io/reader csv-file)]
                 (reduce (fn [m line]
                           (if-let [e (parse-line line)]
                             (update m (first e) (fnil conj []) e) m))
                         {} (rest (line-seq r))))]
      (log/info "Finished parsing CSV -" (count by-a) "octets found")
      (log/info "Processing and writing EDN files...")
      (doseq [[a entries] by-a]
        (spit (str out-dir "/" a ".edn") (pr-str (process-octet entries)))
        (log/info "Wrote" (str a ".edn")))
      (spit (str out-dir "/127.edn") "[1 [1 [1 :not-assigned 1 :localhost]]]")
      (log/info "Wrote 127.edn (localhost)")
      (log/info "Finished generating all EDN files"))))

(defn -main [& _] (create-map-edn-files))

(create-map-edn-files)