:: ============================================================================
:: Description:
::
:: This batch file will attempt to configure optimal setting for running CRUSH 
:: on Windows OS. 
:: It should be called from 'wrapper.sh' exlusively. Otherwise, it will not 
:: have the desired effect.
::
:: Author: Attila Kovacs <attila@sigmyne.com>
:: Date: 11 February 2017
:: ============================================================================

@echo off

:: Start by fail-safe defaults: default java, 32-bit mode, 1 GB of RAM, and
:: default VM with no extra options...
set JAVA=java
set DATAMODEL=32
set USEMB=1000
set JVM=
set EXTRAOPTS=


:: Try to set USEMB to 80% of the physical RAM in the system...
set memsize=
for /f "tokens=* skip=1 USEBACKQ" %%l in (`wmic computersystem get totalphysicalmemory`) do if not defined memsize set memsize=%%l

set memsize=%memsize: =%
set memsize=%memsize:~0,-3%
set /a USEMB=memsize/1311
set memsize=

:: Check if we can run the default Java..
java -version > Nul 2>&1
if errorlevel 1 exit /b 0

:: Now try to figure out if we are using a 64-bit java VM
:: and if we can use the '-server' flag...
set vmtype=
for /f "tokens=* skip=2 USEBACKQ" %%l in (`%JAVA% -version 2^>^&1`) do if not defined  vmtype set vmtype=%%l

echo.%vmtype% | FIND /I "64-" >Nul 
if %errorlevel%==0 ( 
  set DATAMODEL=64
) else (
  set DATAMODEL=32
  rem The Windows 32-bit VM seems to max out at around 1200M heap size...
   if %USEMB% gtr 1000 set LIMIT=1000
)

if defined LIMIT set USEMB=%LIMIT%

echo.%vmtype% | FIND /I "Server VM" > Nul
if %errorlevel%==0 ( 
  set JVM=-server
)

set vmtype=

