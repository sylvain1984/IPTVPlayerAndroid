/**
 * IPTV Live Channel Registry — Cloudflare Worker
 *
 * KV namespace: LIVE_CHANNELS  (bind in wrangler.toml)
 * Secret:       BROADCAST_SECRET  (set via `wrangler secret put BROADCAST_SECRET`)
 *
 * Storage: single key "registry" → JSON array of channels
 * This avoids KV list() calls (free tier: 1000/day limit).
 *
 * Endpoints:
 *   GET  /live/channels          → list active channels (TV app calls this)
 *   PUT  /live/channel           → publish / heartbeat a channel (broadcaster)
 *   DELETE /live/channel/:id     → end broadcast (broadcaster)
 */

const REGISTRY_KEY = 'registry';
const CHANNEL_TTL  = 90; // seconds — 11× heartbeat interval (8s); tolerates transient failures

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const { pathname, method } = { pathname: url.pathname, method: request.method };

    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders() });
    }

    // ── GET /live/channels ──────────────────────────────────────────────────
    if (pathname === '/live/channels' && method === 'GET') {
      const channels = await readRegistry(env);
      return json(channels);
    }

    // ── PUT /live/channel  (broadcaster registers / heartbeats) ────────────
    if (pathname === '/live/channel' && method === 'PUT') {
      const authErr = checkAuth(request, env);
      if (authErr) return authErr;

      let body;
      try { body = await request.json(); } catch { return err('Invalid JSON', 400); }

      const id = body.id || crypto.randomUUID();
      const channel = {
        id,
        name:      body.name    || '专属直播',
        roomId:    body.roomId  || 'iptv_private',
        pinHash:   body.pinHash ?? null,
        hostId:    body.hostId  ?? null,
        startedAt: Date.now() / 1000,
      };

      const channels = await readRegistry(env);
      const updated  = channels.filter(c => c.id !== id);
      updated.push(channel);
      await writeRegistry(env, updated);
      return json({ id });
    }

    // ── DELETE /live/channel/:id  (broadcaster ends stream) ────────────────
    if (pathname.startsWith('/live/channel/') && method === 'DELETE') {
      const authErr = checkAuth(request, env);
      if (authErr) return authErr;

      const id = pathname.slice('/live/channel/'.length).trim();
      if (!id) return err('Missing channel id', 400);

      const channels = await readRegistry(env);
      await writeRegistry(env, channels.filter(c => c.id !== id));
      return new Response('OK', { headers: corsHeaders() });
    }

    return err('Not found', 404);
  },
};

// ── KV helpers ───────────────────────────────────────────────────────────────

async function readRegistry(env) {
  const raw = await env.LIVE_CHANNELS.get(REGISTRY_KEY, { type: 'json' });
  if (!Array.isArray(raw)) return [];
  const cutoff = Date.now() / 1000 - CHANNEL_TTL;
  return raw.filter(c => c.startedAt > cutoff);
}

async function writeRegistry(env, channels) {
  await env.LIVE_CHANNELS.put(REGISTRY_KEY, JSON.stringify(channels));
}

// ── helpers ──────────────────────────────────────────────────────────────────

function checkAuth(request, env) {
  const secret = env.BROADCAST_SECRET;
  if (!secret) return err('Server misconfigured: BROADCAST_SECRET missing', 500);
  if (request.headers.get('Authorization') !== `Bearer ${secret}`) {
    return err('Unauthorized', 401);
  }
  return null;
}

function json(data) {
  return new Response(JSON.stringify(data), {
    headers: { 'Content-Type': 'application/json', ...corsHeaders() },
  });
}

function err(msg, status) {
  return new Response(JSON.stringify({ error: msg }), {
    status,
    headers: { 'Content-Type': 'application/json', ...corsHeaders() },
  });
}

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
  };
}
