#!/bin/bash
#
# record-mobile-demo.sh — Record MCW mobile wallet demo on Android emulator
#
# Usage: bash demo-video/record-mobile-demo.sh
# Result: demo-video/wallet-demo-mobile.mp4
#
# Prerequisites:
#   - Android emulator running (pm2 start android-emulator)
#   - APK installed (adb install -r mobile/android/app/build/outputs/apk/debug/app-debug.apk)
#

set -euo pipefail

ADB="/opt/android-sdk/platform-tools/adb"
PKG="org.multicurrencywallet.mobile"
APK="/root/MultiCurrencyWallet/mobile/android/app/build/outputs/apk/debug/app-debug.apk"
OUT_DIR="/root/MultiCurrencyWallet/demo-video"
RAW_VIDEO="$OUT_DIR/raw-wallet-mobile.mp4"
FINAL_VIDEO="$OUT_DIR/wallet-demo-mobile.mp4"
SRT_FILE="$OUT_DIR/subtitles.srt"
UI_DUMP="/tmp/mcw_ui_dump.xml"

# ═══════════════════════════════════════════════════════════════
# Helper functions
# ═══════════════════════════════════════════════════════════════

log() { echo "[$(date '+%H:%M:%S')] $*"; }

wait_boot() {
  log "Waiting for emulator boot..."
  for i in $(seq 1 90); do
    BOOT=$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$BOOT" = "1" ]; then
      log "Emulator booted!"
      return 0
    fi
    sleep 3
  done
  log "ERROR: Emulator did not boot in 270s"
  exit 1
}

dump_ui() {
  # Dump UI hierarchy and pull to local
  $ADB shell uiautomator dump /sdcard/ui.xml 2>/dev/null || true
  $ADB pull /sdcard/ui.xml "$UI_DUMP" 2>/dev/null || true
}

# Get center coordinates of element containing given text
# Returns "x y" or empty string if not found
find_element() {
  local text="$1"
  dump_ui
  python3 -c "
import xml.etree.ElementTree as ET, sys, re
try:
    tree = ET.parse('$UI_DUMP')
    for node in tree.iter('node'):
        t = node.get('text', '')
        if '$text' in t:
            bounds = node.get('bounds', '')
            m = re.findall(r'\d+', bounds)
            if len(m) == 4:
                cx = (int(m[0]) + int(m[2])) // 2
                cy = (int(m[1]) + int(m[3])) // 2
                print(f'{cx} {cy}')
                sys.exit(0)
except: pass
" 2>/dev/null
}

# Tap on element by text content
tap_text() {
  local text="$1"
  local coords
  coords=$(find_element "$text")
  if [ -z "$coords" ]; then
    log "WARNING: Element '$text' not found, retrying in 2s..."
    sleep 2
    coords=$(find_element "$text")
  fi
  if [ -n "$coords" ]; then
    local x=$(echo "$coords" | cut -d' ' -f1)
    local y=$(echo "$coords" | cut -d' ' -f2)
    log "Tapping '$text' at ($x, $y)"
    $ADB shell input tap "$x" "$y"
    return 0
  else
    log "ERROR: Element '$text' not found after retry"
    return 1
  fi
}

# Tap at absolute coordinates
tap_xy() {
  $ADB shell input tap "$1" "$2"
}

# Swipe (scroll)
swipe_up() {
  local dist="${1:-800}"
  $ADB shell input swipe 540 1600 540 $((1600 - dist)) 600
}

# Type text into focused input
type_text() {
  # Escape special characters for adb input text
  local text="$1"
  $ADB shell input text "$text"
}

pause() {
  sleep "$1"
}

# Extract all seed words from CreateWalletScreen
# Returns array of 12 words
extract_seed_words() {
  dump_ui
  python3 -c "
import xml.etree.ElementTree as ET, re
tree = ET.parse('$UI_DUMP')
words = []
for node in tree.iter('node'):
    text = node.get('text', '')
    # Seed words are in boxes: text like 'abandon', 'zoo', etc
    # They come after index numbers (1-12)
    # The word boxes have a specific structure - look for short lowercase words
    if text and len(text) <= 12 and text.isalpha() and text.islower() and text not in ('of', 'in', 'is', 'it', 'to', 'or', 'at', 'no'):
        words.append(text)
# Return first 12 words
for w in words[:12]:
    print(w)
" 2>/dev/null
}

# Extract requested word indices from ConfirmMnemonicScreen
# Returns indices like "3\n7\n11"
extract_confirm_indices() {
  dump_ui
  python3 -c "
import xml.etree.ElementTree as ET, re
tree = ET.parse('$UI_DUMP')
for node in tree.iter('node'):
    text = node.get('text', '')
    m = re.match(r'Word #(\d+)', text)
    if m:
        print(int(m.group(1)) - 1)  # 0-based index
" 2>/dev/null
}

# ═══════════════════════════════════════════════════════════════
# Main recording flow
# ═══════════════════════════════════════════════════════════════

log "=== MCW Mobile Wallet Demo Recording ==="

# Step 0: Ensure emulator is ready
wait_boot

# Enable screen (turn on display)
$ADB shell input keyevent 26  # POWER to wake
pause 1
$ADB shell input keyevent 82  # MENU to dismiss lock screen
pause 1

