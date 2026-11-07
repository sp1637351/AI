# Moko AI: 60s Continuous Wake Conversation & 3-Mode System (ONLINE / SLEEP / OFFLINE)

This update upgrades Moko with a **60-second continuous conversation window** after saying *"Hi Moko"* / *"Ok Moko"* (so you never have to repeat the wake-word on every command, and after 60 seconds of silence she automatically enters hands-free Standby until you say *"Hi Moko"* again), plus a **3-button top-bar mode switch (`ONLINE` • `SLEEP` • `OFFLINE`)** where **SLEEP** executes offline hardware commands (Calls, Bluetooth, Internet/Wi-Fi, Flashlight, Volume, Brightness, Apps) without AI Q&A, and **OFFLINE** completely shuts down background listening.

## User Review & Critical Decisions

> [!IMPORTANT]
> Your preferences from the clarification step have been locked into this plan:

- **Confirmed Decision 1 (Top-Bar 3-Mode Switch)**: The top app bar will feature a 3-button segmented switch (**`ONLINE` | `SLEEP` | `OFFLINE`**) styled in the Deep Purple & Neon Violet Glass theme with 100% English labels.
- **Confirmed Decision 2 (60-Second Active Conversation Timer & Auto-Standby)**: Saying *"Hi Moko"* or *"Ok Moko"* opens a **60-second active conversation session** with a live countdown badge (`ACTIVE SESSION • 58s`) on the main Violet Glass Orb card. Every spoken command resets the 60-second timer; after 60 seconds of silence, Moko automatically enters **Standby (`STANDBY • SAY 'HI MOKO'`)** and reactivates hands-free the moment you say *"Hi Moko"* again without touching the screen.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Hands-Free 60-Second Continuous Conversation**: Speak *"Hi Moko"* or *"Ok Moko"* once, and converse naturally back-to-back. Every command resets the 60-second inactivity timer. If you don't speak for 60 seconds, Moko closes active command listening and waits in low-power wake-word standby until you say *"Hi Moko"* again—even in Sleep Mode or with the screen locked.
  2. **Three Distinct Operating Modes (`ONLINE` / `SLEEP` / `OFFLINE`)**:
     - **`ONLINE` Mode**: Full AI Q&A + Kalpa Labs Neural Voice + Device & Contact Control + 60-second continuous wake session.
     - **`SLEEP` Mode (Offline Hardware & Call Mode)**: Runs in the background for **direct offline device actions only**—Calling contacts, turning **Bluetooth ON/OFF**, opening/toggling **Internet / Wi-Fi / Mobile Data**, Flashlight, Screen Lock, Brightness, Volume, Music playback, Alarm, Battery check, and launching apps. Open-ended AI Q&A is disabled in Sleep Mode so it works 100% offline without internet. Saying *"Hi Moko"* wakes it hands-free anytime.
     - **`OFFLINE` Mode (Complete Shutdown)**: Completely stops the background voice service, releases the microphone and wake-lock, and keeps the app silent until you tap `ONLINE` or `SLEEP`.
  3. **Direct Bluetooth & Internet/Wi-Fi Voice Control**: Adds offline voice commands to turn Bluetooth ON/OFF (`"ब्लूटूथ ऑफ करो"`, `"Turn off Bluetooth"`, `"Bluetooth on karo"`) and toggle/open Internet, Wi-Fi, or Mobile Data (`"इंटरनेट बंद करो"`, `"Turn off internet"`, `"Wi-Fi off karo"`).

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Wake Once, Talk Continuously (60s Window)**:
     - User says *"Hi Moko"* -> Moko replies warmly and the Orb badge displays `ACTIVE SESSION • 60s`.
     - User immediately says *"Turn on flashlight"*, then *"Volume 80%"*, then *"Call Rahul"* without repeating *"Hi Moko"*. Each command refreshes the timer back to `60s`.
     - If the user says nothing for 60 seconds, the countdown reaches `0s` and the badge transitions to `STANDBY • SAY 'HI MOKO'`—ignoring ambient speech until *"Hi Moko"* / *"Ok Moko"* is spoken again.
  2. **Switching Between `ONLINE`, `SLEEP`, and `OFFLINE`**:
     - Tapping **`ONLINE`** enables full AI + Hardware control + Kalpa neural voice.
     - Tapping **`SLEEP`** switches Moko into Offline Hardware & Call Mode (works without internet for Calls, Bluetooth, Wi-Fi/Internet, Torch, Lock, Volume, Brightness, Apps, and wakes on *"Hi Moko"*).
     - Tapping **`OFFLINE`** completely stops the background service and microphone.
