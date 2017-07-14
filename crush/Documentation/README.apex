
       *** CRUSH User's Manual Extension for the APEX Cameras ***

                              Attila Kovacs
                      <attila[AT]sigmyne.com>

                       Last updated: 20 February 2016



Table of Contents
=================

1. Introduction

2. Locating Scan

   2.1 Data directory organization
   2.2 By file names
   2.3 By location, project, and scan number(s)
   2.4 ESO archived data

3. Glossary of APEX-specific options

4. APEX-specific log quantities




#####################################################################
1. Introduction
#####################################################################


This document contains information specific to using CRUSH-2 to reduce data 
from the cameras of the APEX telescope. It is assumed that you are already 
familiar with the contents of the main CRUSH-2 README (inside the distribution 
directory), especially its Section 1 (Getting Started).

If you run into difficulty understanding the concepts in this document, you
will probably find it useful to study the main README document a little longer.





#####################################################################
2. Locating Scans
#####################################################################


2.1 Data directory organization
===============================

APEX typically stores each project on a separate account, from which the 
designated users can access it. Each scan is stored in a separate directory 
(or merged scan file), whose name is constructed from the scan number, (e.g. 
15043), the observation date (2014-04-22) and the project name (e.g. 
T-093.F-0001-2014), e.g. as:

  APEX-15843-2014-04-22-T-093.F-0001-2014

There is also a global archive (for privileged APEX staff) with all the data, 
where scans are stored under a sub-folder with the project name. E.g., in the
global archive the above scan would reside under:

  rawdata/T-093.F-0001-2014/

Data obtained from the ESO archive has a different naming convention (see 
further below).



2.2 By file name
================

One way (often not the simplest way!) to specify input scan(s) to CRUSH is to 
provide the name of each scan file (or directory). You can use absolute paths:

  > crush laboca ~/rawdata/APEX-15843-2014-04-22-T-093.F-0001-2014

or, you might use paths relative to a common 'datapath':

  > crush laboca -datapath=~/rawdata APEX-15843-2014-04-22-T-093.F-0001-2014 

If all your data uses the same datapath (or if you are concentrating on a 
dataset in one particular location at this time), you might want to put 
'datapath' into '~/.crush2/laboca/default.cfg' to make it a persistent personal
setting. This way, you won't need to set it on the command-line every time...


2.3 By location, project-name and scan number
=============================================

Since the scan naming (and the optional sub-folder structures) involve project 
names and scan names, the easiest way to access the scan data is with a 
combination of 'datapath', and 'project' settings, and scan numbers.

To reduce scan 15843 (from the example above), you would invoke crush as:

  > crush laboca -datapath=~/rawdata -project=T-093.F-0001-2014 15843

CRUSH will then look for your scans both in:

  ~/rawdata

and in:

  ~/rawdata/T-093.F-0001-2014

Thus, the same 'datapath' setting can handle the optional project sub-folders
when the 'project' setting is also defined. Once again, you might put the 
common 'datapath' and 'project' settings in ~/.crush2/laboca/default.cfg. If 
so, you can simply type:

  > crush laboca 15843

(The above will implicitly use your settings from ~/.crush2/laboca/default.cfg
for locating the scan). Now, you might start to see why referring to scans by
scan numbers is more convenient than using full file names.

Suppose you want to reduce two sets of scans, 16010-16014 from project 
T-093.F-0001-2014 and 9833-9838 from T-094.F-0001-2015. You can simply switch
project name (or datapath) mid-command:

  > crush [...] -project=T-093.F-0001-2014 16010-16014 \
    -project=T-094.F-0001-2015 9833-9838

Using ranges of scan numbers is a lot less work (for you) than listing the
filename for each individual input scan.


2.4 ESO archived data
=====================

ESO has its own scan naming and numbering scheme, which is different from APEX.
You can always use complete filenames to reduce these scans just the same as
before. However, the ESO naming will not allow you to use APEX scan numbers
(or ranges) which often make life so much easier.

You can however, restore ESO files to their original APEX names using the
'esorename' tool, included in CRUSH. Once the file is renamed to the APEX
convention, you can locate it as usual by project name and scan number(s) as
describved above.





#####################################################################
3. Glossary of APEX-specific options
#####################################################################


:: febe			@Expert
                        Defines the frontend-backend combination to use.
                        E.g. for LABOCA, this would be set to 'LABOCA-ABBA'.

:: project=<id>    	Some instruments (e.g. APEX bolometers) may require a
                        project ID to be set in order to locate scans by serial
                        number. Use capitalized form when defining APEX
                        projects. E.g.,

                              project T-79.F-0002-2007

:: tau.window=X		@Expert
			@Since: 2.13
                        Set the 1-sigma Gaussian weighting window (in hours)
                        for deriving local weighted average tau values from a
                        table of skydip/tau measurements.
                        @GISMO: since 2.15-2.
                        @See: 'tau', 'scale.window'



#####################################################################
4. APEX-specific log quantities
#####################################################################




------------------------------------------------------------------------------
Copyright (C)2016 -- Attila Kovacs <attila[AT]sigmyne.com>
