# finite-cache

finite-cache is a Clojure caching library that allows you to limit the size of a cache object.

## Requirements
* JDK 1.9+
* You must start the JVM with option `-Djdk.attach.allowAttachSelf`,
otherwise the agent will not be able to dynamically attach to the running
process. For Leiningen, add `:jvm-opts ["-Djdk.attach.allowAttachSelf"]` to
`project.clj`. For Boot, start the process with environment variable
`BOOT_JVM_OPTIONS="-Djdk.attach.allowAttachSelf"`.

## Usage
[![Clojars Project](https://clojars.org/finite-cache/latest-version.svg)](https://clojars.org/finite-cache)

```clojure
(require '[finite-cache.core :refer :all])

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
```

## License

```
MIT License

Copyright 2020 Ertuğrul Çetin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
