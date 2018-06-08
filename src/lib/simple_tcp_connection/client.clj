; simple tcp connection library
;;
;; by Otto Linnemann
;; (C) 2017, GNU General Public Licence

(ns lib.simple-tcp-connection.client
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]
            [lib.dispatch :as dispatch])
  (:use [lib.simple-tcp-connection.send-receive]
        lib.simple-tcp-connection.utils)
  (:import [java.net Socket]))


(comment
  (def s (Socket. "localhost" 5044))
  (. s setSoTimeout 3000)

  (ssend s "hello world\n")
  (sreceive s)
  )


(defn- pr-debug
  "additional output for debugging purposes"
  [msg]
  (comment println msg))


(defn- create-tcp-client-connection
  "tries to create  a tcp client connection to the  specified server address and
  port. Instead  of throwing an  exception when the  client does not  accept the
  connection return nil."
  [addr port]
  (try
    (do
      (pr-debug (str "-------> invoke tcp-client with "  addr ", " port))
      (Socket. addr port))
    (catch Exception e
        (println "could not connect to server on address: " addr ", port: " port " error!\n")
      (Thread/sleep 100)
      nil)))

; (create-tcp-client-connection "localhost" 5044)


(defn- check-and-update-client-connection
  "checks the reference of the TCP client connection (to a server). If it
   is invalidated reconnect and fire a connection event."
  [conref addr port con-event]
  (swap!
   conref
   (fn [c] (or c
               (let [c (create-tcp-client-connection addr port)]
                 (when c (dispatch/fire con-event))
                 c)))))


(defn create-tcp-server-request-process
  "Creates a asynchronous  background thread which reads requests  from an input
  channel, sends  these to the specified  tcp server with specified  address and
  port and waits  at max the speficied  delay time for a response.  When a delay
  time  of zero  is  given the  requests  are  processed in  a  fire and  forget
  operation mode.

  The background process continously keeps trying to setup the TCP connection to
  the server to cover situation where the server crashed and is restarted.

  The  function  returns  a  hash  map   with  the  input  and  output  channels
  for  requests  and  responses  and  among  other  data  and  event  key  where
  external  listeners  might   registering  to  be  informed   e.g.  also  about
  unsolicited events.  The event  loop itself is  implemented with  the function
  start-tcp-server-response-event-loop which needs to be invoked separately."
  [addr port wait-for-response-ms]
  (let [in (chan)
        out (chan)
        conref (atom nil)
        run (atom true)
        resp-event (gensym :received-response)
        con-event (gensym :server-connected)
        discon-event (gensym :server-disconnected)]
    (go
      (loop []
        (let [request (<! in)
              con (check-and-update-client-connection conref addr port con-event)
              timeout-ch (timeout wait-for-response-ms)
              resp-reactor (dispatch/react-to 1 #{resp-event} (fn [evt data] (>!! timeout-ch data)))
              send-result (if con (ssend con request) -99)]
          (if (< send-result 0)
            (do (reset! conref nil)
                (>! out false))
            (do (if (= wait-for-response-ms 0)
                  (>! out true)
                  (>! out (if-let [v (<! timeout-ch)] v false)))))
          (dispatch/delete-reaction resp-reactor))
        (when @run
          (Thread/sleep 5000)
          (recur))))
    (hash-args addr port in out conref run resp-event con-event discon-event)))



(defn start-tcp-server-response-event-loop
  "starts the  background response  process event  loop for  a given  tcp server
  request process."
  [process]
  (go
    (let [{:keys [addr port conref run resp-event con-event discon-event]} process]
      (loop []
        (pr-debug ".... --- entering loop --- ....")
        (if-let [con (check-and-update-client-connection conref addr port con-event)]
          (if-let [resp (try (sreceive con)
                             (catch Exception e
                               (reset! conref nil)
                               (println "read descriptor for address:" addr " became invalid!\n")
                               (dispatch/fire discon-event)
                               nil))]
            (do
              (pr-debug (str resp))
              (dispatch/fire resp-event resp)
              (when @run (recur)))
            (when @run
              (Thread/sleep 1000)
              (recur)))
          (when @run
            (Thread/sleep 1000)
            (recur)))))))



(defn release-tcp-server-processes
  "stops all background threads of the given process"
  [process]
  (reset! (:run process) false)
  (when-let [con @(:conref process)]
    (.close con)))



(defn create-tcp-server-connection-handler
  "shortcut      to      invoke     'create-tcp-server-request-process'      and
  'start-tcp-server-response-event-loop'.  Function   initializes  all  required
  background  handlers and  channels in  order to  maintain communication  to an
  external TCP server which is listening on the specified address and port. When
  no timeout value is specified a default value of 500ms wait time for responses
  is  used.  Specifiy other  values  or  zero  in  order to  completely  disable
  response processing. Refer  to the aforementioned functions  for more detailed
  information."
  ([addr port]
   (create-tcp-server-connection-handler addr port 500))
  ([addr port wait-for-response-ms]
   (let [process (create-tcp-server-request-process addr port wait-for-response-ms)]
     (start-tcp-server-response-event-loop process)
     process)))


(defn send-tcp-server-request
  "sends  request e.g.  remote procedure  call  to given  processor and  returns
  either the answer from the server or false when no answer has been received or
  the connection to the server is broken. When the background request process is
  in fire and forget mode the function  returns 'true' when the request could be
  successfully send, otherwise false."
  [process request]
  (let [in (:in process)
        out (:out process)]
    (>!! in request)
    (<!! out)))


(defn react-to-tcp-server-response
  "registers a callback handler which is invoked for every response from the TCP
  server which  is described  with the given  hash-table 'process'.  The handler
  needs to  process two  arguments, the  first one being  the event  itself, the
  second one provides the response data from the server. The function returns an
  event reactor  handle. The callback  can be  disable by invoking  the function
  'dispatch/delete-reaction'."
  [process cb]
  (dispatch/react-to #{(:resp-event process)} cb))


(defn react-to-tcp-server-connection-event
  "registers a callback handler which is  invoked when the connection to the TCP
  server is established."
  [process cb]
  (dispatch/react-to #{(:con-event process)} cb))


(defn react-to-tcp-server-disconnection-event
  "registers  a callback  handler  which is  invoked when  the  client has  been
  disconneted from TCP server."
  [process cb]
  (dispatch/react-to #{(:discon-event process)} cb))


(comment
  ;; usage illustration

  (def tst-process (create-tcp-server-connection-handler "127.0.0.1" 7777 5000))
  (def tst-process (create-tcp-server-connection-handler "127.0.0.1" 7777 0))
  (def resp (send-tcp-server-request tst-process "sample command\n"))

  (release-tcp-server-processes tst-process)

  (def response-reactor
    (react-to-tcp-server-response
     tst-process
     (fn [evt data] (println "---> received evt: " data))))

  (def connection-reactor
    (react-to-tcp-server-connection-event
     tst-process
     (fn [evt data] (println "---> connected to server"))))

  (def disconnection-reactor
    (react-to-tcp-server-disconnection-event
     tst-process
     (fn [evt data] (println "---> disconnected from server"))))

  (dispatch/delete-reaction response-reactor)
  (dispatch/delete-reaction connection-reactor)
  (dispatch/delete-reaction disconnection-reactor)

  (release-tcp-server-processes tst-process)

  )
