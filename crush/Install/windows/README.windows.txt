# CRUSH-2 on Windows 7/8/10 
	
Author: Attila Kovacs <attila[AT]sigmyne.com>

Last updated: 22 February 2017

-----------------------------------------------------------------------------

#### Table of Contents

1. __Introduction__

2. __Installation__
    - 2.1. Java
    - 2.2. Download CRUSH
    - 2.3. Standard installation (Administrator)
    - 2.4. Custom installation (any user)
    - 2.5  Installation from the archive (ZIP or tarball)

3. __(optional) Java runtime configuration__
    - 3.1. Runtime configuration files
    - 3.2. Runtime configuration syntax

4. __Path names in CRUSH pipeline configuration__

---------------------------------------------------------------------------
 
## 1. Introduction

As of version 2.34-2, CRUSH provides improved support for Windows.

This document is aimed specifically at using CRUSH on Windows OS. It is not
a standalone guide. Rather, it is meant to be used together with the main
README, with this document pointing out only the important differences 
for the Windows OS.



## 2. Installation


### 2.1. Java

 Make sure you have Java installed. To check if you have Java, open a command 
 prompt (CMD) and type 

    java -version

 If Java is installed, you will see printed on the console something like:
 
    java version "1.8.0_121"
    java(TM) SE Runtime Environment (build 1.8.0_121-b13)
    Java HotSpot(TM) 64-bit Server VM (build 25.121-b13, mixed mode)

 If, instead, you see a message about the `java` not being a recognized 
 command, then you need to install Java first.

 If you have a 64-bit Windows, it is recommended that you use a 64-bit Java
 (JRE or JDK) to run CRUSH. You can install the 32-bit and 64-bit JREs 
 side-by-side. When both the 32-bit and 64-bit versions are installed, the 
 64-bit one should be default (which you can confirm by printing the version 
 info as shown above), and your browser should be happy too..
   
 To install Java, grab the desired JRE (Java Runtime Environment) from

   https://www.java.com/en/download/manual.jsp
 


### 2.2. Download CRUSH

 Download the latest CRUSH package from:

   http://www.submm.caltech.edu/~sharc/crush
 
 You probably want to get the executable installer (`.EXE`). If that does not
 suit you, you can also download one of the archives (ZIP or tarball).



### 2.3 Standard installation (Administrator)

 Run the installer as Administrator (in File Explorer, right-click on the CRUSH
 installer and select __Run as Administrator__ in the pop-up menu). It will 
 install CRUSH into 
   
    C:\Program Files\crush

 add crush to the search PATH, and install the show tool in the Start Menu. 
 Provided you have Java installed, you should be ready to go. Open a command 
 prompt (CMD), and type `crush`. You should see a simple help screen on using 
 crush. (You can also try __CRUSH show tool__ in you Start Menu -- it should be 
 ready to go also).




### 2.4 Custom installation (any user)

 If you do not have Administrator privileges and/or you run the installer 
 normally (not as Administrator), the installer will set up crush inside your
 profile directory, e.g. into:

    C:\Users\John Doe\crush
   
 After the installer is finished, you may move the extracted crush folder to 
 anywhere you like, and use crush from inside that directory or otherwise by 
 specifying the full path to it, e.g.:

    C:>"\Users\John Doe\crush\crush" hawc+ -flight=360 66

 Optionally, you may want to edit the default search path to include the
 directory in which CRUSH is installed. Open the Control Panel, search for
 and select __System__, then on the left tab, click on __Advanced System 
 Settings__. Click the __Environment Variables__, and edit the 'PATH' 
 variable. Add the CRUSH directory as your new entry at the end. 



