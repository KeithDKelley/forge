#!/usr/bin/env bash
# MTG Forge devcontainer setup
# Run once from /home/keith/Projects/mtgForgeFork/
# Sets up Docker, fetches the forge fork, and writes .devcontainer/ files.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORK_URL="https://github.com/KeithDKelley/forge.git"
DEFAULT_BRANCH="master"

# ── 1. Docker ─────────────────────────────────────────────────────────────────
echo ">>> [1/4] Installing Docker..."
if ! command -v docker &>/dev/null; then
    sudo apt-get update -qq
    sudo apt-get install -y docker.io
    sudo systemctl enable --now docker
else
    echo "    Docker already installed, skipping."
fi

if ! groups "$USER" | grep -qw docker; then
    echo ">>> Adding $USER to docker group (re-login required after this script)..."
    sudo usermod -aG docker "$USER"
else
    echo "    $USER already in docker group."
fi

# ── 2. Fetch forge into this directory ────────────────────────────────────────
echo ">>> [2/4] Fetching forge fork into $SCRIPT_DIR..."
echo "    (shallow clone, depth=50 — run 'git fetch --unshallow' later for full history)"
cd "$SCRIPT_DIR"

if [ ! -d .git ]; then
    git init .
    git remote add origin "$FORK_URL"
else
    git remote set-url origin "$FORK_URL" 2>/dev/null || true
fi

git fetch --depth=50 origin "$DEFAULT_BRANCH"
if ! git rev-parse --verify "$DEFAULT_BRANCH" &>/dev/null; then
    git checkout -b "$DEFAULT_BRANCH" "origin/$DEFAULT_BRANCH"
else
    git checkout "$DEFAULT_BRANCH"
    git merge --ff-only "origin/$DEFAULT_BRANCH"
fi

# ── 3. Write .devcontainer/ files ─────────────────────────────────────────────
echo ">>> [3/4] Writing .devcontainer/ files..."
mkdir -p .devcontainer

# ── Dockerfile ────────────────────────────────────────────────────────────────
cat > .devcontainer/Dockerfile << 'DOCKERFILE_EOF'
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    DISPLAY=:1 \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends \
    # Java + build
    openjdk-21-jdk \
    maven \
    git \
    # Virtual display + VNC
    xvfb \
    x11vnc \
    novnc \
    websockify \
    # Lightweight window manager + terminal for manual use
    openbox \
    xterm \
    # X11 libs required by Java Swing
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    x11-utils \
    # Fonts for Swing rendering
    fonts-dejavu \
    fonts-liberation \
    fontconfig \
    # Utilities
    procps \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Non-root user, UID 1000 matches host keith — no sudo
RUN groupadd --gid 1000 forge \
    && useradd --uid 1000 --gid 1000 -m --shell /bin/bash forge

USER forge
WORKDIR /workspace

EXPOSE 5901 6080
DOCKERFILE_EOF

# ── devcontainer.json ─────────────────────────────────────────────────────────
cat > .devcontainer/devcontainer.json << 'DEVJSON_EOF'
{
  "name": "MTG Forge Dev",
  "build": {
    "dockerfile": "Dockerfile",
    "context": ".."
  },
  "runArgs": [
    "--memory=6g",
    "--cpus=4",
    "--security-opt", "no-new-privileges:true",
    "--cap-drop", "ALL"
  ],
  "forwardPorts": [5901, 6080],
  "portsAttributes": {
    "5901": {
      "label": "VNC Direct",
      "onAutoForward": "silent"
    },
    "6080": {
      "label": "noVNC Browser",
      "onAutoForward": "notify"
    }
  },
  "containerEnv": {
    "DISPLAY": ":1"
  },
  "postStartCommand": "bash /workspace/.devcontainer/start-vnc.sh",
  "remoteUser": "forge",
  "workspaceMount": "source=${localWorkspaceFolder},target=/workspace,type=bind,consistency=cached",
  "workspaceFolder": "/workspace",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "vscjava.vscode-maven"
      ]
    }
  }
}
DEVJSON_EOF

# ── start-vnc.sh ──────────────────────────────────────────────────────────────
cat > .devcontainer/start-vnc.sh << 'STARTVNC_EOF'
#!/usr/bin/env bash
# Starts virtual display + VNC inside the devcontainer.
# Called by devcontainer.json postStartCommand on every container start.
set -euo pipefail

# Idempotent teardown
pkill -f "Xvfb :1"  2>/dev/null || true
pkill -f "x11vnc"   2>/dev/null || true
pkill -f "websockify" 2>/dev/null || true
pkill -f "openbox"  2>/dev/null || true
sleep 0.5

# Virtual framebuffer
Xvfb :1 -screen 0 1920x1080x24 -nolisten tcp &
sleep 1

# Window manager (right-click desktop → terminal)
DISPLAY=:1 openbox --daemon &
sleep 0.3

# VNC server — no password; Docker port forwarding is the access gate
x11vnc \
    -display :1 \
    -forever \
    -shared \
    -nopw \
    -rfbport 5901 \
    -logfile /tmp/x11vnc.log \
    -bg \
    -quiet

# noVNC browser proxy
websockify \
    --web /usr/share/novnc \
    --log-file /tmp/novnc.log \
    --daemon \
    6080 \
    localhost:5901

echo ""
echo "[VNC ready]"
echo "  Browser : http://localhost:6080/vnc.html"
echo "  VNC client: localhost:5901 (no password)"
echo ""
echo "To build forge:  mvn install -DskipTests -T4"
echo "To run desktop:  cd forge-gui-desktop && mvn exec:java"
STARTVNC_EOF

chmod +x .devcontainer/start-vnc.sh

# ── 4. Done ───────────────────────────────────────────────────────────────────
echo ""
echo ">>> [4/4] Done!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " NEXT STEPS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo " 1. Apply docker group (pick one):"
echo "       newgrp docker          ← applies in current shell only"
echo "       — or — log out and back in"
echo ""
echo " 2. Open in VS Code devcontainer:"
echo "       code $SCRIPT_DIR"
echo "    Then: Ctrl+Shift+P → 'Dev Containers: Reopen in Container'"
echo ""
echo " 3. GUI access once container is running:"
echo "       http://localhost:6080/vnc.html"
echo ""
echo " 4. First build (inside container, takes ~10 min):"
echo "       mvn install -DskipTests -T4"
echo ""
echo " NOTE: devenv-setup.sh is an untracked file in the forge repo."
echo "       Add it to .git/info/exclude if you want git to ignore it locally."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
