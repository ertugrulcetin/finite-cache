(ns finite-cache.core
  (:require [finite-cache.util :as util]
            [finite-cache.clj-memory-meter :as memory]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s])
  (:import (java.util.concurrent ConcurrentHashMap
             TimeUnit
             Executors
             ScheduledExecutorService)))


(s/def ::size (s/and int? pos?))
(s/def ::measurement #{:byte :kb :mb :gb})
(s/def ::threshold (s/tuple ::size ::measurement))

(s/def ::interval (s/and int? pos?))
(s/def ::time-unit #{:milliseconds :seconds :minutes :hours :days})
(s/def ::every (s/tuple ::interval ::time-unit))

(s/def ::delay (s/and int? (complement neg?)))
(s/def ::await-timeout (s/and int? pos?))

(s/def ::options (s/keys :req-un [::threshold ::every]
                   :opt-un [::delay ::await-timeout]))


(defprotocol ICache
  (change-settings [this opts])
  (invalidate [this])
  (restart-executor [this])
  (shutdown-executor [this])
  (executor-state [this])
  (memo-fn [this])
  (cache [this])
  (size [this]))


(defn- get-threshold-in-bytes [[size type]]
  (case type
    :kb (* 1024 size)
    :mb (* 1024 1024 size)
    :gb (* 1024 1024 1024 size)
    size))


(defn- get-interval-and-time-unit [[interval unit-key]]
  [interval
   (case unit-key
     :milliseconds TimeUnit/MILLISECONDS
     :seconds TimeUnit/SECONDS
     :minutes TimeUnit/MINUTES
     :hours TimeUnit/HOURS
     :days TimeUnit/DAYS)])


(defn- ^ScheduledExecutorService create-executor [m opts]
  (let [service            (Executors/newSingleThreadScheduledExecutor)
        threshold-in-bytes (get-threshold-in-bytes (:threshold opts))
        [interval t-unit]  (get-interval-and-time-unit (:every opts))]
    (.scheduleAtFixedRate service
      (fn []
        (let [m-size (memory/measure m :bytes true)]
          (log/debug "Checking map size: " m-size)
          (when (>= m-size threshold-in-bytes)
            (.clear m)
            (log/debug "Cache cleared."))))
      (:delay opts) interval t-unit)
    service))


(deftype FiniteCache [f
                      ^ConcurrentHashMap m
                      ^:unsynchronized-mutable executor
                      ^:unsynchronized-mutable opts]
  ICache
  (change-settings [this opts*]
    (let [{:keys [delay await-timeout]
           :or   {delay 0 await-timeout 1000} :as opts*} opts*
          opts* (assoc opts* :delay delay :await-timeout await-timeout)]
      (shutdown-executor this)
      (set! opts opts*)
      (when (.isTerminated executor)
        (set! executor (create-executor m opts*)))))
  (memo-fn [_] f)
  (cache [_] m)
  (size [_] (memory/measure m :bytes true))
  (invalidate [_]
    (.clear m)
    m)
  (executor-state [_]
    (if (.isTerminated executor) :terminated :running))
  (restart-executor [this]
    (shutdown-executor this)
    (when (.isTerminated executor)
      (set! executor (create-executor m opts))))
  (shutdown-executor [_]
    (try
      (.shutdown executor)
      (when (.awaitTermination executor (:await-timeout opts) TimeUnit/MILLISECONDS)
        (.shutdownNow executor))
      (catch InterruptedException e
        (log/error e)
        (.shutdownNow executor)))))


(defn- check-opts [opts]
  (when-not (s/valid? ::options opts)
    (throw (IllegalArgumentException. ^String (s/explain-str ::options opts)))))


(defn finite-cache [f {:keys [delay await-timeout]
                       :or   {delay 0 await-timeout 1000} :as opts}]
  (check-opts opts)
  (let [memoize* (util/fast-memoize f)
        fn*      (:fn memoize*)
        map*     (:map memoize*)
        opts     (assoc opts :delay delay :await-timeout await-timeout)
        executor (create-executor map* opts)]
    (->FiniteCache fn* map* executor opts)))


(comment
  (def cache* (finite-cache + {;; when size of the map that holds cache exceeds 300MB, it will be cleared
                               :threshold     [300 :mb]
                               ;; executor that checks map's size every 15 minutes
                               :every         [15 :minutes]
                               ;; (Optional) initial delay before executor starts,
                               ;; since :every's time unit is :minutes, :delay has the same time unit
                               ;; so there is 5 minutes delay
                               :delay         5
                               ;; (Optional) await timeout when shutting down the executor, in milliseconds
                               :await-timeout 1500}))

  (def memoized-fn (memo-fn cache*))

  (memoized-fn 1 2 3)
  ;;=> 6

  (size cache*)
  ;; => 312 (cache's size)

  (cache cache*)
  ;; =>  {[1 2 3] 6} (cache itself)

  (invalidate cache*)
  ;; => {} (clears cache)

  (change-settings cache* {:threshold [1 :gb]
                           :every [2 :hours]})
  ;; => changes cache' options and restarts the executor

  (restart-executor cache*)
  ;; => restarts executor

  (shutdown-executor cache*)
  ;; => shutdowns executor

  (executor-state cache*)
  ;;=> :terminated
  )