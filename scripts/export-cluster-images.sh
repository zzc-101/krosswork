#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(dirname "$SCRIPT_DIR")
OUT=${1:-"$PROJECT_DIR/kross-images.tar.gz"}
SERVER_IMAGE=${KROSS_SERVER_IMAGE:-kross-server:local}
WEB_IMAGE=${KROSS_WEB_IMAGE:-kross-web:local}
WORKER_IMAGE=${KROSS_WORKER_IMAGE:-kross-worker:local}

if ! command -v docker >/dev/null 2>&1; then
  echo "错误：未找到 Docker。" >&2
  exit 1
fi

for image in "$SERVER_IMAGE" "$WEB_IMAGE" "$WORKER_IMAGE"; do
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "错误：本地没有镜像 $image，请先构建。" >&2
    exit 1
  fi
done

echo "导出 $SERVER_IMAGE $WEB_IMAGE $WORKER_IMAGE -> $OUT"
docker save "$SERVER_IMAGE" "$WEB_IMAGE" "$WORKER_IMAGE" | gzip >"$OUT"
echo "完成。内网节点导入："
echo "  gunzip -c $OUT | k3s ctr images import -"
echo "或先推到私有仓库，再 helm --set images.registry=... --set images.pullSecrets={regcred}"
