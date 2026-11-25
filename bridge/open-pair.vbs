' Open the AG Remote pairing page. Starts the service first if it is not running.
' Uses an Edge app window when Edge is available, otherwise the default browser.
' Keep this file ASCII-only: wscript reads it in the system ANSI code page.
Option Explicit
Dim sh, fso, root, port, url, i
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)

port = sh.ExpandEnvironmentStrings("%AGR_PORT%")
If port = "%AGR_PORT%" Or port = "" Then port = "5100"
url = "http://127.0.0.1:" & port & "/pair"

If Not PortListening() Then
  sh.Run "wscript """ & root & "\start-service.vbs""", 0, True
  For i = 1 To 30
    If PortListening() Then Exit For
    WScript.Sleep 500
  Next
End If

On Error Resume Next
sh.Run "msedge --app=" & url & " --window-size=900,1000", 1, False
If Err.Number <> 0 Then
  Err.Clear
  sh.Run url, 1, False
End If

Function PortListening()
  PortListening = (sh.Run("powershell -NoProfile -NonInteractive -Command ""if (Get-NetTCPConnection -LocalPort " & port & " -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }""", 0, True) = 0)
End Function
