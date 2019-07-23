#!/bin/bash
#
# ============================================================================
# Description:
#
# This bash script will attempt to configure optimal setting for running CRUSH 
# on Linux, Mac OS X, BSD, and Solaris platforms. For all other POSIX platforms 
# a fail-safe default configuration is set, which may be manually overriden by 
# any settings placed in files under /etc/crush2/startup or ~/.crush2/startup 
# folders.
# It should be called from 'wrapper.sh' exlusively. Otherwise, it will not have
# the desired effect.
#
# Author: Attila Kovacs <attila@sigmyne.com>
# Date: 3 November 2018
# ============================================================================

# Start by fail-safe defaults: default java, 32-bit mode, 1 GB of RAM, and
# default VM with no extra options...
JAVA="java"
USEMB="1000"
JVM=""
EXTRAOPTS=""

# Attempt to configure 80% of total RAM...
OSNAME=`uname`
case $OSNAME in
"Linux")
	# Linux...
	read -a MEMVALS <<< `cat /proc/meminfo | grep "MemTotal"`
	USEMB=$((${MEMVALS[1]}/1280))
	;;

"Darwin")
	# MAC OS X...
	USEMB=$((`sysctl -n hw.memsize`/1310720))
	;;

*"BSD")
	# OpenBSD and FreeBSD
	USEMB=$((`sysctl -n hw.usermem`/1310720))	
	;;

"SunOS")
	# Solaris...
	read -a MEMVALS <<< `prtconf | grep "Memory"`
	USEMB=$((${MEMVALS[2]}*1024/1280))
	;;

*)
	# All other platforms...
	;;
esac


# Now, try locate the default Java, and set the JAVA variable to it.
# If no default java is set, check if there is an Oracle/OpenJDK java installed
# in the default location
JAVA=`command -v java`
if [ "$?" != "0" ] ; then 
  if [ -f /usr/java/latest/bin/java ] ; then 
    JAVA="/usr/java/latest/bin/java"
  else
    echo "Java does not appear to be installed, or is not found."
    exit 1
  fi
fi

# Use 'java -version' to set VM defaults
VMVER=`$JAVA -version 2>&1 | tail -1`


# Sanity check the max memory setting to make use no more than 1900MB is used
# with a 32-bit data model...
if [[ $VMVER != *"64-"* && $VMVER != *"-64"* ]] ; then
  if [[ $USEMB -gt 1900 ]] ; then USEMB="1900"; fi 
fi

# Set "-server" VM if possible
if [[ $VMVER == *"Server VM"* ]] ; then JVM="-server"; fi

