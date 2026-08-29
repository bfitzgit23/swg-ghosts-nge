#!/usr/bin/env bash
set -u

ROOT="/home/swg/swg-main"
CHAT_ROOT="$ROOT/chat"
CONSOLE_ROOT="$ROOT/exe/linux"
CONSOLE_ADDRESS="109.228.61.26"
SERVER_TITLE="SWG-Server"
CHAT_TITLE="SWG-StationChat"
SHUTDOWN_SECONDS=15
LOCK_FILE="/tmp/swg-control.lock"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
	echo "Another SWG-Control launcher is already running."
	echo "Close the existing SWG-Control window first if you want to restart the server."
	sleep 10
	exit 0
fi

server_terminal_pid=""
chat_terminal_pid=""
cleanup_started=0

central_is_listening() {
	ss -ltn 2>/dev/null | grep -q ':61000 '
}

reset_stationchat_rooms() {
	local db="$CHAT_ROOT/var/stationapi/stationchat.db"

	if [ ! -f "$db" ]; then
		return
	fi

	echo "Clearing old StationChat room cache..."
	python3 - "$db" <<'PY'
import sqlite3
import sys

db = sys.argv[1]
con = sqlite3.connect(db)
try:
    con.execute("PRAGMA foreign_keys = ON")
    for table in ("room_administrator", "room_moderator", "room_ban", "room_invite", "room"):
        con.execute(f"DELETE FROM {table}")
    con.commit()
finally:
    con.close()
PY
}

send_central_command() {
	local command="$1"

	if [ -x "$CONSOLE_ROOT/bin/ServerConsole" ]; then
		(
			cd "$CONSOLE_ROOT" || exit 1
			printf '%-1024s' "$command" | ./bin/ServerConsole -- @servercommon.cfg -s ServerConsole serverAddress="$CONSOLE_ADDRESS" serverPort=61000 >/dev/null 2>&1
		)
	fi
}

stop_server_stack() {
	echo "Stopping SWG server processes..."

	if central_is_listening; then
		echo "Requesting graceful game shutdown..."
		send_central_command "game shutdown 0 $SHUTDOWN_SECONDS Desktop launcher shutdown"
		sleep $((SHUTDOWN_SECONDS + 5))
	fi

	(
		cd "$ROOT" || exit 1
		ant stop >/dev/null 2>&1
	)

	pkill -TERM -x stationchat >/dev/null 2>&1 || true
	sleep 2

	pkill -TERM -x LoginServer >/dev/null 2>&1 || true
	pkill -TERM -x CentralServer >/dev/null 2>&1 || true
	pkill -TERM -x ChatServer >/dev/null 2>&1 || true
	pkill -TERM -x CommoditiesServer >/dev/null 2>&1 || true
	pkill -TERM -x ConnectionServer >/dev/null 2>&1 || true
	pkill -TERM -x CustomerServiceServer >/dev/null 2>&1 || true
	pkill -TERM -x LogServer >/dev/null 2>&1 || true
	pkill -TERM -x MetricsServer >/dev/null 2>&1 || true
	pkill -TERM -x PlanetServer >/dev/null 2>&1 || true
	pkill -TERM -x ServerConsole >/dev/null 2>&1 || true
	pkill -TERM -x SwgDatabaseServer >/dev/null 2>&1 || true
	pkill -TERM -x SwgGameServer >/dev/null 2>&1 || true
	pkill -TERM -x TransferServer >/dev/null 2>&1 || true
	pkill -TERM -x TaskManager >/dev/null 2>&1 || true
	sleep 3

	pkill -KILL -x SwgGameServer >/dev/null 2>&1 || true
}

close_old_terminals() {
	pkill -TERM -f "xfce4-terminal.*$SERVER_TITLE" >/dev/null 2>&1 || true
	pkill -TERM -f "xfce4-terminal.*$CHAT_TITLE" >/dev/null 2>&1 || true
}

cleanup() {
	if [ "$cleanup_started" -eq 1 ]; then
		return
	fi

	cleanup_started=1
	trap - EXIT INT TERM HUP

	echo
	echo "Controller is closing. Cleaning up for a fresh next start..."
	stop_server_stack
	close_old_terminals

	if [ -n "$server_terminal_pid" ]; then
		kill "$server_terminal_pid" >/dev/null 2>&1 || true
	fi
	if [ -n "$chat_terminal_pid" ]; then
		kill "$chat_terminal_pid" >/dev/null 2>&1 || true
	fi

	echo "SWG cleanup complete."
}

trap cleanup EXIT
trap 'cleanup; exit 0' INT TERM HUP

echo "Preparing a clean SWG startup..."
stop_server_stack
close_old_terminals
reset_stationchat_rooms

echo "Opening visible server and station chat terminals..."
xfce4-terminal --disable-server --title="$SERVER_TITLE" --working-directory="$ROOT" --hold --command='bash -lc "./startServer.sh; status=$?; echo; echo SWG server terminal exited with status $status.; echo Close this window after checking the output.; exit $status"' &
server_terminal_pid=$!

sleep 3

xfce4-terminal --disable-server --title="$CHAT_TITLE" --working-directory="$CHAT_ROOT" --hold --command='bash -lc "./stationchat; status=$?; echo; echo StationChat terminal exited with status $status.; echo Close this window after checking the output.; exit $status"' &
chat_terminal_pid=$!

echo
echo "The SWG terminals are running."
echo "Leave this controller open while the server is up."
echo "Close this controller window with X to shut down cleanly and prepare for a fresh next start."

while true; do
	sleep 3600 &
	wait $!
done
