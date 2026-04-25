#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  J.A.R.V.I.S — Termux GitHub Push Script
#  Run this in Termux on your Android phone
#  It will: install git, unzip the project, push to GitHub
# ═══════════════════════════════════════════════════════════════

echo ""
echo "  ⬡  J.A.R.V.I.S Setup — Standing by..."
echo "  Preparing to push to GitHub, Sir."
echo ""

# ─── STEP 1: Install required packages ───────────────────────
echo "[1/6] Installing git and unzip..."
pkg update -y -q
pkg install -y git unzip curl 2>/dev/null
echo "✓ Packages ready."

# ─── STEP 2: Configure Git identity ──────────────────────────
echo "[2/6] Configuring Git..."
git config --global user.name "sammysam254"
git config --global user.email "sammysam254@users.noreply.github.com"
git config --global init.defaultBranch main
echo "✓ Git configured."

# ─── STEP 3: Get the gradle wrapper jar (required to build) ──
echo "[3/6] Downloading Gradle wrapper jar..."
mkdir -p ~/vertextjarvis/gradle/wrapper
curl -sL "https://github.com/gradle/gradle/raw/v8.4.0/subprojects/wrapper/src/main/executable/gradle-wrapper.jar" \
     -o ~/vertextjarvis/gradle/wrapper/gradle-wrapper.jar 2>/dev/null

# Alternative download source
if [ ! -s ~/vertextjarvis/gradle/wrapper/gradle-wrapper.jar ]; then
    curl -sL "https://services.gradle.org/distributions/gradle-8.4-wrapper.jar" \
         -o ~/vertextjarvis/gradle/wrapper/gradle-wrapper.jar 2>/dev/null
fi

if [ -s ~/vertextjarvis/gradle/wrapper/gradle-wrapper.jar ]; then
    echo "✓ Gradle wrapper jar downloaded."
else
    echo "⚠ Could not download wrapper jar (network issue)."
    echo "  GitHub Actions will still build without it using its own Gradle."
fi

# ─── STEP 4: Set up the repo directory ───────────────────────
echo "[4/6] Setting up repository..."

# If JarvisAssistant.zip is in Downloads or current dir, unzip it
ZIP_PATH=""
if [ -f ~/storage/downloads/JarvisAssistant.zip ]; then
    ZIP_PATH=~/storage/downloads/JarvisAssistant.zip
elif [ -f ~/JarvisAssistant.zip ]; then
    ZIP_PATH=~/JarvisAssistant.zip
fi

if [ -n "$ZIP_PATH" ]; then
    echo "  Found ZIP at $ZIP_PATH — extracting..."
    cd ~
    unzip -q "$ZIP_PATH" -d jarvis_extract/
    mkdir -p ~/vertextjarvis
    cp -r ~/jarvis_extract/JarvisApp/. ~/vertextjarvis/
    rm -rf ~/jarvis_extract
    echo "✓ Project extracted."
else
    echo "⚠ ZIP not found in Downloads."
    echo "  Please copy JarvisAssistant.zip to your Downloads folder first."
    echo "  Then run: termux-setup-storage (if you haven't)"
    echo "  Then re-run this script."
    exit 1
fi

# ─── STEP 5: Initialize Git repo and push ────────────────────
echo "[5/6] Initializing Git repository..."
cd ~/vertextjarvis

# Clean any stale lock files
rm -f .git/index.lock 2>/dev/null

# Init if needed
if [ ! -d .git ]; then
    git init
fi

# Set remote (replace token inline for auth)
GITHUB_TOKEN="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"
GITHUB_USER="sammysam254"
REPO_NAME="vertextjarvis"

git remote remove origin 2>/dev/null
git remote add origin "https://${GITHUB_TOKEN}@github.com/${GITHUB_USER}/${REPO_NAME}.git"

# Stage all files
git add -A

# Commit
git commit -m "🤖 Initial J.A.R.V.I.S Android project

- Continuous background voice service (immortal, START_STICKY)
- Wake word detection: say 'Jarvis' to activate
- Formal White House butler TTS voice responses
- 20+ voice commands: calls, WiFi, Bluetooth, alarms, flashlight
- Claude AI integration for intelligent responses
- Accessibility service for screen monitoring
- Auto-starts on device reboot
- GitHub Actions workflow for APK build"

echo "✓ Files committed."

# ─── STEP 6: Push to GitHub ───────────────────────────────────
echo "[6/6] Pushing to GitHub..."
git push -u origin main --force

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅  J.A.R.V.I.S successfully deployed to GitHub, Sir!"
echo ""
echo "  Repository: https://github.com/${GITHUB_USER}/${REPO_NAME}"
echo ""
echo "  GitHub Actions will now BUILD the APK automatically."
echo "  Monitor progress at:"
echo "  https://github.com/${GITHUB_USER}/${REPO_NAME}/actions"
echo ""
echo "  Download your APK from the Actions tab once complete."
echo "  (Usually takes 3-5 minutes, Sir.)"
echo "═══════════════════════════════════════════════════════════"
echo ""
