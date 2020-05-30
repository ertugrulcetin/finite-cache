(ns finite-cache.classloader
  (:require [dynapath.util :as dynapath])
  (:import (clojure.lang DynamicClassLoader RT)
           (java.net URL)))


(defonce url-mapping (atom #{}))
(defonce shared-context-classloader
         (delay
           (or
             (when-let [base-loader (RT/baseLoader)]
               (when (instance? DynamicClassLoader base-loader)
                 base-loader))
             (DynamicClassLoader. (.getContextClassLoader (Thread/currentThread))))))


(defn- has-classloader-as-ancestor?
  [^ClassLoader classloader, ^ClassLoader ancestor]
  (cond
    (identical? classloader ancestor) true
    classloader (recur (.getParent classloader) ancestor)
    :else false))


(defn- has-shared-context-classloader-as-ancestor?
  [^ClassLoader classloader]
  (has-classloader-as-ancestor? classloader @shared-context-classloader))


(defn ^ClassLoader the-classloader
  []
  (or
    (let [current-thread-context-classloader (.getContextClassLoader (Thread/currentThread))]
      (when (has-shared-context-classloader-as-ancestor? current-thread-context-classloader)
        current-thread-context-classloader))
    (let [shared-classloader @shared-context-classloader]
      (.setContextClassLoader (Thread/currentThread) shared-classloader)
      shared-classloader)))


(defn- classloader-hierarchy
  [^ClassLoader classloader]
  (reverse (take-while some? (iterate #(.getParent ^ClassLoader %) classloader))))


(defn the-top-level-classloader
  (^DynamicClassLoader []
   (the-top-level-classloader (the-classloader)))

  (^DynamicClassLoader [^DynamicClassLoader classloader]
   (some #(when (instance? DynamicClassLoader %) %)
         (classloader-hierarchy classloader))))


(defn add-to-classpath!
  [^URL url]
  (when-not (@url-mapping url)
    (swap! url-mapping conj url)
    (assert (dynapath/add-classpath-url (the-top-level-classloader) url))))