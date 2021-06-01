(ns theta.util
  (:require
    [theta.log :refer [debug info warn error]]
    #?(:clj [dotenv]))
  #?(:cljs (:require-macros [theta.util]))
  #?(:clj (:import [java.util Date]
                   [java.math RoundingMode BigDecimal])))

#?(:clj (defmacro app-env [] dotenv/app-env))
(println "APP_ENV" (theta.util/app-env))

(defn parse-date [date-str]
  (when date-str
    #?(:clj (when-let [daten (try
                               (Date/parse date-str)
                               (catch Exception ex (warn "parse-date failed for: " date-str (type str))))]
              (Date. ^long daten)))))

(defn parse-double [str]
  (when str
    #?(:clj (try
              (Double/parseDouble str)
              (catch Exception ex (warn "parse-double failed for: " str (type str)))))))

(defn parse-long [str]
  (when str
    #?(:clj (try
              (Long/parseLong ^String (re-find #"\d+" str))
              (catch Exception ex (warn "parse-long failed for: " str (type str)))))))

(defn parse-int [s]
  #?(:clj
      (Integer/parseInt ^String (re-find #"\d+" s))))

(defn parse-bigdec [str]
  (when str
    #?(:clj (try
              (BigDecimal. ^String str)
              (catch Exception ex (warn "parse-bigdec failed for: " str (type str)))))))

(defn parse-bool [str]
  (try
    (cond
      (boolean? str)
      str
      (or (= str "0") (= str "false"))
      false
      :else
      #?(:clj (Boolean/parseBoolean str)
         :cljs (js/Boolean str)))
    #?(:clj (catch Exception ex (warn "failed to parse boolean: " str (type str)))
       :cljs (catch js/Object ex (warn "failed to parse boolean: " str (type str))))))

#?(:clj (defn get-scale [vals]
          (apply
            max (for [val vals]
                  (.scale (bigdec val))))))

#?(:clj (defn max-precision [bigvals]
          (->> bigvals
            (map #(.precision %))
            (apply max))))

#?(:clj (defn big-mean [vals]
          (let [total ^BigDecimal (reduce + vals)
                scale (get-scale vals)
                prec (max-precision vals)
                cnt ^int (count vals)
                numer ^BigDecimal (.setScale total scale RoundingMode/HALF_EVEN)]
            (with-precision prec
              :rounding RoundingMode/HALF_EVEN
              (/ numer cnt)))))