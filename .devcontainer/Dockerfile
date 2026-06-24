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
    # Null ALSA device for JavaSound in headless/VNC sessions
    libasound2t64 \
    libasound2-plugins \
    alsa-utils \
    # Fonts for Swing rendering
    fonts-dejavu \
    fonts-liberation \
    fontconfig \
    # Tiled map editor
    tiled \
    # Utilities
    procps \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# JavaSound needs a default PCM device even when the container has no host audio.
# Route all ALSA playback to a null sink so Forge can start cleanly under VNC.
RUN printf '%s\n' \
    'pcm.!default {' \
    '    type plug' \
    '    slave.pcm "null"' \
    '}' \
    '' \
    'ctl.!default {' \
    '    type null' \
    '}' \
    > /etc/asound.conf

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

COPY --chown=forge:forge .devcontainer/start-vnc.sh /usr/local/bin/start-vnc.sh
RUN chmod +x /usr/local/bin/start-vnc.sh

EXPOSE 5901 6080

CMD ["/usr/local/bin/start-vnc.sh"]
