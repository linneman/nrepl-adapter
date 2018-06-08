;;; TCP REPL Handler for Pixie Lang
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; March 2018, Otto Linnemann

(ns signals
  (:require
   [pixie.ffi :as ffi]
   [pixie.ffi-infer :refer :all]
   [pixie.string :as s]
   [utils :refer [with-config-conditional]]
   [setup :refer :all]))


(with-config-conditional
  (= pixie.platform/name "linux")
  {:library "c"
   :includes ["signal.h"]}
  (defcfn signal)
  (defconst SIGINT)
  (defconst SIGPIPE)
  (defconst SIGUSR1)
  (defconst SIG_IGN)
  (defccallback __sighandler_t))


(defn init-signal-handler
  []
  (when (= pixie.platform/name "linux")
    (signal
     SIGPIPE
     (ffi/ffi-prep-callback __sighandler_t SIG_IGN))))
