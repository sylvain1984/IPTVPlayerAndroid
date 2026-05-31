#!/bin/bash
# 部署 IPTV Live Channel Registry Worker
# 依赖：npm install -g wrangler  &&  wrangler login

set -e
cd "$(dirname "$0")"

# 1. 创建 KV namespace（只需运行一次）
if ! grep -q 'id = ".\+' wrangler.toml 2>/dev/null; then
  echo "=== 创建 KV namespace ==="
  OUTPUT=$(wrangler kv:namespace create LIVE_CHANNELS 2>&1)
  echo "$OUTPUT"
  KV_ID=$(echo "$OUTPUT" | grep -o '"[a-f0-9]\{32\}"' | tr -d '"' | head -1)
  if [ -n "$KV_ID" ]; then
    sed -i.bak "s/id      = \"\"/id      = \"$KV_ID\"/" wrangler.toml
    rm -f wrangler.toml.bak
    echo "KV ID 已写入 wrangler.toml: $KV_ID"
  else
    echo "请手动把 KV namespace id 填入 wrangler.toml"
    exit 1
  fi
fi

# 2. 设置播出密钥（每次可以更新）
echo ""
echo "=== 设置 BROADCAST_SECRET ==="
echo "（主播端 PUT /live/channel 时需要带这个密钥）"
wrangler secret put BROADCAST_SECRET

# 3. 部署
echo ""
echo "=== 部署 Worker ==="
wrangler deploy

echo ""
echo "✓ 部署完成"
echo "  把 Worker 域名填入 local.properties 的 LIVE_REGISTRY_URL"
echo "  格式：LIVE_REGISTRY_URL=https://iptv-live.<你的子域>.workers.dev"
