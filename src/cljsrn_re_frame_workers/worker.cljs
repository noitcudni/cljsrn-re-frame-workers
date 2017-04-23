(ns cljsrn-re-frame-workers.worker
  (:require [cognitect.transit :as t]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-frame.router :refer [event-queue]]
            [re-frame.db :refer [app-db]]))

(defonce subscriptions (atom {}))                           ;; store all subscriptions here
(defonce tr (t/reader :json))                               ;; transit writer for converting json data to clj
(defonce tw (t/writer :json))                               ;; transit writer for converting clj data to json
(defonce trace false)                                       ;; enable for more logging

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Worker -> Main Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn send-reaction-results
  "Most communication with the main thread is forwarding reaction results.
  Wrap it up in a transit message and send it on!"
  [sub-v data-sub]
  (.log js/console "in the beginning of send-reaction-results..") ;;xxx
  (let [operation :reaction-results
        args {:sub-v sub-v
              :data  @data-sub}
        message (do
                  (.log js/console "right before assigning message")
                  [operation args])
        transit-message (do
                          (try
                            (t/write tw message)
                            (catch :default e
                              (.log js/console (str "exception: " (pr-str e)))
                              (.log js/console (str "exception keys: " (pr-str (.keys js/Object e))))
                              (.log js/console (str "exception: line: " (pr-str (.-line e))))
                              (.log js/console (str "exception: column: " (pr-str (.-column e))))
                              (.log js/console (str "exception: data: " (pr-str (.-data e))))

                              ;; (.log js/console (str "problematic message: " message))
                              )))]
    ;; (when trace (.log js/console "WORKER: Trace: Sending results to MAIN" (str transit-message)))
    (.log js/console (str "WORKER: Sending results to Main " (pr-str transit-message))) ;; xxx
    (.postMessage js/global transit-message)
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main -> Worker Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn receive-subscription
  "When the main thread issues a subscribe, subscribe in the worker,
  store it in the subscriptions atom, and deref it in a reagent track!
  statement so all future results get forwarded back to the Main thread."
  [sub-v]
  (.log js/console (str "WORKER: received subscription vector" (pr-str sub-v))) ;xxx
  (when-not (contains? @subscriptions sub-v)
    (let [new-sub (subscribe sub-v)]
      (when trace (.log js/console "WORKER: Trace: Reusults = " @new-sub))
      (.log js/console (str "WORKER: Results = " @new-sub)) ; xxx
      (swap! subscriptions assoc sub-v new-sub)
      (r/track! send-reaction-results sub-v new-sub))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Worker init and fn dispatch
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export on-message
  "Used for message dispatch with cljs-transit. Expects a vector with operation and args.
  Ex. [:subscribe [:hello]]"
  [transit-message]
  (let [message (t/read tr transit-message)
        operation (first message)
        args (first (rest message))]
    ;; (.log js/console "WORKER: Message received:" )
    (.log js/console (str "WORKER: Message received: operation: " operation)) ;xxx
    (case operation
      :subscribe (receive-subscription args)
      :dispatch (dispatch args)
      :dispatch-sync (dispatch-sync args)
      )))

(defn init-worker
  "Turn on console logging, tell the thread to listen for messages using the on-message fn
  and send a message back to the main thread indicating when the worker is ready."
  []
  (enable-console-print!)                                   ;; enable console printing
  (aset js/global "onmessage" on-message)
  (let [ready-transit-m (t/write tw [:worker-ready])]
    (.postMessage js/global ready-transit-m)                    ;; Send a message to the main thread indicating the worker is ready
    (.log js/console "WORKER: Ready")))
