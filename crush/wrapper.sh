#!/bin/bash

# To use a java other than the default version on the system adjust by
# by commentting/uncommenting and/or altering the desired JAVA line. 
#
# It is recommended that you use SUN's latest java, which is generally 
# installed into the default location:
#JAVA="/usr/java/latest/bin/java"
# 
# However, some linux systems come with other java (or other install location)
# which is why the default is to try to use the system default. Note, however,
# that GNU's java is rather buggy and may not run CRUSH correctly.
JAVA="java"

# Here you can specify the options passed to the Java Runtime Environment
# For 64-bit machines use first line instead of second...
# Also, adjust the -Xmx value to reflect the amount of memory in your
# system that you wish to make available to java.
#
# Uncomment and edit the line below for IBM Java.
#JAVAOPTS="-Xbatch -Xmx4000M"
# 
# Uncomment and edit the line below for 64-bit SUN Java on 64-bit Unix OS
JAVAOPTS="-d64 -server -Xbatch -Xmx4000M"
# 
# Default SUN Java setup. Comment if using one of the other configs from above.
#JAVAOPTS="-server -Xbatch -Xmx1000M"

NAME=$0

if [ -L $NAME ]; then
        NAME=`readlink -f $NAME`
fi

CRUSH=`dirname $NAME`
export CRUSH

# This line defines where java will look for the relevant source files. Leave
# it as is, unless you have a good reason to change it...
declare -x CLASSPATH="$CRUSH/crush.jar:$CRUSH/fits.jar:$CRUSH/bin"

