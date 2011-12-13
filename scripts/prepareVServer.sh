#!/bin/bash

MANAGER_USER=$1

if [ -z "$(grep "$MANAGER_USER ALL= NOPASSWD: /usr/sbin/vnamespace,/usr/sbin/vserver" /etc/sudoers)" ]; then 
	echo "$MANAGER_USER ALL= NOPASSWD: /usr/sbin/vnamespace,/usr/sbin/vserver" >> /etc/sudoers
fi

exit 0