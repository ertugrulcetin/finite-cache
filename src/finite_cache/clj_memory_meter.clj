(ns finite-cache.clj-memory-meter
  "Fork of https://github.com/clojure-goes-fast/clj-memory-meter"
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [finite-cache.classloader :as cl])
  (:import java.io.File
           java.lang.management.ManagementFactory
           java.net.URLClassLoader))
;;;; Agent JAR unpacking

(def ^:private jamm-jar-name "jamm-0.3.2.jar")


(defn- unpack-jamm-from-resource []
  (let [dest (File/createTempFile "jamm" ".jar")]
    (io/copy (io/input-stream (io/resource jamm-jar-name)) dest)
    (.getAbsolutePath dest)))

(defonce ^:private extracted-jamm-jar (unpack-jamm-from-resource))

;;;; Agent loading

(defn- tools-jar-url []
  (let [file (io/file (System/getProperty "java.home"))
        file (if (.equalsIgnoreCase (.getName file) "jre")
               (.getParentFile file)
               file)
        file (io/file file "lib" "tools.jar")]
    (io/as-url file)))


(defn- add-url-to-classloader-reflective
  "This is needed for cases when there is no DynamicClassLoader in the classloader
  chain (i.e., the env is not a REPL). Note that this will throw warning on Java
  9/10 and will probably stop working at all from Java 11."
  [^URLClassLoader loader, url]
  (doto (.getDeclaredMethod URLClassLoader "addURL" (into-array Class [java.net.URL]))
    (.setAccessible true)
    (.invoke loader (object-array [url]))))


(defn- get-classloader
  "Find the uppermost DynamicClassLoader in the chain. However, if the immediate
  context classloader is not a DynamicClassLoader, it means we are not run in
  the REPL environment, and have to use reflection to patch this classloader.

  Return a tuple of [classloader is-it-dynamic?]."
  []
  (let [dynamic-cl?
        #(#{"clojure.lang.DynamicClassLoader" "boot.AddableClassLoader"}
          (.getName (class %)))

        ctx-loader (.getContextClassLoader (Thread/currentThread))]
    (if (dynamic-cl? ctx-loader)
      ;; The chain starts with a dynamic classloader, walk the chain up to find
      ;; the uppermost one.
      (loop [loader ctx-loader]
        (let [parent (.getParent loader)]
          (if (dynamic-cl? parent)
            (recur parent)
            [loader true])))

      ;; Otherwise, return the immediate classloader and tell it's not dynamic.
      [ctx-loader false])))


(def ^:private tools-jar-classloader
  (delay
    (let [tools-jar (tools-jar-url)
          [loader dynamic?] (get-classloader)]
      (if dynamic?
        (try
          (.addURL loader tools-jar)
          (catch Exception e
            (log/error "dynamic? loading threw exception: " (.getMessage e))
            (cl/add-to-classpath! tools-jar)))
        (cl/add-to-classpath! tools-jar))
      loader)))


(defn get-virtualmachine-class []
  ;; In JDK9+, the class is already present, no extra steps required.
  (try (resolve 'com.sun.tools.attach.VirtualMachine)
       ;; In earlier JDKs, load tools.jar and get the class from there.
       (catch ClassNotFoundException _
         (Class/forName "com.sun.tools.attach.VirtualMachine"
           false @tools-jar-classloader))))


(defn- get-self-pid
  "Returns the process ID of the current JVM process."
  []
  (let [^String rt-name (.getName (ManagementFactory/getRuntimeMXBean))]
    (subs rt-name 0 (.indexOf rt-name "@"))))


(defn- mk-vm [pid]
  (let [vm-class (get-virtualmachine-class)
        method (.getDeclaredMethod vm-class "attach" (into-array Class [String]))]
    (.invoke method nil (object-array [pid]))))

(defn- load-jamm-agent []
  (let [vm (mk-vm (get-self-pid))]
    (.loadAgent vm extracted-jamm-jar)
    (.detach vm)
    true))

(def ^:private jamm-agent-loaded (delay (load-jamm-agent)))

;;;; Public API

(def ^:private memory-meter
  (delay
    @jamm-agent-loaded
    (.newInstance (Class/forName "org.github.jamm.MemoryMeter"))))


(defn- convert-to-human-readable
  "Taken from http://programming.guide/java/formatting-byte-size-to-human-readable-format.html."
  [bytes]
  (let [unit 1024]
    (if (< bytes unit)
      (str bytes " B")
      (let [exp (int (/ (Math/log bytes) (Math/log unit)))
            pre (nth "KMGTPE" (dec exp))]
        (format "%.1f %siB" (/ bytes (Math/pow unit exp)) pre)))))


(defn measure
  "Measure the memory usage of the `object`. Return a human-readable string.

  :debug   - if true, print the object layout tree to stdout. Can also be set to
             a number to limit the nesting level being printed.
  :shallow - if true, count only the object header and its fields, don't follow
             object references
  :bytes   - if true, return a number of bytes instead of a string
  :meter   - custom org.github.jamm.MemoryMeter object"
  [object & {:keys [debug shallow bytes meter]}]
  (let [m (or meter @memory-meter)
        m (cond (integer? debug) (.enableDebug m debug)
                debug (.enableDebug m)
                :else m)
        byte-count (if shallow
                     (.measure m object)
                     (.measureDeep m object))]
    (if bytes
      byte-count
      (convert-to-human-readable byte-count))))