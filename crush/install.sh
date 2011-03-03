#!/bin/bash
#
# Usage: install.sh [install-root]
#
# This script installs links to the CRUSH binaries and the man pages, into 
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


# First create the links to the binaries
echo Installing CRUSH binaries to $BINDIR...
mkdir -p $BINDIR
cd $BINDIR
ln -sf $CRUSH/crush .
ln -sf $CRUSH/coadd .
ln -sf $CRUSH/detect .
ln -sf $CRUSH/difference .
ln -sf $CRUSH/histogram .
ln -sf $CRUSH/imagetool .
ln -sf $CRUSH/jiggle .
ln -sf $CRUSH/show .
cd $CRUSH

# Now install the man pages
echo Installing CRUSH manuals to $MANDIR...
mkdir -p $MANDIR
cp -a $CRUSH/man/* $MANDIR

cd $CURRENT_DIR
echo Done!

