(ns finite-cache.core-test
  (:require [clojure.test :refer :all]
            [finite-cache.core :as fc]))


(defmacro with-cache
  [bindings & body]
  `(let ~bindings
     (try
       ~@body
       (catch Exception e#
         (println e#)
         e#)
       (finally
         (fc/shutdown-executor ~(first bindings))))))


(deftest cache-test
  (testing "size"
    (with-cache [cache (fc/finite-cache + {:threshold [2 :mb]
                                           :every     [5 :minutes]})
                 memoized-fn (fc/memo-fn cache)]
      (memoized-fn 1 2 3)

      (is (pos? (fc/size cache)))))


  (testing "cache map"
    (with-cache [cache (fc/finite-cache + {:threshold [2 :mb]
                                           :every     [5 :minutes]})
                 memoized-fn (fc/memo-fn cache)]
      (memoized-fn 2 3 3)
                ;; fc/cache is a Java map so it needs casting
      (is (= {[2 3 3] 8} (into {} (fc/cache cache))))))


  (testing "memo-fn"
    (with-cache [cache (fc/finite-cache + {:threshold [2 :mb]
                                           :every     [5 :minutes]})
                 memoized-fn (fc/memo-fn cache)]

      (is (= 21  (memoized-fn 5 6 10)))))


  (testing "invalidate"
    (with-cache [cache (fc/finite-cache + {:threshold [2 :mb]
                                           :every     [5 :minutes]})
                 memoized-fn (fc/memo-fn cache)]
      (memoized-fn 5 6 10)
      (memoized-fn 2 2 2)
      (is (empty? (fc/invalidate cache)))))


  (testing "exceeding threshold"
    (with-cache [cache (fc/finite-cache + {:threshold [300 :byte]
                                           :every     [100 :milliseconds]
                                           :delay     50})
                 memoized-fn (fc/memo-fn cache)
                 _  (memoized-fn 1 2 3 4 5 6 7 8)
                 _  (memoized-fn 5 6 10)
                 _  (memoized-fn 2 2 2)
                 _  (memoized-fn 100 200 300 400)
                 _  (memoized-fn 99 23 13 134)
                 s  (fc/size cache)]
      (Thread/sleep 300)
      (is (> s (fc/size cache)))))


  (testing "executor state"
    (with-cache [cache (fc/finite-cache + {:threshold [2 :mb]
                                           :every     [5 :minutes]})
                 memoized-fn (fc/memo-fn cache)]
      (is (= :running (fc/executor-state cache))))))
