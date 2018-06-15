; nREPL Adapter for Pixie, A small, fast, native lisp with "magical" powers
;;
;; by Otto Linnemann
;; (C) 2018, GNU General Public Licence

(ns nrepl-adapter.core
  (:require [clojure.tools.nrepl.transport :as t]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:use [clojure.tools.nrepl.server :only [start-server stop-server default-handler]]
        [clojure.tools.nrepl.misc :only [response-for uuid]]
        [clojure.tools.nrepl.middleware.pr-values :only [pr-values]]
        [lib.simple-tcp-connection.client])
  (:import java.util.Properties
           [java.net InetAddress])
  (:gen-class))


(def ^{:private true} sessions (atom {}))


(defn get-version
  "reads version data from the pom.properties META-INF file
   refer to:
   https://groups.google.com/d/msg/leiningen/7G24ifiYvOA/h6xmjeWaO3gJ"
  [dep]
  (let [path (str "META-INF/maven/" (or (namespace dep) (name dep))
                  "/" (name dep) "/pom.properties")
        props (io/resource path)]
    (when props
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version"))))))


(defn- split-result-from-prompt
  "returns vector with result and last line which is the prompt"
  [s]
  (let [lines (str/split s #"\n")]
    [(apply str (interpose "\n" (butlast lines)))
     (-> (last lines)
         (str/replace #"[=>].*$" "")
         (str/replace #"[<]" "")
         (str/trim))]))


(defn- strip-cr-and-comments
  "strips out all carriage return linefeed characters and
   remove comments"
  [s]
  (apply str
         (map
          #(str/replace % #"[;].*$" "")
          (str/split s #"[\n\r]"))))


(defn- strip-namespace-tags
  "strip out pixie's namespace tag"
  [s]
  (-> s
      (str/replace #"<Namespace" "")
      (str/replace #"[>]" "")
      (str/trim)))


(defn- expr-supported
  "returns true when code can be evualed

   Clojure, cider and ccw expressions are filtered out."
  [exp]
  (not
   (re-matches
    #"cider.nrepl|clojure.core|\(ccw.debug.serverrepl/namespaces-info\)"
    exp)))


(def ^{:private true} the-client-handle (atom nil))


(defn- bind-inferior-repl-handler-connection
  "retruns a handler which overwrites most improtant nREPL operations
   for forwarding to external Pixie TCP REPL."
  [client-addr client-port]
  (fn [h]
    (fn [{:keys [op ns session code file line column id transport] :as msg}]
      (let [client-handle (swap! the-client-handle
                                 #(or % (create-tcp-server-connection-handler
                                         client-addr client-port 5000)))]
        (case op
          "clone" (h msg)
          "close" (h msg)

          "describe"
          (do (t/send transport
                      (response-for msg
                                    :status :done
                                    :ops {:describe {} :clone {} :close {} :eval {}})))

          "eval"
          (let [exp-valid (expr-supported code)
                resp (if exp-valid
                       (send-tcp-server-request client-handle (strip-cr-and-comments code))
                       "error")
                [result ns] (if resp
                              (split-result-from-prompt resp)
                              ["could not connect to telnet REPL error!" ""])]
            (if (re-matches #"\(clojure-version\)" code)
              (t/send transport (response-for msg :value "Pixie 0.1"))
              (if exp-valid
                (t/send transport
                        (response-for msg :value
                                      (let [code (format "(pr-str %s)" code)]
                                        (if (re-find #"\*ns\*" code)
                                          (pr-str (strip-namespace-tags result))
                                          result))))))
            (t/send transport
                    (if exp-valid
                      (if resp
                        (response-for msg :status :done :ns ns)
                        (response-for msg :status #{:error :unknown-session :done}))
                      (response-for msg {:status #{:eval-error :done}
                                         :ex "expression not supported"
                                         :root-ex "expression not supported"}))))
          (h msg))))))


(defn- start-inf-repl-adapter
  [server-port client-addr client-port]
  (def server (start-server :port server-port :handler
                            ((bind-inferior-repl-handler-connection client-addr client-port)
                             (default-handler)))))


(comment
  (def server (start-inf-repl-adapter 37148 "localhost" 37147))
  (stop-server server)

  (def server (start-server :port 37148 :handler (default-handler)))
  )


(defn str2port-num
  "simple string to integer converter"
  [s]
  (try
    (let [port (.intValue (Integer. s))]
      (if (and (> port 0) (< port 65536)) port nil))
    (catch NumberFormatException e nil)))


; command line interface (leiningen)
(def cli-options
  [["-s" "--server-port PORT" "port where the nrepl is listening to"
    :default 37148
    :validate [#(str2port-num %) "TCP port must be in valid range [1..65535]"]]
   ["-c" "--client-port PORT" "port where the the adapter is connecting to (inferior repl)"
    :default 37147
    :validate [#(str2port-num %) "TCP port must be in valid range [1..65535]"]]
   ["-a" "--client-addr ADDR" "TCP address where the client is connecting to."
    :default "localhost"
    :parse-fn #(when (InetAddress/getByName %) %)]
   ["-h" "--help" "this help string"]])


(defn- cli
  "command line interface"
  [& args]
  (let [opts (parse-opts args cli-options)
        options (:options opts)
        arguments (:arguments opts)
        summary (:summary opts)
        errors (:errors opts)
        invalid-opts (not-empty errors)
        title-str (str
                   "nrepl-adapter: nREPL to inferior Shell REPL Adapter\n"
                   (format "      Version: %s, refer to https://github.com/linneman/nrepl-adapter for more information\n" (get-version 'nrepl-adapter))
                   "      (C) 2018, GNU General Public Licence by Otto Linnemann\n")
        {:keys [server-port client-port client-addr]} options
        [server-port client-port] (map str2port-num [server-port client-port])]
    (println title-str)
    (if (or (:help options) invalid-opts)
      (do
        (if invalid-opts
          (println errors))
        (println "  Invocation:\n")
        (println summary)
        -1)
      (do
        (println (format "Start listening on port %s, forwarding s-expr to addr: %s, port: %s ..."
                         server-port client-addr client-port))
        (start-inf-repl-adapter server-port client-addr client-port)))))


(comment
  (cli "-s" "5000")
  (cli "-s" "5000" "--client-port" "3000" "--client-addr" "maxwell")
  (cli "-s" "5000" "--client-port" "3000" "-a" "maxwell")
  (cli "-a" "xxx")
  (cli "--help")
  (cli "-h")
  (cli)
  )


(defn -main [& args]
  (try
    (apply cli args)
    (catch Throwable t
      (let [msg (format "%s" (.getMessage t))
            cause (.getCause t)
            st (.getStackTrace t)]
        (println msg)
        (when cause (println "cause: " cause))
        (dorun (map #(println %) st))))))