# Enable animations back (they were disabled for CPU optimization, but we need them for demo)
$ADB shell settings put global window_animation_scale 0.5
$ADB shell settings put global transition_animation_scale 0.5
$ADB shell settings put global animator_duration_scale 0.5

# Step 1: Install/reinstall APK (fresh)
log "Installing APK..."
$ADB uninstall "$PKG" 2>/dev/null || true
$ADB install -r "$APK"
log "APK installed"

# Step 2: Start screenrecord in background
log "Starting screen recording..."
$ADB shell "screenrecord --size 1080x2280 --bit-rate 8000000 /sdcard/demo-recording.mp4" &
RECORD_PID=$!
pause 2

# Step 3: Launch the app
log "Launching app..."
$ADB shell am start -n "$PKG/.MainActivity"
pause 4

# ─── Scene 1: Welcome Screen ─────────────────────────────────
log "Scene 1: Welcome Screen"
pause 4  # Show welcome screen

# ─── Scene 2: Create Wallet ──────────────────────────────────
log "Scene 2: Tap 'Create New Wallet'"
tap_text "Create New Wallet"
pause 3

# Read the 12 seed words while they're visible
log "Extracting seed words..."
SEED_WORDS=()
while IFS= read -r word; do
  SEED_WORDS+=("$word")
done < <(extract_seed_words)
log "Found ${#SEED_WORDS[@]} seed words: ${SEED_WORDS[*]}"

# Show seed phrase for 5 seconds
pause 5

# Scroll down to see checkbox
swipe_up 400
pause 1

# Tap checkbox "I have written down my seed phrase..."
tap_text "I have written down"
pause 1

# Tap "Continue to Verification"
tap_text "Continue to Verification"
pause 3

# ─── Scene 3: Confirm Mnemonic ───────────────────────────────
log "Scene 3: Confirm Mnemonic"

# Extract which word indices are requested
INDICES=()
while IFS= read -r idx; do
  INDICES+=("$idx")
done < <(extract_confirm_indices)
log "Requested word indices: ${INDICES[*]}"

# Fill in each word
for i in "${!INDICES[@]}"; do
  idx=${INDICES[$i]}
  word="${SEED_WORDS[$idx]}"
  log "Typing word #$((idx + 1)): $word"

  # Tap the input field for this word
  tap_text "Word #$((idx + 1))"
  pause 0.5

  # Also try tapping the placeholder
  tap_text "Enter word $((idx + 1))"
  pause 0.5

  type_text "$word"
  pause 1

  # Dismiss keyboard if needed - tap somewhere else briefly
  $ADB shell input keyevent 61  # TAB to next field
  pause 0.5
done

pause 1

# Tap "Verify & Create Wallet"
tap_text "Verify"
pause 5  # Wait for wallet creation

# ─── Scene 4: Main Wallet Screen ─────────────────────────────
log "Scene 4: Wallet Screen"
pause 5  # Show wallet with balances

# Scroll down to see asset cards
swipe_up 300
pause 3

# Scroll back up
$ADB shell input swipe 540 800 540 1600 600
pause 2

# ─── Scene 5: History Tab ────────────────────────────────────
log "Scene 5: History Tab"
tap_text "History"
pause 4

# Switch to BTC tab
tap_text "BTC"
pause 2

# Switch back to ETH
tap_text "ETH"
pause 2

# ─── Scene 6: DApps Tab ──────────────────────────────────────
log "Scene 6: DApps Tab"
tap_text "DApps"
pause 4

# Scroll the DApps catalog
swipe_up 400
pause 2

# Scroll back up
$ADB shell input swipe 540 800 540 1600 600
pause 2

# ─── Scene 7: Settings Tab ───────────────────────────────────
log "Scene 7: Settings Tab"
tap_text "Settings"
pause 3

# Scroll down to show Custom RPC and Security sections
swipe_up 500
pause 3

# Scroll down more
swipe_up 400
pause 2

# ─── End Recording ───────────────────────────────────────────
log "Stopping screen recording..."
kill $RECORD_PID 2>/dev/null || true
# Also send Ctrl+C to the screenrecord process on the device
$ADB shell "pkill -2 screenrecord" 2>/dev/null || true
pause 3

# Pull the recording
log "Pulling recording from device..."
$ADB pull /sdcard/demo-recording.mp4 "$RAW_VIDEO"
log "Raw video saved: $RAW_VIDEO"

# Step 4: Post-process with ffmpeg
log "Post-processing video..."

# Scale to 720x1280, add subtitles
ffmpeg -y \
  -i "$RAW_VIDEO" \
  -vf "scale=720:1280:force_original_aspect_ratio=decrease,pad=720:1280:(ow-iw)/2:(oh-ih)/2:color=black,subtitles=$SRT_FILE:force_style='FontName=Arial,FontSize=18,PrimaryColour=&Hffffff,OutlineColour=&H000000,Outline=2,Shadow=1,MarginV=40'" \
  -c:v libx264 -preset slow -crf 20 \
  -an \
  "$FINAL_VIDEO"

log "Final video: $FINAL_VIDEO"
log "$(ls -lh "$FINAL_VIDEO")"

log "=== Recording complete ==="
