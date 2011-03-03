#!/bin/bash
#
# Usage: remove.sh [install-root]
#
# This script uninstalls the CRUSH binaries and the man pages, from 
# the bin/ and share/man/ directories under <install-root> (if specified), or 
# under /usr, if no argument is given.
#
# You may also edit the BINDIR and MANDIR variables for more customized
# installation paths.
#
# The script may require root privileges to complete.

INSTALL_ROOT="/usr"

if [ $1 ]; then
	INSTALL_ROOT=$1
fi


# The location where the binaries should be installed
BINDIR=$INSTALL_ROOT/bin

# The location for the man pages
MANDIR=$INSTALL_ROOT/share/man

# Determine where the script is being run from...
NAME=$0

if [ -L $NAME ]; then
        NAME=`readlink -f $NAME`
fi

CURRENT_DIR=`pwd`
cd `dirname $NAME`
CRUSH=`pwd`

echo Removing CRUSH binaries from $BINDIR...
rm -f $BINDIR/crush
rm -f $BINDIR/coadd
rm -f $BINDIR/detect
rm -f $BINDIR/difference
rm -f $BINDIR/histogram
rm -f $BINDIR/imagetool
rm -f $BINDIR/jiggle
rm -f $BINDIR/show

echo Removing CRUSH manuals from $MANDIR...
cd $CRUSH/man
for page in man1/* ; do
        rm -f $MANDIR/$page
done

cd $CURRENT_DIR

echo Done!

