// AG Remote 电脑端转发服务
// 把 Antigravity 本地 language_server 的对话接口，整理成手机好用的简单 JSON 接口。
// 只依赖 Node 自带模块。默认端口 5100；只接受本机和 Tailscale（100.64.0.0/10）来的连接。
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import tls from 'node:tls';
import { execFile, spawn } from 'node:child_process';
import { randomBytes, randomInt, timingSafeEqual, createHash } from 'node:crypto';
import { networkInterfaces, hostname, release } from 'node:os';
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, statSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
/** 给手机下载的安装包：环境变量 AGREMOTE_APK → 和本文件放在一起的（安装包里带的）→ 仓库上两级的 AGRemote.apk（build.sh 复制过去的）→ 刚编出来的 release 包。 */
function apkPath() {
  const cands = [process.env.AGREMOTE_APK, join(ROOT, 'AGRemote.apk'), join(ROOT, '..', '..', '..', 'AGRemote.apk'),
    join(ROOT, '..', 'android', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk')];
  return cands.find(p => p && existsSync(p)) || cands[1];
}
const DATA = join(ROOT, 'data');
const CONFIG_FILE = join(DATA, 'config.json');
const DRAFTS_FILE = join(DATA, 'drafts.json');
const PORT = Number(process.env.AGR_PORT || 5100);
const APP_VERSION = '1.1.0';
let lastAdminDiscover = 0;
const SVC = '/exa.language_server_pb.LanguageServerService/';

mkdirSync(DATA, { recursive: true });

// ---------------- 配置 / 配对 / 草稿 ----------------
let config = { pairCode: null, devices: [] };
try { config = { ...config, ...JSON.parse(readFileSync(CONFIG_FILE, 'utf8')) }; } catch {}
if (!config.pairCode) config.pairCode = String(randomInt(100000, 1000000));
const saveConfig = () => writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2));
saveConfig();
writeFileSync(join(DATA, 'pairing-code.txt'), config.pairCode + '\n');

let drafts = {};
try { drafts = JSON.parse(readFileSync(DRAFTS_FILE, 'utf8')) || {}; } catch {}
const saveDrafts = () => { try { writeFileSync(DRAFTS_FILE, JSON.stringify(drafts, null, 2)); } catch {} };

// 日志用本机时间（toISOString 是 UTC，会和电脑时钟差几个小时）
const log = (...a) => console.log(new Date().toLocaleTimeString('en-GB', { hour12: false }), ...a);

/** 带令牌的设备；找不到返回 undefined。 */
function deviceOf(tok) {
  if (!tok) return undefined;
  const b = Buffer.from(tok);
  return config.devices.find(d => {
    const x = Buffer.from(d.token);
    return x.length === b.length && timingSafeEqual(x, b);
  });
}
const tokenOk = tok => !!deviceOf(tok);
/** 给配对页用的设备编号（令牌的摘要，不暴露令牌本身）。 */
const deviceId = d => createHash('sha256').update(d.token).digest('hex').slice(0, 12);
/** 每台设备最近一次访问的时间和 IP（只在内存里，配对页显示“在线/最近在线”用）。 */
const deviceSeen = new Map();
/** 每台设备当前开着的推送连接数（>0 就是在线）。 */
const deviceLive = new Map();
function newPairCode() {
  config.pairCode = String(randomInt(100000, 1000000));
  saveConfig();
  writeFileSync(join(DATA, 'pairing-code.txt'), config.pairCode + '\n');
}

// ---------------- 电脑端配对页（只给这台电脑自己用） ----------------
const LOOPBACK_HOST = /^(127\.0\.0\.1|localhost|\[::1\])(:\d+)?$/i;
const LOOPBACK_ORIGIN = /^http:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?$/i;
/** 只放行本机直接打开的请求：来源是回环地址、Host 是本机（防 DNS 重绑定）；
 *  改东西的请求还要带自定义头且 Origin 是本机（别的网页跨站发不出来）。 */
function adminOk(req, ip) {
  const a = normIp(ip);
  if (a !== '127.0.0.1' && a !== '::1') return false;
  if (!LOOPBACK_HOST.test(req.headers.host || '')) return false;
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    if (req.headers['x-agr-admin'] !== '1') return false;
    const o = req.headers.origin;
    if (o && !LOOPBACK_ORIGIN.test(o)) return false;
  }
  return true;
}
function tailscaleAddrs() {
  const out = [];
  for (const list of Object.values(networkInterfaces())) {
    for (const n of list || []) {
      if (n.family === 'IPv4' && !n.internal && allowedRemote(n.address)) out.push(n.address);
    }
  }
  return out;
}
function adminState() {
  const now = Date.now();
  return {
    code: config.pairCode,
    port: PORT,
    host: hostname(),
    addrs: tailscaleAddrs(),
    antigravity: !!ls,
    agState: ls ? 'ok' : agDiag.state,
    devices: config.devices.map(d => {
      const id = deviceId(d);
      const seen = deviceSeen.get(id);
      return {
        id, name: d.name || '', pairedAt: d.pairedAt || '',
        lastSeen: seen ? new Date(seen.at).toISOString() : '', ip: seen?.ip || '',
        online: (deviceLive.get(id) || 0) > 0 || (!!seen && now - seen.at < 15000),
      };
    }),
  };
}
/** 诊断信息会被贴到公开的 GitHub issue：把路径里的用户目录换成 %USERPROFILE%，不暴露 Windows 用户名。 */
function redactUser(obj) {
  const home = process.env.USERPROFILE || '';
  let s = JSON.stringify(obj);
  if (home) s = s.split(JSON.stringify(home).slice(1, -1)).join('%USERPROFILE%');
  return JSON.parse(s);
}
/** 配对页和它的接口；不是这些路径返回 false。 */
async function handleAdmin(req, res, path) {
  if (path === '/pair' || path === '/pair/') {
    const html = readFileSync(join(ROOT, 'pair-ui.html'));
    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'",
    });
    return res.end(html);
  }
  if (path === '/admin/icon.svg') {
    res.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'max-age=86400' });
    return res.end(readFileSync(join(ROOT, 'app-icon.svg')));
  }
  if (path === '/admin/api/state' && req.method === 'GET') {
    // 手机还没连上时没人去找 Antigravity：配对页打开着就每 5 秒找一次，免得一直显示“没开”
    if (!ls && !discovering && Date.now() - lastAdminDiscover > 5000) {
      lastAdminDiscover = Date.now();
      discovering = discover().then(x => { ls = x; return x; }).finally(() => { discovering = null; });
    }
    return send(res, 200, adminState());
  }
  if (path === '/admin/api/diagnostics' && req.method === 'GET') {
    return send(res, 200, { version: APP_VERSION, node: process.versions.node, os: `${process.platform} ${release()}`,
      connected: !!ls, port: PORT, tailscaleAddrs: tailscaleAddrs().length, discovery: redactUser(agDiag) });
  }
  if (path === '/admin/api/new-code' && req.method === 'POST') {
    newPairCode();
    log(`配对码已更换：${config.pairCode}`);
    return send(res, 200, adminState());
  }
  if ((path === '/admin/api/remove' || path === '/admin/api/rename') && req.method === 'POST') {
    const b = await readBody(req);
    const d = config.devices.find(x => deviceId(x) === String(b.id || ''));
    if (!d) return send(res, 404, { error: 'not found' });
    if (path.endsWith('/rename')) {
      d.name = String(b.name || '').trim().slice(0, 60) || d.name;
    } else {
      config.devices = config.devices.filter(x => x !== d);
      const seen = deviceSeen.get(deviceId(d));
      if (seen?.ip) trustedIps.delete(seen.ip); // 代理权限也一起收回
      deviceSeen.delete(deviceId(d));
      log(`已移除设备：${d.name || ''}`);
    }
    saveConfig();
    return send(res, 200, adminState());
  }
  return false;
}

// 代理（手机浏览器走电脑网络）只给已配对的手机用：最近 24 小时内带有效令牌访问过的 IP 才放行
const trustedIps = new Map();
const PROXY_TRUST_MS = 24 * 3600 * 1000;
function normIp(addr = '') { return addr.replace(/^::ffff:/, ''); }
function proxyAllowed(addr = '') {
  const a = normIp(addr);
  if (a === '127.0.0.1' || a === '::1') return true;
  const t = trustedIps.get(a);
  return !!t && Date.now() - t < PROXY_TRUST_MS;
}

function allowedRemote(addr = '') {
  const a = addr.replace(/^::ffff:/, '');
  if (a === '127.0.0.1' || a === '::1') return true;
  const m = a.match(/^100\.(\d+)\./);
  return !!m && +m[1] >= 64 && +m[1] <= 127; // 100.64.0.0/10 = Tailscale
}

// ---------------- 找到 language_server ----------------
let ls = null; // { base, token, pid }

function ps(cmd, timeoutMs = 20000) {
  // 输出强制 UTF-8：用户名/安装路径里有中文等字符时，默认的控制台代码页会把它们弄成乱码
  const full = '[Console]::OutputEncoding = [Text.Encoding]::UTF8; ' + cmd;
  return new Promise(res => execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', full],
    { windowsHide: true, maxBuffer: 4 << 20, encoding: 'buffer', timeout: timeoutMs },
    (e, out, err) => res({ out: out ? out.toString('utf8') : '', err: e ? (String(err || '') || e.message).trim().slice(0, 300) : '' })));
}

let lastGood = null; // 上次连上的 language_server（同进程、同端口、同令牌时不用再试探）
/** 最近一次查找 Antigravity 的经过，给配对页显示原因、给用户复制去报问题（不含令牌）。 */
let agDiag = { at: '', state: 'pending', detail: '', processes: [] };

/** 从启动参数里取 CSRF 令牌：兼容 `--csrf_token xxx`、`--csrf_token=xxx` 和带引号的写法。 */
function csrfFrom(cl) {
  return (String(cl || '').match(/--csrf_token(?:=|\s+)"?([^\s"]+)/) || [])[1] || '';
}

/** Antigravity 的数据目录（~/.gemini/antigravity）。 */
const AG_HOME = join(process.env.USERPROFILE || process.env.HOME || '', '.gemini', 'antigravity');

/** 进程还在不在（Windows 上 signal 0 也能用）。 */
function pidAlive(pid) {
  try { process.kill(Number(pid), 0); return true; } catch (e) { return e.code === 'EPERM'; }
}

/**
 * 首选方式：Antigravity 的后台服务每次启动都会写 ~/.gemini/antigravity/daemon/hub_<端口>.json
 * （pid、httpPort、httpsPort、lsVersion、csrfToken）。2.35 起令牌不再出现在启动参数里，只能从这里拿；
 * 这样也不依赖 PowerShell，Antigravity 以管理员身份运行时同样能读到。目录里会残留旧进程的文件，所以要确认进程还在。
 */
async function discoverFromDaemonFiles(diag) {
  const dir = join(AG_HOME, 'daemon');
  let files = [];
  try {
    files = readdirSync(dir).filter(f => /\.json$/i.test(f))
      .map(f => ({ f, t: statSync(join(dir, f)).mtimeMs })).sort((a, b) => b.t - a.t);
  } catch { /* 没有这个目录：老版本或没装 */ }
  diag.daemonFiles = files.length;
  for (const { f } of files) {
    let j;
    try { j = JSON.parse(readFileSync(join(dir, f), 'utf8')); } catch { continue; }
    const port = Number(j.httpPort), token = String(j.csrfToken || '');
    const d = { file: f, pid: j.pid, version: j.lsVersion || '', alive: pidAlive(j.pid), hasToken: !!token, port, probe: '' };
    diag.daemon.push(d);
    if (!d.alive || !token || !port) continue;
    const cand = { base: `http://127.0.0.1:${port}`, token, pid: j.pid, version: d.version, exe: '' };
    if (lastGood && lastGood.base === cand.base && lastGood.token === token && lastGood.pid === cand.pid) { d.probe = 'ok'; return lastGood; }
    try {
      await probe(cand);
      d.probe = 'ok';
      return cand;
    } catch (e) {
      d.probe = String(e.status ? `HTTP ${e.status}` : (e.cause?.code || e.name || e.message)).slice(0, 120);
    }
  }
  return null;
}

async function discover() {
  const diag = { at: new Date().toISOString(), state: '', detail: '', method: '', daemonFiles: 0, daemon: [], antigravityProcesses: 0, processes: [] };
  const viaFile = await discoverFromDaemonFiles(diag);
  if (viaFile) {
    if (lastGood !== viaFile) log(`language_server 已连接：pid ${viaFile.pid} 端口 ${viaFile.base.split(':').pop()}（${viaFile.version || '?'}）`);
    lastGood = viaFile;
    agDiag = { ...diag, state: 'ok', method: 'daemon', detail: `pid ${viaFile.pid} ${viaFile.base} v${viaFile.version}` };
    return viaFile;
  }
  // 退路：老版本没有 daemon 文件时，从进程启动参数里找令牌
  const r = await ps(
    // 不同版本进程名可能带后缀（如 language_server_windows_x64.exe），所以按前缀找；顺便看主程序在不在，用来区分“没开”和“开了但找不到后台”
    "$ag = @(Get-CimInstance Win32_Process -Filter \"Name='Antigravity.exe'\" -ErrorAction SilentlyContinue).Count; " +
    "$ls = @(Get-CimInstance Win32_Process -Filter \"Name LIKE 'language_server%'\" -ErrorAction SilentlyContinue | ForEach-Object { " +
    "$p=$_.ProcessId; $ports=(Get-NetTCPConnection -State Listen -OwningProcess $p -ErrorAction SilentlyContinue).LocalPort -join ','; " +
    "[pscustomobject]@{pid=$p; name=$_.Name; ports=$ports; cl=$_.CommandLine; exe=$_.ExecutablePath} }); " +
    "[pscustomobject]@{ag=$ag; ls=$ls} | ConvertTo-Json -Compress -Depth 4");
  diag.method = 'cmdline';
  let data = null;
  try { data = JSON.parse(r.out || 'null'); } catch {}
  if (!data) {
    agDiag = { ...diag, state: 'psFailed', detail: r.err || 'PowerShell returned nothing' };
    return null;
  }
  diag.antigravityProcesses = Number(data.ag) || 0;
  let list = data.ls || [];
  if (!Array.isArray(list)) list = [list];
  for (const p of list) {
    const token = csrfFrom(p.cl);
    const d = { pid: p.pid, name: p.name || '', exe: p.exe || '', canReadArgs: !!p.cl, hasToken: !!token,
      ports: String(p.ports || '').split(',').filter(Boolean).map(Number), probes: [] };
    diag.processes.push(d);
    if (!token) continue;
    for (const port of d.ports) {
      const cand = { base: `http://127.0.0.1:${port}`, token, pid: p.pid, exe: p.exe || '' };
      if (lastGood && lastGood.base === cand.base && lastGood.token === token && lastGood.pid === p.pid) {
        agDiag = { ...diag, state: 'ok', detail: `pid ${p.pid} port ${port}` };
        return lastGood;
      }
      try {
        await probe(cand);
        lastGood = cand;
        log(`language_server 已连接：pid ${p.pid} 端口 ${port}`);
        d.probes.push({ port, ok: true });
        agDiag = { ...diag, state: 'ok', detail: `pid ${p.pid} port ${port}` };
        return cand;
      } catch (e) {
        d.probes.push({ port, ok: false, error: String(e.status ? `HTTP ${e.status}` : (e.cause?.code || e.name || e.message)).slice(0, 120) });
      }
    }
  }
  // 没连上：给出最可能的原因
  const procs = diag.processes;
  let state;
  if (diag.daemon.some(d => d.alive && d.hasToken && d.probe && d.probe !== 'ok')) state = 'probeFailed';
  else if (!procs.length) state = diag.antigravityProcesses ? 'noLanguageServer' : 'notRunning';
  else if (procs.every(p => !p.canReadArgs)) state = 'noAccess';     // 多半是 Antigravity 以管理员身份运行
  else if (procs.every(p => !p.hasToken)) state = 'noToken';         // 启动参数里没有令牌：可能是不兼容的新版本
  else if (procs.every(p => !p.ports.length)) state = 'noPort';
  else state = 'probeFailed';
  agDiag = { ...diag, state };
  if (state !== 'notRunning') log('没连上 Antigravity：', state, JSON.stringify(procs.map(p => ({ pid: p.pid, args: p.canReadArgs, token: p.hasToken, ports: p.ports, probes: p.probes }))));
  return null;
}

/** 试探一个端口是不是 Antigravity 的接口：先用很轻的模型配置接口；老版本没有它时再用对话列表接口。 */
async function probe(cand) {
  try {
    await rawRpc(cand, 'GetCascadeModelConfigData', {}, 12000);
  } catch (e) {
    if (e.status === 404) await rawRpc(cand, 'GetAllCascadeTrajectories', {}, 12000);
    else throw e;
  }
}

