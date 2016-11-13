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
# Author: Attila Kovacs <attila@submm.caltech.edu>
# Date: 15 April 2015
# ============================================================================

# Start by fail-safe defaults: default java, 32-bit mode, 1 GB of RAM, and
# default VM with no extra options...
JAVA="java"
DATAMODEL="32"
USEMB="1000"
JVM=""
EXTRAOPTS=""

# Now, try locate the default Java, and set the JAVA variable to it.
# If no default java is set, check if there is an Oracle/OpenJDK java installed
# in the default location
JAVA=`which java`
if [ -z ${JAVA+x} ] ; then 
  if [ -f /usr/java/latest/bin/java ] ; then 
    JAVA="/usr/java/latest/bin/java"
  fi
fi

# If no JRE was found, then print an informative error message and exit.
if [ -z ${JAVA+x} ] ; then
  echo ""
  echo "ERROR! No Java Runtime Environment (JRE) found."
  echo "       If Java is installed on your system, then set the JAVA variable"
  echo "       manually to point to it, in a config file under"
  echo "       /etc/crush2/startup/ or ~/.crush2/startup/"
  echo "       E.g. place the line:"
  echo ""
  echo "          JAVA=\"/opt/java/bin/java\""
  echo ""
  echo "       in '~/.crush2/startup/java.conf'."
  echo "       Otherwise, install a Java Runtime Environment (JRE), e.g."
  echo "       from www.java.com."
  echo ""
  exit 1
fi

# Use 'java -version' to set VM defaults
JVER=`$JAVA -version 2>&1 | tail -1`

# Attempt to determine data mode (32 or 64 bit)
if [[ $JVER == *"64-"* ]] ; then DATAMODEL="64"
elif [[ $JVER == *"-64"* ]] ; then DATAMODEL="64"
fi

# Set "-server" VM for Oracle and OpenJDK java
#if [[ $JVER == *"OpenJDK"* ]] ; then JVM="-server"
#elif [[ $JVER == *"HotSpot"* ]] ; then JVM="-server"
#fi

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


# Sanity check the max memory setting to make use no more than 1900MB is used
# with a 32-bit data model...
if [[ $DATAMODEL == "32" && $USEMB -gt 1900 ]] ; then USEMB="1900" ; fi


# Report the autoconf settings if DEBUG is set to "TRUE"
if [[ $DEBUG == "TRUE" ]] ; then
  echo "Autoconf:"
  echo "  JAVA=$JAVA"
  echo "  JVM=$JVM"
  echo "  DATAMODEL=$DATAMODEL"
  echo "  USEMB=$USEMB"
  echo "  EXTRAOPTS=$EXTRAOPTS"
  echo
fi


