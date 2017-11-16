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

# The share directory
SHAREDIR=$INSTALL_ROOT/share

# Determine the directory of this script
SCRIPTNAME=$(readlink -f $0)
SCRIPTDIR=$(dirname $SCRIPTNAME)

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
cd $SCRIPTDIR/man
for page in man1/* ; do
        rm -f $MANDIR/$page
done

# [Linux only] Remove icons and application launcher(s)
OSNAME=`uname`
if [ $OSNAME == "Linux" ] ; then
	echo Removing CRUSH icons and launchers from $SHAREDIR
	rm -f $SHAREDIR/applications/crush*.desktop
	rm -f $SHAREDIR/icons/hicolor/*/*/crush.png
fi

cd $CURRENT_DIR

echo Done!