async function rawRpc(target, method, body, timeoutMs = 15000) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetch(target.base + SVC + method, {
      method: 'POST', signal: ac.signal,
      headers: { 'Content-Type': 'application/json', 'Connect-Protocol-Version': '1', 'x-codeium-csrf-token': target.token },
      body: JSON.stringify(body ?? {}),
    });
    const text = await r.text();
    if (!r.ok) {
      const err = new Error(`${method} HTTP ${r.status}: ${text.slice(0, 300)}`);
      err.status = r.status;
      throw err;
    }
    return text ? JSON.parse(text) : {};
  } finally { clearTimeout(t); }
}

let discovering = null;
async function rpc(method, body, timeoutMs) {
  for (let attempt = 0; attempt < 2; attempt++) {
    if (!ls) {
      discovering ??= discover().finally(() => { discovering = null; });
      ls = await discovering;
      if (!ls) throw Object.assign(new Error('找不到 Antigravity，请确认它正在运行'), { status: 503 });
    }
    try {
      return await rawRpc(ls, method, body, timeoutMs);
    } catch (e) {
      // 超时只说明 Antigravity 正忙，不是没开：直接报错，别丢掉连接重新查找
      if (e.name === 'AbortError' || e.name === 'TimeoutError') {
        throw Object.assign(new Error(`${method} 超时（Antigravity 可能正忙）`), { status: 504 });
      }
      // 连接被拒或令牌失效 → 重新发现一次（Antigravity 可能重启过）
      if (e.status && e.status !== 401 && e.status !== 403) throw e;
      ls = null;
      if (attempt) throw e;
    }
  }
}

// ---------------- 数据整理 ----------------
const short = (s, p) => String(s || '').replace(p, '').toLowerCase();
const STATUS_MAP = { generating: 'running', queued: 'queued', pending: 'running', running: 'running', waiting: 'waiting',
  done: 'done', invalid: 'error', cleared: 'hidden', canceled: 'canceled', error: 'error', interrupted: 'canceled' };

const HIDDEN = new Set(['checkpoint', 'ephemeral_message', 'conversation_history', 'system_message', 'ki_insertion',
  'knowledge_artifacts', 'memory', 'retrieve_memory', 'directory_rules', 'brain_update', 'dummy', 'wait',
  'trajectory_choice', 'tool_call_choice', 'suggested_responses', 'code_acknowledgement', 'lint_diff', 'task_boundary']);

const TITLES = {
  run_command: '运行命令', command_status: '命令状态', send_command_input: '向命令输入', read_terminal: '读取终端',
  view_file: '查看文件', view_file_outline: '查看文件大纲', view_code_item: '查看代码', view_content_chunk: '查看内容',
  list_directory: '列出目录', grep_search: '搜索内容', find: '查找文件', code_search: '搜索代码', internal_search: '内部搜索',
  search_web: '网页搜索', read_url_content: '读取网页', file_change: '修改文件', code_action: '编辑文件',
  write_to_file: '写入文件', propose_code: '提议代码', move: '移动文件', delete_directory: '删除目录',
  mcp_tool: 'MCP 工具', invoke_subagent: '子 agent', browser_subagent: '浏览器子 agent', open_browser_url: '打开网页',
  generate_image: '生成图片', ask_question: '提问', notify_user: '通知', error_message: '错误', finish: '完成',
  tool_search: '查找工具', git_commit: 'Git 提交', clipboard: '剪贴板',
};

const uriPath = u => { try { return decodeURIComponent(String(u).replace(/^file:\/\/\/?/, '')); } catch { return String(u); } };
const cut = (s, n = 6000) => { s = String(s ?? ''); return s.length > n ? s.slice(0, n) + `\n…（已截断，共 ${s.length} 字）` : s; };

function firstString(o, keys) {
  for (const k of keys) {
    const v = o?.[k];
    if (typeof v === 'string' && v.trim()) return v;
  }
  return '';
}

function payloadOf(step) {
  for (const [k, v] of Object.entries(step)) {
    if (['type', 'status', 'metadata', 'error', 'permissions', 'taskDetails', 'customExtensions', 'requestedInteraction',
      'completedInteractions', 'attachments', 'userAnnotations', 'subtrajectory', 'genericStep'].includes(k)) continue;
    if (v && typeof v === 'object') return v;
  }
  return {};
}

function normalizeStep(step, index) {
  const type = short(step.type, 'CORTEX_STEP_TYPE_');
  let status = STATUS_MAP[short(step.status, 'CORTEX_STEP_STATUS_')] || 'done';
  const p = payloadOf(step);
  const out = { i: index, type, status, kind: 'tool', title: TITLES[type] || type.replace(/_/g, ' '), text: '', detail: '',
    time: step.metadata?.completedAt || step.metadata?.finishedGeneratingAt || step.metadata?.createdAt || '',
    trajectoryId: step.metadata?.sourceTrajectoryStepInfo?.trajectoryId || '' };

  if (type === 'user_input') {
    out.kind = 'user';
    out.text = ((p.items || []).map(x => x.text || '').join('') || p.userResponse || '').trim();
    out.mentions = (p.items || []).map(x => x.item?.plugin).filter(Boolean)
      .map(pl => ({ name: pl.displayName || pl.name || '', logo: pl.logo || '' }));
    // 这条消息带的附件（Antigravity 存在 brain/<对话>/.user_uploaded 里），只传路径，图片由手机再单独取
    out.attachments = (p.media || []).filter(m => m.uri).map(m => ({
      name: m.displayName || String(m.uri).split(/[\\/]/).pop(),
      mime: m.mimeType || '',
      path: String(m.uri),
    }));
  } else if (type === 'planner_response') {
    out.kind = 'assistant';
    out.text = p.modifiedResponse || p.response || '';
    out.thinking = cut(p.thinking || '', 4000);
    out.model = step.metadata?.generatorModel || '';
    out.attachments = mediaAttachments(p.media, index);
    // 回复里用 ![..](file:///C:/..) 引用电脑上的图片：当附件缩略图显示，图片语法从正文去掉
    const refs = fileRefs(out.text, true);
    for (const r of refs) if (!out.attachments.some(a => a.path.toLowerCase() === r.path.toLowerCase())) out.attachments.push(r);
    out.text = out.text.replace(FILE_IMG_RE, '').replace(/\n{3,}/g, '\n\n').trim();
    if (!out.text && !out.thinking && !out.attachments.length) out.kind = 'hidden';
  } else if (type === 'run_command') {
    out.text = p.commandLine || p.proposedCommandLine || [p.command, ...(p.args || [])].filter(Boolean).join(' ');
    out.sub = p.cwd ? uriPath(p.cwd) : '';
    out.detail = cut(p.combinedOutput?.full || p.combinedOutput?.truncated || p.combinedOutputSnapshot?.full || '');
    if (p.exitCode !== undefined) out.sub = `${out.sub ? out.sub + ' · ' : ''}退出码 ${p.exitCode}`;
  } else if (['file_change', 'write_to_file', 'propose_code'].includes(type)) {
    out.kind = 'file';
    out.text = uriPath(p.absolutePathUri || p.targetFileUri || '');
    out.sub = short(p.fileChangeType, 'FILE_CHANGE_TYPE_');
    out.detail = cut(diffText(p.diff) || (p.replacementChunks || []).map(c => `- ${c.targetContent ?? ''}\n+ ${c.replacementContent ?? ''}`).join('\n'));
  } else if (type === 'code_action') {
    out.kind = 'file';
    const spec = p.actionSpec || {};
    out.text = uriPath(firstString(spec.createFile || spec.command || spec, ['absoluteUri', 'absolutePathUri', 'path', 'uri'])
      || findDeep(spec, /uri$|path$/i) || '');
    out.sub = p.description || '';
    out.detail = cut(diffText(p.actionResult?.edit?.diff || findDeepObj(p.actionResult, 'diff')));
    if (p.diffStats) out.sub = `${out.sub ? out.sub + ' · ' : ''}+${p.diffStats.linesAdded || 0} −${p.diffStats.linesRemoved || 0}`;
  } else if (type === 'error_message') {
    out.kind = 'error';
    out.text = p.error?.userErrorMessage || p.error?.shortError || p.error?.message || findDeep(p.error, /message|error/i) || '出错了';
    if (p.shouldShowUser === false) out.kind = 'hidden';
  } else if (type === 'notify_user') {
    out.kind = 'assistant';
    out.text = p.notificationContent || '';
    out.attachments = (p.reviewAbsoluteUris || []).map(u => {
      const path = uriPath(u).replace(/\\/g, '/');
      return { name: path.split('/').pop(), mime: mimeOf(path), path };
    });
  } else if (type === 'ask_question') {
    out.kind = 'ask';
  } else if (HIDDEN.has(type)) {
    out.kind = 'hidden';
  } else {
    out.text = uriPath(firstString(p, ['commandLine', 'absolutePathUri', 'directoryPathUri', 'uri', 'url', 'query',
      'searchPattern', 'pattern', 'filePath', 'path', 'toolName', 'name', 'prompt', 'message'])
      || findDeep(p, /uri$|path$|query$|url$/i) || '');
  }

  if (step.error && out.kind !== 'error' && status === 'error') {
    out.sub = `${out.sub ? out.sub + ' · ' : ''}${step.error.userErrorMessage || step.error.shortError || '出错'}`;
  }

  // 待你确认的操作
  const ri = step.requestedInteraction;
  if (ri && status === 'waiting') {
    const [k, spec] = Object.entries(ri)[0] || [];
    if (k) {
      out.waiting = { kind: k, spec: spec || {} };
      if (k === 'askQuestion') out.waiting.questions = (spec?.questions || []).map(q => ({
        question: q.question || '', multi: !!q.isMultiSelect,
        options: (q.options || []).map(o => ({ id: o.id || o.optionId || '', label: o.label || o.text || o.title || o.id || '' })),
      }));
      if (k === 'permission') {
        const action = spec?.resource?.action || '';
        const target = spec?.resource?.target || '';
        const pattern = spec?.suggestedPersistPattern || target;
        out.waiting.action = action;
        out.waiting.target = target;
        out.waiting.label = spec?.actionDescription || '';
        out.waiting.reason = spec?.reason || '';
        out.waiting.grant = action && pattern ? `${action}(${pattern})` : '';
        out.waiting.persistBlocked = spec?.persistSuggestionType === 'PERSIST_SUGGESTION_TYPE_BLOCKED';
        out.waiting.scopes = Array.isArray(spec?.approvalScopeFilter) ? spec.approvalScopeFilter : [];
      }
      if (k === 'filePermission') {
        out.waiting.label = uriPath(spec?.absolutePathUri || '');
        out.waiting.target = out.waiting.label;
        out.waiting.reason = spec?.blockReason === 'BLOCK_REASON_OUTSIDE_WORKSPACE' ? '目标位于当前工作区之外'
          : spec?.blockReason === 'BLOCK_REASON_GITIGNORED' ? '目标在 .gitignore 忽略列表中' : '';
      }
    }
  }
  if (status === 'hidden') out.kind = 'hidden';
  return out;
}

const MIME = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif', bmp: 'image/bmp', svg: 'image/svg+xml',
  pdf: 'application/pdf', md: 'text/markdown', markdown: 'text/markdown', txt: 'text/plain', log: 'text/plain',
  json: 'application/json', csv: 'text/csv', html: 'text/html', htm: 'text/html', xml: 'text/xml', yaml: 'text/plain', yml: 'text/plain',
  js: 'text/plain', mjs: 'text/plain', ts: 'text/plain', py: 'text/plain', kt: 'text/plain', java: 'text/plain', c: 'text/plain',
  cpp: 'text/plain', h: 'text/plain', cs: 'text/plain', go: 'text/plain', rs: 'text/plain', sh: 'text/plain', ps1: 'text/plain',
  bat: 'text/plain', cmd: 'text/plain', css: 'text/plain', ini: 'text/plain', toml: 'text/plain', gradle: 'text/plain', kts: 'text/plain',
  zip: 'application/zip', apk: 'application/vnd.android.package-archive',
  mp4: 'video/mp4', mov: 'video/quicktime', webm: 'video/webm', mkv: 'video/x-matroska', m4v: 'video/mp4', '3gp': 'video/3gpp',
  mp3: 'audio/mpeg', wav: 'audio/wav',
};
// ![alt](file:///C:/a/b.png) 或 ![alt](C:/a/b.png)
const FILE_IMG_RE = /!\[[^\]]*\]\((?:file:\/\/\/?)?([A-Za-z]:[\\/][^)\s]+)\)/g;
// [文字](file:///C:/a/b.md) 或裸的 file:///C:/... 链接
const FILE_LINK_RE = /\]\((?:file:\/\/\/?)([A-Za-z]:[\\/][^)\s]+)\)|(?:^|\s)file:\/\/\/([A-Za-z]:\/[^\s)]+)/g;

function fileRefs(text, imagesOnly = false) {
  const out = [];
  const add = raw => {
    let path = raw;
    try { path = decodeURIComponent(raw); } catch {}
    path = path.replace(/#.*$/, '').replace(/\\/g, '/');
    if (path && !out.some(x => x.path.toLowerCase() === path.toLowerCase())) out.push({ name: path.split('/').pop(), mime: mimeOf(path), path, ref: '' });
  };
  for (const m of String(text || '').matchAll(FILE_IMG_RE)) add(m[1]);
  if (!imagesOnly) for (const m of String(text || '').matchAll(FILE_LINK_RE)) add(m[1] || m[2]);
  return out;
}

function mimeOf(path) { return MIME[String(path).split('.').pop().toLowerCase()] || 'application/octet-stream'; }

/** Media 列表 → 手机用的附件；有文件路径的给路径，只有内嵌数据的给 “步骤号:序号” 让手机再来取。 */
function mediaAttachments(media, index) {
  return (media || []).map((m, k) => {
    const path = m.uri ? uriPath(m.uri).replace(/\\/g, '/') : '';
    return {
      name: m.displayName || (path ? path.split('/').pop() : `附件${k + 1}`),
      mime: m.mimeType || (path ? mimeOf(path) : ''),
      path,
      ref: path ? '' : `${index}:${k}`,
    };
  });
}

function diffText(diff) {
  if (!diff || typeof diff !== 'object') return '';
  const lines = diff.lines || diff.unifiedDiff?.lines || findDeepObj(diff, 'lines');
  if (Array.isArray(lines)) {
    return lines.map(l => {
      const t = short(l.type, 'UNIFIED_DIFF_LINE_TYPE_');
      const sign = t.includes('insert') || t.includes('add') ? '+' : t.includes('delet') || t.includes('remov') ? '-' : ' ';
      return sign + (l.text ?? l.line ?? '');
    }).join('\n');
  }
  return typeof diff.unifiedDiff === 'string' ? diff.unifiedDiff : '';
}
function findDeep(o, re, depth = 0) {
  if (!o || typeof o !== 'object' || depth > 4) return '';
  for (const [k, v] of Object.entries(o)) {
    if (typeof v === 'string' && re.test(k) && v) return v;
  }
  for (const v of Object.values(o)) {
    const r = findDeep(v, re, depth + 1);
    if (r) return r;
  }
  return '';
}
function findDeepObj(o, key, depth = 0) {
  if (!o || typeof o !== 'object' || depth > 5) return null;
  if (o[key] && typeof o[key] === 'object') return o[key];
  for (const v of Object.values(o)) {
    const r = findDeepObj(v, key, depth + 1);
    if (r) return r;
  }
  return null;
}

function runStatus(s) { return short(s, 'CASCADE_RUN_STATUS_') || 'idle'; }

const AG_ROOT = join(process.env.USERPROFILE || '', '.gemini', 'antigravity');

/** 解析 protobuf 文本格式里的转义字符串（含 \346\211\213 这种八进制 UTF-8）。 */
function decodePbTextString(raw) {
  const bytes = [];
  for (let i = 0; i < raw.length; i++) {
    if (raw[i] === '\\' && i + 1 < raw.length) {
      const oct = raw.slice(i + 1, i + 4);
      if (/^[0-7]{3}$/.test(oct)) { bytes.push(parseInt(oct, 8)); i += 3; continue; }
      const nxt = raw[++i];
      const map = { n: 10, r: 13, t: 9, '\\': 92, '"': 34, "'": 39 };
      bytes.push(map[nxt] ?? nxt.charCodeAt(0));
    } else {
      for (const b of Buffer.from(raw[i], 'utf8')) bytes.push(b);
    }
  }
  return Buffer.from(bytes).toString('utf8');
}

/** 读取未加载进内存的历史对话标注（annotations/<id>.pbtxt）。 */
function readDiskAnnotation(id) {
  const f = join(AG_ROOT, 'annotations', `${id}.pbtxt`);
  if (!existsSync(f)) return {};
  try {
    const s = readFileSync(f, 'utf8');
    const titleM = /\btitle:"((?:[^"\\]|\\.)*)"/.exec(s);
    const viewM = /\blast_user_view_time:\{[^}]*seconds:(\d+)/.exec(s);
    const presetM = /\bpermission_preset:"([^"]*)"/.exec(s);
    return {
      title: titleM ? decodePbTextString(titleM[1]) : '',
      pinned: /\bpinned:true\b/.test(s),
      archived: /\barchived:true\b/.test(s),
      markedAsUnread: /\bmarked_as_unread:true\b/.test(s),
      lastUserViewTime: viewM ? new Date(Number(viewM[1]) * 1000).toISOString() : '',
      permissionPreset: presetM ? presetM[1] : '',
    };
  } catch { return {}; }
}

