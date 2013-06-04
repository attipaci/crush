#!/bin/bash
#
# ===========================================================================
# Description: Java configuration wrapper script for CRUSH tools.
# Author: Attila Kovacs <attila@submm.caltech.edu>
# Updated: 26 June 2012
# ===========================================================================  


# Most computers come with a suitable version of Java (Oracle, OpenJDK/IcedTea)
# these days. Therefore, using the system default Java should work out of
# the box in most cases: 

JAVA="java"


# Alternatively, If you experience problems with the default version of Java 
# (esp. GNU's buggy and sluggish gcj/gij), or you want to use a specific Java
# version on your system, just uncomment the JAVA setting below and modify,
# if you wish, to your preference.
# It is recommended that you use Oracle's latest Java, which is generally 
# found (on UNIX platforms) at:

#JAVA="/usr/java/latest/bin/java"


# Now, specify the options passed to the Java Runtime Environment
# The default is 32-bit mode with 1GB of RAM for the VM.
# Adjust the -Xmx value to reflect the amount of memory in you wish to make 
# available to Java.
# Default options for Oracle and OpenJDK/IcedTea VMs:

JAVAOPTS="-server -Xbatch -Xmx1000M"


# To run in 64-bit mode (needed for accessing RAM beyond 2GB but requires 
# both a 64-bit OS *AND* a 64-bit version of Java), uncomment and edit:

#JAVAOPTS="-d64 -server -Xbatch -Xmx4000M"


# Uncomment and edit the line below if you are using IBM Java:

#JAVAOPTS="-Xbatch -Xmx4000M"



# --------------------- DO NOT CHANGE BELOW THIS LINE -----------------------

NAME=$0

if [ -L $NAME ]; then
        NAME=`readlink -f $NAME`
fi

CRUSH=`dirname $NAME`
export CRUSH

CLASSPATH="$CRUSH/crush.jar:$CRUSH/fits.jar:$CRUSH/util.jar:$CRUSH/crush2.jar"

# ---------------------------- END OF SCRIPT --------------------------------
