(ns cljsrn-re-frame-workers.worker-api
  (:require [cljsrn-re-frame-workers.worker-utils :as worker-utils]
            [cognitect.transit :as t]
            [re-frame.core])
  (:require-macros [reagent.ratom :refer [reaction]]))

;; transit readers and writers
(defonce tr (t/reader :json))                               ;; transit writer for converting json data to clj
(defonce tw (t/writer :json))                               ;; transit writer for converting clj data to json

;;;;;;;  API  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; When in production pass all re-frame requests to the worker
;; When in debug force all data to go trough transit in order to
;; simulate catch potential serialization errors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce use-worker? (atom false))

(defn dispatch [dispatch-v]
  (if @use-worker?
    (worker-utils/dispatch dispatch-v)
    (re-frame.core/dispatch (->> dispatch-v
                                 (t/write tw)
                                 (t/read tr)))))

(defn dispatch-sync [dispatch-v]
  (if @use-worker?
    (worker-utils/dispatch-sync dispatch-v)
    (re-frame.core/dispatch-sync (->> dispatch-v
                                      (t/write tw)
                                      (t/read tr)))))

(defn subscribe [sub-v]
  (if @use-worker?
    (worker-utils/subscribe sub-v)
    (reaction (->> @(re-frame.core/subscribe sub-v)
                   (t/write tw)
                   (t/read tr)))))

(defn init-worker [worker-file ready-fn]
  (reset! use-worker? true)
  (worker-utils/init-worker worker-file ready-fn))
