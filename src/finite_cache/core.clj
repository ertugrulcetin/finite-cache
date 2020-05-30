(ns finite-cache.core
  (:require [finite-cache.util :as util]
            [finite-cache.clj-memory-meter :as memory]
            [clojure.tools.logging :as log])
  (:import (java.util.concurrent ConcurrentHashMap
                                 ExecutorService
                                 TimeUnit
                                 Executors
                                 ScheduledExecutorService)))


(defprotocol CacheSettings
  (change-settings [this opts])
  (invalidate [this])
  (shutdown-executor [this]))


(deftype FiniteCache [f ^ConcurrentHashMap m ^ExecutorService executor opts]
  CacheSettings
  (change-settings [this opts*]
    1)
  (invalidate [_]
    (.clear m))
  (shutdown-executor [_]
    (try
      (.shutdown executor)
      (when (.awaitTermination executor 1000 TimeUnit/MILLISECONDS)
        (.shutdownNow executor))
      (catch InterruptedException e
        (log/error e)
        (.shutdownNow executor)))))


(defn create-executor [m opts]
  (let [service (Executors/newSingleThreadScheduledExecutor)]
    (.scheduleAtFixedRate service (fn []
                                   (println "Checking map size: " (memory/measure m :bytes true)))
               0 1000 TimeUnit/MILLISECONDS)
    service))


(defn finite-cache [f opts]
  (let [memoize* (util/fast-memoize f)
        fn*      (:fn memoize*)
        map*     (:map memoize*)
        executor (create-executor map* opts)]
    (->FiniteCache fn* map* executor opts)))

(def cache* (finite-cache + {:every     [10 :minutes]
                             :threshold [20 :mb]}))

(def ff (.-f cache*))
(memory/measure [])
(ff 3 2 1 4 4)