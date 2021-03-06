               *** CRUSH User's Manual Extension for MAKO ***

                              Attila Kovacs
                      <attila[AT]sigmyne.com>

                        Last updated: 23 Apr 2013



Table of Contents
=================

1. Introduction
2. Locating Scans
3. Extinction Correction
4. Glossary of MAKO-specific options





#####################################################################
1. Introduction
#####################################################################

This document contains information specific to using CRUSH-2 to reduce MAKO
data. It is assumed that you are already familiar with the contents of the main
CRUSH-2 README (inside the distribution directory), especially its Section 1
(Getting Started).

If you run into difficulty understanding the concepts in this document, you
will probably find it useful to study the main README document a little longer.




#####################################################################
2. Locating Scans
#####################################################################

MAKO scans come in three varieties: resonance searches, calibrations and
stream scans. They all are in the same directory, typically, and have names
such 

   mako20130421_093731Stream.fits


One can always submit stream scans to CRUSH by full datapath, or by filename
(if 'datapath' is configured). However, it may be easiest to let crush find
the files by date and time (again, assuming 'datapath' is set).

For example, you can reduce the above file as:

   crush mako [...] -date=2013-04-21 09:37

The 'date' option can be specified either as 'YYYY-MM-DD' or as 'YYYYMMDD'.
Similarly, the time specification can be 'hh:mm' or 'hhmm'. You can reduce
multiple files from different days, e.g. as:

   crush mako -date=2013-04-21 09:37 09:55 -date=2013-04-22 1259 13:11 1324 


#####################################################################
3. Extinction correction
#####################################################################

By default, MAKO uses a weighting local average value of the 225 GHz tau tipper
record from the CSO, to calculate an appropriate in-band (using SHARC-2's 350um
tau scaling relation), line-of-sight extinction correction. You can adjust the 
1-sigma time-window for the Gaussian weighting function via the 'tau.window'
keyword. The default setting is 0.5 (hours).

Alternatively, you can manually set tau values. E.g.:

   > crush [...] -tau.225GHz=0.055 -tau=225GHz <scans>

(The first option specifies a 225GHz tau value, while the second option tells
CRUSH to use the 225 GHz value for calculating the in-band tau. Other than
'225GHz' you can specify '350um' tau values, or 'PWV' (precipitable water 
vapor vales in mm), as well as direct in-band 'mako' zenith tau values.



#####################################################################
4. Glossary of MAKO-specific options
#####################################################################

The MAKO modules of CRUSH share much of their DNA with the SHARC-2 module.
Thus, nearly all options that work for SHARC-2 will also work with MAKO.
If you are familiar with SHARC-2 and CRUSH-1.xx, you should read the
'README.crush-1.xx' also. Below are the options, which are specific to MAKO 
only:  

   assign		Attempt to assign resonances to pixels. This is
			a prerequisite for making maps, but is not required
			for timestream analysis or for 'pixelmap'.
			Resonances that cannot be assigned will be ignored.

   assing.max=X		The maximum distance a resoance may fall from the
			predicted value for valid assignment. The distance
			is expressed as a multiple of the rms deviation
			of the resonances from the model. E.g. values
			between 2 and 3 (sigma deviation) are good 
			conservative starting points.

   convert=<path>	The IQ --> frequency shift conversion software to use, 
			with full path specification (unless it's in the user's
			default path).

   convert.naming=<spec>	The renaming convention that the IQ --> 
				frequency shift conversion tool. This is the
			string that is inserted between periods before
			the 'fits' filename extension. I.e. it is assumed
			that the converter renames files as:

			  <original>.fits --> <original>.<naming>.fits

   date=YYYY-MM-DD      Specify the observing date (UT) in the format
                        YYYY-MM-DD, or YYYYMMDD. This is used for constructing 
			scan identifiers by combining with time-stamps.
                        You also need to specify 'datapath' before CRUSH is 
			able to find scans by time.
                        @See: 'datapath', 'object'

  
------------------------------------------------------------------------------
Copyright (C)2013 -- Attila Kovacs <attila[AT]sigmyne.com>

