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
