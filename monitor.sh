#!/bin/bash
exec 2>/dev/null

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/oracle/18.3/client/bin:/usr/lib/jvm/zulu-17-x86/bin/:.
export LD_PRELOAD=/usr/local/lib/cfree_fix32.so
export LD_LIBRARY_PATH=/usr/lib/oracle/18.3/client/lib:/usr/include/oracle/18.3/client:/usr/lib/oracle/18.3/client/lib:/usr/lib/jvm/zulu-17-x86/lib/:/usr/lib/jvm/zulu-17-x86/lib/server/:./
export JAVA_HOME=/usr/lib/jvm/zulu-11-x86

cd /home/swg/swg-main/exe/linux
LOG=/home/swg/swg-main/monitor.log

check_process() {
    local name=$1
    local pattern=$2
    if ! pgrep -f "$pattern" > /dev/null 2>&1; then
        echo "[$(date)] $name is DOWN" >> $LOG
        return 1
    fi
    return 0
}

restart_process() {
    local name=$1
    local cmd=$2
    echo "[$(date)] Restarting $name..." >> $LOG
    nohup $cmd > /dev/null 2>&1 &
    sleep 2
    if pgrep -f "$name" > /dev/null 2>&1; then
        echo "[$(date)] $name restarted OK" >> $LOG
    else
        echo "[$(date)] $name FAILED to restart" >> $LOG
    fi
}

# Check critical infrastructure
check_process "LoginServer" "bin/LoginServer"
if [ $? -ne 0 ]; then
    restart_process "LoginServer" "./bin/LoginServer -- @servercommon.cfg"
fi

check_process "TaskManager" "bin/TaskManager"
if [ $? -ne 0 ]; then
    restart_process "TaskManager" "./bin/TaskManager -- @servercommon.cfg"
fi

check_process "CentralServer" "bin/CentralServer"
if [ $? -ne 0 ]; then
    echo "[$(date)] CentralServer down - critical, needs manual restart" >> $LOG
fi

check_process "ChatServer" "bin/ChatServer"
if [ $? -ne 0 ]; then
    restart_process "ChatServer" "./bin/ChatServer -- @servercommon.cfg -s ChatServer centralServerAddress=109.228.61.26 clusterName=SWG_NGE"
fi

check_process "ConnectionServer" "bin/ConnectionServer"
if [ $? -ne 0 ]; then
    restart_process "ConnectionServer" "./bin/ConnectionServer -- @servercommon.cfg -s ConnectionServer clusterName=SWG_NGE startPublicServer=true centralServerAddress=109.228.61.26 connectionServerNumber=1"
fi

check_process "stationchat" "./stationchat"
if [ $? -ne 0 ]; then
    cd /home/swg/swg-main/chat && restart_process "stationchat" "./stationchat"
fi

# Check at least one PlanetServer is alive
PLANET_COUNT=$(pgrep -f "bin/PlanetServer" | grep -v grep | wc -l)
if [ "$PLANET_COUNT" -eq 0 ]; then
    echo "[$(date)] NO PlanetServers running!" >> $LOG
fi

# Check total GameServer count
GS_COUNT=$(pgrep -f "bin/SwgGameServer" | grep -v grep | wc -l)
echo "[$(date)] Status: GS=$GS_COUNT Planet=$PLANET_COUNT" >> $LOG
