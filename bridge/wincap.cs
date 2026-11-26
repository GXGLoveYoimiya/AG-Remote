using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

class WinCap {
    delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] static extern int GetSystemMetrics(int nIndex);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc lpEnumFunc, IntPtr lParam);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);
    [DllImport("user32.dll")] static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] static extern void mouse_event(uint dwFlags, int dx, int dy, int dwData, UIntPtr dwExtraInfo);
    [DllImport("user32.dll")] static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
    [DllImport("user32.dll", SetLastError = true)] static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
    [DllImport("user32.dll")] static extern bool OpenClipboard(IntPtr hWndNewOwner);
    [DllImport("user32.dll")] static extern bool CloseClipboard();
    [DllImport("user32.dll")] static extern bool EmptyClipboard();
    [DllImport("user32.dll")] static extern IntPtr GetClipboardData(uint uFormat);
    [DllImport("user32.dll")] static extern IntPtr SetClipboardData(uint uFormat, IntPtr hMem);
    [DllImport("user32.dll")] static extern bool GetCursorInfo(ref CURSORINFO pci);
    [DllImport("user32.dll")] static extern IntPtr LoadCursor(IntPtr hInstance, IntPtr lpCursorName);
    [DllImport("user32.dll")] static extern IntPtr CopyIcon(IntPtr hIcon);
    [DllImport("user32.dll")] static extern bool DestroyIcon(IntPtr hIcon);
    [DllImport("user32.dll")] static extern bool GetIconInfo(IntPtr hIcon, out ICONINFO piconinfo);
    [DllImport("user32.dll")] static extern bool DrawIconEx(IntPtr hdc, int xLeft, int yTop, IntPtr hIcon, int cxWidth, int cyWidth, uint istepIfAniCur, IntPtr hbrFlickerFreeDraw, uint diFlags);
    [DllImport("user32.dll", SetLastError = true)] static extern bool InitializeTouchInjection(uint maxCount, uint dwMode);
    [DllImport("user32.dll", SetLastError = true)] static extern bool InjectTouchInput(uint count, [MarshalAs(UnmanagedType.LPArray), In] POINTER_TOUCH_INFO[] contacts);
    [DllImport("gdi32.dll")] static extern bool DeleteObject(IntPtr hObject);
    [DllImport("kernel32.dll")] static extern IntPtr GlobalAlloc(uint uFlags, UIntPtr dwBytes);
    [DllImport("kernel32.dll")] static extern IntPtr GlobalLock(IntPtr hMem);
    [DllImport("kernel32.dll")] static extern bool GlobalUnlock(IntPtr hMem);

    [StructLayout(LayoutKind.Sequential)] struct POINT { public int x, y; }
    [StructLayout(LayoutKind.Sequential)] struct CURSORINFO { public int cbSize, flags; public IntPtr hCursor; public POINT ptScreenPos; }
    [StructLayout(LayoutKind.Sequential)] struct ICONINFO { public bool fIcon; public int xHotspot, yHotspot; public IntPtr hbmMask, hbmColor; }
    [StructLayout(LayoutKind.Sequential)] struct POINTER_INFO {
        public uint pointerType, pointerId, frameId, pointerFlags;
        public IntPtr sourceDevice, hwndTarget;
        public POINT ptPixelLocation, ptHimetricLocation, ptPixelLocationRaw, ptHimetricLocationRaw;
        public uint dwTime, historyCount;
        public int inputData;
        public uint dwKeyStates;
        public ulong PerformanceCount;
        public int ButtonChangeType;
    }
    [StructLayout(LayoutKind.Sequential)] struct POINTER_TOUCH_INFO {
        public POINTER_INFO pointerInfo;
        public uint touchFlags, touchMask;
        public RECT rcContact, rcContactRaw;
        public uint orientation, pressure;
    }

    static bool touchInited = false;
    static bool touchActive = false;

    static bool EnsureTouch() {
        if (touchInited) return true;
        // TOUCH_FEEDBACK_NONE = 0x3：不显示 Windows 触摸水波纹高亮
        touchInited = InitializeTouchInjection(10, 0x3);
        return touchInited;
    }

    static bool SendTouch(uint flags, int px, int py) {
        if (!EnsureTouch()) return false;
        var ti = new POINTER_TOUCH_INFO {
            pointerInfo = new POINTER_INFO {
                pointerType = 0x00000002, // PT_TOUCH
                pointerId = 0,
                pointerFlags = flags,
                ptPixelLocation = new POINT { x = px, y = py },
            },
            touchFlags = 0,
            touchMask = 0x0007, // CONTACTAREA | ORIENTATION | PRESSURE
            rcContact = new RECT { Left = px - 2, Top = py - 2, Right = px + 2, Bottom = py + 2 },
            orientation = 90,
            pressure = 32000,
        };
        return InjectTouchInput(1, new[] { ti });
    }

    const uint CF_UNICODETEXT = 13;
    const uint GMEM_MOVEABLE = 0x0002;
    const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    const uint MOUSEEVENTF_LEFTUP = 0x0004;
    const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    const uint MOUSEEVENTF_WHEEL = 0x0800;
    const uint KEYEVENTF_KEYUP = 0x0002;
    const uint KEYEVENTF_UNICODE = 0x0004;

    [StructLayout(LayoutKind.Sequential)] struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] struct HARDWAREINPUT { public uint uMsg; public ushort wParamL, wParamH; }
    [StructLayout(LayoutKind.Explicit)] struct InputUnion {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public InputUnion U; }

    static bool FindAntigravityWindow(out IntPtr bestHwnd, out RECT bestRect) {
        bestHwnd = IntPtr.Zero;
        bestRect = new RECT();
        var pids = new HashSet<uint>();
        foreach (var p in Process.GetProcessesByName("Antigravity")) {
            try { pids.Add((uint)p.Id); } catch {}
        }
        if (pids.Count == 0) return false;
        IntPtr found = IntPtr.Zero;
        RECT foundRect = new RECT();
        long bestArea = 0;
        EnumWindows((h, l) => {
            uint pid;
            GetWindowThreadProcessId(h, out pid);
            if (!pids.Contains(pid) || !IsWindowVisible(h) || IsIconic(h)) return true;
            RECT r;
            if (!GetWindowRect(h, out r)) return true;
            int w = r.Right - r.Left, ht = r.Bottom - r.Top;
            if (w < 300 || ht < 200) return true;
            long area = (long)w * ht;
            if (area > bestArea) { bestArea = area; found = h; foundRect = r; }
            return true;
        }, IntPtr.Zero);
        bestHwnd = found;
        bestRect = foundRect;
        return bestHwnd != IntPtr.Zero;
    }

    static string GetClipboardText() {
        for (int attempt = 0; attempt < 5; attempt++) {
            if (OpenClipboard(IntPtr.Zero)) {
                try {
                    IntPtr h = GetClipboardData(CF_UNICODETEXT);
                    if (h == IntPtr.Zero) return "";
                    IntPtr ptr = GlobalLock(h);
                    if (ptr == IntPtr.Zero) return "";
                    try { return Marshal.PtrToStringUni(ptr) ?? ""; }
                    finally { GlobalUnlock(h); }
                } finally { CloseClipboard(); }
            }
            Thread.Sleep(15);
        }
        return "";
    }

    static bool SetClipboardText(string text) {
        string s = text ?? "";
        for (int attempt = 0; attempt < 5; attempt++) {
            if (OpenClipboard(IntPtr.Zero)) {
                try {
                    EmptyClipboard();
                    if (s.Length == 0) return true;
                    byte[] bytes = Encoding.Unicode.GetBytes(s + "\0");
                    IntPtr hMem = GlobalAlloc(GMEM_MOVEABLE, (UIntPtr)bytes.Length);
                    if (hMem == IntPtr.Zero) return false;
                    IntPtr ptr = GlobalLock(hMem);
                    if (ptr == IntPtr.Zero) return false;
                    Marshal.Copy(bytes, 0, ptr, bytes.Length);
                    GlobalUnlock(hMem);
                    return SetClipboardData(CF_UNICODETEXT, hMem) != IntPtr.Zero;
                } finally { CloseClipboard(); }
            }
            Thread.Sleep(15);
        }
        return false;
    }

    static void SendUnicodeText(string text) {
        if (string.IsNullOrEmpty(text)) return;
        int sz = Marshal.SizeOf(typeof(INPUT));
        foreach (char ch in text) {
            var arr = new INPUT[2];
            arr[0].type = 1;
            arr[0].U.ki = new KEYBDINPUT { wVk = 0, wScan = ch, dwFlags = KEYEVENTF_UNICODE };
            arr[1].type = 1;
            arr[1].U.ki = new KEYBDINPUT { wVk = 0, wScan = ch, dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP };
            SendInput(2, arr, sz);
        }
    }

    static void TapKey(byte vk, bool ctrl) {
        if (ctrl) keybd_event(0x11, 0, 0, UIntPtr.Zero);
        keybd_event(vk, 0, 0, UIntPtr.Zero);
        Thread.Sleep(15);
        keybd_event(vk, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
        if (ctrl) keybd_event(0x11, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
    }

    static bool ResolvePoint(string target, double nx, double ny, bool activateWindow, out int px, out int py) {
        px = 0; py = 0;
        nx = Math.Max(0.0, Math.Min(1.0, nx));
        ny = Math.Max(0.0, Math.Min(1.0, ny));
        if (target == "screen") {
            int sw = GetSystemMetrics(0);
            int sh = GetSystemMetrics(1);
            if (sw <= 0 || sh <= 0) return false;
            px = (int)Math.Round(sw * nx);
            py = (int)Math.Round(sh * ny);
            return true;
        }
        double rightRatio = double.Parse(target, CultureInfo.InvariantCulture);
        IntPtr hwnd; RECT r;
        if (!FindAntigravityWindow(out hwnd, out r)) return false;
        if (activateWindow) { SetForegroundWindow(hwnd); Thread.Sleep(20); }
        int fullW = r.Right - r.Left;
        int fullH = r.Bottom - r.Top;
        int srcX = 0, srcW = fullW;
        if (rightRatio > 0.15 && rightRatio < 0.95) {
            srcW = Math.Max(200, (int)Math.Round(fullW * rightRatio));
            srcX = fullW - srcW;
        }
        px = r.Left + srcX + (int)Math.Round(srcW * nx);
        py = r.Top + (int)Math.Round(fullH * ny);
        return true;
    }

    static string CaptureCursorPng(string lastId) {
        var ci = new CURSORINFO { cbSize = Marshal.SizeOf(typeof(CURSORINFO)) };
        IntPtr hCur = (GetCursorInfo(ref ci) && ci.hCursor != IntPtr.Zero)
            ? ci.hCursor
            : LoadCursor(IntPtr.Zero, (IntPtr)32512); // IDC_ARROW：触摸/打字隐藏光标时仍返回电脑当前系统光标主题
        if (hCur == IntPtr.Zero) return "ERR NO_CURSOR";
        string id = hCur.ToInt64().ToString(CultureInfo.InvariantCulture);
        if (!string.IsNullOrEmpty(lastId) && lastId == id) return "SAME " + id;
        IntPtr hCopy = CopyIcon(hCur);
        if (hCopy == IntPtr.Zero) return "ERR COPY_FAIL";
        try {
            ICONINFO ii;
            if (!GetIconInfo(hCopy, out ii)) return "ERR INFO_FAIL";
            int w = 32, h = 32;
            try {
                if (ii.hbmColor != IntPtr.Zero) {
                    using (var cb = Image.FromHbitmap(ii.hbmColor)) { w = cb.Width; h = cb.Height; }
                } else if (ii.hbmMask != IntPtr.Zero) {
                    using (var mb = Image.FromHbitmap(ii.hbmMask)) { w = mb.Width; h = mb.Height / 2; }
                }
            } catch {}
            if (w <= 0 || w > 256) w = 32;
            if (h <= 0 || h > 256) h = 32;
            if (ii.hbmColor != IntPtr.Zero) DeleteObject(ii.hbmColor);
            if (ii.hbmMask != IntPtr.Zero) DeleteObject(ii.hbmMask);

            using (var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb)) {
                using (var g = Graphics.FromImage(bmp)) {
                    g.Clear(Color.Transparent);
                    IntPtr hdc = g.GetHdc();
                    DrawIconEx(hdc, 0, 0, hCopy, w, h, 0, IntPtr.Zero, 0x0003); // DI_NORMAL
                    g.ReleaseHdc(hdc);
                }
                using (var ms = new MemoryStream()) {
                    bmp.Save(ms, ImageFormat.Png);
                    string b64 = Convert.ToBase64String(ms.ToArray());
                    return string.Format(CultureInfo.InvariantCulture, "CURSOR {0} {1} {2} {3} {4} {5}", id, ii.xHotspot, ii.yHotspot, w, h, b64);
                }
            }
        } finally {
            DestroyIcon(hCopy);
        }
    }

    static string ExecuteInput(string action, string target, double nx, double ny, string extra) {
        if (action == "cursor") return CaptureCursorPng(extra);
        int px = 0, py = 0;
        bool needPos = action == "move" || action == "click" || action == "dblclick" || action == "rightclick" ||
                       action == "mousedown" || action == "mouseup" || action == "wheel" ||
                       action == "touchdown" || action == "touchmove" || action == "touchup" || action == "touchcancel";
        bool activate = needPos && action != "move" && !action.StartsWith("touch");
        if (needPos && !ResolvePoint(target, nx, ny, activate, out px, out py)) return "ERR HIDDEN";

        switch (action) {
            case "touchdown":
                if (touchActive) SendTouch(0x00040000 | 0x00000400, px, py); // UP | CANCELED
                touchActive = SendTouch(0x00010000 | 0x00000002 | 0x00000004, px, py); // DOWN | INRANGE | INCONTACT
                if (!touchActive) return "ERR TOUCH_FAIL";
                break;
            case "touchmove":
                if (!touchActive) touchActive = SendTouch(0x00010000 | 0x00000002 | 0x00000004, px, py);
                else SendTouch(0x00020000 | 0x00000002 | 0x00000004, px, py); // UPDATE | INRANGE | INCONTACT
                break;
            case "touchup":
                if (touchActive) { SendTouch(0x00040000, px, py); touchActive = false; } // UP
                break;
            case "touchcancel":
                if (touchActive) { SendTouch(0x00040000 | 0x00000400, px, py); touchActive = false; } // UP | CANCELED
                break;
            case "move":
                SetCursorPos(px, py);
                break;
            case "mousedown":
                SetCursorPos(px, py);
                Thread.Sleep(10);
                mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
                break;
            case "mouseup":
                SetCursorPos(px, py);
                Thread.Sleep(10);
                mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
                break;
            case "click":
                SetCursorPos(px, py);
                Thread.Sleep(10);
                mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
                Thread.Sleep(15);
                mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
                break;
            case "dblclick":
                SetCursorPos(px, py);
                Thread.Sleep(10);
                for (int k = 0; k < 2; k++) {
                    mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
                    Thread.Sleep(15);
                    mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
                    if (k == 0) Thread.Sleep(45);
                }
                break;
            case "rightclick":
                SetCursorPos(px, py);
                Thread.Sleep(10);
                mouse_event(MOUSEEVENTF_RIGHTDOWN, 0, 0, 0, UIntPtr.Zero);
                Thread.Sleep(15);
                mouse_event(MOUSEEVENTF_RIGHTUP, 0, 0, 0, UIntPtr.Zero);
                break;
            case "wheel":
                int delta = 0;
                int.TryParse(extra, NumberStyles.Integer, CultureInfo.InvariantCulture, out delta);
                if (delta == 0) delta = -240;
                SetCursorPos(px, py);
                Thread.Sleep(8);
                mouse_event(MOUSEEVENTF_WHEEL, 0, 0, delta, UIntPtr.Zero);
                break;
            case "text":
                SendUnicodeText(extra);
                break;
            case "readfield": {
                // 清空剪贴板 → Ctrl+A 全选当前输入框 → Ctrl+C 复制 → 返回已有文本
                SetClipboardText("");
                Thread.Sleep(25);
                TapKey(0x41, true); // Ctrl+A
                Thread.Sleep(45);
                TapKey(0x43, true); // Ctrl+C
                Thread.Sleep(70);
                string cur = GetClipboardText();
                // 取消高亮选区，把光标移回末尾
                TapKey(0x27, false); // Right
                string b64 = Convert.ToBase64String(Encoding.UTF8.GetBytes(cur));
                return "OK " + b64;
            }
            case "replacefield": {
                // 全选当前输入框并替换为编辑后的完整文字
                TapKey(0x41, true); // Ctrl+A
                Thread.Sleep(35);
                if (string.IsNullOrEmpty(extra)) {
                    TapKey(0x08, false); // Backspace
                } else {
                    SetClipboardText(extra);
                    Thread.Sleep(25);
                    TapKey(0x56, true); // Ctrl+V
                }
                break;
            }
            case "key":
                switch (extra.ToLowerInvariant()) {
                    case "enter": TapKey(0x0D, false); break;
                    case "backspace": TapKey(0x08, false); break;
                    case "delete": case "del": TapKey(0x2E, false); break;
                    case "escape": case "esc": TapKey(0x1B, false); break;
                    case "tab": TapKey(0x09, false); break;
                    case "home": TapKey(0x24, false); break;
                    case "end": TapKey(0x23, false); break;
                    case "up": TapKey(0x26, false); break;
                    case "down": TapKey(0x28, false); break;
                    case "left": TapKey(0x25, false); break;
                    case "right": TapKey(0x27, false); break;
                    case "f5": TapKey(0x74, false); break;
                    case "ctrl+c": TapKey(0x43, true); break;
                    case "ctrl+v": TapKey(0x56, true); break;
                    case "ctrl+x": TapKey(0x58, true); break;
                    case "ctrl+a": TapKey(0x41, true); break;
                    case "ctrl+z": TapKey(0x5A, true); break;
                    case "ctrl+w": TapKey(0x57, true); break;
                }
                break;
        }
        return "OK";
    }

    static int RunDaemon() {
        // 常驻输入守护进程：逐行读 stdin，格式：action\ttarget\tnx\tny\tbase64Extra
        Console.InputEncoding = Encoding.UTF8;
        Console.OutputEncoding = Encoding.UTF8;
        string line;
        while ((line = Console.ReadLine()) != null) {
            if (line.Length == 0) continue;
            try {
                string[] parts = line.Split('\t');
                if (parts.Length < 4) { Console.WriteLine("ERR BAD_ARGS"); Console.Out.Flush(); continue; }
                string action = parts[0];
                string target = parts[1];
                double nx = double.Parse(parts[2], CultureInfo.InvariantCulture);
                double ny = double.Parse(parts[3], CultureInfo.InvariantCulture);
                string extra = parts.Length > 4 && parts[4].Length > 0
                    ? Encoding.UTF8.GetString(Convert.FromBase64String(parts[4])) : "";
                string resp = ExecuteInput(action, target, nx, ny, extra);
                Console.WriteLine(resp);
                Console.Out.Flush();
            } catch (Exception ex) {
                Console.WriteLine("ERR " + ex.Message.Replace('\n', ' '));
                Console.Out.Flush();
            }
        }
        return 0;
    }

    static void EncodeJpegToStdout(Bitmap srcBmp, int srcX, int srcY, int srcW, int srcH, int maxW, long quality) {
        int dstW = srcW > maxW ? maxW : srcW;
        int dstH = Math.Max(1, (int)Math.Round((double)srcH * dstW / srcW));
        using (var outBmp = new Bitmap(dstW, dstH, PixelFormat.Format24bppRgb)) {
            using (var g2 = Graphics.FromImage(outBmp)) {
                g2.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g2.DrawImage(srcBmp, new Rectangle(0, 0, dstW, dstH), new Rectangle(srcX, srcY, srcW, srcH), GraphicsUnit.Pixel);
            }
            ImageCodecInfo jpeg = null;
            foreach (var c in ImageCodecInfo.GetImageEncoders()) {
                if (c.MimeType == "image/jpeg") { jpeg = c; break; }
            }
            var ep = new EncoderParameters(1);
            ep.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
            using (var ms = new MemoryStream()) {
                if (jpeg != null) outBmp.Save(ms, jpeg, ep);
                else outBmp.Save(ms, ImageFormat.Jpeg);
                var bytes = ms.ToArray();
                using (var stdout = Console.OpenStandardOutput()) {
                    stdout.Write(bytes, 0, bytes.Length);
                }
            }
        }
    }

    // 连续推流（电脑实况）：常驻进程里循环截屏、缩放、压 JPEG，按“4 字节长度(大端) + JPEG”写到 stdout。
    // 画面没变化就不发（每 2 秒发一帧保活）；stdout 被关掉（手机断开）时退出。
    static byte[] hashBuf;
    static int FrameHash(Bitmap b) {
        var rect = new Rectangle(0, 0, b.Width, b.Height);
        var d = b.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
        int len = d.Stride * b.Height;
        if (hashBuf == null || hashBuf.Length != len) hashBuf = new byte[len];
        Marshal.Copy(d.Scan0, hashBuf, 0, len);
        b.UnlockBits(d);
        unchecked {
            int h = 17;
            for (int i = 0; i < len; i += 5) h = h * 31 + hashBuf[i];
            return h;
        }
    }

    static int RunStream(long quality, int maxW, int fps) {
        int sw = GetSystemMetrics(0), sh = GetSystemMetrics(1);
        if (sw <= 0 || sh <= 0) { Console.Error.Write("NO_SCREEN"); return 2; }
        int dstW = sw > maxW ? maxW : sw;
        int dstH = Math.Max(1, (int)Math.Round((double)sh * dstW / sw));
        ImageCodecInfo jpeg = null;
        foreach (var c in ImageCodecInfo.GetImageEncoders()) if (c.MimeType == "image/jpeg") { jpeg = c; break; }
        var ep = new EncoderParameters(1);
        ep.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
        var stdout = Console.OpenStandardOutput();
        int frameMs = 1000 / Math.Max(1, fps);
        var clock = Stopwatch.StartNew();
        long lastSent = -100000;
        int lastHash = 0;
        var head = new byte[4];
        using (var bmp = new Bitmap(sw, sh, PixelFormat.Format24bppRgb))
        using (var g = Graphics.FromImage(bmp))
        using (var outBmp = new Bitmap(dstW, dstH, PixelFormat.Format24bppRgb))
        using (var g2 = Graphics.FromImage(outBmp))
        using (var ms = new MemoryStream()) {
            g2.InterpolationMode = InterpolationMode.Bilinear;
            while (true) {
                long t0 = clock.ElapsedMilliseconds;
                bool captured = true;
                try { g.CopyFromScreen(0, 0, 0, 0, new Size(sw, sh), CopyPixelOperation.SourceCopy); }
                catch { captured = false; } // 锁屏/切换桌面时会失败，稍后再试
                if (captured) {
                    g2.DrawImage(bmp, new Rectangle(0, 0, dstW, dstH), new Rectangle(0, 0, sw, sh), GraphicsUnit.Pixel);
                    int h = FrameHash(outBmp);
                    if (h != lastHash || t0 - lastSent > 2000) {
                        ms.SetLength(0);
                        if (jpeg != null) outBmp.Save(ms, jpeg, ep); else outBmp.Save(ms, ImageFormat.Jpeg);
                        int n = (int)ms.Length;
                        head[0] = (byte)(n >> 24); head[1] = (byte)(n >> 16); head[2] = (byte)(n >> 8); head[3] = (byte)n;
                        try { stdout.Write(head, 0, 4); stdout.Write(ms.GetBuffer(), 0, n); stdout.Flush(); }
                        catch { return 0; } // 对方断开
                        lastHash = h; lastSent = t0;
                    }
                }
                int spent = (int)(clock.ElapsedMilliseconds - t0);
                Thread.Sleep(captured ? Math.Max(1, frameMs - spent) : 500);
            }
        }
    }

    static int Main(string[] args) {
        try { SetProcessDPIAware(); } catch {}
        if (args.Length > 0 && args[0] == "daemon") return RunDaemon();
        if (args.Length > 0 && args[0] == "stream") {
            long q = args.Length > 1 ? long.Parse(args[1], CultureInfo.InvariantCulture) : 65;
            int w = args.Length > 2 ? int.Parse(args[2], CultureInfo.InvariantCulture) : 1280;
            int f = args.Length > 3 ? int.Parse(args[3], CultureInfo.InvariantCulture) : 15;
            return RunStream(q, w, f);
        }
        if (args.Length > 0 && args[0] == "input") {
            if (args.Length < 5) return 1;
            double nx = double.Parse(args[3], CultureInfo.InvariantCulture);
            double ny = double.Parse(args[4], CultureInfo.InvariantCulture);
            string extra = args.Length > 5 ? args[5] : "";
            string resp = ExecuteInput(args[1], args[2], nx, ny, extra);
            if (resp.StartsWith("ERR ")) { Console.Error.Write(resp.Substring(4)); return 2; }
            Console.WriteLine(resp);
            return 0;
        }

        string target = args.Length > 0 ? args[0] : "screen";
        long quality = args.Length > 1 ? long.Parse(args[1], CultureInfo.InvariantCulture) : 80;
        int maxW = args.Length > 2 ? int.Parse(args[2], CultureInfo.InvariantCulture) : 1600;

        if (target == "screen") {
            int sw = GetSystemMetrics(0);
            int sh = GetSystemMetrics(1);
            if (sw <= 0 || sh <= 0) { Console.Error.Write("NO_SCREEN"); return 2; }
            using (var bmp = new Bitmap(sw, sh, PixelFormat.Format24bppRgb)) {
                using (var g = Graphics.FromImage(bmp)) {
                    g.CopyFromScreen(0, 0, 0, 0, new Size(sw, sh), CopyPixelOperation.SourceCopy);
                }
                EncodeJpegToStdout(bmp, 0, 0, sw, sh, maxW, quality);
            }
            return 0;
        }

        double rightRatio = double.Parse(target, CultureInfo.InvariantCulture);
        IntPtr bestHwnd; RECT bestRect;
        if (!FindAntigravityWindow(out bestHwnd, out bestRect)) { Console.Error.Write("HIDDEN"); return 2; }

        int fullW = bestRect.Right - bestRect.Left;
        int fullH = bestRect.Bottom - bestRect.Top;

        using (var fullBmp = new Bitmap(fullW, fullH, PixelFormat.Format24bppRgb)) {
            using (var g = Graphics.FromImage(fullBmp)) {
                IntPtr hdc = g.GetHdc();
                bool ok = PrintWindow(bestHwnd, hdc, 2);
                g.ReleaseHdc(hdc);
                if (!ok) { Console.Error.Write("CAPTURE_FAILED"); return 3; }
            }
            int srcX = 0, srcW = fullW;
            if (rightRatio > 0.15 && rightRatio < 0.95) {
                srcW = Math.Max(200, (int)Math.Round(fullW * rightRatio));
                srcX = fullW - srcW;
            }
            EncodeJpegToStdout(fullBmp, srcX, 0, srcW, fullH, maxW, quality);
        }
        return 0;
    }
}