function normIso(t) {
  if (!t) return '';
  return String(t).trim().replace(' ', 'T').replace(/\+00:00$/, 'Z');
}

/** 从 conversation_summaries.db 补齐 Antigravity 重启后尚未加载进内存的历史会话。 */
function listDiskConversations() {
  const dbPath = join(AG_ROOT, 'conversation_summaries.db');
  if (!existsSync(dbPath)) return [];
  let db;
  try {
    db = new DatabaseSync(dbPath, { readOnly: true });
    const rows = db.prepare(`
      SELECT conversation_id, title, step_count, last_modified_time, workspace_uris, status, parent_conversation_id, nesting_depth
      FROM conversation_summaries
      WHERE (parent_conversation_id = '' OR parent_conversation_id IS NULL) AND nesting_depth = 0
      ORDER BY last_modified_time DESC
    `).all();
    const out = [];
    for (const row of rows) {
      const id = String(row.conversation_id || '');
      if (!id) continue;
      if (!existsSync(join(AG_ROOT, 'conversations', `${id}.db`)) && !existsSync(join(AG_ROOT, 'conversations', `${id}.pb`))) continue;
      const a = readDiskAnnotation(id);
      const updated = normIso(row.last_modified_time);
      const viewed = a.lastUserViewTime || '';
      const ws = String(row.workspace_uris || '').split(/[\n,]/).map(x => uriPath(x.trim())).filter(Boolean)[0] || '';
      out.push({
        id,
        title: a.title || row.title || '新对话',
        status: 'idle',
        steps: Number(row.step_count) || 0,
        updated,
        created: updated,
        workspace: ws,
        pinned: !!a.pinned,
        archived: !!a.archived,
        unread: !!a.markedAsUnread || (!!viewed && !!updated && Date.parse(updated) - Date.parse(viewed) > 3000),
        waiting: false,
        permissionPreset: a.permissionPreset || '',
      });
    }
    return out;
  } catch (e) {
    log('读取历史会话索引失败', e.message);
    return [];
  } finally {
    try { db?.close(); } catch {}
  }
}

async function listConversations() {
  const r = await rpc('GetAllCascadeTrajectories', { excludeSubtrajectories: true });
  const map = new Map();
  // 先铺磁盘上的全部历史会话，再用内存中实时会话覆盖（内存态有最新运行状态与待确认状态）
  for (const c of listDiskConversations()) map.set(c.id, c);
  for (const [id, s] of Object.entries(r.trajectorySummaries || {})) {
    const a = s.annotations || {};
    const updated = s.lastModifiedTime || s.createdTime || '';
    const viewed = a.lastUserViewTime || '';
    map.set(id, {
      id,
      title: a.title || s.summary || '新对话',
      status: runStatus(s.status),
      steps: s.stepCount || 0,
      updated,
      created: s.createdTime || '',
      workspace: (s.workspaces || []).map(w => uriPath(w.workspaceFolderAbsoluteUri || w.uri || '')).filter(Boolean)[0] || '',
      pinned: !!a.pinned,
      archived: !!a.archived,
      // 官方逻辑：手动标了未读，或者最后一次查看之后又有新动静
      unread: !!a.markedAsUnread || (!!viewed && Date.parse(updated) - Date.parse(viewed) > 3000),
      waiting: (s.waitingSteps || []).length > 0,
      permissionPreset: a.permissionPreset || '',
    });
  }
  return [...map.values()].sort((a, b) => (b.pinned - a.pinned) || b.updated.localeCompare(a.updated));
}

async function annotate(id, b) {
  const ann = {};
  if (typeof b.title === 'string') ann.title = b.title.trim().slice(0, 200);
  if (typeof b.pinned === 'boolean') ann.pinned = b.pinned;
  if (typeof b.unread === 'boolean') ann.markedAsUnread = b.unread;
  if (typeof b.archived === 'boolean') { ann.archived = b.archived; ann.archivalStatusTimestamp = new Date().toISOString(); }
  if (b.viewed) { ann.lastUserViewTime = new Date().toISOString(); ann.markedAsUnread = false; }
  if (typeof b.permissionPreset === 'string' && b.permissionPreset) ann.permissionPreset = b.permissionPreset;
  await rpc('UpdateConversationAnnotations', { cascadeId: id, annotations: ann, mergeAnnotations: true });
}

async function getSteps(id, offset = 0) {
  const r = await rpc('GetCascadeTrajectorySteps', { cascadeId: id, stepOffset: offset }, 30000);
  const raw = r.steps || [];
  // 这段步骤里最后一次回复用的模型（没有就是 ''，手机端沿用之前的）
  const lastModel = [...raw].reverse().find(s => s.metadata?.generatorModel)?.metadata.generatorModel || '';
  return { steps: raw.map((s, k) => normalizeStep(s, offset + k)), model: lastModel };
}

// ---------------- 模型 / 账户 ----------------
let modelCache = { at: 0, data: null };

async function modelData() {
  if (modelCache.data && Date.now() - modelCache.at < 30000) return modelCache.data;
  const d = await rpc('GetCascadeModelConfigData', {});
  modelCache = { at: Date.now(), data: d };
  return d;
}

/** 全部按 Antigravity 给的数据分组：groupedModels 里有系列名和思考强度名，没进分组的模型单独成项。 */
function groupOf(d, c) {
  const id = c.modelId || '';
  for (const [key, g] of Object.entries(d.groupedModels || {})) {
    for (const t of g.supportedThinkingLevels || []) {
      if (id === `${key}-${t.thinkingSuffix}`) return { family: g.displayName || key, level: t.thinkingDisplayName || t.thinkingSuffix, grouped: true };
    }
  }
  return { family: c.label, level: '', grouped: false };
}

async function listModels() {
  const d = await modelData();
  const byLabel = new Map((d.clientModelConfigs || []).map(c => [c.label, c]));
  const order = (d.clientModelSorts?.[0]?.groups || []).flatMap(g => g.modelLabels || []);
  const labels = [...order, ...[...byLabel.keys()].filter(l => !order.includes(l))];
  const models = labels.map(l => byLabel.get(l)).filter(Boolean).map(c => {
    const { family, level, grouped } = groupOf(d, c);
    return {
      model: c.modelOrAlias?.model || '',
      id: c.modelId || '',
      label: c.label,
      family,
      level,
      grouped,
      desc: c.tagDescription || '',
      tag: c.tagTitle || '',
      // 有 quotaInfo 但没有 remainingFraction = 用完了（值为 0 时会被省略）
      remaining: c.quotaInfo ? (c.quotaInfo.remainingFraction ?? 0) : null,
      reset: c.quotaInfo?.resetTime || '',
      images: !!c.supportsImages,
    };
  });
  return { defaultModel: d.defaultOverrideModelConfig?.modelOrAlias?.model || models[0]?.model || '', models };
}

async function account() {
  const u = (await rpc('GetUserStatus', {})).userStatus || {};
  const ps = u.planStatus || {};
  const pi = ps.planInfo || {};
  // 额度用 Antigravity 官方的汇总：按模型组，每组有每周和 5 小时两个限额
  let quotaGroups = [];
  let quotaNote = '';
  try {
    const q = (await rpc('RetrieveUserQuotaSummary', {})).response || {};
    quotaNote = q.description || '';
    quotaGroups = (q.groups || []).map(g => ({
      name: g.displayName || '',
      models: String(g.description || '').replace(/^Models within this group:\s*/i, '').split(/,\s*/).filter(Boolean),
      buckets: (g.buckets || []).map(b => ({
        name: b.displayName || '',
        window: b.window || '',
        remaining: b.remainingFraction ?? 0, // 0 会被省略，缺了就是用完了
        reset: b.resetTime || '',
        desc: b.description || '',
      })),
    }));
  } catch (e) { log('读取额度汇总失败', e.message); }
  return {
    name: u.name || '', email: u.email || '',
    plan: u.userTier?.name || pi.planName || '',
    promptCredits: { available: ps.availablePromptCredits ?? null, monthly: pi.monthlyPromptCredits ?? null },
    flowCredits: { available: ps.availableFlowCredits ?? null, monthly: pi.monthlyFlowCredits ?? null },
    upgradeText: u.userTier?.upgradeSubscriptionText || '',
    quotaGroups,
    quotaNote,
  };
}

// ---------------- 项目 ----------------
async function listProjects() {
  const ids = ((await rpc('ListProjects', {})).projectIds || []).filter(x => x !== 'outside-of-project');
  if (!ids.length) return [];
  const r = await rpc('ReadProjects', { ids });
  return (r.projects || []).filter(p => !p.archived).map(p => ({
    id: p.id,
    name: p.name || '未命名项目',
    folders: (p.projectResources?.resources || []).map(x => x.folderUri ? uriPath(x.folderUri) : '').filter(Boolean),
    conversations: (p.projectConversations?.conversations || []).map(c => c.conversationId),
    sidecar: !!p.sidecar?.sidecarId, // 自动化/插件自带的项目
  }));
}

async function readProject(id) {
  const r = await rpc('ReadProjects', { ids: [id] });
  const p = r.projects?.[0];
  if (!p) throw Object.assign(new Error('项目不存在'), { status: 404 });
  return p;
}

async function createProject(name, folder) {
  const id = randomUUID();
  const project = { id, name: String(name || '新项目').slice(0, 100) };
  if (folder) {
    const f = String(folder).trim().replace(/\\/g, '/');
    project.projectResources = { resources: [{ folderUri: f.startsWith('file:') ? f : 'file:///' + f.replace(/^\/+/, '') }] };
  }
  await rpc('CreateProject', { project });
  return id;
}

async function setConversationProject(cid, projectId) {
  // 先从原来所在的项目里拿掉，再加到新项目
  const all = await listProjects();
  for (const p of all) {
    if (p.conversations.includes(cid) && p.id !== projectId) {
      const full = await readProject(p.id);
      full.projectConversations = { conversations: (full.projectConversations?.conversations || []).filter(c => c.conversationId !== cid) };
      await rpc('UpdateProject', { project: full });
    }
  }
  if (projectId) {
    const full = await readProject(projectId);
    const list = full.projectConversations?.conversations || [];
    if (!list.some(c => c.conversationId === cid)) {
      full.projectConversations = { conversations: [...list, { conversationId: cid }] };
      await rpc('UpdateProject', { project: full });
    }
  }
}

// ---------------- 自动化（Antigravity 的定时 sidecar） ----------------
async function listAutomations() {
  const r = await rpc('GetSidecars', {});
  return (r.sidecars || []).filter(x => x.config?.builtin === 'schedule').map(x => {
    const args = x.config.args || [];
    const dash = args.indexOf('--');
    return {
      id: x.sidecarId,
      name: x.config.displayName || x.sidecarId,
      description: x.config.description || '',
      schedule: args[0] || '',
      prompt: dash >= 0 ? args.slice(dash + 1).join(' ') : '',
      enabled: x.userConfig?.enabled !== false && !x.config.disabled,
      status: short(x.status, 'SIDECAR_STATUS_'),
      error: x.lastError || '',
      projectId: x.userConfig?.projectId || '',
    };
  });
}

function automationConfig(b) {
  return {
    builtin: 'schedule',
    args: [String(b.schedule), 'agentapi', 'new-conversation', ...(b.model ? [`--model=${b.model}`] : []), '--', String(b.prompt)],
    restartPolicy: 'always',
    displayName: String(b.name || '自动化').slice(0, 100),
    description: String(b.description || ''),
  };
}

async function saveAutomation(b) {
  if (!String(b.schedule || '').trim() || !String(b.prompt || '').trim()) throw Object.assign(new Error('时间和内容都要填'), { status: 400 });
  if (b.id) {
    await rpc('ManageSidecar', { sidecarId: b.id, action: 'SIDECAR_ACTION_UPDATE', config: automationConfig(b) });
    return b.id;
  }
  const slug = (String(b.name || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'automation') + '-' + randomBytes(3).toString('hex');
  await rpc('ManageSidecar', { sidecarId: slug, action: 'SIDECAR_ACTION_CREATE', config: automationConfig(b) });
  return slug;
}

// ---------------- 动作 ----------------
/** 手机传来的附件 [{mime, data(base64), name}] → Antigravity 的 media（会存进 brain/<对话>/.user_uploaded 给 agent 看）。 */
function toMedia(attachments) {
  return (attachments || []).filter(a => a && a.data).slice(0, 10).map(a => ({
    mimeType: String(a.mime || 'application/octet-stream'),
    inlineData: String(a.data),
    ...(a.name ? { displayName: String(a.name).slice(0, 200) } : {}),
  }));
}

/** 手机传来的 @ 连接器 -> Antigravity 的上下文项（和电脑上 @Google Drive 一样），放在文字前面。 */
async function mentionItems(mentions) {
  if (!Array.isArray(mentions) || !mentions.length) return [];
  let plugins = {};
  try { plugins = (await freshJetbox()).userConfig?.plugins || {}; } catch {}
  const out = [];
  for (const m of mentions.slice(0, 10)) {
    const key = String(m.key || '');
    if (!key) continue;
    if ((m.kind || 'plugin') === 'plugin') {
      const dir = plugins[key]?.installDir || join(process.env.USERPROFILE || '', '.gemini', 'config', 'plugins', key);
      let manifest = {};
      try { manifest = JSON.parse(readFileSync(join(dir, 'plugin.json'), 'utf8')); } catch {}
      out.push({ item: { plugin: { name: manifest.name || key, displayName: manifest.displayName || String(m.name || key), path: dir, logo: manifest.logo || '' } } });
    } else {
      out.push({ text: `@${m.name || key} ` });
    }
  }
  if (out.length) out.push({ text: ' ' });
  return out;
}

async function sendMessage(id, text, model, attachments, mentions, delivery) {
  const body = { cascadeId: id, items: [...await mentionItems(mentions), { text }], clientMessageId: randomUUID() };
  if (delivery === 'now') body.deliveryStrategy = 'MESSAGE_DELIVERY_STRATEGY_NEXT_INVOCATION';
  else if (delivery === 'queue') body.deliveryStrategy = 'MESSAGE_DELIVERY_STRATEGY_WHEN_IDLE';
  const media = toMedia(attachments);
  if (media.length) body.media = media;
  if (model) {
    // 沿用这个对话上一条消息的完整 agent 配置，只把模型换掉
    const r = await rpc('GetCascadeTrajectorySteps', { cascadeId: id }, 30000);
    const lastUser = [...(r.steps || [])].reverse().find(s => s.userInput?.userConfig);
    const cfg = structuredClone(lastUser?.userInput.userConfig || {});
    cfg.plannerConfig = { ...(cfg.plannerConfig || {}), planModel: model };
    body.cascadeConfig = cfg;
  }
  await rpc('SendUserCascadeMessage', body, 30000);
}
function randomUUID() { return globalThis.crypto.randomUUID(); }

// 极简 protobuf wire 读取，用来从 pendingAgentMessages 的 stepPayload 里解出用户排队消息的原文
function pbFields(buf) {
  const out = [];
  let i = 0;
  const varint = () => {
    let v = 0, s = 0;
    while (i < buf.length) { const b = buf[i++]; v |= (b & 0x7f) << s; if (!(b & 0x80)) break; s += 7; }
    return v >>> 0;
  };
  while (i < buf.length) {
    const tag = varint(), field = tag >>> 3, wire = tag & 7;
    if (wire === 0) varint();
    else if (wire === 1) i += 8;
    else if (wire === 5) i += 4;
    else if (wire === 2) { const n = varint(); out.push([field, buf.subarray(i, i + n)]); i += n; }
    else break;
  }
  return out;
}

function decodePendingText(m) {
  if (m.content) return String(m.content);
  if (!m.stepPayload) return '';
  try {
    const stepBuf = Buffer.from(m.stepPayload, 'base64');
    const uiBuf = pbFields(stepBuf).find(([f]) => f === 19)?.[1];
    if (!uiBuf) return '';
    const ui = pbFields(uiBuf);
    const items = ui.filter(([f]) => f === 3).map(([, b]) => pbFields(b).find(([f]) => f === 1)?.[1]?.toString('utf8') || '').join('');
    if (items.trim()) return items.trim();
    const resp = ui.find(([f]) => f === 2)?.[1]?.toString('utf8') || ui.find(([f]) => f === 1)?.[1]?.toString('utf8') || '';
    return resp.trim();
  } catch { return ''; }
}

/** 读取某个对话的首帧 StreamAgentStateUpdates（0..0 分页，十几毫秒拿到排队消息、后台任务、产物、文件变更）。 */
async function getAgentStateUpdate(id) {
  if (!ls) await rpc('GetAllCascadeTrajectories', {});
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), 3000);
  try {
    const r = await fetch(ls.base + SVC + 'StreamAgentStateUpdates', {
      method: 'POST', signal: ac.signal,
      headers: { 'Content-Type': 'application/connect+json', 'Connect-Protocol-Version': '1', 'x-codeium-csrf-token': ls.token },
      body: envelope({
        conversationId: id,
        subscriberId: randomUUID(),
        initialStepsPageBounds: { startIndex: 0, endIndexExclusive: 0 },
        initialGeneratorMetadatasPageBounds: { startIndex: 0, endIndexExclusive: 0 },
        initialExecutorMetadatasPageBounds: { startIndex: 0, endIndexExclusive: 0 },
        initialBackgroundTasksPageBounds: { startIndex: 0, endIndexExclusive: 50 },
      }),
    });
    if (!r.ok || !r.body) return null;
    const reader = r.body.getReader();
    let buf = Buffer.alloc(0);
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buf = Buffer.concat([buf, Buffer.from(value)]);
      if (buf.length >= 5) {
        const len = buf.readUInt32BE(1);
        if (buf.length >= 5 + len) {
          const msg = JSON.parse(buf.subarray(5, 5 + len).toString('utf8') || '{}');
          try { ac.abort(); } catch {}
          return msg?.update || null;
        }
      }
    }
    return null;
  } catch { return null; }
  finally { clearTimeout(t); }
}

