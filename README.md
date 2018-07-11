# nREPL Adapter for the Pixie Programming Language

[Pixie](http://pixielang.org) is  promoted as 'A  small, fast, native  lisp with
"magical"  powers' and  in fact  offers major  benefits such  as a  signficantly
reduced  start up  time and  memory foot  print. Since  it directly  compiles to
native code and thus does not need the large Java Platform, it enables the usage
and application  of Clojure programs on  platforms with limited resources  as in
example System on Chips based on ARM and Linux or Android.

Like all programming languages with a  Lisp heritage the predominant concept for
development  and the  major  advantage over  traditional programming  techniques
is  its  Read-Eval-Print  Loop  (REPL)  which  allows  to  constantly  modify  a
programm while  its is running. Pixie's  REPL environment is based  on a command
prompt using  standard I/O  or a  TCP based telnet  interface. On  contrast most
clojure development environments  such as [Cider](https://cider.readthedocs.io),
[Counterclockwise](http://doc.ccw-ide.org) or [Cursive](https://cursive-ide.com/)
connect to  a message based REPL  server which implements the  so called network
repl  or  [nREPL](https://github.com/clojure/tools.nrepl) protocol.  Pixie  does
currently  not  support  the  nREPL  protocol and  thus  expressions  cannot  be
evaluated out of  the box using standard Clojure development  tools. The goal of
the nREPL adapter is to overcome this limitation to and at least to allow remote
evaluation of S-expression in a Pixie instance using these tools.


## Compiling nREPL Adapter

You need  the clojure build  tool leinignen  for compilation. Download  the lein
script file from Github using the following shell commands:

    $ cd ~/bin
    $ wget http://github.com/technomancy/leiningen/raw/stable/bin/lein
    $ chmod +x lein

and type

    $ lein self-install

The following commands will generate a stand-alone jar file:

    $ lein uberjar

Refer also to [Zef's Leiningen page](http://zef.me/2470/building-clojure-projects-with-leiningen)
for more specific information about build options.


## Usage

The nREPL adapter is started by the following command:

    java -jar target/nrepl-adapter-<VERSION>-standalone.jar \
       --server-port <THE SERVER PORT> \
       --client-addr <THE CLIENT IP ADDRESS> \
       --client-port <THE CLIENT PORT>

It creates its own nREPL server instance at the given port on the local host and
forwards received commands to  a TCP based telnet REPL on  the given tcp address
and port.

A sample TCP  REPL implementation for Pixie is provided  within the subdirectory
'pixie-tcp-repl'.  When not  otherwise specified  the Pixie  instance uses  port
37147 and  the nREPL adapter  uses port 37148 by  default on localhost.  Use the
script 'start-server.sh'  to start the  Pixie sample implementation.  It assumes
that the Pixie interpreter is installed on '/usr/local/bin/pixie'.


# Example

Invoke the following command in two different shells to try the provided example
implementation:

## Shell 1

    ./pixie-tcp-repl/start-server.sh

When successfully started the Pixie REPL instance shell display:
    ___ TCP-REPL SERVER RUNNING ___

## Shell 2

Now invoke the nREPL adapter on a second shell by the following command:

    java -jar target/nrepl-adapter-<VERSION>-standalone.jar

When this was successful you shell get the following output:

    Start listening on port 37148,
    forwarding s-expr to addr: localhost, port: 37147 ...

You  can now  connect your  preferred  Clojure nREPL  client to  port 37148.  In
example with Emacs Cider type M-x  cider-connect, then enter 'localhost' for the
hostname and  37148 for the port.  All expressions which are  entered within the
nREPL client are forwarded and evaluated on the Pixie instance.


## License

Copyright © 2018 Otto Linnemann

Distributed under  the GNU LESSER GENERAL  PUBLIC LICENSE either version  2.1 or
(at your option) any later version.
