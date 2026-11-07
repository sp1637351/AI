# Moko AI: Screen Share Vision & Voice Click Engine, Clean Minimal Orb HUD, and Full Offline System Control

This update adds a dedicated **Screen Share (`SHARE`)** button right next to the message bar at the bottom of the screen so Moko can see, analyze, and click/interact with screen elements by voice, unlocks the screen on `"Unlock screen"` / `"लॉक खोलो"`, guarantees **`MR. SHASHANK`** attribution whenever asked who developed the app, removes visual clutter below the Orb so only the **Center Voice Orb + Moko Reply + User Spoken Text** appear, and expands **100% Offline System & Settings Control** when the internet is turned off.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed User Decisions from Clarification**:
> - **Clean Main Screen Layout**: All extra quick-action chips and clutter below the Center Orb are removed. The main screen displays **only the Center Voice Orb (with Moko's response right in the center card) and the User's Spoken Text (`USER SPOKE`) below it**, plus the bottom bar.
> - **Bottom Bar Screen Share Capabilities**: The new **Screen Share** button next to the bottom message input performs **both AI Screen Analysis** (reading and explaining what is currently on the screen) **and Voice Click / Unlock / Navigation actions** (clicking buttons/items by voice, scrolling, and unlocking the screen).

- **Confirmed Decision 1 (Developer Identity — `MR. SHASHANK`)**: Any Hindi, Hinglish, or English question asking who created, built, coded, or developed Moko (`"तुम्हें कौन डेवलप किया है"`, `"किसने बनाया है"`, `"who developed you"`, `"developer name"`) always answers **`MR. SHASHANK`** clearly in both voice and display text.
- **Confirmed Decision 2 (Full Offline System & Settings Control Without Internet)**: When the internet is OFF (or in `SLEEP` / offline state), Moko locally executes all possible device, settings, and app actions without needing any internet connection—including locking and **unlocking** the screen (`"लॉक खोलो"` / `"Unlock"`), clicking UI buttons (`"Click Settings"`, `"Click History"`), toggling Bluetooth, Wi-Fi/Internet, Hotspot, Location/GPS, Airplane Mode, Do Not Disturb (DND), Auto-Rotate, Battery Saver, Flashlight, Brightness, Volume, Alarms, Timers, Opening any System Setting page, and Launching any installed app.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Bottom Bar Screen Share Button (`SCREEN SHARE`)**: Placed right beside the Voice button and Message input in the bottom bar. Tapping it (or saying `"Share screen"` / `"स्क्रीन देखो"` / `"Analyze screen"`) activates **Live Screen Share & Vision Mode**, capturing the current screen context/snapshot so Moko can read out what is on the screen, answer questions about it, and click any visible button or option when the user says `"Click [target]"` / `"इस पर क्लिक करो"`.
  2. **Clean Center & Bottom Transcript Hierarchy**:
     - **Center**: The Deep Purple & Neon Violet Glass Orb Card shows the animated Voice Orb, 60-second countdown badge, and **Moko's Response (`MOKO REPLY`)** right in the center.
     - **Bottom (Above Input Bar)**: A dedicated, clean **User Spoken Bar (`YOU SAID`)** shows only what the user spoke, free of cluttered chip rows.
  3. **Voice Screen Unlock (`"लॉक खोलो"` / `"Unlock Screen"`) & Voice Click (`"इसे क्लिक करो"`)**:
     - Saying `"लॉक खोलो"`, `"स्क्रीन अनलॉक करो"`, or `"Unlock screen"` immediately dismisses the security lock overlay, wakes the screen if dim/asleep, and opens the keyguard dismissal flow.
     - Saying `"Click [Button Name]"` / `"[नाम] पर क्लिक करो"` (e.g., `"Click Settings"`, `"Click History"`, `"Click Online"`, `"Click Sleep"`, `"Click Torch"`, `"Click Call"`, or any on-screen element during Screen Share) triggers the matching UI action automatically.
  4. **Complete Offline Settings & Phone Control (Internet OFF)**:
     - Works 100% offline when Wi-Fi and Mobile Data are turned off: opens or toggles Bluetooth, Wi-Fi, Mobile Data, Hotspot, Location, Sound/Vibrate/Silent mode, Do Not Disturb, Airplane Mode settings, Display settings, Battery Saver, App Info, Camera, Calculator, Clock/Alarm, Contacts, Dialer, and any installed app.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Clean Main Screen Flow**:
     - Top Bar: Side Menu button, `MOKO` title, and the `ONLINE | SLEEP | OFFLINE` 3-mode switch.
     - Center Area: Neon Violet Glass Orb Card containing the Voice Orb (`60s` active session ring) and **Moko's Center Response Display**.
     - Lower Area: Clean **User Spoken Transcript Card** showing only what the user just said (`"YOU SPOKE"`), followed by the Bottom Bar (`VOICE` | `SCREEN SHARE` | `Message Input` | `SEND`).
  2. **Screen Share & Voice Click Flow**:
     - User taps the **Screen Share** icon button in the bottom bar (or speaks `"Share screen"` / `"स्क्रीन शेयर करो"`).
     - A glowing **LIVE SCREEN SHARE ACTIVE** indicator badge appears on the center card. Moko captures and analyzes the current screen state/elements, speaks a summary of what is visible on screen, and listens for follow-up commands such as `"What is written on screen?"`, `"Click Settings"`, `"Click History"`, or `"Unlock screen"`.
  3. **Offline Mode / No-Internet Hardware & Settings Flow**:
     - Even with Internet completely turned OFF, the user can say `"लॉक खोलो"` (unlocks screen), `"ब्लूटूथ बंद करो"`, `"वाइफाइ चालू करो"`, `"हॉटस्पॉट खोलो"`, `"लोकेशन सेटिंग खोलो"`, `"साइलेंट मोड चालू करो"`, `"अलार्म लगाओ"`, or `"कोई भी ऐप खोलो"`, and Moko executes it immediately offline.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Restored Deep Purple & Neon Violet Glass (`SuaObsidian #0B0514`, `SuaDeepNavy #150A26`, `SuaCyan #D500F9`, `SuaElectricBlue #7C4DFF`, `SuaEmerald #00E676`) with zero clutter on the main screen.
  - **100% English App UI Text**: All UI buttons, badges, and placeholders remain strictly in English while supporting full Hindi and English voice conversation.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Integrated Screen Share Snapshot + Semantic UI Tree Analyzer & Voice Click Dispatcher**
  - *Chosen Approach*: Combine an on-device window/screen bitmap & semantic UI element analyzer with multimodal AI vision (when online) and instant local screen-reader/click execution (both online and offline).
  - *Why*: Works reliably inside the Android runtime without requiring restricted accessibility permissions that could block installation, while allowing Moko to see every screen element, describe it, and click/trigger buttons by voice.
- **Decision 2: Comprehensive Offline Intent Engine for All Settings & System Actions**
  - *Chosen Approach*: Expand the local deterministic parser (`SuaBrain.parseHardwareIntentLocally`) and `SuaDeviceController` to cover all Android Settings panels, audio profiles (Silent/Vibrate/Ring), screen lock/unlock, voice clicks, alarms, and installed apps BEFORE checking network status.
  - *Why*: Ensures 100% of device operations work instantaneously with zero internet connection.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌───────────────────────────────────────────────────────────────────────────┐
│                   MOKO CLEAN VIOLET GLASS MAIN SCREEN                     │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Top Bar: [Menu]  MOKO (KALPA AI)   [ONLINE | SLEEP | OFFLINE]  [⚙] │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                  CENTER NEON VIOLET GLASS ORB CARD                  │  │
│  │   [ACTIVE SESSION • 60s]                   [SCREEN SHARE: ON/OFF]   │  │
│  │                        (  ANIMATED VOICE ORB  )                     │  │
│  │   CENTER DISPLAY: Moko's Spoken/Text Reply & Screen Analysis        │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │   BOTTOM SPOKEN BAR: Only What User Spoke ("YOU SPOKE: ...")        │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Bottom Bar: [VOICE] [SCREEN SHARE] [ Ask Moko...           ] [SEND] │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          ▼                           ▼                           ▼
┌───────────────────┐     ┌───────────────────────┐   ┌─────────────────────┐
│ Screen Share &    │     │ Developer Identity &  │   │ Full Offline System │
│ Voice Click Engine│     │ Offline Brain Parser  │   │ & Settings Executor │
│ • Screen Snapshot │     │ • Always replies      │   │ • Unlock Screen     │
│ • AI + Offline    │     │   "MR. SHASHANK"      │   │ • Bluetooth/Wi-Fi   │
│   Screen Analysis │     │ • Voice Clicks        │   │ • Hotspot/Location  │
│ • Click by Name   │     │ • Unlock / Lock       │   │ • Silent/Vibrate/DND│
└───────────────────┘     └───────────────────────┘   └─────────────────────┘
```

- **Data Model & State Updates**:
  - `DeviceActionType`: Add `UNLOCK_PHONE`, `SCREEN_SHARE_ANALYZE`, `VOICE_CLICK`, `OPEN_SYSTEM_SETTING`, and `RINGER_MODE_SET`.
  - `SuaStateRepository`: Add `isScreenShareActive: StateFlow<Boolean>` and `currentScreenContextSummary: StateFlow<String>` so Moko knows what is currently displayed on screen at any moment.
- **Interactive Component & State Mapping**:
  - **Bottom Bar Screen Share Button**: Toggles `isScreenShareActive` and immediately triggers an analysis of the current screen, speaking the summary aloud and enabling voice click commands (`"Click Settings"`, `"Click History"`, `"Click Torch"`, etc.).
  - **Voice Unlock (`UNLOCK_PHONE`)**: Calls `SuaStateRepository.setScreenLockedOverlay(false)`, wakes the screen via `PowerManager.ACQUIRE_CAUSES_WAKEUP`, and requests keyguard dismissal.
  - **Offline Settings Dispatcher (`OPEN_SYSTEM_SETTING` & `RINGER_MODE_SET`)**: Opens any requested Android Setting (`Wi-Fi`, `Bluetooth`, `Hotspot`, `Location`, `Airplane Mode`, `Sound`, `Display`, `Battery`, `Security`, `Date & Time`, `Accessibility`) and switches audio ringer mode (`Silent`, `Vibrate`, `Normal/Ring`) completely offline.
