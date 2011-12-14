#!/bin/bash

#bash example: download ourvirt jar and place this script within the same directory, then simply execute it
#passing both arguments for the chosen hypervisor and ourvirt user that will control the hypervisor

if [ $# -ne 2 ]; then
	echo "Usage: <hypervisor> <ourvirt-user>"
	exit 1
fi

HYPERVISOR=$1
OURVIRT_USER=$2

su $OURVIRT_USER -c true
if  [ $? -ne 0 ] ; then
	echo "User $OURVIRT_USER does not exist, please create it or select another user." 
	exit 1
fi

java -cp ourvirt.jar org/ourgrid/virt/PrepareEnvironment $HYPERVISOR $OURVIRT_USER
exit $?
