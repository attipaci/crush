@echo off

:: Check if the CRUSH variable has been defined to specify the crush 
:: installation directory. If not, then assume we are running this from
:: within the crush directory
::
if not defined CRUSH set CRUSH=%CD%

:: Set up the common variables for JAVA...
call "%CRUSH%\wrapper.bat"

:: Run tool with the supplied arguments...
%JAVA% %JAVAOPTS% -classpath %CLASSPATH% ImageJiggler %*
