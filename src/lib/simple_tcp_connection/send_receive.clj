;; simple tcp connection library
;;
;; by Otto Linnemann
;; (C) 2017, GNU General Public Licence

(ns lib.simple-tcp-connection.send-receive
  (:require [clojure.java.io :as io]))


(defn sreceive
  "Read data from the given socket"
  [socket]
  (let [reader (io/reader socket)
        buf (char-array 8192)
        bytes-read (.read reader buf 0 (count buf))]
    (String. buf 0 bytes-read)))


(defn ssend
  "Send the given string message out over the given socket"
  [socket msg]
  (let [writer (io/writer socket)]
    (.write writer msg)
    (.flush writer)
    (count msg)))
