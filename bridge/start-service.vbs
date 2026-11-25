' Start the AG Remote PC service in the background (no window). The watchdog loop is run-forever.cmd.
' Does nothing if something is already listening on the port. Logs: data\server.log
' Keep this file ASCII-only: wscript reads it in the system ANSI code page.
Option Explicit
Dim sh, fso, root
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)

If PortListening() Then WScript.Quit

sh.CurrentDirectory = root
sh.Run "cmd /c """ & root & "\run-forever.cmd""", 0, False

' Language-independent check (netstat prints LISTENING translated on non-English Windows)
Function PortListening()
  PortListening = (sh.Run("powershell -NoProfile -NonInteractive -Command ""if (Get-NetTCPConnection -LocalPort " & PortNumber() & " -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }""", 0, True) = 0)
End Function

Function PortNumber()
  Dim p
  p = sh.ExpandEnvironmentStrings("%AGR_PORT%")
  If p = "%AGR_PORT%" Or p = "" Then p = "5100"
  PortNumber = p
End Function
