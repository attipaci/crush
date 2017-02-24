@echo off

:: Find the absolute path to CRUSH 
set CRUSH=%~dp0

:: Set up the common variables for JAVA...
call "%CRUSH%startup\wrapper.bat" crush

:: Run crush with the supplied arguments...
%JAVA% %JAVAOPTS% -classpath "%CLASSPATH%" crush.CRUSH %*

:: if run without arguments then wait for confirmation before exiting or
:: closing command window...
if "%1"=="" pause 
