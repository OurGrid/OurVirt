echo off
setlocal EnableDelayedExpansion
set "networkfile=%TEMP%.\networks.reg"
set "driversfile=%TEMP%.\nodes.txt"

regedit.exe /e "%networkfile%" HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Class\{4D36E972-E325-11CE-BFC1-08002BE10318}
type "%networkfile%" | findstr "NetCfgInstanceId DeviceInstanceID" > "%driversfile%"
del "%networkfile%"

set "node="
FOR /F "tokens=1,2 delims==" %%A IN ('type "%driversfile%"') DO (
 set "lastnode=!node!"
 set "node=%%B"
 set "test=%%A"
 
 if [!test!] == ["DeviceInstanceID"] (
  if [!node!] == ["%1"] (
   set "TAPINTERFACE=!lastnode:~1,-1!"
  )
 )
)
del "%driversfile%"

set "nodename=%2"
echo Windows Registry Editor Version 5.00 > "%networkfile%"
echo. >> "%networkfile%"
echo [HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Network\{4D36E972-E325-11CE-BFC1-08002BE10318}\%TAPINTERFACE%\Connection] >> "%networkfile%"
echo "Name"="%nodename%" >> "%networkfile%"

regedit.exe /s "%networkfile%"
del "%networkfile%"