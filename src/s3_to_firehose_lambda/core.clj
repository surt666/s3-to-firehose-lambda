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

(def s3-bucket (System/getenv "s3_bucket_source"))

(def hose (System/getenv "fh_target"))

(def filter-func (System/getenv "filter_func_name"))

(defn filter1 [m]
  (prn "M" m)
  {:vejnavn (get-in m [:vurderingsejendom :adresse :vejnavn])
   :husnr (get-in m [:vurderingsejendom :adresse :husnr])
   :postnr (get-in m [:vurderingsejendom :adresse :postnr])
   :doer (get-in m [:vurderingsejendom :adresse :doer])
   :etage (get-in m [:vurderingsejendom :adresse :etage])
   :vurderingsejendomid (get-in m [:vurderingsejendom :vurderingsejendomid])})

(defn filter2 [m]
  (assoc m :test2 "bar"))

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
  (prn "I" m)
  (let [bucket s3-bucket
        keys (map #(get-in % [:s3 :object :key]) (:Records m))
        func (cond
               (= filter-func "filter1") filter1
               (= filter-func "filter2") filter2)
        objects (flatten (mapv #(line-seq (io/reader (:input-stream (get-object :bucket-name bucket :key %)))) keys))
        _ (prn "O1" objects)
        filtered-objects (mapv #(func (decode % true)) objects)]
    (prn "O2" filtered-objects)
    (mapv #(fh/put-record hose  (encode %)) filtered-objects)))

(def -handleRequest (mk-req-handler insert))