/** 读取某个对话当前排队等待发送的用户消息（Antigravity 2.0 存在 pendingAgentMessages 里）。 */
async function getPendingMessages(id) {
  const u = await getAgentStateUpdate(id);
  const list = u?.pendingAgentMessagesUpdate?.pendingAgentMessages || [];
  return list
    .filter(m => !m.hideFromUser && (m.sender === 'user' || !m.sender))
    .map(m => ({
      id: m.id || '',
      text: decodePendingText(m),
      time: m.timestamp || '',
      immediate: m.deliveryStrategy === 'MESSAGE_DELIVERY_STRATEGY_NEXT_INVOCATION',
    }))
    .filter(m => m.id && m.text);
}

/** 读取与电脑 Antigravity 完全同步的概览、子代理、后台任务数据。 */
async function getOverview(id) {
  const [u, all] = await Promise.all([
    getAgentStateUpdate(id),
    rpc('GetAllCascadeTrajectories', {}).catch(() => ({ trajectorySummaries: {} })),
  ]);

  // 1) 产物 / 文件：合并 artifactSnapshotsUpdate 与 brain/<id> 目录
  const artMap = new Map();
  const dir = join(process.env.USERPROFILE || '', '.gemini', 'antigravity', 'brain', id);
  if (existsSync(dir)) {
    for (const ent of readdirSync(dir, { withFileTypes: true })) {
      if (!ent.isFile()) continue;
      const name = ent.name;
      if (name.startsWith('.') || name.endsWith('.metadata.json') || /\.resolved(\.\d+)?$/.test(name)) continue;
      const full = join(dir, name);
      try {
        const st = statSync(full);
        let summary = '';
        const meta = full + '.metadata.json';
        if (existsSync(meta)) {
          try { summary = String(JSON.parse(readFileSync(meta, 'utf8')).summary || ''); } catch {}
        }
        const p = full.replace(/\\/g, '/');
        artMap.set(p.toLowerCase(), { name, path: p, mime: mimeOf(name), size: st.size, updated: st.mtime.toISOString(), summary });
      } catch {}
    }
  }
  for (const a of (u?.artifactSnapshotsUpdate?.artifactSnapshots || [])) {
    const p = uriPath(a.artifactAbsoluteUri || '').replace(/\\/g, '/');
    if (!p || artMap.has(p.toLowerCase())) continue;
    let size = 0;
    try { if (existsSync(p)) size = statSync(p).size; } catch {}
    const name = p.split('/').pop() || a.artifactName || 'file';
    artMap.set(p.toLowerCase(), { name, path: p, mime: mimeOf(name), size, updated: a.lastEdited || '', summary: '' });
  }
  const artifacts = [...artMap.values()].sort((a, b) => (b.updated || '').localeCompare(a.updated || ''));

  // 2) accumulatedSections: file_diffs / background_tasks / skill_used
  const secs = u?.accumulatedSectionsUpdate?.accumulatedSections || [];
  const secOf = k => secs.find(s => s.key === k)?.items || [];

  const fileDiffs = secOf('file_diffs').map(it => {
    const fd = it.renderInfo?.fileDiff || {};
    const p = uriPath(fd.uri || it.id || '').replace(/\\/g, '/');
    const name = p.split('/').pop() || p;
    const slash = p.lastIndexOf('/');
    return {
      path: p, name, dir: slash > 0 ? p.slice(0, slash) : '',
      isArtifact: !!fd.isArtifactFile,
      updated: fd.lastModifiedAt || '',
    };
  }).filter(x => x.path).reverse();

  const skillsUsed = secOf('skill_used').map(it => ({
    name: it.renderInfo?.label || it.id || '',
    path: uriPath(it.renderInfo?.fileUri || '').replace(/\\/g, '/'),
  })).filter(x => x.name);

  // 3) 后台任务：运行中的（backgroundTasksUpdate）置顶 + 全部历史（accumulatedSections[background_tasks]）
  const bgMap = new Map();
  for (const item of (u?.backgroundTasksUpdate?.backgroundTasks || [])) {
    const snap = item.taskSnapshot || {};
    const td = item.step?.taskDetails || {};
    const tid = snap.taskId || td.id || '';
    if (!tid) continue;
    const status = STATUS_MAP[short(item.step?.status, 'CORTEX_STEP_STATUS_')] || 'running';
    bgMap.set(tid, {
      id: tid,
      title: snap.toolSummary || td.title || snap.toolName || tid.split('/').pop(),
      command: snap.description || td.description || '',
      running: status === 'running' || status === 'waiting',
      status,
      progress: td.progress || '',
      logPath: uriPath(snap.logUri || td.logUri || '').replace(/\\/g, '/'),
      startTime: snap.startTime || '',
    });
  }
  for (const item of secOf('background_tasks')) {
    const tid = item.id || '';
    if (!tid || bgMap.has(tid)) continue;
    const tv = item.renderInfo?.taskView || {};
    bgMap.set(tid, {
      id: tid,
      title: item.renderInfo?.label || tid.split('/').pop(),
      command: tv.header || '',
      running: false,
      status: 'done',
      progress: '',
      logPath: uriPath(tv.logUri || '').replace(/\\/g, '/'),
      startTime: '',
    });
  }
  const backgroundTasks = [...bgMap.values()];

  // 4) 子代理：GetAllCascadeTrajectories 里挂在本对话下的子轨迹
  const subagents = Object.entries(all.trajectorySummaries || {})
    .filter(([sid, s]) => sid !== id && (s.trajectoryMetadata?.parentConversationId === id || s.trajectoryMetadata?.rootConversationId === id))
    .map(([sid, s]) => ({
      id: sid,
      role: s.trajectoryMetadata?.subagentSpec?.role || s.summary || s.trajectoryMetadata?.subagentSpec?.typeName || '子代理',
      typeName: s.trajectoryMetadata?.subagentSpec?.typeName || s.trajectoryMetadata?.agentScript?.name || '',
      status: STATUS_MAP[short(s.status, 'CASCADE_RUN_STATUS_')] || 'idle',
      steps: s.stepCount || 0,
      killed: !!s.killed,
      created: s.createdTime || '',
      updated: s.lastModifiedTime || s.createdTime || '',
    }))
    .sort((a, b) => b.updated.localeCompare(a.updated));

  const cw = u?.contextWindowUsage || null;
  const contextUsage = cw ? { used: cw.estimatedTokensUsed || 0, max: cw.maxContextTokens || 0 } : null;

  return { artifacts, fileDiffs, skillsUsed, subagents, backgroundTasks, contextUsage };
}

/** 读取电脑上 Antigravity 内置浏览器的标签页状态（app_storage.json 的 aux-pane-v2-session）。 */
async function getPcBrowserTabs(cid) {
  const file = join(process.env.APPDATA || '', 'Antigravity', 'app_storage.json');
  let storage = {};
  try { storage = JSON.parse(readFileSync(file, 'utf8')); } catch {}
  let session = {};
  try { session = JSON.parse(storage['aux-pane-v2-session'] || '{}'); } catch {}
  const panes = session.conversationPanes || {};

  // 附带会话标题方便识别其他对话里的标签页
  const titles = new Map();
  try {
    const all = await rpc('GetAllCascadeTrajectories', {});
    for (const [id, s] of Object.entries(all.trajectorySummaries || {})) {
      titles.set(id, s.annotations?.title || s.summary || '未命名对话');
    }
  } catch {}

  const parseTabs = (convId, pane) => {
    const out = [];
    for (const t of (pane?.tabs || [])) {
      const cv = t?.content?.customView;
      if (cv?.customPaneKey !== 'browser') continue;
      let inp = {};
      try { inp = JSON.parse(cv.serializedInputs || '{}'); } catch {}
      const url = String(inp.entryUrl || inp.initialUrl || '').trim();
      out.push({
        id: String(t.id || inp.tabId || ''),
        url,
        title: String(inp.pageTitle || url || '新标签页'),
        favicon: String(inp.faviconDataUrl || ''),
        cid: convId,
        convTitle: titles.get(convId) || '',
      });
    }
    return out;
  };

  const curPane = cid ? panes[cid] : null;
  const tabs = curPane ? parseTabs(cid, curPane) : [];
  const activeTabId = String(curPane?.activeTabId || tabs[0]?.id || '');
  const otherTabs = [];
  for (const [otherId, pane] of Object.entries(panes)) {
    if (otherId === cid) continue;
    otherTabs.push(...parseTabs(otherId, pane));
  }
  const auxPaneWidth = Math.min(85, Math.max(35, Number(storage.auxPaneWidth) || 60));
  return { activeTabId, tabs, otherTabs, auxPaneWidth };
}

/** 关闭电脑上 Antigravity 内置浏览器的指定标签页，并同步写回 app_storage.json。 */
async function closePcBrowserTab(targetCid, tabId, curCid) {
  const file = join(process.env.APPDATA || '', 'Antigravity', 'app_storage.json');
  let storage = {};
  try { storage = JSON.parse(readFileSync(file, 'utf8')); } catch {}
  let session = {};
  try { session = JSON.parse(storage['aux-pane-v2-session'] || '{}'); } catch {}
  const panes = session.conversationPanes || {};

  const matchTab = (t) => {
    let inp = {};
    try { inp = JSON.parse(t?.content?.customView?.serializedInputs || '{}'); } catch {}
    return String(t?.id || inp.tabId || '') === tabId;
  };

  let removed = false;
  const candidateIds = targetCid && panes[targetCid] ? [targetCid] : Object.keys(panes);
  for (const k of candidateIds) {
    const pane = panes[k];
    if (!pane || !Array.isArray(pane.tabs)) continue;
    const idx = pane.tabs.findIndex(matchTab);
    if (idx >= 0) {
      pane.tabs.splice(idx, 1);
      if (pane.activeTabId === tabId) {
        const next = pane.tabs[Math.min(idx, pane.tabs.length - 1)];
        pane.activeTabId = next ? String(next.id || '') : null;
      }
      removed = true;
      break;
    }
  }

  if (removed) {
    storage['aux-pane-v2-session'] = JSON.stringify(session);
    writeFileSync(file, JSON.stringify(storage, null, 2), 'utf8');
  }
  return { ok: true, removed, ...(await getPcBrowserTabs(curCid || targetCid || '')) };
}

/** 捕获电脑上 Antigravity 窗口画面（mode=pane 只截右侧内置浏览器区，mode=full 截整个窗口）。 */
const WINCAP_EXE = join(ROOT, 'wincap.exe');
let screenInflight = new Map();
function captureAntigravityScreen(mode = 'screen') {
  const m = mode === 'pane' ? 'pane' : 'screen';
  if (screenInflight.has(m)) return screenInflight.get(m);
  const p = new Promise(resolve => {
    if (!existsSync(WINCAP_EXE)) return resolve({ ok: false, reason: 'NO_WINCAP' });
    const target = m === 'pane' ? paneRatioFor('pane') : 'screen';
    const maxW = m === 'pane' ? '1280' : '1600';
    execFile(WINCAP_EXE, [target, '80', maxW], { encoding: 'buffer', maxBuffer: 8 << 20, windowsHide: true, timeout: 4000 }, (err, stdout, stderr) => {
      if (err || !stdout || stdout.length < 100) {
        const reason = (stderr ? stderr.toString('utf8').trim() : '') || (err?.message || 'CAPTURE_FAILED');
        return resolve({ ok: false, reason });
      }
      resolve({ ok: true, data: stdout });
    });
  }).finally(() => screenInflight.delete(m));
  screenInflight.set(m, p);
  return p;
}

function paneRatioFor(mode) {
  if (mode !== 'pane') return 'screen';
  try {
    const file = join(process.env.APPDATA || '', 'Antigravity', 'app_storage.json');
    const st = JSON.parse(readFileSync(file, 'utf8'));
    const pct = Math.min(85, Math.max(35, Number(st.auxPaneWidth) || 60));
    return (pct / 100).toFixed(2);
  } catch { return '0.60'; }
}

/** 常驻 wincap.exe daemon，把手机鼠标/拖拽/滚轮/键盘/读取编辑输入框以 <2ms 延迟发往电脑。 */
let inputProc = null;
let inputBuf = '';
let inputQueue = [];

