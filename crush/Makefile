#!/bin/bash
#
# Usage: 
#          make install
#	   make remove
#
# This script installs links to the CRUSH binaries and the man pages, into 
# the /usr/bin/ and /usr/share/man/ directories, with DESTDIR prepended if 
# specified.
#
#
# You may also edit INSTALL_ROOT for a more customized installation path.
# (e.g. /usr/local or /opt)
#
# The script may require root privileges to complete.

INSTALL_ROOT=$(DESTDIR)/usr

all: help

help:

	@echo
	@echo " Install or remove CRUSH binaries and man pages: "
	@echo " (with support for staged installs via DESTDIR)"
	@echo
	@echo "    make install" 
	@echo "    make remove"
	@echo ""

install:

	sh install.sh $(INSTALL_ROOT)

remove:

	sh remove.sh $(INSTALL_ROOT)

