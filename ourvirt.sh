#!/bin/bash
LIB=`dirname $0`/lib
java -Djava.ext.dirs=$LIB org/ourgrid/virt/Main $@
exit $?