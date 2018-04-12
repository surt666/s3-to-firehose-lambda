(ns s3-to-firehose-lambda.core
  (:gen-class
   :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler])
  (:require [amazonica.aws.s3 :refer [get-object]]
           ; [amazonica.aws.sns :refer [publish]]
            [amazonica.aws.kinesisfirehose :as fh]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f])
  (:import (java.io ByteArrayInputStream)))

(defn mk-req-handler
  "Makes a request handler"
  [f & [wrt]]
  (fn [this is os context]
    (let [w (io/writer os)
             res (-> (parse-stream (io/reader is) keyword)
                  f)]
      (prn "R" res)
      ((or wrt
           (fn [res w] (.write w (prn-str res))))
        res w)
      (.flush w))))

(defn insert [m]
  (prn "M" m)
  (let [bucket "opgave-aggregates"
        keys (map #(get-in % [:s3 :object :key]) (:Records m))
        objects (flatten (map #(line-seq (io/reader (:input-stream (get-object :bucket-name bucket :key %)))) keys))]
    (prn "O" objects)
    (mapv #(fh/put-record "opgaver" %) objects)))

(def -handleRequest (mk-req-handler insert))
