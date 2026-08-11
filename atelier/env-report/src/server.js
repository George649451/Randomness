// env-report: a single page reporting information about the environment it
// runs in. No dependencies; serves on PORT (default 8080).
//
// Secrets policy: environment VARIABLE VALUES are never rendered except for an
// explicit allowlist of non-sensitive platform metadata. For everything else
// only the variable NAME is listed.

const http = require('http');
const os = require('os');

const PORT = process.env.PORT || 8080;

// Non-sensitive platform metadata that is safe to display with its value.
const SAFE_ENV_ALLOWLIST = [
  'CONTAINER_APP_NAME',
  'CONTAINER_APP_REVISION',
  'CONTAINER_APP_REPLICA_NAME',
  'CONTAINER_APP_ENV_DNS_SUFFIX',
  'CONTAINER_APP_PORT',
  'HOSTNAME',
  'NODE_VERSION',
  'PORT',
  'TZ',
  'LANG',
];

const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));

const fmtBytes = (n) => `${(n / 1024 / 1024).toFixed(0)} MiB`;

const fmtUptime = (secs) => {
  const d = Math.floor(secs / 86400);
  const h = Math.floor((secs % 86400) / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return `${d}d ${h}h ${m}m`;
};

function rows(pairs) {
  return pairs
    .map(([k, v]) => `<tr><th>${escapeHtml(k)}</th><td>${escapeHtml(v)}</td></tr>`)
    .join('\n');
}

function renderPage() {
  const mem = process.memoryUsage();

  const runtime = rows([
    ['App', process.env.CONTAINER_APP_NAME || 'env-report'],
    ['Generated at (UTC)', new Date().toISOString()],
    ['Node.js', process.version],
    ['Process uptime', fmtUptime(process.uptime())],
    ['Process RSS', fmtBytes(mem.rss)],
    ['Heap used / total', `${fmtBytes(mem.heapUsed)} / ${fmtBytes(mem.heapTotal)}`],
    ['PID / UID', `${process.pid} / ${typeof process.getuid === 'function' ? process.getuid() : 'n/a'}`],
  ]);

  const host = rows([
    ['Hostname', os.hostname()],
    ['Platform', `${os.platform()} ${os.release()} (${os.arch()})`],
    ['OS uptime', fmtUptime(os.uptime())],
    ['CPUs', `${os.cpus().length} × ${os.cpus()[0] ? os.cpus()[0].model : 'unknown'}`],
    ['Memory (free / total)', `${fmtBytes(os.freemem())} / ${fmtBytes(os.totalmem())}`],
    ['Load average (1/5/15m)', os.loadavg().map((x) => x.toFixed(2)).join(' / ')],
  ]);

  const safeEnv = rows(
    SAFE_ENV_ALLOWLIST.filter((k) => process.env[k] !== undefined)
      .map((k) => [k, process.env[k]])
  );

  const otherEnvNames = Object.keys(process.env)
    .filter((k) => !SAFE_ENV_ALLOWLIST.includes(k))
    .sort();

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>env-report</title>
<style>
  :root { color-scheme: light dark; }
  body { font: 15px/1.5 system-ui, sans-serif; margin: 2rem auto; max-width: 52rem; padding: 0 1rem; }
  h1 { font-size: 1.4rem; }
  h2 { font-size: 1.05rem; margin-top: 2rem; }
  table { border-collapse: collapse; width: 100%; }
  th, td { text-align: left; padding: .35rem .6rem; border-bottom: 1px solid color-mix(in srgb, currentColor 20%, transparent); vertical-align: top; }
  th { width: 16rem; font-weight: 600; white-space: nowrap; }
  td { font-family: ui-monospace, monospace; word-break: break-all; }
  p.note { opacity: .7; font-size: .85rem; }
</style>
</head>
<body>
<h1>Environment report</h1>
<p class="note">Live snapshot of the container this page is served from. Refresh to update.</p>

<h2>Runtime</h2>
<table>${runtime}</table>

<h2>Host / container</h2>
<table>${host}</table>

<h2>Platform metadata (allowlisted env vars)</h2>
<table>${safeEnv || '<tr><td>none present</td></tr>'}</table>

<h2>Other environment variables (${otherEnvNames.length}, names only)</h2>
<table>${rows(otherEnvNames.map((k) => [k, '(value hidden)']))}</table>

<p class="note">Values of non-allowlisted variables are intentionally hidden — they may contain secrets.</p>
</body>
</html>`;
}

const server = http.createServer((req, res) => {
  if (req.url === '/healthz') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(renderPage());
});

server.listen(PORT, () => {
  console.log(`env-report listening on :${PORT}`);
});
