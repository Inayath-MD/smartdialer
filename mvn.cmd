@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.2.2\plugins\maven\lib\maven3\bin\mvn.cmd" %*