function ensureInputDaemon() {
  if (inputProc && !inputProc.killed && inputProc.exitCode === null) return inputProc;
  if (!existsSync(WINCAP_EXE)) throw new Error('找不到控制组件');
  const p = spawn(WINCAP_EXE, ['daemon'], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  inputBuf = '';
  p.stdout.setEncoding('utf8');
  p.stdout.on('data', chunk => {
    inputBuf += chunk;
    let idx;
    while ((idx = inputBuf.indexOf('\n')) >= 0) {
      const line = inputBuf.slice(0, idx).replace(/\r$/, '');
      inputBuf = inputBuf.slice(idx + 1);
      const job = inputQueue.shift();
      if (!job) continue;
      if (line.startsWith('ERR ')) job.reject(new Error(line.slice(4) || '操作失败'));
      else job.resolve(line);
    }
  });
  const failAll = err => {
    if (inputProc === p) inputProc = null;
    while (inputQueue.length) inputQueue.shift().reject(err);
  };
  p.on('exit', () => failAll(new Error('控制进程已重启')));
  p.on('error', e => failAll(e));
  inputProc = p;
  return p;
}

function sendAntigravityInput({ action, mode = 'screen', nx = 0.5, ny = 0.5, delta = 0, text = '', key = '' }) {
  const allowed = new Set(['move', 'mousedown', 'mouseup', 'click', 'dblclick', 'rightclick', 'wheel', 'text', 'key', 'readfield', 'replacefield', 'cursor', 'touchdown', 'touchmove', 'touchup', 'touchcancel']);
  if (!allowed.has(action)) return Promise.reject(new Error('不支持的操作'));
  const target = mode === 'pane' ? paneRatioFor('pane') : 'screen';
  const extra = action === 'wheel' ? String(Math.round(Number(delta) || -240))
    : (action === 'text' || action === 'replacefield' || action === 'cursor') ? String(text ?? '')
    : action === 'key' ? String(key || '') : '';
  const b64 = extra ? Buffer.from(extra, 'utf8').toString('base64') : '';
  return new Promise((resolve, reject) => {
    let p;
    try { p = ensureInputDaemon(); } catch (e) { return reject(e); }
    const timer = setTimeout(() => reject(new Error('操控超时')), 5000);
    inputQueue.push({
      resolve: line => {
        clearTimeout(timer);
        if (action === 'readfield' && line.startsWith('OK ')) {
          const decoded = Buffer.from(line.slice(3).trim(), 'base64').toString('utf8');
          resolve({ ok: true, text: decoded });
        } else if (action === 'cursor' && line.startsWith('SAME ')) {
          resolve({ ok: true, same: true, id: line.slice(5).trim() });
        } else if (action === 'cursor' && line.startsWith('CURSOR ')) {
          const [, id, xHot, yHot, w, h, png] = line.split(' ');
          resolve({ ok: true, same: false, id, xHot: +xHot || 0, yHot: +yHot || 0, w: +w || 32, h: +h || 32, png: png || '' });
        } else {
          resolve({ ok: true });
        }
      },
      reject: err => { clearTimeout(timer); reject(err); },
    });
    p.stdin.write(`${action}\t${target}\t${Number(nx) || 0}\t${Number(ny) || 0}\t${b64}\n`);
  });
}

/** 读取某个对话里 Gemini 的浏览器操作轨迹与截图（browser_subagent / open_browser_url / search_web 等）。 */
async function getBrowserActivity(cid) {
  const actions = [];
  const screenshots = [];
  let running = false;
  if (!cid) return { running, actions, screenshots };

  const [{ steps }, all] = await Promise.all([
    getSteps(cid, 0).catch(() => ({ steps: [] })),
    rpc('GetAllCascadeTrajectories', {}).catch(() => ({ trajectorySummaries: {} })),
  ]);

  const BROWSER_TYPES = new Set(['browser_subagent', 'open_browser_url', 'read_url_content', 'search_web']);
  for (const st of steps) {
    if (!BROWSER_TYPES.has(st.type)) continue;
    if (st.status === 'running' || st.status === 'waiting') running = true;
    actions.push({ title: st.title, text: st.text, sub: st.sub, status: st.status, time: st.time, source: 'main' });
    for (const a of (st.attachments || [])) {
      if (a.mime?.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/i.test(a.name || '')) {
        screenshots.push({ name: a.name, path: a.path, mime: a.mime, ref: a.ref || '', cid });
      }
    }
  }

  // 同时抓取挂载在本对话下的 browser 子代理轨迹与截图
  const browserSubs = Object.entries(all.trajectorySummaries || {}).filter(([sid, s]) => {
    if (sid === cid) return false;
    const m = s.trajectoryMetadata || {};
    if (m.parentConversationId !== cid && m.rootConversationId !== cid) return false;
    const tn = String(m.subagentSpec?.typeName || m.agentScript?.name || '').toLowerCase();
    return tn.includes('browser') || /browser/i.test(String(m.subagentSpec?.role || ''));
  });
  for (const [sid, s] of browserSubs) {
    if (short(s.status, 'CASCADE_RUN_STATUS_') === 'running') running = true;
    const subSteps = (await getSteps(sid, 0).catch(() => ({ steps: [] }))).steps;
    for (const st of subSteps) {
      if (st.kind === 'hidden') continue;
      actions.push({
        title: st.kind === 'assistant' ? '浏览器子代理' : st.title,
        text: st.text, sub: st.sub, status: st.status, time: st.time, source: 'subagent',
      });
      for (const a of (st.attachments || [])) {
        if (a.mime?.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/i.test(a.name || '')) {
          screenshots.push({ name: a.name, path: a.path, mime: a.mime, ref: a.ref || '', cid: sid });
        }
      }
    }
  }
  return { running, actions: actions.slice(-30).reverse(), screenshots: screenshots.slice(-12).reverse() };
}

/** 新建对话：StartCascade 建空对话，再发第一条消息（这样才能带附件、指定模型）。 */
async function newConversation(text, model, attachments, preset, projectId, mentions) {
  const start = { source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT', trajectoryType: 'CORTEX_TRAJECTORY_TYPE_CASCADE' };
  if (projectId) start.projectEnvConfig = { projectId, defaultProjectEnvironment: {} };
  const r = await rpc('StartCascade', start);
  const cid = r.cascadeId;
  if (!cid) throw new Error('新建对话失败');
  if (preset) await annotate(cid, { permissionPreset: preset }).catch(e => log('设置权限预设失败', e.message));
  if (projectId) await setConversationProject(cid, projectId).catch(e => log('加入项目失败', e.message));
  const body = { cascadeId: cid, items: [...await mentionItems(mentions), { text }], clientMessageId: randomUUID() };
  if (model) body.cascadeConfig = { plannerConfig: { planModel: model } };
  const media = toMedia(attachments);
  if (media.length) body.media = media;
  await rpc('SendUserCascadeMessage', body, 30000);
  return cid;
}

async function cancel(id) {
  await rpc('CancelCascadeInvocation', { cascadeId: id });
}

// 回应 agent 等你确认的操作
async function respond(id, b) {
  const accept = !!b.accept;
  const interaction = { trajectoryId: b.trajectoryId || id, stepIndex: b.stepIndex };
  const scope = b.scope || 'PERMISSION_SCOPE_ONCE';
  switch (b.kind) {
    case 'runCommand':
      interaction.runCommand = {
        confirm: accept,
        proposedCommandLine: b.command || '',
        submittedCommandLine: b.command || '',
        ...(b.sandboxOverride ? { sandboxOverride: true } : {}),
      };
      break;
    case 'permission':
      if (accept) {
        const perm = { allow: true, scope };
        if (b.grant && ['PERMISSION_SCOPE_GLOBAL', 'PERMISSION_SCOPE_WORKSPACE', 'PERMISSION_SCOPE_PROJECT'].includes(scope)) {
          perm.persistGrants = { allow: [b.grant] };
        }
        if (b.sandboxOverride) perm.sandboxOverride = true;
        interaction.permission = perm;
      } else {
        interaction.permission = { allow: false, userDenyInstruction: b.text || '' };
      }
      break;
    case 'filePermission':
      interaction.filePermission = { allow: accept, scope, absolutePathUri: b.path || '' };
      break;
    case 'askQuestion':
      interaction.askQuestion = accept
        ? { responses: (b.answers || []).map(a => ({ question: a.question || '', selectedOptionIds: a.optionIds || [], writeInResponse: a.text || '' })) }
        : { cancelled: true };
      break;
    case 'elicitation':
      interaction.elicitation = { action: accept ? 'accept' : 'decline', contentJson: b.text || '' };
      break;
    default:
      interaction[b.kind || 'approvalInteraction'] = { confirm: accept };
  }
  await rpc('HandleCascadeUserInteraction', { cascadeId: id, interaction });
}

// ---------------- Antigravity 自带的中文翻译表 ----------------
// app.asar 里有 ["English","中文"] 这样的词条，导出来给手机用：有官方译名的字符串直接用官方中文。
let i18nCache = null;
function i18nTable() {
  if (i18nCache) return i18nCache;
  const map = {};
  try {
    // 优先按正在运行的 language_server 找（…\resources\bin\language_server.exe → …\resources\app.asar），
    // 这样装在别的位置（比如 Program Files）也能找到
    const asar = [ls?.exe && join(dirname(dirname(ls.exe)), 'app.asar'),
      join(process.env.LOCALAPPDATA || '', 'Programs', 'antigravity', 'resources', 'app.asar'),
      join(process.env.ProgramFiles || 'C:\Program Files', 'Antigravity', 'resources', 'app.asar')].find(f => f && existsSync(f));
    if (!asar) throw new Error('app.asar not found');
    const text = readFileSync(asar).toString('utf8');
    const re = /\["((?:[^"\\]|\\.){1,400})","((?:[^"\\]|\\.){1,400})"\]/g;
    let m;
    while ((m = re.exec(text))) {
      const zh = m[2];
      if (!/[一-鿿]/.test(zh) || /[一-鿿]/.test(m[1])) continue;
      try { map[JSON.parse(`"${m[1]}"`)] = JSON.parse(`"${zh}"`); } catch {}
    }
  } catch (e) { log('读取 Antigravity 翻译表失败', e.message); }
  // 2.35 起界面不再打包在 app.asar 里，读不到词条；读到过就存一份，读不到时用上次存的
  const saved = join(DATA, 'i18n-zh.json');
  if (Object.keys(map).length) { i18nCache = map; try { writeFileSync(saved, JSON.stringify(map)); } catch {} }
  else { try { Object.assign(map, JSON.parse(readFileSync(saved, 'utf8'))); if (Object.keys(map).length) i18nCache = map; } catch {} }
  log(`Antigravity 中文词条 ${Object.keys(map).length} 条`);
  return map;
}

// ---------------- 谷歌翻译（走电脑网络 + 系统代理） ----------------
const trCache = new Map();
const trInflight = new Map();
const CHROME_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36';
let trActive = 0;
const trQueue = [];
function withTrSlot(fn) {
  return new Promise((resolve, reject) => {
    const run = () => {
      trActive++;
      Promise.resolve().then(fn).then(
        v => { trActive--; trQueue.shift()?.(); resolve(v); },
        e => { trActive--; trQueue.shift()?.(); reject(e); },
      );
    };
    if (trActive < 3) run(); else trQueue.push(run);
  });
}

/** 通过电脑网络（自动走 Windows 系统代理）发起 HTTP/HTTPS 请求，支持重定向。 */
async function pcFetch(urlStr, { method = 'GET', headers = {}, body = null, timeoutMs = 20000, redirects = 5 } = {}) {
  const u = new URL(urlStr);
  const up = isLocalHost(u.hostname) ? null : await getUpstreamProxy();
  if (!up) {
    const r = await fetch(urlStr, { method, headers: { 'User-Agent': CHROME_UA, ...headers }, body, signal: AbortSignal.timeout(timeoutMs) });
    return { ok: r.ok, status: r.status, contentType: r.headers.get('content-type') || '', data: Buffer.from(await r.arrayBuffer()) };
  }
  const bodyBuf = body == null ? null : Buffer.isBuffer(body) ? body : Buffer.from(String(body), 'utf8');
  const reqHeaders = { 'User-Agent': CHROME_UA, ...headers };
  if (bodyBuf && !reqHeaders['Content-Length'] && !reqHeaders['content-length']) reqHeaders['Content-Length'] = String(bodyBuf.length);

  return new Promise((resolve, reject) => {
    let settled = false;
    const done = (err, val) => { if (!settled) { settled = true; clearTimeout(timer); err ? reject(err) : resolve(val); } };
    const timer = setTimeout(() => done(new Error('请求超时')), timeoutMs);

    const handleRes = res => {
      const loc = res.headers.location;
      if (loc && res.statusCode >= 300 && res.statusCode < 400 && redirects > 0) {
        res.resume();
        const next = new URL(loc, u).toString();
        pcFetch(next, { method: 'GET', headers, timeoutMs, redirects: redirects - 1 }).then(v => done(null, v), e => done(e));
        return;
      }
      const chunks = [];
      res.on('data', d => chunks.push(d));
      res.on('end', () => {
        const status = res.statusCode || 0;
        done(null, { ok: status >= 200 && status < 300, status, contentType: String(res.headers['content-type'] || ''), data: Buffer.concat(chunks) });
      });
      res.on('error', e => done(e));
    };

    if (u.protocol === 'http:') {
      const req = http.request({ host: up.host, port: up.port, method, path: urlStr, headers: { ...reqHeaders, Host: u.host } }, handleRes);
      req.on('error', e => done(e));
      if (bodyBuf) req.write(bodyBuf);
      req.end();
      return;
    }

    // HTTPS：通过系统代理建立 CONNECT 隧道再走 TLS
    const port = Number(u.port) || 443;
    const sock = net.connect(up.port, up.host, () => {
      sock.write(`CONNECT ${u.hostname}:${port} HTTP/1.1\r\nHost: ${u.hostname}:${port}\r\n\r\n`);
    });
    sock.on('error', e => done(e));
    let head = Buffer.alloc(0);
    const onData = chunk => {
      head = Buffer.concat([head, chunk]);
      const end = head.indexOf('\r\n\r\n');
      if (end < 0) return;
      sock.off('data', onData);
      const line = head.subarray(0, end).toString('latin1').split('\r\n')[0] || '';
      if (!/ 200\b/.test(line)) { sock.destroy(); return done(new Error('代理隧道失败: ' + line)); }
      const tlsSock = tls.connect({ socket: sock, servername: u.hostname });
      tlsSock.on('error', e => done(e));
      const req = https.request({
        host: u.hostname, port, method, path: u.pathname + u.search,
        headers: { ...reqHeaders, Host: u.host, Connection: 'close' },
        createConnection: () => tlsSock,
      }, handleRes);
      req.on('error', e => done(e));
      if (bodyBuf) req.write(bodyBuf);
      req.end();
    };
    sock.on('data', onData);
  });
}

async function translateOne(q, to) {
  return withTrSlot(async () => {
    const form = new URLSearchParams({ q }).toString();
    const r = await pcFetch(`https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encodeURIComponent(to)}&dt=t`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: form,
      timeoutMs: 20000,
    });
    if (r.ok) {
      const j = JSON.parse(r.data.toString('utf8'));
      return (j[0] || []).map(x => x?.[0] || '').join('');
    }
    // 主接口限流时自动切换备用谷歌翻译接口
    const r2 = await pcFetch(`https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=${encodeURIComponent(to)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: form,
      timeoutMs: 20000,
    });
    if (!r2.ok) throw new Error(`谷歌翻译返回 HTTP ${r2.status}`);
    const j2 = JSON.parse(r2.data.toString('utf8'));
    if (Array.isArray(j2)) return Array.isArray(j2[0]) ? String(j2[0][0] || '') : String(j2[0] || '');
    return String(j2?.sentences?.map?.(x => x?.trans || '').join('') || '');
  });
}

async function translate(text, to = 'zh-CN') {
  const key = to + '\n' + text;
  if (trCache.has(key)) return trCache.get(key);
  if (trInflight.has(key)) return trInflight.get(key);
  const p = (async () => {
    // 太长就按段落切开分批翻
    const parts = [];
    let cur = '';
    for (const para of text.split('\n')) {
      if (cur && cur.length + para.length > 4000) { parts.push(cur); cur = ''; }
      cur = cur ? cur + '\n' + para : para;
    }
    if (cur) parts.push(cur);
    const out = [];
    for (const part of parts) out.push(await translateOne(part, to));
    const res = out.join('\n');
    if (trCache.size > 2000) trCache.clear();
    trCache.set(key, res);
    return res;
  })().finally(() => trInflight.delete(key));
  trInflight.set(key, p);
  return p;
}

// ---------------- 语音转写（用 Antigravity 自己的识别） ----------------
// 手机录 16kHz 单声道 PCM，分段 POST 过来；这里转给 language_server 的流式转写，结果通过 SSE 推回手机。
const stt = new Map(); // sid -> { finals: [], partial, done, waiters: [] }

function envelope(obj) {
  const body = Buffer.from(JSON.stringify(obj));
  const head = Buffer.alloc(5);
  head.writeUInt32BE(body.length, 1);
  return Buffer.concat([head, body]);
}

function sttError(e) {
  const m = String(e.message || e.code || '');
  const wait = m.match(/reset after ([\dhms.]+)/i)?.[1]?.replace(/\.+$/, '');
  if (/exhausted|quota/i.test(m)) return `语音识别额度暂时用完了${wait ? '，' + wait.replace('h', ' 小时 ').replace('m', ' 分 ').replace(/([\d.]+)s/, (_, x) => Math.ceil(+x) + ' 秒') + '后恢复' : '，稍后再试'}`;
  return '语音识别出错：' + m.slice(0, 200);
}

function sttText(s) { return [...s.finals, s.partial].filter(Boolean).join(' ').trim(); }

async function sttStart(lang) {
  if (!ls) await rpc('GetAllCascadeTrajectories', {});
  const r = await fetch(ls.base + SVC + 'StreamAudioTranscription', {
    method: 'POST',
    headers: { 'Content-Type': 'application/connect+json', 'Connect-Protocol-Version': '1', 'x-codeium-csrf-token': ls.token },
    body: envelope({ mimeType: 'audio/l16;rate=16000', languageCodes: lang?.length ? lang : ['zh-CN', 'en-US'] }),
  });
  if (!r.ok) throw new Error(`语音识别启动失败 HTTP ${r.status}`);
  const reader = r.body.getReader();
  let buf = Buffer.alloc(0);
  let sid = null;
  let resolveReady, rejectReady;
  const ready = new Promise((res, rej) => { resolveReady = res; rejectReady = rej; setTimeout(() => rej(new Error('语音识别没有响应')), 8000); });
  (async () => {
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buf = Buffer.concat([buf, Buffer.from(value)]);
        while (buf.length >= 5) {
          const flags = buf[0], len = buf.readUInt32BE(1);
          if (buf.length < 5 + len) break;
          const msg = JSON.parse(buf.subarray(5, 5 + len).toString('utf8') || '{}');
          buf = buf.subarray(5 + len);
          if (flags & 2) {
            // 流结束；还没开始就结束说明出错了（比如额度暂时用完），把原因告诉手机
            if (!sid && msg.error) rejectReady(new Error(sttError(msg.error)));
            continue;
          }
          if (msg.ready) {
            sid = msg.ready.sessionId;
            stt.set(sid, { finals: [], partial: '', done: false, waiters: [] });
            resolveReady(sid);
          } else if (msg.transcription && sid) {
            const s = stt.get(sid);
            if (msg.transcription.isFinal) { s.finals.push(msg.transcription.text || ''); s.partial = ''; }
            else s.partial = msg.transcription.text || '';
            push({ type: 'stt', sid, text: sttText(s), final: false });
          } else if (msg.complete && sid) {
            break;
          }
        }
      }
    } catch (e) { log('语音识别出错', e.message); }
    const s = sid && stt.get(sid);
    if (s) {
      s.done = true;
      push({ type: 'stt', sid, text: sttText(s), final: true });
      s.waiters.forEach(w => w());
      setTimeout(() => stt.delete(sid), 60000);
    }
  })();
  return ready;
}

async function sttChunk(sid, seq, data) {
  await rpc('SendAudioChunk', { sessionId: sid, data: data.toString('base64'), sequenceNumber: seq });
}

async function sttEnd(sid, last) {
  const s = stt.get(sid);
  await rpc('EndAudioSession', { sessionId: sid, ...(last >= 0 ? { lastSequenceNumber: last } : {}) });
  if (s && !s.done) await new Promise(res => { s.waiters.push(res); setTimeout(res, 3000); });
  return s ? sttText(s) : '';
}

function readRaw(req, max = 1 << 20) {
  return new Promise((res, rej) => {
    const chunks = []; let n = 0;
    req.on('data', d => { n += d.length; if (n > max) { rej(new Error('too large')); req.destroy(); } else chunks.push(d); });
    req.on('end', () => res(Buffer.concat(chunks)));
    req.on('error', rej);
  });
}

// ---------------- 和电脑上的 Antigravity 同步：当前选的模型、权限设置、已装插件 ----------------
// JetboxSubscribeToState 第一帧就是完整状态，之后每次变化再推一帧。写回用 JetboxWriteState（整份写回，先读后改）。
let jetbox = null; // 最近一次的完整状态 { appState, userConfig }
let jetboxWatching = false;

function syncInfo(st = jetbox) {
  return {
    model: st?.appState?.lastSelectedAgentModel || '',
    permissionPreset: st?.userConfig?.userSettings?.permissionPreset || '',
    plugins: Object.values(st?.userConfig?.plugins || {}).map(x => x?.installedFrom?.id).filter(Boolean),
    voice: voiceSettings(st),
  };
}

function readFrames(resp, onFrame) {
  return (async () => {
    const rd = resp.body.getReader();
    let buf = Buffer.alloc(0);
    for (;;) {
      const { value, done } = await rd.read();
      if (done) return;
      buf = Buffer.concat([buf, Buffer.from(value)]);
      while (buf.length >= 5) {
        const flags = buf[0], len = buf.readUInt32BE(1);
        if (buf.length < 5 + len) break;
        const txt = buf.subarray(5, 5 + len).toString('utf8');
        buf = buf.subarray(5 + len);
        if (!(flags & 2)) onFrame(JSON.parse(txt || '{}'));
      }
    }
  })();
}

/** 常驻订阅：电脑上一改模型/权限/插件，就推给手机。 */
async function watchJetbox() {
  if (jetboxWatching) return;
  jetboxWatching = true;
  for (;;) {
    try {
      if (!ls) await rpc('GetAllCascadeTrajectories', {});
      const r = await fetch(ls.base + SVC + 'JetboxSubscribeToState', {
        method: 'POST',
        headers: { 'Content-Type': 'application/connect+json', 'Connect-Protocol-Version': '1', 'x-codeium-csrf-token': ls.token },
        body: envelope({}),
      });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      await readFrames(r, st => {
        const before = JSON.stringify(syncInfo());
        jetbox = { appState: st.appState || jetbox?.appState, userConfig: st.userConfig || jetbox?.userConfig };
        const now = syncInfo();
        if (JSON.stringify(now) !== before) push({ type: 'sync', ...now });
      });
    } catch (e) {
      // Node 自带的 fetch 会把 5 分钟没有数据的长连接断掉（UND_ERR_BODY_TIMEOUT），这只是订阅要重连，
      // 不代表 Antigravity 没了；只有连不上（进程退出/重启）时才丢掉连接重新查找
      const idle = e?.cause?.code === 'UND_ERR_BODY_TIMEOUT' || /terminated|body timeout/i.test(String(e?.message));
      if (!idle) ls = null;
    }
    await new Promise(res => setTimeout(res, 3000));
  }
}

async function freshJetbox() {
  // 等订阅拿到第一帧（最多 5 秒）
  for (let i = 0; i < 50 && !jetbox; i++) await new Promise(res => setTimeout(res, 100));
  if (!jetbox) throw new Error('读不到 Antigravity 的设置');
  return structuredClone(jetbox);
}

async function writeSync(b) {
  const st = await freshJetbox();
  if (b.model) {
    st.appState = { ...(st.appState || {}), lastSelectedAgentModel: String(b.model) };
    await rpc('JetboxWriteState', { appState: st.appState });
  }
  if (b.permissionPreset) {
    st.userConfig = { ...(st.userConfig || {}), userSettings: { ...(st.userConfig?.userSettings || {}), permissionPreset: String(b.permissionPreset) } };
    await rpc('JetboxWriteState', { userConfig: st.userConfig });
  }
  jetbox = st;
  push({ type: 'sync', ...syncInfo(st) });
}

// ---------------- 连接器：已装插件（带登录授权的）+ MCP 服务 ----------------
async function listConnectors() {
  const st = await freshJetbox();
  const plugins = st.userConfig?.plugins || {};
  const out = [];
  for (const [key, cfg] of Object.entries(plugins)) {
    let manifest = {};
    const dir = cfg.installDir || join(process.env.USERPROFILE || '', '.gemini', 'config', 'plugins', key);
    try { manifest = JSON.parse(readFileSync(join(dir, 'plugin.json'), 'utf8')); } catch {}
    let auth = [];
    try { auth = (await rpc('ListPluginAuthProviders', { plugin: key }, 8000)).providers || []; } catch {}
    out.push({
      key, kind: 'plugin',
      name: manifest.displayName || manifest.name || key,
      description: manifest.description || '',
      logo: manifest.logo || '',
      enabled: cfg.enabled !== false,
      connected: auth.length ? auth.some(a => a.connected) : null,
      account: auth.map(a => a.identity).filter(Boolean)[0] || '',
      needsAuth: auth.some(a => a.needsAuth && !a.optional),
    });
  }
  let mcp = [];
  try { mcp = (await rpc('GetMcpServerStates', {})).states || []; } catch {}
  for (const x of mcp) {
    out.push({
      key: x.spec?.serverName || x.spec?.name || x.serverInfo?.name || '', kind: 'mcp',
      name: x.spec?.serverName || x.spec?.name || x.serverInfo?.name || '',
      description: (x.tools || []).length ? `${x.tools.length} 个工具` : '', logo: '',
      enabled: !String(x.status || '').includes('DISABLED'), connected: null, account: '', needsAuth: false,
      error: x.error || '',
    });
  }
  return out;
}

async function toggleConnector(b) {
  if (b.kind === 'mcp') return rpc('ToggleMcpServer', { serverName: String(b.key), enabled: !!b.enabled });
  const st = await freshJetbox();
  const cfg = st.userConfig?.plugins?.[b.key];
  if (!cfg) throw Object.assign(new Error('找不到这个插件'), { status: 404 });
  // 启用 = 去掉 enabled 字段（Antigravity 默认就是启用），停用 = 写 false；这样和电脑上点开关的结果一样
  if (b.enabled) delete cfg.enabled; else cfg.enabled = false;
  await rpc('JetboxWriteState', { userConfig: st.userConfig });
}

// ---------------- 推送（SSE） ----------------
const clients = new Set();
let lastList = new Map(); // id -> "status|steps|updated"
let watcher = null;

function push(ev) {
  const line = `data: ${JSON.stringify(ev)}\n\n`;
  for (const c of clients) c.write(line);
}

async function tick() {
  try {
    const list = await listConversations();
    const now = new Map(list.map(c => [c.id, `${c.status}|${c.steps}|${c.updated}|${c.title}|${c.pinned}|${c.archived}|${c.unread}|${c.waiting}`]));
    const changed = list.filter(c => lastList.get(c.id) !== now.get(c.id)).map(c => c.id);
    const removed = [...lastList.keys()].some(k => !now.has(k));
    if (changed.length || removed) push({ type: 'changed', ids: changed });
    lastList = now;
    push({ type: 'alive', ls: true });
  } catch (e) {
    // 只有真的找不到 Antigravity（503）才告诉手机“没开”；超时、数据库被占用等临时错误不算
    push({ type: 'alive', ls: e.status !== 503, error: e.message });
  }
}
function ensureWatcher() {
  if (!watcher && clients.size) watcher = setInterval(tick, 1200);
  if (watcher && !clients.size) { clearInterval(watcher); watcher = null; }
}

// ---------------- HTTP ----------------
function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}
function readBody(req) {
  return new Promise((res, rej) => {
    let n = 0; const chunks = [];
    // 带照片/视频/文件附件，放宽到 64MB
    req.on('data', d => { n += d.length; if (n > 64 << 20) { rej(new Error('附件太大')); req.destroy(); } else chunks.push(d); });
    req.on('end', () => { try { res(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); } catch (e) { rej(e); } });
    req.on('error', rej);
  });
}

const pairFails = new Map();
const logoCache = new Map();

// ---------------- 电脑网络代理（供手机内置浏览器走电脑网络） ----------------
let sysProxyCache = { at: 0, value: null };

function isLocalHost(host) {
  const h = String(host || '').toLowerCase().replace(/^\[|\]$/g, '');
  return h === 'localhost' || h === '::1' || h.startsWith('127.') || h.startsWith('10.') ||
    h.startsWith('192.168.') || /^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(h);
}

function parseProxySpec(spec) {
  if (!spec) return null;
  let s = String(spec).trim();
  if (s.includes('=')) {
    const parts = Object.fromEntries(s.split(';').map(p => p.split('=')).filter(a => a.length === 2));
    s = parts.https || parts.http || '';
  }
  s = s.replace(/^https?:\/\//i, '').replace(/\/.*$/, '').trim();
  const idx = s.lastIndexOf(':');
  if (idx <= 0) return null;
  const host = s.slice(0, idx).replace(/^\[|\]$/g, '');
  const port = Number(s.slice(idx + 1));
  return host && port > 0 && port < 65536 ? { host, port } : null;
}

function getUpstreamProxy() {
  const envP = parseProxySpec(process.env.HTTPS_PROXY || process.env.HTTP_PROXY || process.env.https_proxy || process.env.http_proxy);
  if (envP) return Promise.resolve(envP);
  if (Date.now() - sysProxyCache.at < 15000) return Promise.resolve(sysProxyCache.value);
  return new Promise(resolve => {
    execFile('reg.exe', ['query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'], { windowsHide: true }, (err, out = '') => {
      let val = null;
      if (!err) {
        const en = /ProxyEnable\s+REG_DWORD\s+0x([0-9a-f]+)/i.exec(out);
        const srv = /ProxyServer\s+REG_SZ\s+([^\r\n]+)/i.exec(out);
        if (en && parseInt(en[1], 16) === 1 && srv) val = parseProxySpec(srv[1]);
      }
      sysProxyCache = { at: Date.now(), value: val };
      resolve(val);
    });
  });
}

async function handleHttpProxy(req, res) {
  let target;
  try { target = new URL(req.url); } catch { res.writeHead(400); return res.end('bad proxy url'); }
  // 不许借代理访问本服务自己（否则手机里的网页能绕过“只限本机”的配对页）
  const th = target.hostname.toLowerCase().replace(/^\[|\]$/g, '');
  if ((th === 'localhost' || th === '::1' || th.startsWith('127.')) && (Number(target.port) || 80) === PORT) {
    res.writeHead(403); return res.end('forbidden');
  }
  const up = isLocalHost(target.hostname) ? null : await getUpstreamProxy();
  const headers = { ...req.headers };
  delete headers['proxy-connection'];
  delete headers['proxy-authorization'];
  const opts = up
    ? { host: up.host, port: up.port, method: req.method, path: req.url, headers }
    : { host: target.hostname, port: Number(target.port) || 80, method: req.method, path: target.pathname + target.search, headers };
  const preq = http.request(opts, pres => {
    res.writeHead(pres.statusCode || 502, pres.headers);
    pres.pipe(res);
  });
  preq.on('error', e => {
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('proxy error: ' + e.message);
  });
  req.pipe(preq);
}

const server = http.createServer(async (req, res) => {
  const ip = req.socket.remoteAddress || '';
  if (!allowedRemote(ip)) { res.destroy(); return; }
  if (/^http:\/\//i.test(req.url || '')) {
    if (!proxyAllowed(ip)) { res.writeHead(407, { 'Content-Type': 'text/plain' }); return res.end('not paired'); }
    return handleHttpProxy(req, res);
  }
  const url = new URL(req.url, 'http://x');
  const path = url.pathname;
  try {
    if (path === '/api/ping') return send(res, 200, { ok: true, name: 'AG Remote', version: APP_VERSION });
    if (path === '/' || path === '/pair' || path === '/pair/' || path.startsWith('/admin/')) {
      if (!adminOk(req, ip)) return send(res, 403, { error: 'This page can only be opened on this computer.' });
      if (path === '/') { res.writeHead(302, { Location: '/pair' }); return res.end(); }
      if ((await handleAdmin(req, res, path)) !== false) return;
      return send(res, 404, { error: 'not found' });
    }
    if (path === '/dl' && req.method === 'GET') {
      const apk = apkPath();
      const sizeMb = existsSync(apk) ? (statSync(apk).size / 1048576).toFixed(2) : '?';
      const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>AGRemote.apk</title>
<style>body{font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;background:#111;color:#fff}
.card{background:#1e1e1e;padding:28px 24px;border-radius:20px;width:min(340px,88vw);text-align:center;box-shadow:0 8px 32px #0008}
h2{margin:0 0 8px;font-size:20px}p{margin:0 0 20px;color:#aaa;font-size:14px}
button,a.btn{display:block;width:100%;box-sizing:border-box;padding:14px;border-radius:14px;border:0;background:#4f8cff;color:#fff;font-size:16px;font-weight:600;text-decoration:none;cursor:pointer;margin-bottom:10px}
a.sec{background:#2d2d2d;color:#ddd;font-size:14px;font-weight:500}
#st{font-size:13px;color:#8ab4ff;min-height:20px;margin-top:8px}</style></head>
<body><div class="card"><h2>AGRemote.apk</h2><p>大小：${sizeMb} MB</p>
<button id="b" onclick="dl()">下载 AGRemote.apk</button>
<a class="btn sec" href="/AGRemote.apk" download="AGRemote.apk">直接链接下载（备用）</a>
<div id="st"></div></div>
<script>
async function dl(){
  const b=document.getElementById('b'), st=document.getElementById('st');
  b.disabled=true; st.textContent='正在通过浏览器下载…';
  try{
    const r=await fetch('/AGRemote.apk?t='+Date.now());
    if(!r.ok) throw new Error('HTTP '+r.status);
    const total=+r.headers.get('content-length')||0, reader=r.body.getReader(), chunks=[];
    let got=0;
    while(true){
      const {done,value}=await reader.read();
      if(done) break;
      chunks.push(value); got+=value.length;
      st.textContent=total?('下载中 '+Math.round(got*100/total)+'% ('+(got/1048576).toFixed(2)+' MB)'):('已下载 '+(got/1048576).toFixed(2)+' MB');
    }
    const blob=new Blob(chunks,{type:'application/vnd.android.package-archive'});
    const u=URL.createObjectURL(blob), a=document.createElement('a');
    a.href=u; a.download='AGRemote.apk'; document.body.appendChild(a); a.click(); a.remove();
    st.textContent='下载完成！请在浏览器下载记录里点击 AGRemote.apk 安装';
  }catch(e){ st.textContent='失败：'+e.message; }
  b.disabled=false;
}
</script></body></html>`;
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      return res.end(html);
    }
    if ((path === '/apk' || path === '/AGRemote.apk' || path === '/agremote.apk') && (req.method === 'GET' || req.method === 'HEAD')) {
      const apk = apkPath();
      if (!existsSync(apk)) return send(res, 404, { error: '找不到安装包' });
      const data = readFileSync(apk);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="AGRemote.apk"',
        'Content-Length': data.length,
        'Accept-Ranges': 'bytes',
        'Cache-Control': 'no-store',
      });
      return req.method === 'HEAD' ? res.end() : res.end(data);
    }

    if (path === '/api/pair' && req.method === 'POST') {
      const f = pairFails.get(ip) || { n: 0, until: 0 };
      if (Date.now() < f.until) return send(res, 429, { error: '尝试太多次，请 10 分钟后再试' });
      const b = await readBody(req);
      if (String(b.code || '').trim() !== config.pairCode) {
        f.n++; if (f.n >= 5) { f.n = 0; f.until = Date.now() + 600000; }
        pairFails.set(ip, f);
        return send(res, 403, { error: '配对码不对' });
      }
      const token = randomBytes(32).toString('hex');
      config.devices.push({ token, name: String(b.name || '手机').slice(0, 60), pairedAt: new Date().toISOString() });
      saveConfig();
      log(`新设备已配对：${b.name || '手机'}`);
      return send(res, 200, { token });
    }

    const auth = (req.headers.authorization || '').replace(/^Bearer\s+/i, '') || url.searchParams.get('token');
    const dev = deviceOf(auth);
    if (!dev) return send(res, 401, { error: '未配对' });
    trustedIps.set(normIp(ip), Date.now());
    deviceSeen.set(deviceId(dev), { at: Date.now(), ip: normIp(ip) });

    if (path === '/api/events') {
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
      res.write(': hi\n\n');
      if (jetbox) res.write(`data: ${JSON.stringify({ type: 'sync', ...syncInfo() })}\n\n`);
      clients.add(res);
      ensureWatcher();
      tick();
      const did = deviceId(dev);
      deviceLive.set(did, (deviceLive.get(did) || 0) + 1);
      req.on('close', () => {
        clients.delete(res); ensureWatcher();
        deviceLive.set(did, Math.max(0, (deviceLive.get(did) || 1) - 1));
        deviceSeen.set(did, { at: Date.now(), ip: normIp(ip) }); // 断开时刷新“最近在线”
      });
      return;
    }
    if (path === '/api/conversations' && req.method === 'GET') return send(res, 200, { conversations: await listConversations() });
    if (path === '/api/models') return send(res, 200, await listModels());
    if (path === '/api/translate' && req.method === 'POST') {
      const b = await readBody(req);
      return send(res, 200, { text: await translate(String(b.text || ''), String(b.to || 'zh-CN')) });
    }
    if (path === '/api/stt/start' && req.method === 'POST') {
      const b = await readBody(req);
      return send(res, 200, { sid: await sttStart(b.languages) });
    }
    if (path === '/api/stt/chunk' && req.method === 'POST') {
      await sttChunk(url.searchParams.get('sid'), +url.searchParams.get('seq') || 0, await readRaw(req));
      return send(res, 200, { ok: true });
    }
    if (path === '/api/stt/end' && req.method === 'POST') {
      const b = await readBody(req);
      return send(res, 200, { text: await sttEnd(String(b.sid || ''), Number.isInteger(b.last) ? b.last : -1) });
    }
    if (path === '/api/account') return send(res, 200, await account());
    if (path === '/api/skills') {
      const r = await rpc('GetAllSkills', {});
      return send(res, 200, { skills: (r.skills || []).map(k => ({ name: k.name || '', description: k.description || '', builtin: !!k.isBuiltin })) });
    }
    if (path === '/api/mcp' && req.method === 'GET') {
      const r = await rpc('GetMcpServerStates', {});
      return send(res, 200, { loading: !!r.isLoading, servers: (r.states || []).map(x => ({
        name: x.spec?.serverName || x.spec?.name || x.serverInfo?.name || '',
        status: short(x.status, 'MCP_SERVER_STATUS_'),
        error: x.error || '',
        tools: (x.tools || []).length,
        enabled: !String(x.status || '').includes('DISABLED'),
      })) });
    }
    if (path === '/api/mcp/toggle' && req.method === 'POST') {
      const b = await readBody(req);
      await rpc('ToggleMcpServer', { serverName: String(b.name || ''), enabled: !!b.enabled });
      return send(res, 200, { ok: true });
    }
    if (path === '/api/i18n') return send(res, 200, { zh: i18nTable() });
    if (path === '/api/connectors' && req.method === 'GET') return send(res, 200, { connectors: await listConnectors() });
    if (path === '/api/connectors/toggle' && req.method === 'POST') { await toggleConnector(await readBody(req)); return send(res, 200, { ok: true }); }
    if (path === '/api/sync' && req.method === 'GET') { await freshJetbox(); return send(res, 200, syncInfo()); }
    if (path === '/api/sync' && req.method === 'POST') { await writeSync(await readBody(req)); return send(res, 200, { ok: true }); }
    if (path === '/api/voice/config' && req.method === 'GET') return send(res, 200, await voiceConfig());
    if (path === '/api/voice/settings' && req.method === 'POST') return send(res, 200, await writeVoiceSettings(await readBody(req)));
    if (path === '/api/voice/sample' && req.method === 'GET') {
      // 音色试听：转给 language_server 的 /voices/<名字>_<1-3>.mp3
      const name = String(url.searchParams.get('name') || '').toLowerCase();
      const n = Math.min(3, Math.max(1, Number(url.searchParams.get('n')) || 1));
      if (!/^[a-z]{2,16}$/.test(name)) return send(res, 400, { error: 'bad name' });
      if (!ls) await rpc('GetCascadeModelConfigData', {});
      const r = await fetch(`${ls.base}/voices/${name}_${n}.mp3`);
      if (!r.ok) return send(res, 404, { error: 'no sample' });
      const data = Buffer.from(await r.arrayBuffer());
      res.writeHead(200, { 'Content-Type': 'audio/mpeg', 'Content-Length': data.length, 'Cache-Control': 'max-age=86400' });
      return res.end(data);
    }
    if (path === '/api/drafts' && req.method === 'GET') return send(res, 200, { drafts });
    if (path === '/api/drafts' && req.method === 'POST') {
      const b = await readBody(req);
      const cid = String(b.cid || '__new__').slice(0, 100);
      const text = String(b.text || '');
      if (text) drafts[cid] = text;
      else delete drafts[cid];
      saveDrafts();
      push({ type: 'draft', cid, text });
      return send(res, 200, { ok: true });
    }

    // 项目
    if (path === '/api/projects' && req.method === 'GET') return send(res, 200, { projects: await listProjects() });
    if (path === '/api/projects' && req.method === 'POST') {
      const b = await readBody(req);
      return send(res, 200, { id: await createProject(b.name, b.folder) });
    }
    const pm = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/(\w+)$/);
    if (pm && req.method === 'POST') {
      const [, pid, act] = pm;
      const b = await readBody(req);
      if (act === 'delete') { await rpc('DeleteProject', { id: pid }); return send(res, 200, { ok: true }); }
      if (act === 'rename') {
        const full = await readProject(pid);
        full.name = String(b.name || full.name).slice(0, 100);
        await rpc('UpdateProject', { project: full, rename: true });
        return send(res, 200, { ok: true });
      }
    }
    // 对话移到某个项目（projectId 为空 = 移出项目）
    if (path === '/api/move' && req.method === 'POST') {
      const b = await readBody(req);
      await setConversationProject(String(b.cid), String(b.projectId || ''));
      push({ type: 'changed', ids: [] });
      return send(res, 200, { ok: true });
    }

    // 自动化
    if (path === '/api/automations' && req.method === 'GET') return send(res, 200, { automations: await listAutomations() });
    if (path === '/api/automations' && req.method === 'POST') return send(res, 200, { id: await saveAutomation(await readBody(req)) });
    const am = path.match(/^\/api\/automations\/([\w.\/-]+)\/(enable|disable|delete|restart)$/);
    if (am && req.method === 'POST') {
      await rpc('ManageSidecar', { sidecarId: am[1], action: 'SIDECAR_ACTION_' + am[2].toUpperCase() });
      return send(res, 200, { ok: true });
    }

    // 插件：已安装的 + 插件市场
    if (path === '/api/plugins' && req.method === 'GET') {
      const [inst, market] = await Promise.all([rpc('GetAllPlugins', {}), rpc('GetAvailableCascadePlugins', {}, 30000)]);
      const installed = (inst.plugins || []).map(x => ({
        id: x.pluginId || x.id || x.name || '',
        title: x.title || x.displayName || x.name || x.pluginId || x.id || '',
        description: x.description || '',
        enabled: x.disabled !== true && x.forceDisabled !== true,
      }));
      const ids = new Set(installed.map(x => x.id));
      return send(res, 200, {
        installed,
        market: (market.plugins || []).map(x => ({
          id: x.id || '', title: x.title || x.id || '', description: x.description || '', link: x.link || '',
          trust: short(x.trustLevel, ''), kind: x.remote ? 'remote' : (x.local ? 'local' : ''), installed: ids.has(x.id),
        })),
      });
    }
    const plm = path.match(/^\/api\/plugins\/(install|delete)$/);
    if (plm && req.method === 'POST') {
      const b = await readBody(req);
      if (plm[1] === 'install') await rpc('InstallCascadePlugin', { pluginId: String(b.id || '') }, 60000);
      else await rpc('DeletePlugin', { pluginId: String(b.id || '') });
      log(`插件${plm[1] === 'install' ? '安装' : '删除'}：${b.id}`);
      return send(res, 200, { ok: true });
    }

    // 插件市场（跟电脑上 “自定义扩展” 页一样的数据）：分类、推荐、全部插件、哪些已装
    if (path === '/api/market' && req.method === 'GET') {
      const q = String(url.searchParams.get('q') || '');
      const [search, featured, states] = await Promise.all([
        rpc('SearchMarketplace', q ? { query: q } : {}, 30000),
        q ? Promise.resolve({}) : rpc('GetMarketplaceFeatured', {}, 30000).catch(() => ({})),
        rpc('GetCustomizationStates', {}).catch(() => ({})),
      ]);
      const all = [...(search.customizations || []), ...(featured.customizations || [])];
      const seen = new Set();
      const items = all.filter(x => x.id && !seen.has(x.id) && seen.add(x.id)).map(x => ({
        id: x.id, name: x.displayName || x.id.split('/').pop(), description: x.description || '',
        logo: x.logo || '', author: x.author?.name || x.author || '', version: x.version || '',
        homepage: x.homepage || x.repository || '',
      }));
      // 已安装：自定义扩展状态里类型是插件的，按名字（id 最后一段）对上
      const installedIds = new Set(syncInfo().plugins);
      const installedNames = new Set((states.states || []).filter(x => /PLUGIN/.test(x.type || '')).map(x => String(x.name || '').toLowerCase()));
      return send(res, 200, {
        featured: (featured.featured?.[0]?.memberIds || []),
        groups: (search.groups || []).map(g => ({ id: g.id, name: g.displayName || g.id, description: g.description || '', members: g.memberIds || [] })),
        items,
        installed: items.filter(x => installedIds.has(x.id) || installedNames.has(x.id.split('/').pop().toLowerCase())).map(x => x.id),
      });
    }
    if (path === '/api/market/install' && req.method === 'POST') {
      const b = await readBody(req);
      const r = await rpc('InstallCustomization', { id: String(b.id || '') }, 120000);
      log(`安装插件 ${b.id}：${r.success ? '成功' : r.message}`);
      if (r.success === false) return send(res, 500, { error: r.message || '安装失败' });
      return send(res, 200, { ok: true, message: r.message || '' });
    }
    if (path === '/api/market/uninstall' && req.method === 'POST') {
      const b = await readBody(req);
      await rpc('DeletePlugin', { pluginId: String(b.id || '').split('/').pop() });
      log(`删除插件 ${b.id}`);
      return send(res, 200, { ok: true });
    }
    // 插件图标：由电脑去取（走电脑网络 + 系统代理），只允许 https 图片
    if (path === '/api/logo') {
      const u = String(url.searchParams.get('u') || '');
      if (!/^https:\/\//.test(u)) return send(res, 400, { error: 'bad url' });
      let hit = logoCache.get(u);
      if (!hit) {
        const r = await pcFetch(u, { timeoutMs: 15000 });
        const type = r.contentType || '';
        if (!r.ok || !/^image\//.test(type)) return send(res, 404, { error: 'no image' });
        hit = { type, data: r.data };
        if (hit.data.length < 2 << 20) { if (logoCache.size > 300) logoCache.clear(); logoCache.set(u, hit); }
      }
      res.writeHead(200, { 'Content-Type': hit.type, 'Content-Length': hit.data.length, 'Cache-Control': 'max-age=604800' });
      return res.end(hit.data);
    }

    // 自定义扩展（skills、rules、workflows、MCP…）
    if (path === '/api/customizations') {
      const r = await rpc('GetCustomizationStates', {});
      return send(res, 200, { items: (r.states || []).map(x => ({
        type: short(x.type, 'REFRESH_CUSTOMIZATION_TYPE_'),
        name: x.name || '',
        path: x.path || '',
        scope: short(x.scope, 'SCOPE_'),
        status: short(x.status, 'STATUS_'),
        error: x.error || x.errorMessage || '',
      })) });
    }
    if (path === '/api/media') {
      const cid = String(url.searchParams.get('cid') || '');
      const ref = String(url.searchParams.get('ref') || '');
      // 1) 回复里内嵌的数据（没有文件路径的）
      if (cid && ref) {
        const [i, k] = ref.split(':').map(Number);
        const r = await rpc('GetCascadeTrajectorySteps', { cascadeId: cid, stepOffset: i }, 30000);
        const m = r.steps?.[0]?.plannerResponse?.media?.[k];
        if (!m?.inlineData) return send(res, 404, { error: '找不到附件' });
        const data = Buffer.from(m.inlineData, 'base64');
        res.writeHead(200, { 'Content-Type': m.mimeType || 'application/octet-stream', 'Content-Length': data.length, 'Cache-Control': 'max-age=86400' });
        return res.end(data);
      }
      // 2) 文件：Antigravity 对话目录（brain）里的，或者这个对话的消息里引用到的
      const f = String(url.searchParams.get('path') || '').replace(/\\/g, '/');
      const norm = f.toLowerCase();
      const root = join(process.env.USERPROFILE || '', '.gemini', 'antigravity', 'brain').replace(/\\/g, '/').toLowerCase();
      let ok = norm.startsWith(root + '/') && !norm.includes('/.system_generated/') && !norm.includes('..');
      if (!ok && cid) {
        const { steps } = await getSteps(cid, 0);
        ok = steps.some(st =>
          (st.attachments || []).some(x => x.path && x.path.toLowerCase() === norm) ||
          (st.kind === 'file' && st.text && st.text.replace(/\\/g, '/').toLowerCase() === norm) ||
          fileRefs(st.text).some(x => x.path.toLowerCase() === norm));
      }
      if (!ok) return send(res, 403, { error: '不允许读这个文件' });
      if (!existsSync(f) || !statSync(f).isFile()) return send(res, 404, { error: '文件不存在' });
      // 先看大小再读，免得把超大文件整个读进内存
      if (statSync(f).size > 50 << 20) return send(res, 413, { error: '文件太大' });
      const data = readFileSync(f);
      res.writeHead(200, { 'Content-Type': mimeOf(f), 'Content-Length': data.length, 'Cache-Control': 'max-age=3600' });
      return res.end(data);
    }
    if (path === '/api/browser/tabs' && req.method === 'GET') {
      const cid = String(url.searchParams.get('cid') || '');
      return send(res, 200, await getPcBrowserTabs(cid));
    }
    if (path === '/api/browser/tabs/close' && req.method === 'POST') {
      const b = await readBody(req);
      return send(res, 200, await closePcBrowserTab(String(b.tabCid || b.cid || ''), String(b.tabId || ''), String(b.curCid || b.cid || '')));
    }
    if (path === '/api/browser/screen' && req.method === 'GET') {
      const mode = url.searchParams.get('mode') === 'pane' ? 'pane' : 'full';
      const r = await captureAntigravityScreen(mode);
      if (!r.ok) return send(res, 404, { error: r.reason || '窗口不可见' });
      res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Content-Length': r.data.length, 'Cache-Control': 'no-store' });
      return res.end(r.data);
    }
    if (path === '/api/browser/activity' && req.method === 'GET') {
      const cid = String(url.searchParams.get('cid') || '');
      return send(res, 200, await getBrowserActivity(cid));
    }
    if (path === '/api/browser/input' && req.method === 'POST') {
      return send(res, 200, await sendAntigravityInput(await readBody(req)));
    }
    if (path === '/api/browser/cursor' && req.method === 'GET') {
      const last = String(url.searchParams.get('last') || '');
      return send(res, 200, await sendAntigravityInput({ action: 'cursor', text: last }));
    }
    if (path === '/api/conversations' && req.method === 'POST') {
      const b = await readBody(req);
      if (!String(b.text || '').trim()) return send(res, 400, { error: '内容为空' });
      return send(res, 200, { id: await newConversation(String(b.text), b.model || '', b.attachments, b.permissionPreset || '', b.projectId || '', b.mentions) });
    }
    const m = path.match(/^\/api\/conversations\/([0-9a-f-]{36})(?:\/(\w+))?$/);
    if (m) {
      const [, id, action] = m;
      if (!action && req.method === 'GET') {
        const offset = Math.max(0, parseInt(url.searchParams.get('offset') || '0', 10) || 0);
        const list = await listConversations();
        const c = list.find(x => x.id === id);
        const [{ steps, model }, queued] = await Promise.all([
          getSteps(id, offset),
          c && c.status !== 'idle' ? getPendingMessages(id) : Promise.resolve([]),
        ]);
        return send(res, 200, { conversation: c || null, steps, model, queued });
      }
      if (action === 'overview' && req.method === 'GET') {
        return send(res, 200, await getOverview(id));
      }
      if (action === 'taskLog' && req.method === 'GET') {
        const raw = String(url.searchParams.get('taskId') || '').split('/').pop() || '';
        if (!/^task-\d+$/.test(raw)) return send(res, 400, { error: '无效任务 ID' });
        const f = join(process.env.USERPROFILE || '', '.gemini', 'antigravity', 'brain', id, '.system_generated', 'tasks', `${raw}.log`);
        if (!existsSync(f)) return send(res, 200, { log: '' });
        const buf = readFileSync(f);
        const text = (buf.length > 65536 ? buf.subarray(buf.length - 65536) : buf).toString('utf8');
        return send(res, 200, { log: text });
      }
      if (req.method === 'POST') {
        const b = await readBody(req);
        if (action === 'send') {
          if (!String(b.text || '').trim()) return send(res, 400, { error: '内容为空' });
          let delivery = String(b.delivery || '');
          if (!delivery) {
            const cur = (await listConversations()).find(x => x.id === id);
            if (cur && cur.status !== 'idle') delivery = 'queue';
          }
          await sendMessage(id, String(b.text), String(b.model || ''), b.attachments, b.mentions, delivery);
          push({ type: 'changed', ids: [id] });
          return send(res, 200, { ok: true });
        }
        if (action === 'cancel') { await cancel(id); return send(res, 200, { ok: true }); }
        if (action === 'sendQueued') {
          const mid = String(b.messageId || '');
          const text = String(b.text || '');
          if (mid) await rpc('DeleteAgentMessage', { recipient: id, messageId: mid });
          if (text.trim()) await sendMessage(id, text, String(b.model || ''), [], [], 'now');
          push({ type: 'changed', ids: [id] });
          return send(res, 200, { ok: true });
        }
        if (action === 'deleteQueued') {
          const mid = String(b.messageId || '');
          if (mid) await rpc('DeleteAgentMessage', { recipient: id, messageId: mid });
          push({ type: 'changed', ids: [id] });
          return send(res, 200, { ok: true });
        }
        if (action === 'annotate') { await annotate(id, b); push({ type: 'changed', ids: [id] }); return send(res, 200, { ok: true }); }
        if (action === 'delete') {
          await rpc('DeleteCascadeTrajectory', { cascadeId: id });
          log(`已删除对话 ${id}`);
          push({ type: 'changed', ids: [] });
          return send(res, 200, { ok: true });
        }
        if (action === 'respond') { await respond(id, b); return send(res, 200, { ok: true }); }
        if (action === 'feedback') {
          const ack = b.vote === 1 ? 'ACKNOWLEDGEMENT_TYPE_ACCEPT'
            : b.vote === -1 ? 'ACKNOWLEDGEMENT_TYPE_REJECT' : 'ACKNOWLEDGEMENT_TYPE_CLEARED';
          try {
            await rpc('RecordInteractiveCascadeFeedback', {
              cascadeId: id, stepIndex: Number(b.stepIndex) || 0, chunkIndex: 0, acknowledgementType: ack,
            });
          } catch (e) { log('反馈上报跳过', e.message); }
          return send(res, 200, { ok: true });
        }
      }
    }
    send(res, 404, { error: 'not found' });
  } catch (e) {
    log('错误', path, e.message);
    send(res, e.status === 503 ? 503 : 500, { error: e.message });
  }
});

