#!/usr/bin/env bash
# Starts virtual display + VNC inside the devcontainer.
# Called by devcontainer.json postStartCommand on every container start.
set -euo pipefail

# JavaSound needs a default PCM device even when the container has no host audio.
# Keep this in the startup script as well as the Dockerfile so existing containers
# pick up the fix before the image is rebuilt.
cat > "${HOME}/.asoundrc" <<'EOF'
pcm.!default {
    type plug
    slave.pcm "null"
}

ctl.!default {
    type null
}
EOF

# Idempotent teardown
pkill -x Xvfb 2>/dev/null || true
pkill -x x11vnc 2>/dev/null || true
pkill -f "[w]ebsockify" 2>/dev/null || true
pkill -x openbox 2>/dev/null || true
sleep 0.5

# Virtual framebuffer
setsid -f Xvfb :1 -screen 0 1920x1080x24 -nolisten tcp >/tmp/xvfb.log 2>&1
sleep 1

# Window manager (right-click desktop -> terminal)
DISPLAY=:1 setsid -f openbox >/tmp/openbox.log 2>&1
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