### 2.5. Installation from the archives (ZIP or tarball)

 Unpack the archive. In the extracted crush directory, run `windows\install`
 (`.bat`) either as Administrator (for complete system-wide install) or as
 user (to install in user's profile directory). It works the same as the
 Windows installer (see above).

 After the installation is complete, you can delete the crush directory that
 was extracted from the archive.





## 3. (optional) Java Runtime Configuration

As of version 2.34-2, CRUSH behaves very similarly on Windows as it does on 
POSIX (e.g. Linux, BSD, or MacOS X) systems.

This means, that CRUSH will attempt to automatically configure optimal runtime 
settings for your system. If you want to exert more control over the runtime 
configuration, you can do it in a similar way as you would on a POSIX OS.
   
Thus, this section covers only the important ways the Windows configuration 
differs from the usual POSIX one. For further details, please look at the __Java Runtime Configuration__ section of the main README.



### 3.1. Runtime configuration files

 You can place systrem default runtime settings inside the

    C:\Program Data\crush2\startup

 directory. The settings should be stored in batch (.bat) files placed
 in that directory. CRUSH will _call_ all batch files in that directory
 before Java is launched. However, there is no specific order in which the
 files are called, if and when multiple config files are present.

 You can also add user-level configurations, by placing similar batch (.bat)
 files inside a `.crush2\startup\` directory inside your user profile. E.g.:

    C:\Users\John Doe\.crush2\startup\

 The user-level configuration files will be parsed __AFTER__ the system-wide
 default configurations stored in `C:\Program Data\crush2\startup`.

 You may also add program-level runtime configurations, if for example you want
 to run `show` with different settings than used for the other programs of the 
 CRUSH suite. Accordingly you can place batch files under 
   
    ... startup\show\
  
 both for system-wide (in `C:\ProgramData\crush2``) and user-specific ways 
 (e.g. in `C:\Users\John Doe\.crush2\`). Such program-level files will be
 parsed __AFTER__ the universal settings above, with the system-wide settings
 first, followed by any user-specific ones.



### 3.2. Runtime configuration syntax

 As mentioned, the runtime configuration files are basically regular Windows 
 batch (.bat) files that are called each time one of the CRUSH tools is 
 launched. Unfortunately, the Windows command interpreter is not as flexible as
 say `bash`. This means that you have to pay special attention and follow the 
 Windows command rules strictly. Even the smallest errors can break the CRUSH 
 startup process entirely.

 A typical runtime configuration file may contain entries like:
 
    set JAVA=C:\Program Files\Java\jre6\bin\java
    set DATAMODEL=64
    set USEMB=4000
    set JVM=-server
    set EXTRAOPTS=-showversion

 (You can read more about these settings in the __Java Runtime 
 Configuration__ section of the main README.)

 Notice, that none of the values contain quotes. Unlike in bash, where the
 quotes are optional (even encouraged) and are automatically removed (unless 
 escaped), in Windows any quotes are retained as literal parts of the 
 assignment, and their accidental presence will likely break the functionality 
 of CRUSH.

 You can also set other environment variables that may be used by crush, such 
 as:

    set CRUSH_NO_UPDATE_CHECK=1
    set CRUSH_NO_VM_CHECK=1

 (Again, consult the main README on what these do, and why you probably
 should not use them.) 





## 4. Path names in CRUSH pipeline configuration

Path names generally follow the rules of your OS. However, in order to enable 
platform independent configuration files, the UNIX-like `/` is always permitted
(and is generally preferred) as a path separator. As a result, you should avoid
using path names that contain literal `/` characters (even in quoted or escaped
forms!) other than those separating directories.

Since CRUSH allows the use of environment variables when defining settings (see
above), you can use `{@HOMEPATH}` for your user folder under Windows. Or, 
`{#user.home}` has the same effect universally, by referring to it as the Java 
property storing the location of the user's home folder.

The tilde character `~` is also universally understood to mean your home 
folder. And, finally, the UNIX-like `~johndoe` may specify johndoe's home 
directory in any OS as long as it shares the same parent directory with your 
home folder (e.g. both johndoe and your home folder reside under a common 
`/home` or `C:\Users`).

Thus, any of the following path specification may be used in the CRUSH
pipeline configuration:

    "~\My Data"                    # Using the '~' shorthand
    "{$HOMEPATH}\My Data"          # Using environment variables
    "{#user.home}\My Data"         # Java properties
    D:/data/Sharc2/2010            # UNIX-style paths (preferred)
    D:\data\Sharc2\2010            # proper Windows paths
    "~John Doe\Data"               # relative to another user



----------------------------------------------------------------------------
Copyright (C)2017 -- Attila Kovacs