// ---------------- 实时语音通话（Antigravity 2.35+：language_server 的 /voice/ws） ----------------
// 手机连 /api/voice/ws，这里核对令牌后把 TCP 原样接到 language_server 的 /voice/ws（不解包音频，几乎不增加延迟）。
// 协议：子协议 jetski-voice-proto，二进制 protobuf；上行 VoiceClientMessage（mic_audio = 16 kHz s16le），
// 下行 VoiceServerMessage（voice_audio = 24 kHz s16le、识别文字、AI 说的文字、打断等）。播放速度由服务端处理。
const VOICE_SPEEDS = [0.75, 1, 1.25, 1.5, 1.75, 2];
const VOICE_FALLBACK = ['Fola', 'Arlo', 'Cleo', 'Finn', 'Gero', 'Lora', 'Neno', 'Olin', 'Tari'].map(name => ({ name, tone: '' }));
let uiMeta = null; // { at, voices: [{name, tone}], models: Map(name -> number) }，从 Antigravity 自己的界面代码里读

/** 极简 protobuf 读取：[{no, wt, val}]，只给解析内嵌的文件描述符用。 */
function pbParse(buf) {
  const out = []; let i = 0;
  const rv = () => { let r = 0, s = 0, c; do { c = buf[i++]; r += (c & 127) * 2 ** s; s += 7; } while (c & 128 && i < buf.length); return r; };
  while (i < buf.length) {
    const tag = rv(), no = Math.floor(tag / 8), wt = tag & 7;
    if (wt === 0) out.push({ no, wt, val: rv() });
    else if (wt === 2) { const l = rv(); out.push({ no, wt, val: buf.subarray(i, i + l) }); i += l; }
    else if (wt === 1) i += 8; else if (wt === 5) i += 4; else break;
  }
  return out;
}

