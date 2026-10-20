@echo off
set JAVA_HOME=C:\Users\MR\.dsh\ungrouped\tools\jdk21\jdk-21.0.12.1+1
cd /d C:\Users\MR\.dsh\ungrouped\paperlocal
call C:\Users\MR\.dsh\ungrouped\tools\gradle-8.7\bin\gradle.bat assembleDebug --no-daemon -g C:\Users\MR\.gradle
echo EXITCODE=%ERRORLEVEL%
