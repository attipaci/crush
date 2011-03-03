@echo off

:: To use a java other than the default version on the system adjust by
:: by commentting/uncommenting and/or altering the desired JAVA line. 
::
:: It is recommended that you use SUN's latest java, which is generally 
:: installed into the default location:
::set JAVA="C:\Program Files\Java\jre6\bin\java"
:: 
:: If the java executable is already in your path (probably the case), then
:: the following should suffice
set JAVA="java"

:: Here you can specify the options passed to the Java Runtime Environment
:: E.g., adjust the -Xmx value to reflect the amount of memory in your
:: system that you wish to make available to java.
::
set JAVAOPTS="-Xbatch -Xmx1000M"

:: Check if the CRUSH variable has been defined to specify the crush 
:: installation directory. If not, then assume we are running this from
:: the crush directory itself

if not defined CRUSH set CRUSH=%CD%

:: This line defines where java will look for the relevant source files. Leave
:: it as is, unless you have a good reason to change it...
set CLASSPATH="%CRUSH%\crush.jar;%CRUSH%\fits.jar;%CRUSH%\bin"

