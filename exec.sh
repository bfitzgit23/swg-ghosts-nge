#!/bin/bash

cd exe/linux
export TNS_ADMIN=/u01/app/oracle/product/19.3.0/dbhome_1/network/admin

./bin/LoginServer -- @servercommon.cfg &

sleep 4

./bin/TaskManager -- @servercommon.cfg
