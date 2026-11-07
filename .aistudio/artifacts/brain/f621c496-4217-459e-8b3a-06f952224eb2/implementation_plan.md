# Moko — Pitch-Black AMOLED UI, Multi-AI API Hub & Custom Voice Engine

Upgrades the application with a **Crisp Pitch-Black AMOLED & Neon Blue UI** (removing all blurry effects), moves **Chat History & Memory Vault into Settings** for a 100% clean main screen, adds **"Ok Google" style low-battery wake listening**, credits **MR. SHASHANK** as the developer, allows customizing **User Name, Assistant Name, Voice Tone & Custom Commands**, and supports **Google Gemini, OpenAI ChatGPT, Claude AI, or any Custom API Link + Key**.

---

### User Review & Confirmed Decisions

> [!IMPORTANT]
> All visual and functional preferences confirmed with you are incorporated below:

- **Confirmed Decision 1 — Pitch-Black AMOLED & Sharp Neon Blue Theme**: Pure `#000000` AMOLED background with razor-sharp `#00F0FF` Neon Blue and `#FFFFFF` borders—zero blurry overlays or laggy animations.
- **Confirmed Decision 2 — Ultra-Clean Main Screen (History Moved to Settings)**: The main screen displays **only** the Top Bar (`☰` Menu, Assistant Name, `ONLINE / SLEEP` switch), the **Center Voice Orb**, and the bottom **Voice + Message Bar**. All **Chat History & Memory Vault** management now lives inside the **Settings / Side Menu**.
- **Confirmed Decision 3 — Developer Credit (`MR. SHASHANK`)**: Whenever anyone asks who created or developed the assistant (*"तुम्हें किसने डेवलप किया है?"* / *"Who developed you?"*), it proudly replies that it was developed by **MR. SHASHANK**.
- **Confirmed Decision 4 — Custom Assistant Name, User Name, Voices & Custom Commands**: Users can change the assistant's wake-word/name (e.g., `Moko` or any name), set their own User Name, pick affectionate voice presets (Pitch & Speed), and create custom voice command rules in Settings.
- **Confirmed Decision 5 — Universal Multi-AI API Hub (Gemini + ChatGPT + Claude + Custom URL)**: Users can paste any API key (auto-detecting Gemini, ChatGPT, Claude, or Groq) or click **Add API Link + Key** to connect any custom AI website endpoint.

---

### 1. Overview & Core Concept

- **What It Does**: A fast, low-battery background voice assistant created by **MR. SHASHANK** that listens like *"Ok Google"* using your chosen wake-word, speaks warmly in Hindi, controls phone hardware & apps by voice, and connects to any AI provider (Gemini, ChatGPT, Claude, or Custom URL).
- **Key Value**: Combines a crystal-clear AMOLED interface, zero-lag touch and voice response, deep personalization, and universal AI API compatibility.

---

### 2. User Experience & Visual Design

- **Main Screen (100% Clean & Sharp)**:
  - **Top Header**: Left **`☰` Menu / Settings** button, Center **Dynamic Assistant Name** (e.g., `MOKO`), Right **`ONLINE` / `SLEEP`** mode pill switch.
  - **Centerpiece Crisp AMOLED Voice Orb**: Sharp high-contrast neon blue & white concentric rings with instant touch response to toggle listening, plus a clean response card showing the latest reply.
  - **Bottom Voice & Message Bar**: Instant **Voice Mic Button (`🎙️`)** alongside the **Message Input Box (`💬`)** and **Send Button**.
- **Settings & Control Hub (Accessible via `☰`)**:
  1. **Identity & Personalization**:
     - **Developer Badge**: Permanently displays **Developed by MR. SHASHANK**.
     - **Your User Name**: Input box to set who is using the app (e.g., `Mr. Shashank`) so the assistant addresses you affectionately by name.
     - **Assistant Name / Wake-Word**: Input box to rename the assistant (default `Moko`)—updates the UI header and wake-word detector (`"Hi <Name>"` / `"Ok <Name>"`).
  2. **Universal Multi-AI API Manager**:
     - **Provider Selector & Auto-Detect**: Supports **Google Gemini**, **OpenAI ChatGPT**, **Anthropic Claude**, and **Custom API URL**.
     - **Optional API Endpoint Link**: If the user has a custom website/API link, they can enter it; if left blank, the app automatically uses the official endpoint for their API key.
  3. **Affectionate Voice Profiles & Custom Commands**:
     - **Voice Tone Presets**: *Sweet & Loving (प्यारी आवाज़)*, *Crisp Assistant*, *Calm Voice*, *Energetic* + **Pitch & Speed Sliders** + system voice picker.
     - **Custom Voice Commands**: Add custom *"When I say X ➔ Do/Speak Y"* rules.
  4. **Conversation History & Memory Vault (Moved from Main Screen)**:
     - View, bookmark/keep (`🔖`), or delete (`🗑️`) individual chat history items and saved memories.
  5. **Low-Battery Background & Permissions Matrix**:
     - One-tap **Allow All Permissions**, **Device Admin**, **Brightness Control**, and **Low-Battery Ok-Google Style Duty Cycle** toggle.

---

### 3. Technical Architecture & Multi-AI Routing

```
┌───────────────────────────────────────────────────────────────────────────┐
│                 PITCH-BLACK AMOLED MAIN SCREEN (ULTRA-CLEAN)              │
│   [☰ Settings]         [Dynamic Name: MOKO]         [ONLINE / SLEEP]      │
│                                                                           │
│                     ((  SHARP NEON BLUE VOICE ORB  ))                     │
│                     "Hi Moko" • Low-Battery Wake Mode                     │
│                                                                           │
│   [🎙️ Voice ON/OFF]    [💬 Message Input...]               [➤ Send]       │
└───────────────────┬───────────────────────────────────────┬───────────────┘
                    │                                       │
                    ▼                                       ▼
┌───────────────────────────────────────┐ ┌─────────────────────────────────┐
│     SETTINGS & CONTROL HUB (☰)        │ │  LOW-BATTERY WAKE & APP ENGINE  │
│ • Developer: MR. SHASHANK             │ │ • Smart Duty-Cycle Recognizer   │
│ • User Name & Assistant Wake Name     │ │ • Open Any Installed App Voice  │
│ • Voice Tone (Sweet/Pitch/Speed)      │ │ • Hardware: Call, SMS, Torch,   │
│ • Custom Commands ("Say X -> Do Y")   │ │   Lock, Brightness, Volume      │
│ • History & Memory Vault (Keep/Delete)│ └─────────────────┬───────────────┘
└───────────────────┬───────────────────┘                   │
                    │                                       │
                    ▼                                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     UNIVERSAL MULTI-AI ROUTER ENGINE                      │
│  • Auto-Detects Key Prefix (AIza* -> Gemini, sk-ant* -> Claude, sk-* ->   │
│    ChatGPT) OR uses Custom Endpoint URL + API Key                         │
│  • Injects User Name, Assistant Name, MR. SHASHANK Creator Credit,        │
│    Custom Commands & Saved Memory Vault into every AI request             │
└───────────────────────────────────────────────────────────────────────────┘
```
