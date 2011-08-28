Set objShell = WScript.CreateObject("WScript.Shell")
objShell.Run "VBoxHeadless -startvm " & Wscript.Arguments(0), 0
Set objShell = Nothing