- **Visual Identity & Theme**:
  - Preserves the **Deep Purple & Neon Violet Glass Theme** (`SuaObsidian = #0A0614`, `SuaDeepNavy = #130B24`, `SuaCyan = #D946EF`, `SuaElectricBlue = #8B5CF6`) with **100% English UI** across all buttons, badges, and chips.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: 60-Second Inactivity Timer Reset on End-of-TTS Speech**
  - *Chosen Approach*: The 60-second countdown starts/resets *after* Moko finishes speaking her response (`SuaVoiceSpeaker.speak` completion callback), rather than while she is still talking.
  - *Why*: Ensures the user gets a full 60 seconds of silence to reply after Moko finishes speaking, preventing the timer from expiring during long responses.
- **Decision 2: SLEEP Mode vs OFFLINE Mode Separation**
  - *Chosen Approach*: Introduce an explicit 3-state `MokoOperatingMode` (`ONLINE`, `SLEEP`, `OFFLINE`). `ONLINE` and `SLEEP` both keep the hands-free `"Hi Moko"` wake engine alive (with `SLEEP` restricted to offline hardware/calls/Bluetooth/Internet and zero AI Q&A calls), while `OFFLINE` terminates `SuaVoiceService` completely.
  - *Why*: Matches your exact workflow where Sleep Mode still executes offline phone tasks and responds to *"Hi Moko"* without touching the phone, while Offline Mode turns the background service completely off.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                     Top App Bar 3-Mode Segmented Switch                   │
│             [ ONLINE (Full AI) ]   [ SLEEP (Hardware) ]   [ OFFLINE ]     │
└──────────────────────┬───────────────────────┬──────────────────┬─────────┘
                       │                       │                  │
                       ▼                       ▼                  ▼
        ┌───────────────────────────────────────────┐    ┌──────────────────┐
        │     SuaVoiceService (Foreground Loop)     │    │   stopService()  │
        │  • Hands-Free Wake: "Hi Moko" / "Ok Moko" │    │  Mic & WakeLock  │
        │  • 60s Active Session Countdown Ticker    │    │  Completely OFF  │
        └──────────────────────┬────────────────────┘    └──────────────────┘
                               │
         ┌─────────────────────┴─────────────────────┐
         ▼ (Mode == ONLINE)                          ▼ (Mode == SLEEP)
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│   Full AI + Hardware Pipeline   │       │  Offline Hardware-Only Pipeline │
│ • Calls, SMS, WhatsApp Confirm  │       │ • Calls, SMS, WhatsApp          │
│ • Bluetooth / Wi-Fi / Internet  │       │ • Bluetooth ON/OFF              │
│ • Torch, Lock, Vol, Brightness  │       │ • Internet / Wi-Fi Toggle       │
│ • Open AI Chat (Gemini/GPT/etc) │       │ • Torch, Lock, Vol, Brightness  │
│ • Kalpa Labs Neural Voice TTS   │       │ • Blocks AI Q&A (Offline Fast)  │
└─────────────────────────────────┘       └─────────────────────────────────┘
```

### Data Model & State
- **`MokoOperatingMode`**: Enum with `ONLINE`, `SLEEP`, and `OFFLINE`.
- **`SuaStateRepository`**:
  - `operatingMode: StateFlow<MokoOperatingMode>`
  - `wakeSessionRemainingSeconds: StateFlow<Int>` (`0..60`, updated every second while active; `0` means Standby waiting for `"Hi Moko"`).
  - `WAKE_CONVERSATION_WINDOW_MS = 60_000L` (60 seconds).
- **`DeviceActionType` Additions**:
  - `BLUETOOTH_ON`, `BLUETOOTH_OFF`
  - `INTERNET_ON`, `INTERNET_OFF`

### Interactive Component & State Mapping
- **Top Bar & Side Drawer 3-Mode Switch**: Selecting `ONLINE` starts `SuaVoiceService` in `MokoOperatingMode.ONLINE`; selecting `SLEEP` starts/switches `SuaVoiceService` to `MokoOperatingMode.SLEEP` (offline hardware & call mode); selecting `OFFLINE` calls `SuaVoiceService.stopService()` and sets `MokoOperatingMode.OFFLINE`.
- **60s Countdown Badge (`ACTIVE SESSION • Xs` / `STANDBY • SAY 'HI MOKO'`)**: Displayed on the main Violet Glass Orb card and updated live by a 1-second coroutine ticker in `SuaVoiceService`. Tapping the Orb in `ONLINE` or `SLEEP` mode also manually wakes the 60s session if it is in Standby.
