#/bin/sh
cd $(dirname "$0")

PIXIE=/usr/local/bin/pixie
PIXIE_PATH="-l src"
# pixie $PIXIE_PATH src/server/app/core.pxi

CMD="$PIXIE $PIXIE_PATH src/core.pxi"
$CMD "$@"