/** 从 language_server 提供的界面脚本里读出：音色列表、模型枚举（名字 → 数字，语音通道要数字）。 */
async function loadUiMeta() {
  if (uiMeta && Date.now() - uiMeta.at < 3600_000) return uiMeta;
  if (!ls) await rpc('GetCascadeModelConfigData', {});
  const meta = { at: Date.now(), voices: [], models: new Map() };
  try {
    const r = await fetch(ls.base + '/main.js', { headers: { 'x-codeium-csrf-token': ls.token } });
    const js = await r.text();
    for (const m of js.matchAll(/\{name:"([A-Z][a-z]{1,15})",tone:"([^"]{1,60})",palette:/g)) {
      if (!meta.voices.some(v => v.name === m[1])) meta.voices.push({ name: m[1], tone: m[2] });
    }
    // 界面里内嵌了 protobuf 文件描述符（base64）；找 codeium_common.proto 里的 enum Model
    for (const m of js.matchAll(/"(C[A-Za-z0-9+/]{400,}={0,2})"/g)) {
      const head = Buffer.from(m[1].slice(0, 200), 'base64').toString('latin1');
      if (!head.includes('codeium_common')) continue;
      const file = pbParse(Buffer.from(m[1], 'base64'));
      for (const e of file.filter(f => f.no === 5)) {           // FileDescriptorProto.enum_type
        const ef = pbParse(e.val);
        if (Buffer.from(ef.find(f => f.no === 1)?.val || []).toString() !== 'Model') continue;
        for (const v of ef.filter(f => f.no === 2)) {            // EnumDescriptorProto.value
          const vf = pbParse(v.val);
          const name = Buffer.from(vf.find(f => f.no === 1)?.val || []).toString();
          const num = vf.find(f => f.no === 2)?.val;
          if (name && typeof num === 'number') meta.models.set(name, num);
        }
      }
      if (meta.models.size) break;
    }
  } catch (e) { log('读取语音/模型信息失败', e.message); }
  if (!meta.voices.length) meta.voices = VOICE_FALLBACK;
  uiMeta = meta;
  return meta;
}

