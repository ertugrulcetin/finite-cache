# finite-cache

A Clojure library designed to ... well, that part is up to you.

## Usage

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

Copyright © 2020 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
