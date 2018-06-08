;;; radiomat (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; March 2018, Otto Linnemann

(ns utils
  (:require [pixie.ffi :as ffi]
            [pixie.string :as s]))


(defn sum [x] (reduce + x))


(defn cstr2str
  "convert NULL terminated C string from buffer to pixie string type"
  ([pcstr] (cstr2str pcstr 10000))
  ([pcstr maxlen]
   (loop [s "" idx 0]
     (let [c (ffi/unpack pcstr idx CUInt8)]
       (if (and (not (= c 0)) (< idx maxlen))
         (recur (str s (char c)) (inc idx))
         s)))))

(defn bytes2cstr!
  "store a pixie string into memory at given address"
  [bytes pointer]
  (when (and bytes pointer)
    (loop [r bytes idx 0]
      (when-let [c (first r)]
        (ffi/pack! pointer idx CUInt8 c)
        (recur (rest r) (inc idx))))))


(defn str2int
  "stupidly transform string to integer value"
  [s]
  (let [s (s/trim s)
        [sign s] (if (= (first s) \-)
                   [-1 (rest s)]
                   (if (= (first s) \+)
                     [1 (rest s)]
                     [1 s]))]
    (* sign
       (reduce
        (fn [res c]
          (+ (* 10 res)
             (- (int c) 48)))
        0
        s))))


(defmacro hash-args
  "constructs a hash map with given arguments is value
   and the corresponding keywords as keys.
   example:  (let [a 42 b 43] (hash-args a b))
          -> {:b 43, :a 42}"
  [& symbols]
  (reduce #(assoc %1 (keyword (str %2)) %2) {} symbols))


(defmacro with-config-conditional
  "conditional version of with-config
   which is exclusively evaluated when test condition is true"
  [test config & body]
  (if (eval test) (cons 'with-config (cons config body))))


(defn stringify-hash-map-keys
  [m]
  "make sure that all within a hash map are strings. This is required
   for transformation to json data e.g. with the callstate hash map.
   Be aware that this function does not handle nested data."
  (reduce
   (fn [res [key value]]
     (assoc res (str key) value))
   {}
   m))


(comment illustration how to overwrite println

  (def ^:dynamic print-buf (atom ""))

  (defn sprintln [& args]
    (let [sargs (apply str
                       (interpose " " (map str args)))]
      (swap! print-buf str sargs "\n")))

  (map sprintln (range 10))

  (let [print-buf (atom "")
        println-orig println
        println-subst (fn sprintln [& args]
                        (let [sargs (apply str
                                           (interpose " " (map str args)))]
                          (swap! print-buf str sargs "\n")
                          nil))
        _ (def println println-subst)
        res (do (println "hello")
                (println "world")
                (range 10))]
    (def println println-orig)
    (str @print-buf res))
  )
