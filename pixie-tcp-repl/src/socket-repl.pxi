;;; TCP/UDS multiplex socket for the Pixie Programming Language
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; March 2018, Otto Linnemann

(ns socket-repl
  (:require [setup :refer :all]
            [utils :refer :all]
            [pixie.io.tcp :refer :all]
            [pixie.stacklets :as st]
            [pixie.string :as s]
            [pixie.csp :refer :all]))


(defn- inject-println
  "patches 'binding' to replace printfn in order to
   put the result "
  [req]
  (let [s-exp-str
        "(let [print-buf (atom \"\")
               println-orig println
               println-subst (fn sprintln [& args]
                  (let [sargs (apply str
                                     (interpose \" \" (map str args)))]
                    (swap! print-buf str sargs \"\n\")
                    nil))
               _ (def println println-subst)
               res __req__]
          (def println println-orig)
          (str @print-buf res))"
        s-exp-str (s/replace s-exp-str "__req__" req)]
    s-exp-str))


(defn create-evaluator
  ([{:keys [show-prompt redirect-println]
     :or {show-prompt false redirect-println false}}]
   (let [prompt (fn [] (str (name pixie.stdlib/*ns*) " => "))
         in (chan)
         out (chan)]
     (go
       (loop []
         (let [req (<! in)
               req (if redirect-println (inject-println req) req)
               res (try
                     (eval (read-string req))
                     (catch ex (str "ERROR: \n" ex)))]
           (>! out (if show-prompt (str res "\n" (prompt)) res))
           (recur))))
     {:in in :out out})))


(defn- repl-handler [stream]
  (let [buflen 4096
        buf (buffer buflen)]
    (try
      (>! (:in evaluator) "(str \"Have a lot of fun with the Pixie REPL!\n\")")
      (write stream (str (<! (:out evaluator))))
      (loop [rbytes (read stream buf buflen)]
        (let [req (cstr2str buf rbytes)]
          (>! (:in evaluator) req)
          (write stream (str (<! (:out evaluator))))
          (when (> rbytes 0)
            (recur (read stream buf buflen)))))
      (catch e (println e)))))


(defn create-repl-server
  [addr port]
  (let [evaluator (create-evaluator {:show-prompt true :redirect-println true})
        server (tcp-server addr port repl-handler)]
    (def evaluator evaluator)
    {:server server :evaluator evaluator}))


(defn dispose-repl-server!
  [server]
  (let [evaluator (:evaluator server)
        in (:in evaluator)
        out (:out evaluator)]
    (close! in)
    (close! out)
    (dispose! (:server server))))


(comment
  (def repl-server (create-repl-server *tcp-repl-addr* *tcp-repl-port*))
  (dispose-repl-server! repl-server))
