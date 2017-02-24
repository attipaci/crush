:: ============================================================================
:: Description:
::
:: This batch file will install CRUSH on a Windows system into the 'Program
:: Files' folder, or under the user's profile directory, removing any POSIX 
:: content and, if possible, adding crush onto the PATH for easy access to the 
:: CRUSH executables.
::
:: Author: Attila Kovacs <attila@sigmyne.com>
:: Date: 23 February 2017
::
:: Usage:
::
::    INSTALL.BAT		-- Install to the default location, which is
::    				   "C:\Program Files" if run as Administrator
::    				   or %USERPROFILE%\crush, e.g. 
::    				   "C:\Program Files\John Doe\crush"
::    				   without elevated privilege.
::    				   
:: ============================================================================

@echo off

echo.
echo ----------------------------------------------------------
echo CRUSH Windows Installer -- Copyright ^(c^)2017 Attila Kovacs
echo ----------------------------------------------------------
echo.

:: Enable delayed expansion so we can test for variables inside if statements
:: and loops...
setlocal EnableDelayedExpansion

:: Set the default exit status
set EXIT_STATUS=0

:: Change to the directory in which this script resides
cd %~dp0

if exist "..\windows\" (
 cd ..\..
) else if exist "crush\" (
 cd crush
)


if not exist "..\crush\" (
  echo ERROR! Cannot find the crush distribution directory^(?^)... 
  echo Aborting.
  pause
  exit /b 1
)


:: Set the install from location for cleanup purposes...
set INSTALL_FROM=%CD%

:: 
echo Configuring distribution ^(in situ^)...

:: If the script is in its distribution location then move the scripts to
:: the main CRUSH directory one up and then change to it...
if exist Install\windows\nul  (
  echo . Copying Windows-specific files to main CRUSH directory...
  attrib -h Install\windows\*.bat
  copy /y /l Install\windows\* . >nul 2>&1
  copy /y /l Install\windows\startup\* startup\ >nul 2>&1
  del /f /q install.bat >nul 2>&1
  move /y *.txt Documentation\ >nul 2>&1
)

:: Delete all the bash scripts
echo . Deleting UNIX shell scripts...
del /f /q *.sh >nul 2>&1
del /f /q startup\*.sh >nul 2>&1

for %%f in (.\*.bat) do (
  set batchfile=%%f
  set script=!batchfile:~0,-4!
  if exist !script! del /f /q !script! >nul 2>&1
)



:: Move one up to the current host directory of CRUSH
cd ..

:: Check Administrator privileges
echo.
net session > nul 2>&1
if %errorlevel% equ 0 (
  set ADMIN=1
  set DST=C:\Program Files
  echo Administrator privileges confirmed.
) else (
  set ADMIN=
  set DST=%USERPROFILE%
  echo WARNING!
  echo You do not appear running the CRUSH installer as Administrator.
  echo If you want to install CRUSH under C:\Program Files, add the CRUSH
  echo executables to the system search path, and create start menu shortcuts,
  echo then you should exit now, and run the installer again as Administrator.
)
echo.


if "%DST%"=="%INSTALL_FROM%" ( 
  echo.
  echo CRUSH is already at the default install destination and is ready to go.
  echo.
  pause
  goto :END
)

echo.
echo CRUSH will be installed in %DST%...

set /p agree="--> Proceed (Y/[N])? "
if /i not "%agree%"=="y" (
  echo Aborting.
  goto :END
)
echo.

set CRUSH=%DST%\crush

:: Remove any prior installation

if exist "%CRUSH%\" (
  echo WARNING^> Existing %CRUSH% directory.
  set /p agree="--> Do you want to replace it (Y/[N])? "

  if "!agree!"=="y" (
    echo . Removing existing %CRUSH%
    del /s /f /q "%CRUSH%" >null 2>&1
    rmdir /s /q "%CRUSH%" >null 2>&1
  ) else (
    echo Aborting.
    goto :END
  )
)
echo.

echo . Copying files to %DST%
xcopy crush "%CRUSH%\" /e /c /q /b /h >nul 2>&1

:: Removing Install\ subdirectory from installation
echo . Removing the Install directory from the deployment...
del /s /f /q "%CRUSH%\Install" >nul 2>&1
rmdir /s /q "%CRUSH%\Install" >nul 2>&1


:: Go to the installation directory
cd %CRUSH%


:: If not running as Administrator, then we are done here...
if not defined ADMIN (
  echo . Skipping Start Menu Shortcuts and search PATH configuration.
  del /f *.lnk >nul 2>&1
  echo Successful local user install!
  goto :END
)

:: Install the Start Menu shortcuts... 
set startmenu=%ALLUSERSPROFILE%\Microsoft\Windows\Start Menu\Programs\CRUSH

echo . Creating Start Menu shortcuts...
if exist "%startmenu%\" ( 
  del /f /q "%startmenu%\*"
) else (
  mkdir "%startmenu%"
)
copy /y /l *.lnk "%startmenu%\" >nul 2>&1


:: Add CRUSH to path (if not already there...)
echo.%PATH% | find /I "%CRUSH%" > Nul
if errorlevel 1 ( 
  echo . Adding %CRUSH% to system search path...
  reg add HKCU\Environment /v PATH /d "%PATH%;%CRUSH%" /f 
) else (
  echo . CRUSH is already in the search path.
)

echo Successful system-wide install!


:: Exit gracefully, whenever possible...
:END  
endlocal
echo Done.

exit /b %EXIT_STATUS%