/** 模型枚举名 → 数字；读不到表时按 MODEL_PLACEHOLDER_Mxxx = 1000 + xxx 的规律，再不行给 0（服务端用默认模型）。 */
async function modelNumber(name) {
  if (!name) return 0;
  if (/^\d+$/.test(name)) return Number(name);
  const meta = await loadUiMeta();
  if (meta.models.has(name)) return meta.models.get(name);
  const m = /^MODEL_PLACEHOLDER_M(\d+)$/.exec(name);
  return m ? 1000 + Number(m[1]) : 0;
}

function voiceSettings(st = jetbox) {
  const us = st?.userConfig?.userSettings || {};
  return { voiceName: us.voiceName || '', voiceSpeed: Number(us.voiceSpeed) || 1, voiceVolume: us.voiceVolume ?? 100 };
}

async function voiceConfig() {
  let health = null;
  try {
    if (!ls) await rpc('GetCascadeModelConfigData', {});
    const r = await fetch(ls.base + '/voice/health', { signal: AbortSignal.timeout(5000) });
    health = r.ok ? await r.json() : { ready: false, state: `HTTP ${r.status}` };
  } catch (e) { health = { ready: false, state: e.status === 503 ? 'no_antigravity' : 'unsupported' }; }
  const meta = await loadUiMeta().catch(() => ({ voices: VOICE_FALLBACK }));
  await freshJetbox().catch(() => {});
  // 通话中切换模型要发枚举数字：把当前可选模型的数字一起给手机
  const models = {};
  try { for (const m of (await listModels()).models) if (m.model) models[m.model] = await modelNumber(m.model); } catch {}
  return { available: !!health?.ready, state: health?.state || '', voices: meta.voices, speeds: VOICE_SPEEDS, settings: voiceSettings(), models };
}

async function writeVoiceSettings(b) {
  const st = await freshJetbox();
  const us = { ...(st.userConfig?.userSettings || {}) };
  const meta = await loadUiMeta();
  if (typeof b.voiceName === 'string' && meta.voices.some(v => v.name === b.voiceName)) us.voiceName = b.voiceName;
  if (b.voiceSpeed !== undefined && VOICE_SPEEDS.includes(Number(b.voiceSpeed))) us.voiceSpeed = Number(b.voiceSpeed);
  if (b.voiceVolume !== undefined) us.voiceVolume = Math.max(0, Math.min(100, Math.round(Number(b.voiceVolume) || 0)));
  st.userConfig = { ...(st.userConfig || {}), userSettings: us };
  await rpc('JetboxWriteState', { userConfig: st.userConfig });
  jetbox = st;
  push({ type: 'sync', ...syncInfo(st) });
  return voiceSettings(st);
}

/** 手机的 WebSocket 升级请求：/api/voice/ws?conversation_id=&model=&voice=&sample_rate=16000 */
async function handleVoiceUpgrade(req, socket, head) {
  const fail = (code, text) => { try { socket.end(`HTTP/1.1 ${code} ${text}\r\nContent-Length: 0\r\n\r\n`); } catch {} };
  const url = new URL(req.url, 'http://x');
  const dev = deviceOf((req.headers.authorization || '').replace(/^Bearer\s+/i, '') || url.searchParams.get('token'));
  if (!dev) return fail(401, 'Unauthorized');
  try {
    if (!ls) await rpc('GetCascadeModelConfigData', {});
    let conv = url.searchParams.get('conversation_id') || '';
    if (!conv) { // 没有对话就先开一个空对话（和电脑上直接点语音按钮一样）
      const body = { source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT', trajectoryType: 'CORTEX_TRAJECTORY_TYPE_CASCADE' };
      const pid = url.searchParams.get('project_id');
      if (pid) body.projectEnvConfig = { projectId: pid, defaultProjectEnvironment: {} };
      conv = (await rpc('StartCascade', body)).cascadeId;
    }
    const q = new URLSearchParams({ conversation_id: conv, plan_model: String(await modelNumber(url.searchParams.get('model') || '')),
      sample_rate: String(Number(url.searchParams.get('sample_rate')) || 16000) });
    const voice = url.searchParams.get('voice') || voiceSettings().voiceName;
    if (voice) q.set('voice', voice);
    const port = Number(new URL(ls.base).port);
    const up = net.connect(port, '127.0.0.1');
    up.setNoDelay(true); socket.setNoDelay(true);
    up.on('connect', () => {
      up.write([`GET /voice/ws?${q} HTTP/1.1`, `Host: 127.0.0.1:${port}`, 'Upgrade: websocket', 'Connection: Upgrade',
        `Sec-WebSocket-Key: ${req.headers['sec-websocket-key']}`, `Sec-WebSocket-Version: ${req.headers['sec-websocket-version'] || 13}`,
        'Sec-WebSocket-Protocol: jetski-voice-proto', '', ''].join('\r\n'));
      if (head?.length) up.write(head);
      up.pipe(socket); socket.pipe(up);
    });
    const close = () => { up.destroy(); socket.destroy(); };
    up.on('error', close); socket.on('error', close); up.on('close', close); socket.on('close', close);
    deviceSeen.set(deviceId(dev), { at: Date.now(), ip: normIp(socket.remoteAddress || '') });
    log(`语音通话：${dev.name || ''} → 对话 ${conv}`);
  } catch (e) {
    log('语音通话连接失败', e.message);
    fail(e.status === 503 ? 503 : 502, 'Voice Unavailable');
  }
}

server.on('upgrade', (req, socket, head) => {
  if (!allowedRemote(socket.remoteAddress || '')) return socket.destroy();
  if (new URL(req.url, 'http://x').pathname === '/api/voice/ws') return handleVoiceUpgrade(req, socket, head);
  socket.end('HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n');
});

server.on('connect', async (req, clientSocket, head) => {
  clientSocket.pause();
  clientSocket.setNoDelay(true);
  const ip = clientSocket.remoteAddress || '';
  if (!allowedRemote(ip)) { clientSocket.destroy(); return; }
  if (!proxyAllowed(ip)) { clientSocket.write('HTTP/1.1 407 Proxy Authentication Required\r\n\r\n'); clientSocket.destroy(); return; }
  const raw = String(req.url || '');
  const idx = raw.lastIndexOf(':');
  const host = (idx > 0 ? raw.slice(0, idx) : raw).replace(/^\[|\]$/g, '');
  const port = Number(idx > 0 ? raw.slice(idx + 1) : 443) || 443;
  if (!host || (isLocalHost(host) && port === PORT)) {
    clientSocket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
    clientSocket.destroy();
    return;
  }

  const connectDirect = () => {
    const sock = net.connect(port, host, () => {
      sock.setNoDelay(true);
      clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
      if (head && head.length) sock.write(head);
      sock.pipe(clientSocket);
      clientSocket.pipe(sock);
      clientSocket.resume();
    });
    sock.on('error', () => { try { clientSocket.write('HTTP/1.1 502 Bad Gateway\r\n\r\n'); } catch {} clientSocket.destroy(); });
    clientSocket.on('error', () => sock.destroy());
    clientSocket.on('close', () => sock.destroy());
    sock.on('close', () => clientSocket.destroy());
  };

  const up = isLocalHost(host) ? null : await getUpstreamProxy();
  if (!up) return connectDirect();

  const targetAddr = host.includes(':') ? `[${host}]:${port}` : `${host}:${port}`;
  let established = false;
  let fellBack = false;
  const fallbackDirect = () => {
    if (established || fellBack || clientSocket.destroyed) return;
    fellBack = true;
    connectDirect();
  };

  const sock = net.connect(up.port, up.host, () => {
    sock.setNoDelay(true);
    sock.write(`CONNECT ${targetAddr} HTTP/1.1\r\nHost: ${targetAddr}\r\nProxy-Connection: keep-alive\r\n\r\n`);
  });
  let buf = Buffer.alloc(0);
  const onData = chunk => {
    buf = Buffer.concat([buf, chunk]);
    const end = buf.indexOf('\r\n\r\n');
    if (end < 0) return;
    sock.off('data', onData);
    const statusLine = buf.subarray(0, end).toString('latin1').split('\r\n')[0] || '';
    if (!/ 200\b/.test(statusLine)) {
      sock.destroy();
      fallbackDirect();
      return;
    }
    established = true;
    clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
    const rest = buf.subarray(end + 4);
    if (rest.length) clientSocket.write(rest);
    if (head && head.length) sock.write(head);
    sock.pipe(clientSocket);
    clientSocket.pipe(sock);
    clientSocket.resume();
  };
  sock.on('data', onData);
  sock.on('error', () => {
    if (!established) { fallbackDirect(); return; }
    clientSocket.destroy();
  });
  clientSocket.on('error', () => sock.destroy());
  clientSocket.on('close', () => sock.destroy());
  sock.on('close', () => {
    if (!established && !fellBack) { fallbackDirect(); return; }
    if (established) clientSocket.destroy();
  });
});

watchJetbox();
// 端口被占（通常是已经有一个 AG Remote 在跑）：退出码 3，守护脚本看到 3 就不再反复重启
server.on('error', e => {
  if (e.code === 'EADDRINUSE') { log(`端口 ${PORT} 已被占用，可能已经有一个 AG Remote 在运行，本次退出`); process.exit(3); }
  throw e;
});
server.listen(PORT, '0.0.0.0', () => {
  log(`AG Remote 已启动，端口 ${PORT}`);
  log(`配对码：${config.pairCode}（也写在 data\\pairing-code.txt）`);
  log(`配对页（只能在这台电脑上打开）：http://127.0.0.1:${PORT}/pair`);
});
