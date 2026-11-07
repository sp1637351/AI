# Moko — Personal AI Voice & Hardware Assistant

Rebrands and transforms the assistant into **Moko ("मोको")** featuring a sleek **Deep Purple & Neon Violet Glass UI**, a decluttered main screen with a **Slide-Out Side Navigation Drawer**, a dedicated **Online vs. Sleep Mode** switcher that works reliably even when the phone is locked, and a persistent **Memory & Conversation History Engine** with individual keep and delete controls.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product and design decisions have been aligned based on your preferences and requirements:

- **Confirmed Decision 1 — Visual Theme**: **Deep Purple & Neon Violet Glass Theme** replacing the old cyan/gold layout with rich cosmic violet surfaces (`#0B0714`), frosted glass cards (`#17102B`), luminous neon violet highlights (`#A855F7`), and electric magenta accents (`#EC4899`).
- **Confirmed Decision 2 — Uncluttered Main Screen & Side Drawer**: All secondary hardware toggles, sliders, and configuration tools move into a **Slide-Out Side Menu Drawer (`☰`)**, leaving the main screen clean and focused on the **Moko Voice Orb**, **Online / Sleep Toggle**, **Voice & Message Controls**, and **Conversation & Memory Stream**.
- **Confirmed Decision 3 — Auto-Saved History & Contextual AI Memory**: Moko automatically saves conversation history and important facts (with options to pin/keep or delete any entry) and feeds saved memories into every response so it remembers past context.
- **Confirmed Decision 4 — "Hey Moko" Wake-Word & Online / Sleep Mode**:
  - **Online Mode**: Runs continuously in the background and while the phone is locked (`PARTIAL_WAKE_LOCK` + Foreground Microphone Service), responding whenever you say **"Hi Moko"**, **"Hey Moko"**, or **"Moko"** ("हाय मोको" / "मोको").
  - **Sleep Mode**: Completely halts background microphone listening and releases wake locks until switched back to Online.

---

### 1. Overview & Core Concept

- **What It Does**: **Moko** is a hands-free Hindi & English voice and chat assistant for Android that controls device hardware (calls, SMS, flashlight, screen lock, brightness, volume, music, alarms) and holds memory-aware conversations in fluent Hindi voice.
- **Target Audience / Persona**: Users who want a clean, modern personal AI companion named **Moko** that remembers their personal preferences and executes phone actions reliably—even from a locked screen—when addressed with *"Hi Moko"* or *"Hey Moko"*.
- **Key Value**: Eliminates main-screen visual clutter while combining hardware control, contextual long-term memory, and one-tap **Online / Sleep** background power management.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Main Screen (Voice + Message Hub)**:
     - **Top Header**: Left **`☰` Side Menu Button** opens the navigation drawer; center displays **MOKO** branding; right features a two-state **ONLINE / SLEEP** pill switch.
     - **Centerpiece Moko Neural Orb**: An animated Neon Violet & Magenta glowing orb that visualizes live voice input waves and status (*Online Listening*, *Sleep Mode*, *Processing*, *Speaking*).
     - **Voice & Message Mode Bar**: Dedicated **Voice Mic Toggle (`🎙️`)** and **Text Message Input (`💬`)** are both active on the main screen so users can speak or type commands effortlessly.
     - **Recent Chat & Memory Stream**: Displays the conversation timeline directly on the main screen with one-tap **Pin to Memory (`📌`)** and **Delete (`🗑️`)** buttons on every item.
  2. **Online vs. Sleep Mode Switching**:
     - Tapping **SLEEP** immediately stops the background voice service, turns the orb into a calm dim state, and ensures zero background mic usage.
     - Tapping **ONLINE** (or tapping the sleeping orb) activates the foreground background service and partial wake lock so Moko stays awake even after locking the phone screen, waiting for *"Hi Moko"* / *"Hey Moko"*.
  3. **Slide-Out Side Menu Drawer (`☰`)**:
     - **Memory & History Vault**: View all auto-saved memories and chat logs, add a custom memory note, or delete individual/all items.
     - **Quick Hardware Controls**: Instant access to Flashlight, Screen Lock, Brightness slider, Volume slider, and Media controls without cluttering the main screen.
     - **Settings & Permissions**: One-click **Allow All Permissions**, **Unrestricted Battery / Lock-Screen Setup**, **Gemini API Key (Save / Delete)**, **Gemini Model Selector**, and **Wake-Word Toggle**.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Futuristic Glassmorphic AI Companion—clean, atmospheric, and distraction-free.
  - *Color Palette & Mood*:
    - `--background`: `#0B0714` (Deep Cosmic Obsidian Violet)
    - `--surface-glass`: `#17102B` (Frosted Violet Glass Container)
    - `--primary`: `#A855F7` (Neon Violet Core)
    - `--accent`: `#EC4899` (Electric Magenta Pulse)
    - `--status-online`: `#10B981` (Emerald Online Glow)
    - `--status-sleep`: `#64748B` (Muted Slate Sleep State)
  - *Typography & Hierarchy*: `Space Grotesk` for Moko headers and mode badges, `Plus Jakarta Sans` for clean conversational bubbles and memory cards, and `JetBrains Mono` for timestamps and status pills.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dedicated Online vs. Sleep State Machine with Screen-Off WakeLock**
  - *Chosen Approach*: Pair an explicit `MokoPowerMode` (`ONLINE` vs. `SLEEP`) with an Android `PARTIAL_WAKE_LOCK` and auto-recovering `SpeechRecognizer` loop inside the foreground service.
  - *Why*: Android aggressively pauses speech recognition when the screen locks unless a foreground microphone service and partial CPU wake lock are held. Having an explicit **Sleep** button also gives the user total control to stop background battery/mic use whenever desired.
  - *Alternatives Considered*: Relying only on UI lifecycle listeners, which stop working as soon as the phone screen turns off.

