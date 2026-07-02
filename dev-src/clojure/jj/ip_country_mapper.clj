(ns jj.ip-country-mapper
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jj.iso.countries :as countries]))
(defn download [url dest]
  (log/info "Starting download from" url)
  (io/copy (io/input-stream url) (io/file dest))
  (log/info "Finished downloading CSV to" dest))
(defn parse-line
  "Parse a CSV line into entries of [a b c n country weight].
  weight is nil for networks of /24 or shorter (n full /24 blocks);
  for longer prefixes n is 1 and weight is the address count, so the
  country covering most of the /24 can win. Networks shorter than /8
  are split into one entry per first octet."
  [line]
  (let [[cidr _ _ _ cc] (str/split line #",")]
    (when-let [country (countries/alpha-2->name cc)]
      (let [[ip plen] (str/split cidr #"/")
            [a b c _] (map #(Integer/parseInt %) (str/split ip #"\."))
            plen (Integer/parseInt plen)]
        (if (<= plen 24)
          (loop [a a
                 start (+ (* b 256) c)
                 remaining (bit-shift-left 1 (- 24 plen))
                 acc []]
            (if (or (zero? remaining) (> a 255))
              acc
              (let [n (min remaining (- 65536 start))]
                (recur (inc a) 0 (- remaining n)
                       (conj acc [a (quot start 256) (rem start 256) n country nil])))))
          [[a b c 1 country (bit-shift-left 1 (- 32 plen))]])))))
(defn rle [^objects arr]
  (loop [i (int 0) res (transient [])]
    (if (>= i 256)
      (persistent! res)
      (let [v (aget arr i)
            end (loop [j (inc i)] (if (and (< j 256) (= (aget arr j) v)) (recur (inc j)) j))]
        (recur (int end) (conj! (conj! res (- end i)) v))))))
(defn simplify [v]
  (if (and (= 2 (count v)) (= 256 (first v))) (second v) v))
(defn process-octet [entries]
  (let [^"[[Ljava.lang.Object;" arr (into-array (repeatedly 256 #(object-array (repeat 256 :not-assigned))))]
    (doseq [[_ b c n country weight] entries
            :when (nil? weight)]
      (loop [i (long 0) bb (long b) cc (long c)]
        (when (and (< i (long n)) (< bb 256))
          (let [^objects row (aget arr (int bb))]
            (aset row (int cc) country))
          (let [nc (inc cc)]
            (if (< nc 256)
              (recur (inc i) bb nc)
              (recur (inc i) (inc bb) 0))))))
    (let [weighted (reduce (fn [m [_ b c _ country weight]]
                             (if weight
                               (update-in m [[b c] country] (fnil + 0) weight)
                               m))
                           {} entries)]
      (doseq [[[b c] countries->weight] weighted]
        (let [max-weight (apply max (vals countries->weight))
              winner (->> countries->weight
                          (filter #(= max-weight (val %)))
                          (map key)
                          (sort-by name)
                          first)]
          (aset ^objects (aget arr (int b)) (int c) winner))))
    (let [l2 (mapv (fn [b] (simplify (rle ^objects (aget arr (int b))))) (range 256))]
      (simplify (vec (rle (object-array l2)))))))

(defn create-map-edn-files []
  (let [url "https://raw.githubusercontent.com/datasets/geoip2-ipv4/master/data/geoip2-ipv4.csv"
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
                           (reduce (fn [m e] (update m (first e) (fnil conj []) e))
                                   m (parse-line line)))
                         {} (rest (line-seq r))))]
      (log/info "Finished parsing CSV -" (count by-a) "octets found")
      (log/info "Processing and writing EDN files...")
      (doseq [[a entries] by-a]
        (spit (str out-dir "/" a ".edn") (pr-str (process-octet entries)))
        (log/info "Wrote" (str a ".edn")))
      (spit (str out-dir "/127.edn") ":localhost")
      (log/info "Wrote 127.edn (localhost)")
      (log/info "Finished generating all EDN files"))))

(defn -main [& _] (create-map-edn-files))

(create-map-edn-files)
