#!/bin/bash
#
# Usage: install.sh [install-root]
#
# This script installs links to the CRUSH binaries and the man pages, into 
# the bin/ and share/man/ directories under <install-root> (if specified), or 
# under /usr, if no argument is given.
#
# You may also edit the BINDIR, MANDIR, SHAREDIR variables for more customized
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

# The location for the shared data
if [ -z "$SHAREDIR" ] ; then 
	SHAREDIR=$INSTALL_ROOT/share
	echo SHAREDIR set automatically to $SHAREDIR
fi



# Determine where the script is being run from...
NAME=$0

if [ -L $NAME ]; then
        NAME=`readlink -f $NAME`
fi

CURRENT_DIR=`pwd`


# FIRST install the man pages
# Go to the directory from where this script was called from (i.e. CRUSH)
cd `dirname $NAME`

# Make CRUSHDIR carry the absolute path to CRUSH
CRUSHDIR=`pwd`
echo CRUSH is located at $CRUSHDIR

echo Installing CRUSH manuals to $MANDIR...

# Set the proper SELinux context for the man directory and its contents
chcon -R -u system_u man
chcon -R -t man_t man 

# Now copy the man pages to their destination.
mkdir -p $MANDIR
cp -r man/* $MANDIR

# [Linux only] Also install the icons and desktop launchers now...
OSNAME=`uname`

if [ $OSNAME == "Linux" ] ; then
	echo Installing Icons and desktop launchers under $SHAREDIR...
	
	chcon -R -u system_u share
	chcon -R -t usr_t share

	mkdir -p $SHAREDIR
	cp -r share/* $SHAREDIR
fi 


# Now install the symbolic links to the binaries. 
echo Installing CRUSH binaries to $BINDIR...
mkdir -p $BINDIR
cd $BINDIR
ln -sf $CRUSHDIR/crush .
ln -sf $CRUSHDIR/coadd .
ln -sf $CRUSHDIR/detect .
ln -sf $CRUSHDIR/difference .
ln -sf $CRUSHDIR/esorename .
ln -sf $CRUSHDIR/histogram .
ln -sf $CRUSHDIR/imagetool .
ln -sf $CRUSHDIR/show .

cd $CURRENT_DIR


echo Done!

