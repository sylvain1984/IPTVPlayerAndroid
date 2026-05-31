/**
 * IPTV Live Channel Registry — Cloudflare Worker
 *
 * KV namespace: LIVE_CHANNELS  (bind in wrangler.toml)
 * Secret:       BROADCAST_SECRET  (set via `wrangler secret put BROADCAST_SECRET`)
 *
 * Endpoints:
 *   GET  /live/channels          → list active channels (TV app calls this)
 *   PUT  /live/channel           → publish / heartbeat a channel (broadcaster)
 *   DELETE /live/channel/:id     → end broadcast (broadcaster)
 */

const CHANNEL_TTL = 28800; // 8 hours — KV auto-expires stale entries
const KV_PREFIX   = 'ch:';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const { pathname, method } = { pathname: url.pathname, method: request.method };

    // ── CORS preflight ──────────────────────────────────────────────────────
    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders() });
    }

    // ── GET /live/channels ──────────────────────────────────────────────────
    if (pathname === '/live/channels' && method === 'GET') {
      const list = await env.LIVE_CHANNELS.list({ prefix: KV_PREFIX });
      const channels = (
        await Promise.all(list.keys.map(k => env.LIVE_CHANNELS.get(k.name, { type: 'json' })))
      ).filter(Boolean);
      return json(channels);
    }

    // ── PUT /live/channel  (broadcaster registers / heartbeats) ────────────
    if (pathname === '/live/channel' && method === 'PUT') {
      const authErr = checkAuth(request, env);
      if (authErr) return authErr;

      let body;
      try { body = await request.json(); } catch { return err('Invalid JSON', 400); }

      const id      = body.id || crypto.randomUUID();
      const channel = {
        id,
        name:      body.name     || '专属直播',
        roomId:    body.roomId   || 'iptv_private',
        pinHash:   body.pinHash  ?? null,
        hostId:    body.hostId   ?? null,
        startedAt: Date.now() / 1000,   // always refresh timestamp on heartbeat
      };

      await env.LIVE_CHANNELS.put(`${KV_PREFIX}${id}`, JSON.stringify(channel), {
        expirationTtl: CHANNEL_TTL,
      });
      return json({ id });
    }

    // ── DELETE /live/channel/:id  (broadcaster ends stream) ────────────────
    if (pathname.startsWith('/live/channel/') && method === 'DELETE') {
      const authErr = checkAuth(request, env);
      if (authErr) return authErr;

      const id = pathname.slice('/live/channel/'.length).trim();
      if (!id) return err('Missing channel id', 400);
      await env.LIVE_CHANNELS.delete(`${KV_PREFIX}${id}`);
      return new Response('OK', { headers: corsHeaders() });
    }

    return err('Not found', 404);
  },
};

// ── helpers ─────────────────────────────────────────────────────────────────

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
