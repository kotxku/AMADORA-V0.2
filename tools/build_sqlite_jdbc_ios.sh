#!/bin/bash
# Builds the sqlite-jdbc native that Distant Horizons shades to dh_sqlite, for iOS arm64, so DH loads a signed
# in-bundle libsqlitejdbc from java.library.path instead of the unsigned copy it extracts to tmp/.
# usage: build_sqlite_jdbc_ios.sh <sqlite-jdbc-source-dir (tag 3.53.2.1)> <output.dylib>
set -euo pipefail
SRC="$(cd "$1" && pwd)"
OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$SRC/build/ios-dh"
AMAL=sqlite-amalgamation-3530200

# Plain amalgamation: upstream regenerates it with --update-limit (needs Tcl), but DH never uses UPDATE/DELETE ... LIMIT
rm -rf "$WORK" && mkdir -p "$WORK"
curl -L --max-redirs 0 -fsS -o "$WORK/$AMAL.zip" "https://www.sqlite.org/2026/$AMAL.zip"
echo "8a310d0a16c7a90cacd4c884e70faa51c902afed2a89f63aaa0126ab83558a32  $WORK/$AMAL.zip" | shasum -a 256 -c -
unzip -q "$WORK/$AMAL.zip" -d "$WORK"

# Same amalgamation edits as upstream's Makefile
perl -p -e 's/sqlite3_api;/sqlite3_api = 0;/g' "$WORK/$AMAL/sqlite3ext.h" > "$WORK/sqlite3ext.h"
perl -p -e 's/^opendb_out:/  if(!db->mallocFailed && rc==SQLITE_OK){ rc = RegisterExtensionFunctions(db); }\nopendb_out:/;' \
    "$WORK/$AMAL/sqlite3.c" > "$WORK/sqlite3.c.tmp"
perl -p -e 's/^(static const char \* const sqlite3azCompileOpt.+)$/\1\n\n\/* This has been automatically added by sqlite-jdbc *\/\n  "JDBC_EXTENSIONS",/;' \
    "$WORK/sqlite3.c.tmp" > "$WORK/sqlite3.c"
cat "$SRC"/src/main/ext/*.c >> "$WORK/sqlite3.c"
grep -q 'rc = RegisterExtensionFunctions(db); }' "$WORK/sqlite3.c" && grep -q '"JDBC_EXTENSIONS",' "$WORK/sqlite3.c" \
    || { echo "amalgamation edits did not apply"; exit 1; }

# DH relocates org.sqlite -> dh_sqlite; apply the same rename to every JNI symbol and class string
sed -e 's/Java_org_sqlite_/Java_dh_1sqlite_/g' -e 's#org/sqlite/#dh_sqlite/#g' \
    "$SRC/src/main/java/org/sqlite/core/NativeDB.c" > "$WORK/NativeDB.c"
if grep -nE 'org_sqlite|org/sqlite' "$WORK/NativeDB.c"; then echo "unrelocated references left"; exit 1; fi
# javac -h output only adds prototypes, and NativeDB.c never calls a JNI function before defining it
echo '#include <jni.h>' > "$WORK/NativeDB.h"

CC=(xcrun -sdk iphoneos clang -target arm64-apple-ios14.0 -Os -fPIC -fvisibility=hidden -Wno-implicit-function-declaration
    -I"$WORK" -I"$WORK/$AMAL" -I"$SRC/lib/inc_mac")
# Defines copied from upstream's Makefile (ENABLE_UPDATE_DELETE_LIMIT dropped, see above)
"${CC[@]}" -c -o "$WORK/sqlite3.o" \
    -DSQLITE_ENABLE_LOAD_EXTENSION=1 -DSQLITE_HAVE_ISNAN -DHAVE_USLEEP=1 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_CORE \
    -DSQLITE_ENABLE_FTS3 -DSQLITE_ENABLE_FTS3_PARENTHESIS -DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_RTREE \
    -DSQLITE_ENABLE_PERCENTILE -DSQLITE_ENABLE_STAT4 -DSQLITE_ENABLE_DBSTAT_VTAB -DSQLITE_ENABLE_MATH_FUNCTIONS \
    -DSQLITE_THREADSAFE=1 -DSQLITE_DEFAULT_MEMSTATUS=0 -DSQLITE_DEFAULT_FILE_PERMISSIONS=0666 \
    -DSQLITE_MAX_VARIABLE_NUMBER=250000 -DSQLITE_MAX_MMAP_SIZE=1099511627776 -DSQLITE_MAX_LENGTH=2147483647 \
    -DSQLITE_MAX_COLUMN=32767 -DSQLITE_MAX_SQL_LENGTH=1073741824 -DSQLITE_MAX_FUNCTION_ARG=127 \
    -DSQLITE_MAX_ATTACHED=125 -DSQLITE_MAX_PAGE_COUNT=4294967294 -DSQLITE_DISABLE_PAGECACHE_OVERFLOW_STATS \
    "$WORK/sqlite3.c"
"${CC[@]}" -c -o "$WORK/NativeDB.o" "$WORK/NativeDB.c"
printf '_Java_dh_1sqlite_*\n_JNI_OnLoad\n_JNI_OnUnload\n' > "$WORK/exported.txt"
mkdir -p "$(dirname "$OUT")"
"${CC[@]}" -dynamiclib -o "$OUT" "$WORK/NativeDB.o" "$WORK/sqlite3.o" -lm \
    -exported_symbols_list "$WORK/exported.txt" -install_name @rpath/libsqlitejdbc.dylib

# Fail the build rather than ship a macOS binary or one whose exports differ from DH's own Mac/aarch64 library
otool -l "$OUT" | grep -A4 LC_BUILD_VERSION | grep -q "platform 2" || { echo "not an iOS binary"; exit 1; }
diff <(nm -gU "$OUT" | awk '{print $3}' | LC_ALL=C sort -u) <(LC_ALL=C sort -u "$HERE/dh_sqlite_jdbc_symbols.txt") \
    || { echo "exports differ from DH's sqlite-jdbc"; exit 1; }
strings "$OUT" | grep 'dh_sqlite/core/NativeDB' >/dev/null || { echo "FindClass strings not relocated"; exit 1; }
echo "Built $OUT"