- **Decision 2: Dual-Layer Memory & Chat History (Auto-Extraction + Manual Keep/Delete)**
  - *Chosen Approach*: Store both **Conversation History** (with per-message delete & pin-to-memory buttons) and **Key Memory Facts** (auto-extracted when the user shares personal info or preferences, plus Gemini-extracted `memoryFact` fields) in local encrypted/persistent storage, and inject them into Gemini's system prompt.
  - *Why*: Allows Moko to remember user preferences, names, and previous questions across sessions while giving the user full transparency to keep or delete any memory or history item.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        MOKO ANDROID APPLICATION                           │
├───────────────────────────────────┬───────────────────────────────────────┤
│     MAIN SCREEN (CLEAN HUB)       │     SLIDE-OUT SIDE DRAWER (☰)         │
│  ┌─────────────────────────────┐  │  ┌─────────────────────────────────┐  │
│  │ Top Bar: [☰] MOKO [ONLINE/  │  │  │ • Memory & History Vault        │  │
│  │                    SLEEP]   │  │  │   (Keep / Pin / Delete Items)   │  │
│  ├─────────────────────────────┤  │  ├─────────────────────────────────┤  │
│  │   Neon Violet Neural Orb    │  │  │ • Quick Hardware Controls       │  │
│  │  ("Say 'Hi Moko' to wake")  │  │  │   (Torch, Lock, Bright, Sound)  │  │
│  ├─────────────────────────────┤  │  ├─────────────────────────────────┤  │
│  │ Live Chat & Memory Stream   │  │  │ • System Settings & Permissions │  │
│  │ [📌 Keep Memory] [🗑️ Delete]│  │  │   (Allow All, API Key, Models)  │  │
│  ├─────────────────────────────┤  │  └─────────────────────────────────┘  │
│  │ [🎙️ Voice ON] [💬 Message ] │  │                                       │
│  └─────────────────────────────┘  │                                       │
└─────────────────┬─────────────────┴───────────────────┬───────────────────┘
                  │                                     │
                  ▼                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│              MOKO STATE REPOSITORY & PERSISTENT MEMORY STORE              │
│  • Online / Sleep Mode State       • Saved Memory Facts (Auto + Pinned)   │
│  • Deletable Conversation History  • Encrypted Gemini API Key & Contacts  │
└─────────────────┬─────────────────────────────────────┬───────────────────┘
                  │                                     │
                  ▼                                     ▼
┌───────────────────────────────────┐ ┌─────────────────────────────────────┐
│   MOKO BACKGROUND VOICE SERVICE   │ │    MOKO BRAIN & DEVICE CONTROLLER   │
│  • "Hi Moko / Hey Moko" Wake Gate │ │  • Context + Memory Prompt Builder  │
│  • Screen-Off Partial WakeLock    │ │  • Hindi -> Roman Contact Matcher   │
│  • Hindi TTS Voice Synthesizer    │ │  • Real Call, SMS, Lock, Torch, etc.│
└───────────────────────────────────┘ └─────────────────────────────────────┘
```

- **Data Model & State**:
  - `MokoMemoryItem(id, factText, timestamp, isPinned)`: Represents a remembered fact or preference about the user that Moko includes in future conversations.
  - `CommandLogEntry(id, timestamp, userCommand, mokoResponse, actionType, actionSuccess, isSavedToMemory)`: Represents a single conversation turn in history that can be individually deleted or saved to memory.
  - `isOnlineMode: Boolean`: Controls whether the background service is actively running (`ONLINE`) or completely stopped (`SLEEP`).

- **Interactive Component & State Mapping**:
  - **Online / Sleep Toggle**: Switching to `ONLINE` verifies `RECORD_AUDIO`, starts the foreground service, acquires the screen-off wake lock, and enables *"Hi Moko"* wake detection. Switching to `SLEEP` stops the service and sets status to `SLEEP`.
  - **Voice & Message Bar**: Tapping the Mic button toggles active voice listening; typing in the Message box and tapping Send dispatches the text through the exact same Moko Brain + Hardware Controller pipeline with full memory context.
  - **History & Memory Management**: Tapping `🗑️` on any chat bubble or memory card deletes that specific item from state and persistent storage; tapping `📌` promotes a chat item into Moko's permanent Memory list; tapping `Clear All` wipes the history list.
