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
# Date: 11 February 2017
# ============================================================================

# Start by fail-safe defaults: default java, 32-bit mode, 1 GB of RAM, and
# default VM with no extra options...
JAVA="java"
DATAMODEL="32"
USEMB="1000"
JVM=""
EXTRAOPTS=""

# Attempt to configure 80% of total RAM...
case `uname` in
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
JAVA=`which java`
if [ -z ${JAVA+x} ] ; then 
  if [ -f /usr/java/latest/bin/java ] ; then 
    JAVA="/usr/java/latest/bin/java"
  fi
fi

if [ -z ${JAVA+x} ] ; then
  unset JAVA
  exit 0
fi

# Use 'java -version' to set VM defaults
JVER=`$JAVA -version 2>&1 | tail -1`

# Attempt to determine data mode (32 or 64 bit)
if [[ $JVER == *"64-"* ]] ; then DATAMODEL="64"
elif [[ $JVER == *"-64"* ]] ; then DATAMODEL="64"
fi

# Set "-server" VM if possible
if [[ $JVER == *"Server VM"* ]] ; then JVM="-server"
fi


# Sanity check the max memory setting to make use no more than 1900MB is used
# with a 32-bit data model...
if [[ $DATAMODEL == "32" && $USEMB -gt 1900 ]] ; then USEMB="1900" ; fi


