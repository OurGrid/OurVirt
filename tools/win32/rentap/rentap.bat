echo off
setlocal EnableDelayedExpansion
set "networkfile=%HOMEDRIVE%%HOMEPATH%\networks.reg"
set "driversfile=%HOMEDRIVE%%HOMEPATH%\nodes.txt"
set "openvpncfg=%ProgramFiles%\OpenVPN\config\client.ovpn"

regedit.exe /e "%networkfile%" HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Class\{4D36E972-E325-11CE-BFC1-08002BE10318}
type "%networkfile%" | findstr "NetCfgInstanceId DriverDesc" > "%driversfile%"
del "%networkfile%"

set "node="
FOR /F "tokens=1,2 delims==" %%A IN ('type "%driversfile%"') DO (
 set "lastnode=!node!"
 set "node=%%B"
 set "test=%%A"
 if [!test!] == ["DriverDesc"] (
  if [!node!] == ["TAP-Win32 Adapter V9"] (
   set "TAPINTERFACE=!lastnode:~1,-1!"
  )
 )
)
type "%openvpncfg%" | findstr dev-node > "%driversfile%"
FOR /F "tokens=1,2" %%A IN ('type "%driversfile%"') DO (
if [%%A] == [dev-node] (
set "nodename=%%B"
)
)
del "%driversfile%"

echo Windows Registry Editor Version 5.00 > "%networkfile%"
echo. >> "%networkfile%"
echo
[HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Network\{4D36E972-E325-11CE-BFC1-08002BE10318}\%TAPINTERFACE%\Connection]
>> "%networkfile%"
echo "Name"="%nodename%" >> "%networkfile%"

regedit.exe /s "%networkfile%"
del "%networkfile%"