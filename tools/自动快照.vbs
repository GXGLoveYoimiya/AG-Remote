' Run every 30 min by scheduled task "AG Remote 自动快照": commit a git snapshot if anything changed (no window).
' Restore: in the project folder run "git log", then "git checkout <id> -- <file>".
Set sh = CreateObject("WScript.Shell")
' This script lives in <repo>\tools, so the repo is two levels up from it.
Set fso = CreateObject("Scripting.FileSystemObject")
repo = fso.GetParentFolderName(fso.GetParentFolderName(WScript.ScriptFullName))
cmd = "cmd /c cd /d """ & repo & """ && git add -A && git diff --cached --quiet || git commit -q -m ""auto snapshot"""
sh.Run cmd, 0, True
