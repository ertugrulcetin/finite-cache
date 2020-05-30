(ns finite-cache.util
  (:require [clj-tuple :as t])
  (:import (java.util.concurrent ConcurrentHashMap)))


(definline re-nil [x]
  `(let [x# ~x]
     (if (identical? ::nil x#) nil x#)))


(definline de-nil [x]
  `(let [x# ~x]
     (if (nil? x#) ::nil x#)))


(defmacro memoize-form [m f & args]
  `(let [k# (t/vector ~@args)]
     (let [v# (.get ~m k#)]
       (if-not (nil? v#)
         (re-nil v#)
         (let [v# (de-nil (~f ~@args))]
           (re-nil (or (.putIfAbsent ~m k# v#) v#)))))))


(defn fast-memoize
  [f]
  (let [m (ConcurrentHashMap.)]
    {:map m
     :fn  (fn
            ([]
             (memoize-form m f))
            ([x]
             (memoize-form m f x))
            ([x y]
             (memoize-form m f x y))
            ([x y z]
             (memoize-form m f x y z))
            ([x y z w]
             (memoize-form m f x y z w))
            ([x y z w u]
             (memoize-form m f x y z w u))
            ([x y z w u v]
             (memoize-form m f x y z w u v))
            ([x y z w u v & rest]
             (let [k (list* x y z w u v rest)]
               (let [v (.get ^ConcurrentHashMap m k)]
                 (if-not (nil? v)
                   (re-nil v)
                   (let [v (de-nil (apply f k))]
                     (or (.putIfAbsent m k v) v)))))))}))