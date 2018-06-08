;; simple tcp connection library
;;
;; by Otto Linnemann
;; (C) 2017, GNU General Public Licence

(ns lib.simple-tcp-connection.utils)


(defmacro hash-args
  "constructs a hash map with given arguments is value
   and the corresponding keywords as keys.
   example:  (let [a 42 b 43] (hash-args a b))
          -> {:b 43, :a 42}"
  [& symbols]
  (doall (reduce #(assoc %1 (keyword %2) %2) {} symbols)))
