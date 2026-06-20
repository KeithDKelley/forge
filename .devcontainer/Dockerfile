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
# Ubuntu 24.04 base already has a group/user at 1000; rename them rather than creating new ones
RUN existing_group=$(getent group 1000 | cut -d: -f1) ; \
    if [ -n "$existing_group" ]; then \
        groupmod -n forge "$existing_group" ; \
    else \
        groupadd --gid 1000 forge ; \
    fi && \
    existing_user=$(getent passwd 1000 | cut -d: -f1) ; \
    if [ -n "$existing_user" ]; then \
        usermod -l forge -d /home/forge -m -s /bin/bash "$existing_user" ; \
    else \
        useradd --uid 1000 --gid 1000 -m --shell /bin/bash forge ; \
    fi

USER forge

# Bypass Claude Code permission prompts — safe here because the container is the security boundary
RUN mkdir -p /home/forge/.claude \
    && printf '{\n  "permissions": { "defaultMode": "bypassPermissions" },\n  "skipDangerousModePermissionPrompt": true\n}\n' \
       > /home/forge/.claude/settings.json

WORKDIR /workspace

EXPOSE 5901 6080
