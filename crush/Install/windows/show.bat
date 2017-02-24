@echo off

:: Find the absolute path to CRUSH 
set CRUSH=%~dp0

:: Set up the common variables for JAVA...
call "%CRUSH%startup\wrapper.bat" show

:: Run tool with the supplied arguments...
%JAVA% %JAVAOPTS% -classpath "%CLASSPATH%" crushtools.ShowData %*

