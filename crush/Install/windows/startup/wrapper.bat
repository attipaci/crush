:: ===========================================================================
:: Description: Java configuration wrapper batch file for CRUSH tools.
:: Author: Attila Kovacs <attila@sigmyne.com>
:: Updated: 11 February 2017
:: ===========================================================================  
@echo off

:: Attempt to auto configure CRUSH. This should provide optimal settings on
:: or else set fail-safe defaults. Users can override settings by uncommenting 
:: or editing lines further below, or preferably by adding their own persistent
:: settings in under C:\ProgramData\crush2\startup\ or .crush2\startup\ inside
:: your user profile (e.g. C:\Users\John Doe\.crush\startup\java.conf).
:: If you do not want automatic configuration, then comment or delete the line
:: below.
set STARTUP=%~dp0
call "%STARTUP%\autoconf.bat"

:: You may uncomment/edit settings in the section below. However, the preferred
:: method to configure the Java runtime is through configuration files placed
:: in C:\ProgramData\crush2\startup\  or .crush2\startup\ directories inside
:: the user profile (e.g. C:\Users\John Doe\.crush2\startup). For example,
:: You may place entries like:
::
::   set JAVA=C:\Program Files\Java\jre6\bin\java
::   set DATAMODEL=64
::   set USEMB=4000
::   set EXTRAOPTS=
::
:: (The contents of which are parsed as a DOS batch file, so you may put other 
:: batch directives in there also.). The above would define a user runtime 
:: configuration that uses the latest Oracle java in 64-bit mode, allowing to 
:: use up to 4GB of ram, with no extra options.
::
:: Add your specific installation defaults below... 
:: -----------------------------------------------------------------------------
   

:: To use a java other than the default version on the system adjust by
:: by commentting/uncommenting and adjusting the line below. 
::
::set JAVA=java

:: Set the data model to be 32-bit or 64-bit. To use 64-bit model, you need
:: a 64-bit OS and a 64-bit Java installation
::
::set DATAMODEL=32

:: Choose the maximum amount of RAM (in MB) that you will allow Java to use.
:: The default is to use up to 80% of the total available RAM. On 32-bit
:: machines (or when DATAMODEL is set to "32") the value should remain 
:: significantly below 2000, e.g. 1900. In 64-bit mode, you can specify more
::
::set USEMB=1900


:: --------------------- DO NOT CHANGE BELOW THIS LINE -----------------------

:: Parse startup configuration files in crush2\startup directory inside
:: the common user profile (usually C:\ProgramData).
for %%f in ("%ALLUSERSPROFILE%\crush2\startup\*") do call %%f

:: Parse startup configuration files in .crush2\startup directory inside
:: the user profile directory
for %%f in ("%USERPROFILE%\.crush2\startup\*") do call %%f

:: Parse program-specific startup configurations if this wrapper script
:: was called with at least one argument that specifies the program name.
if not "%1"=="" (
  for %%f in ("%ALLUSERSPROFILE%\crush2\startup\%1\*") do call %%f
  for %%f in ("%USERPROFILE%\.crush2\startup\%1\*") do call %%f
)

:: Verify the runtime configuration settings before launch...
if defined DEBUG (
  echo Pre-launch:
  echo   JAVA=%JAVA%
  echo   JVM=%JVM%
  echo   DATAMODEL=%DATAMODEL%
  echo   USEMB=%USEMB%
  echo   EXTRAOPTS=%EXTRAOPTS%
  echo .
)

:: Check to make sure JAVA is set
if not defined JAVA (
  echo.
  echo ERROR! No Java Runtime Environment ^(JRE^) found.
  echo        If Java is installed on your system, then set the JAVA variable
  echo        manually to point to it, in a config file placed within
  echo        C:\ProgramData\crush2\startup\ or .crush2\startup\ inside your
  echo        profile directory.
  echo        E.g. place the line:
  echo.
  echo           JAVA=C:\ProgramFiles\Java\jre6\bin\java
  echo.
  echo        in '%USERPROFILE%\.crush2\startup\java.conf'.
  echo        Otherwise, install a Java Runtime Environment ^(JRE^), e.g.
  echo        from www.java.com.
  echo.
  exit 1
)


:: Set the Java runtime options based on the settings so far...
set JAVAOPTS=%JVM% -Xmx%USEMB%M %EXTRAOPTS%

:: This line defines where java will look for the relevant source files. Leave
:: it as is, unless you have a good reason to change it...
set CLASSPATH=%CRUSH%lib\*

:: --------------------------- END OF BATCH FILE -----------------------------
