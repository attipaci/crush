@echo off

:: Check if the CRUSH variable has been defined to specify the crush 
:: installation directory. If not, then assume we are running this from
:: within the crush directory
::
if not defined CRUSH set CRUSH=%CD%

:: Set up the common variables for JAVA...
call "%CRUSH%\wrapper.bat"

:: Run crush with the supplied arguments...
%JAVA% %JAVAOPTS% -classpath %CLASSPATH% crush.CRUSH %* 
