@echo off
REM ==================================================================================================
REM   Run SOLCLIENTJ sample applications
REM
REM   Copyright 2004-2017 Solace Corporation. All rights reserved.
REM ==================================================================================================
if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_SOLCLIENTJ_HOME=%~dp0..

if "%SOLCLIENTJ_HOME%"=="" set SOLCLIENTJ_HOME=%DEFAULT_SOLCLIENTJ_HOME%
set DEFAULT_SOLCLIENTJ_HOME=

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).
set SOLCLIENTJ_CMD_LINE_ARGS=%1
if ""%1""=="""" goto errorMsg
set FOUND=1
for %%i in (samples.DirectPubSub samples.PerfPubSub samples.ActiveFlowIndication samples.AdPubAck samples.NoLocalPubSub samples.TopicDispatch samples.RRDirectRequester samples.RRDirectReplier samples.RRGuaranteedRequester samples.RRGuaranteedReplier samples.Transactions samples.SyncCacheRequest samples.ASyncCacheRequest samples.TopicToQueueMapping samples.SimpleFlowToTopic samples.SimpleFlowToQueue samples.SubscribeOnBehalfOfClient samples.MessageTTLAndDeadMessageQueue samples.MessageSelectorsOnQueue samples.Replication samples.DTOPubSub samples.QueueProvision samples.SecureSession samples.CutThroughFlowToQueue  samples.PerfCutThroughFlowToQueue samples.PerfADPubSub samples.JMSHeaders ) do (
  if ""%%i""==""%1"" set FOUND=0
)

if "%FOUND%"=="1" goto errorMsg

shift

:setupArgs
if ""%1""=="""" goto doneStart
set SOLCLIENTJ_CMD_LINE_ARGS=%SOLCLIENTJ_CMD_LINE_ARGS% %1
shift
goto setupArgs
rem This label provides a place for the argument list loop to break out 
rem and for NT handling to skip to.

:doneStart
set _JAVACMD=%JAVACMD%
set LOCALCLASSPATH=%SOLCLIENTJ_HOME%\classes;%CLASSPATH%
for %%i in ("%SOLCLIENTJ_HOME%\..\lib\*.jar") do call "%SOLCLIENTJ_HOME%\bin\lcp.bat" %%i

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto run

:noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=java.exe
echo.
echo Warning: JAVA_HOME environment variable is not set.
echo.

:run

if "%SOLCLIENTJ_OPTS%" == "" set SOLCLIENTJ_OPTS=-Xmx128M

REM Uncomment to enable remote debugging
REM SET SOLCLIENTJ_DEBUG_OPTS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005

REM Check if os is 32 or 64 bit
reg Query "HKLM\Hardware\Description\System\CentralProcessor\0" | findstr /i "x86" > NUL && set "PLATFORM=Win32" || set "PLATFORM=Win64"

REM To locate logging configuration
set LOCALCLASSPATH=%SOLCLIENTJ_HOME%\config;%LOCALCLASSPATH%
REM To load sample related libs
set PATH=%SOLCLIENTJ_HOME%\..\bin\%PLATFORM%;%PATH%
echo =================================================================================================
echo using JAVA_HOME=%JAVA_HOME%
echo -Djava.library.path=%~dp0..\..\bin\%PLATFORM%
echo =================================================================================================
"%_JAVACMD%" %SOLCLIENTJ_DEBUG_OPTS% %SOLCLIENTJ_OPTS% -Djava.util.logging.config.file="%SOLCLIENTJ_HOME%\config\sample_logging_config.properties" -Djava.library.path="%SOLCLIENTJ_HOME%\..\bin\%PLATFORM%" -classpath "%LOCALCLASSPATH%" com.solacesystems.solclientj.core.%SOLCLIENTJ_CMD_LINE_ARGS%
goto end

:errorMsg

echo Expecting one of the following as the first argument:
echo samples.DirectPubSub
echo samples.PerfPubSub
echo samples.ActiveFlowIndication
echo samples.AdPubAck
echo samples.NoLocalPubSub
echo samples.TopicDispatch
echo samples.RRDirectRequester
echo samples.RRDirectReplier
echo samples.RRGuaranteedRequester
echo samples.RRGuaranteedReplier
echo samples.Transactions
echo samples.SyncCacheRequest
echo samples.ASyncCacheRequest
echo samples.TopicToQueueMapping
echo samples.SimpleFlowToTopic
echo samples.SimpleFlowToQueue
echo samples.SubscribeOnBehalfOfClient
echo samples.MessageTTLAndDeadMessageQueue
echo samples.MessageSelectorsOnQueue
echo samples.Replication
echo samples.DTOPubSub
echo samples.QueueProvision
echo samples.SecureSession
echo samples.CutThroughFlowToQueue
echo samples.PerfCutThroughFlowToQueue
echo samples.PerfADPubSub
echo samples.JMSHeaders
goto end

:end
set LOCALCLASSPATH=
set _JAVACMD=
set SOLCLIENTJ_CMD_LINE_ARGS=

if "%OS%"=="Windows_NT" @endlocal

:mainEnd

