#!/bin/bash
# Oracle client environment. The servers link libclntsh and OCI refuses to
# initialise without ORACLE_HOME ("OciEnvCreate failed with error code -1"), and
# this script - not the TaskManager - launches LoginServer, so export it here
# rather than relying on whatever shell started 'ant start' (2026-10-11).
export ORACLE_HOME=/opt/oracle/product/21c/dbhomeXE
export LD_LIBRARY_PATH="$ORACLE_HOME/lib:${LD_LIBRARY_PATH:-/usr/lib}"
export TNS_ADMIN=/etc/oracle-ghosts

# One cluster start at a time: the minute-cron watchdog and a person typing
# 'ant start' must not both run this (two LoginServers is worse than none).
exec 9>/tmp/nge-exec.lock
flock -n 9 || { echo "[exec.sh] another cluster start is already running; exiting"; exit 0; }

cd exe/linux

# Wait for the database before starting anything. At boot the watchdog can fire
# before Oracle has opened the swg service on XEPDB1; LoginServer treats a failed
# connect as fatal, dies in its exit path, and the watchdog then restarts the
# whole cluster in a loop (that loop is what filled the box with crash dumps).
DB_DSN=${DB_DSN:-//127.0.0.1/xepdb1}
DB_USER=${DB_USER:-swg/swg}
echo "[exec.sh] waiting for the database at $DB_DSN ..."
for i in $(seq 1 120); do
  out=$("$ORACLE_HOME/bin/sqlplus" -s "${DB_USER}@${DB_DSN}" <<< "select 'DBUP' as marker from dual;" 2>&1)
  if echo "$out" | grep -q DBUP; then
    echo "[exec.sh] database answered (attempt $i)"
    break
  fi
  [ $((i % 6)) -eq 0 ] && echo "[exec.sh] still waiting for the database (attempt $i/120)"
  if [ "$i" -eq 120 ]; then
    echo "[exec.sh] WARNING: database never answered, starting anyway: $(echo "$out" | head -2 | tr '\n' ' ')"
  fi
  sleep 5
done

# Release the start lock: the servers must not inherit it, or the watchdog would
# see a held lock forever and never be able to restart a genuinely dead cluster.
exec 9>&-

./bin/LoginServer -- @servercommon.cfg &

sleep 4

./bin/TaskManager -- @servercommon.cfg
