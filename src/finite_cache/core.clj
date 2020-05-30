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


;;TODO logs -> debug
(defn- ^ScheduledExecutorService create-executor [m opts]
  (let [service            (Executors/newSingleThreadScheduledExecutor)
        threshold-in-bytes (get-threshold-in-bytes (:threshold opts))
        [interval t-unit] (get-interval-and-time-unit (:every opts))]
    (.scheduleAtFixedRate service
      (fn []
        (let [m-size (memory/measure m :bytes true)]
          (log/info "Checking map size: " m-size)
          (when (>= m-size threshold-in-bytes)
            (.clear m)
            (log/info "Cache cleared."))))
      (:delay opts) interval t-unit)
    service))


(deftype FiniteCache [f
                      ^ConcurrentHashMap m
                      ^:unsynchronized-mutable executor
                      ^:unsynchronized-mutable opts]
  ICache
  (change-settings [this opts*]
    (shutdown-executor this)
    (set! opts opts*)
    (when (.isTerminated executor)
      (set! executor (create-executor m opts*))))
  (memo-fn [_] f)
  (cache [_] m)
  (size [_] (memory/measure m :bytes true))
  (invalidate [_]
    (.clear m))
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


(defn finite-cache [f opts]
  (check-opts opts)
  (let [memoize* (util/fast-memoize f)
        fn*      (:fn memoize*)
        map*     (:map memoize*)
        opts     (cond-> opts
                   (not (:delay opts)) (assoc :delay 0)
                   (not (:await-timeout opts)) (assoc :await-timeout 1000))
        executor (create-executor map* opts)]
    (->FiniteCache fn* map* executor opts)))


(comment
  (s/explain ::options {:threshold     [300 :bytes]
                        :every         [1000 :milliseconds]
                        :delay         10
                        :await-timeout 1000})
  (def cch* (finite-cache + {:threshold     [300 :byte]
                             :every         [1000 :milliseconds]
                             :delay         10
                             :await-timeout 1000}))
  (change-settings cch* {})
  (shutdown-executor cch*)
  (invalidate cch*))