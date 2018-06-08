;;; TCP REPL Handler for Pixie Lang
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; March 2018, Otto Linnemann

(ns core
  (:require [pixie.io :refer [slurp spit]]
            [pixie.repl :refer [repl]]
            [pixie.io.tcp :refer :all]
            [pixie.string :as s]
            [socket-repl :refer [create-repl-server dispose-repl-server!
                                            create-evaluator]]
            [signals :refer [init-signal-handler]]
            [setup :refer :all]))



;; start servers
(def repl-server (create-repl-server *tcp-repl-addr* *tcp-repl-port*))


(println "___ TCP-REPL SERVER RUNNING ___")


(comment
  ;; use the following expression to stop the REPL server
  (dispose-repl-server! repl-server)
  )


;; for aborting on receiving SIGPIPE
;; refer to https://nikhilm.github.io/uvbook/filesystem.html
(init-signal-handler)


;; open just another repl to keep the application running
(repl)
