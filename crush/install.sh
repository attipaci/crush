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
if [ -z "$BINDIR" ] ; then 
	BINDIR=$INSTALL_ROOT/bin
	echo BINDIR set automatically to $BINDIR
fi

# The location for the man pages
if [ -z "$MANDIR" ] ; then 
	MANDIR=$INSTALL_ROOT/share/man
	echo MANDIR set automatically to $MANDIR
fi

echo "### $BINDIR"
echo "### $MANDIR"

# Determine where the script is being run from...
NAME=$0

if [ -L $NAME ]; then
        NAME=`readlink -f $NAME`
fi

CURRENT_DIR=`pwd`


# FIRST install the man pages
# Go to the directory from where this script was called from (i.e. CRUSH)
cd `dirname $NAME`

echo Installing CRUSH manuals to $MANDIR...
mkdir -p $MANDIR
cp -a man/* $MANDIR

cd $CURRENT_DIR


# Now install the symbolic links to the binaries. Use the relative path
# if it is defined, or else use the absolute path to CRUSH
if [ -z "$BIN_TO_CRUSH" ] ; then
	BIN_TO_CRUSH=`pwd`
	echo Found path to CRUSH: $BIN_TO_CRUSH
fi

echo Installing CRUSH binaries to $BINDIR...
mkdir -p $BINDIR
cd $BINDIR
ln -sf $BIN_TO_CRUSH/crush .
ln -sf $BIN_TO_CRUSH/coadd .
ln -sf $BIN_TO_CRUSH/detect .
ln -sf $BIN_TO_CRUSH/difference .
ln -sf $BIN_TO_CRUSH/esorename .
ln -sf $BIN_TO_CRUSH/histogram .
ln -sf $BIN_TO_CRUSH/imagetool .
ln -sf $BIN_TO_CRUSH/jiggle .
ln -sf $BIN_TO_CRUSH/show .
cd $CURRENT_DIR


echo Done!

