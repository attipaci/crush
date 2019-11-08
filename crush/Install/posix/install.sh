#!/bin/bash
#
# Usage: install.sh [install-root]
#
# This script installs links to the CRUSH binaries and copies the man pages, 
# into the bin/ and share/man/ directories under <install-root> (if specified), # or under /usr, if no argument is given.
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


# Find the absolute path to this script (follow links manually, not using 
# 'readlink -f' which does not work on MacOS X)
CURRENT_DIR=`pwd`
cd `dirname $0`
SCRIPTNAME=`basename $0`
while [ -L $SCRIPTNAME ] ; do
  SCRIPTNAME=`readlink $(basename $SCRIPTNAME)`
  cd `dirname $SCRIPTNAME`
  SCRIPTNAME=`basename $SCRIPTNAME`
done
SCRIPTDIR=`pwd`
cd $CURRENT_DIR


INSTALL_FROM=$SCRIPTDIR


# Go into the CRUSH distribution directory for this script...
# It's two levels up from the install script's location.
cd ${INSTALL_FROM}/../..

# Set the distribution directory
CRUSHDIR=`pwd`

echo Installing CRUSH manuals to $MANDIR...

# Set the proper SELinux context for the man directory and its contents
#chcon -R -u system_u $INSTALL_FROM/man
#chcon -R -t man_t $INSTALL_FROM/man 

# Now copy the man pages to their destination.
mkdir -p $MANDIR
cp -r $INSTALL_FROM/man/* $MANDIR

# [Linux only] Also install the icons and desktop launchers now...
OSNAME=`uname`

if [ $OSNAME == "Linux" ] ; then
	echo Installing Icons and desktop launchers under $SHAREDIR...
	
	#chcon -R -u system_u $INSTALL_FROM/share
	#chcon -R -t usr_t $INSTALL_FROM/share

	mkdir -p $SHAREDIR
	cp -r $INSTALL_FROM/share/* $SHAREDIR
fi 


# Now install the symbolic links to the binaries. 
echo Installing CRUSH binaries to $BINDIR...
mkdir -p $BINDIR
cd $BINDIR

ln -sf $CRUSHDIR/crush .
ln -sf $CRUSHDIR/coadd .
ln -sf $CRUSHDIR/detect .
ln -sf $CRUSHDIR/difftool .
ln -sf $CRUSHDIR/esorename .
ln -sf $CRUSHDIR/histogram .
ln -sf $CRUSHDIR/imagetool .
ln -sf $CRUSHDIR/show .

cd $CURRENT_DIR

echo Done!

