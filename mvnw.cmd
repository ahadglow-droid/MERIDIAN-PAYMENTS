@echo off
if exist "C:\Users\Ahad\tools\apache-maven-3.9.9\bin\mvn.cmd" (
    call "C:\Users\Ahad\tools\apache-maven-3.9.9\bin\mvn.cmd" %*
) else (
    mvn %*
)
