# Moko — Gemini 3 API Fix, Echo-Loop Prevention & Zero-Lag Optimization

Fixes the **HTTP 404 Gemini Model Error** shown in your screenshot, stops the assistant from listening to its own spoken voice (which caused the endless error loop), and removes heavy continuous animations so **Moko** runs smoothly with zero lag.

---

### User Review & Critical Root-Cause Fixes

> [!IMPORTANT]
> From your screenshot, we identified and planned fixes for three exact issues:

1. **Fix 1 — Gemini HTTP 404 Error (`models/gemini-2.5-pro is no longer available to new users`)**:
   - **Root Cause**: Google's API rejected `gemini-2.5-*` for new projects and explicitly instructed: *"Please update your code to use `models/gemini-3.1-pro-preview`"*.
   - **Solution**: Upgrade the model engine to **`gemini-3-flash-preview`**, **`gemini-3.1-pro-preview`**, and **`gemini-3.1-flash-lite-preview`**, plus add **Automatic Model Discovery** (`/v1beta/models` lookup + 404 redirect parsing) so Moko automatically uses whichever Gemini 3 model is enabled for your key.

2. **Fix 2 — Stop Moko From Hearing Its Own Voice (Echo Loop Fix)**:
   - **Root Cause**: In your screenshot (`CMD > "Maph kijie sar gemini sadasy..."`), the microphone turned back on while the speaker was still reading the long error message aloud, causing the app to hear its own voice and trigger an infinite loop.
   - **Solution**:
     - Lock out `SpeechRecognizer` while `TextToSpeech` is speaking and wait for a clean **900ms post-speech silence buffer** before re-enabling the mic.
     - Add an **Acoustic Self-Echo Filter** that ignores any recognized text matching Moko's own recent voice output or system phrases (*"माफ़ कीजिए"*, *"जी बॉस"*).
     - Keep spoken error messages short and natural in Hindi without reading raw English URLs or HTTP stack traces aloud.

3. **Fix 3 — Eliminate UI & Audio Lag**:
   - **Root Cause**: Continuous infinite Canvas rotation animations were redrawing every frame at 60 FPS even when idle, competing with `SpeechRecognizer` and `TextToSpeech` on the main thread.
   - **Solution**: Pause orb animations when in Sleep/Idle mode and use lightweight, hardware-accelerated Compose drawing so the app feels instant and lag-free.

---

### 1. Overview & Core Concept

- **What It Does**: Delivers the redesigned **Moko ("मोको")** assistant with the **Deep Purple & Neon Violet Glass UI**, **Slide-Out Side Drawer (`☰`)**, **Online vs. Sleep Mode**, **Memory & History Vault**, and a lag-free **Gemini 3** voice pipeline.
- **Key Value**: Fast, accurate responses on new Gemini API keys with zero self-echo loops and smooth UI performance.

---

### 2. User Experience & Visual Design

- **Clean Main Screen + Side Menu Drawer (`☰`)**:
  - **Main Screen**: Features the top **`☰` Side Drawer Button**, **MOKO** header, **ONLINE / SLEEP** switcher, lightweight **Neon Violet Glass Orb**, **Chat History & Memory tabs** (with per-item **Keep `🔖`** and **Delete `🗑️`** buttons), and the bottom **Voice (`🎙️`) + Message (`💬`)** bar.
  - **Side Menu Drawer (`☰`)**: Houses **Settings, API Key & Permissions**, **Online / Sleep Mode controls**, **Memory & History shortcuts**, and **Quick Hardware Controls** (Torch, Lock Phone, Call, SMS, Brightness, Volume, Music).

---

### 3. Technical Architecture & Anti-Lag Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     MOKO ZERO-LAG VOICE & AI PIPELINE                   │
├─────────────────────────────────────────────────────────────────────────┤
│  1. Mic Input ("Hi Moko...")                                            │
│       │                                                                 │
│       ▼                                                                 │
│  2. Self-Echo Guard & Wake-Word Filter                                  │
│     • Rejects audio if TTS is active or within 900ms post-TTS cooldown  │
│     • Rejects text matching Moko's own last spoken reply                │
│       │                                                                 │
│       ▼                                                                 │
│  3. Gemini 3 Router + Auto Model Discovery                              │
│     • Primary: gemini-3-flash-preview / gemini-3.1-pro-preview          │
│     • Auto-Fallback: Queries /v1beta/models on 404 to pick active model │
│     • Injects Saved Memory Vault + Recent Chat History                  │
│       │                                                                 │
│       ▼                                                                 │
│  4. Hardware Execution & Clean Hindi TTS                                │
│     • Executes Call/SMS/Torch/Lock/Brightness/Memory                    │
│     • Speaks concise Hindi reply -> waits 900ms -> resumes Mic cleanly  │
└─────────────────────────────────────────────────────────────────────────┘
```
