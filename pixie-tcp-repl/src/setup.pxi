;;; pixie tcp repl
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; March 2018, Otto Linnemann

(ns setup)


;;; --- Port and domain setup ---

;; TCP address the REPL is bound to. Use 0.0.0.0 to
;; accept all connection. Use 127.0.0.1 to accept
;; exclusively local connections
(def ^:dynamic *tcp-repl-addr* "0.0.0.0")

;; TCP port the REPL is bound to
(def ^:dynamic *tcp-repl-port* 37147)
