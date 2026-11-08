# Moko AI Voice Assistant (Developed by MR. SHASHANK)

**Moko** एक आधुनिक Hindi + English Android AI Voice & Hardware Assistant है, जो Jetpack Compose (Material 3) और Kotlin में बना है।

---

## ✨ मुख्य फ़ीचर्स (Key Features)

1. **3-Mode Operating Architecture (`ONLINE` / `SLEEP` / `OFFLINE`)**:
   - **ONLINE Mode**: 60-सेकंड का कंटीन्यूअस बातचीत विंडो, नेचुरल मिड-सेंटेंस Barge-In (बीच में टोकने या नया सवाल पूछने पर तुरंत सुनना), Acoustic Self-Echo प्रोटेक्शन, और मल्टी-मॉडल Cloud AI + Kalpa Labs TTS सपोर्ट।
   - **SLEEP Mode**: ऑफलाइन हार्डवेयर, कॉलिंग, ब्लूटूथ और वॉल्यूम कंट्रोल (बिना क्लाउड Q&A के)।
   - **OFFLINE Mode**: बैकग्राउंड सर्विस और माइक्रोफोन पूरी तरह बंद।
2. **100% English Emotion-Aware YouTube & Music Coexistence**:
   - हिंदी या हिंग्लिश में बोले गए गानों और मूड (Sad, Romantic, Party, Devotional, Lofi, Bhojpuri) को अपने-आप **100% साफ़ English YouTube Search Queries** में बदलता है।
   - म्यूजिक चलते समय `FLAG_SHOW_UI` के बिना साइलेंट वॉल्यूम कंट्रोल करता है ताकि गाना कभी न रुके।
3. **Real Screen Share (`MediaProjection` + Gemini Vision) & Cross-App Voice Click (`AccessibilityService`)**:
   - यूज़र की अनुमति (MediaProjection Consent + AccessibilityService) के साथ असली स्क्रीन कैप्चर और विंडो टेक्स्ट का विश्लेषण।
   - `MokoAccessibilityService` के ज़रिए किसी भी बटन या UI एलिमेंट पर असली टच/क्लिक।
4. **Biometric / PIN Keyguard Unlock (`KeyguardManager.requestDismissKeyguard`)**:
   - कभी भी OS लॉक को बायपास करने का झूठा दावा नहीं करता — डिवाइस लॉक होने पर आधिकारिक Android Biometric/PIN प्रॉम्प्ट खोलता है।
5. **On-Device AES-256-GCM Encryption & Zero Cloud Backup**:
   - यूज़र का नाम, कॉन्टैक्ट्स, मेमोरी वॉल्ट, कमांड हिस्ट्री (अधिकतम 50 एंट्रीज़), और API Keys को `AndroidKeyStore` (`AES/GCM/NoPadding`) से एन्क्रिप्ट करके रखता है।
   - `android:allowBackup="false"` और `backup_rules.xml` / `data_extraction_rules.xml` के ज़रिए क्लाउड बैकअप से पूरी तरह बाहर।

---

## 🔑 API Keys और `.env` सेटअप (Secrets Configuration)

सोर्स कोड में कोई भी API Key हार्डकोड नहीं है। अपनी API Keys को सुरक्षित रूप से सेट करने के लिए:

1. **AI Studio Secrets Panel** या लोकल `.env` फ़ाइल (`.env.example` से कॉपी करें) में ये कुंजियाँ जोड़ें:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   KALPA_API_KEY=your_kalpa_labs_api_key_here
   ```
2. या ऐप के अंदर **Side Menu ➔ Settings** में जाकर अपनी Gemini / OpenAI / Claude / Kalpa Labs API Key सेव करें (जो डिवाइस पर AES-256-GCM से एन्क्रिप्ट होती है)।
3. **Security Header**: Gemini API कॉल में कुंजी कभी भी URL query (`?key=`) में नहीं भेजी जाती, बल्कि हमेशा `x-goog-api-key` HTTP header में भेजी जाती है।

---

## 🔐 Release APK / AAB Signing Configuration

`app/build.gradle.kts` में `release` signingConfig निम्नलिखित Environment Variables का उपयोग करता है:

| Environment Variable | विवरण (Description) |
| :--- | :--- |
| `KEYSTORE_PATH` | आपके रिलीज़ `.jks` या `.keystore` फ़ाइल का पाथ |
| `STORE_PASSWORD` | Keystore का पासवर्ड |
| `KEY_PASSWORD` | Key alias (`upload`) का पासवर्ड |

> **Note:** Keystore के अंदर key alias का नाम **`upload`** होना चाहिए।

### स्थानीय रूप से Release Keystore बनाने का कमांड:
```bash
keytool -genkeypair -v \
  -keystore release-upload.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### Release Build चलाने का कमांड:
```bash
export KEYSTORE_PATH="/path/to/release-upload.jks"
export STORE_PASSWORD="your_store_password"
export KEY_PASSWORD="your_key_password"
gradle assembleRelease
```

---

## 🛡️ Android Permissions & Play Store Policy Justification

ऐप में केवल वही परमिशन मांगी गई हैं जो सीधे यूज़र के वॉइस कमांड्स के लिए आवश्यक हैं:

- `RECORD_AUDIO` & `FOREGROUND_SERVICE_MICROPHONE`: यूज़र की स्पष्ट सहमति (Voice & Cloud Privacy Consent) के बाद वॉइस कमांड और वेक-वर्ड (`Hi Moko`) सुनने के लिए।
- `CALL_PHONE` & `READ_CONTACTS`: यूज़र के बोलने पर सीधे कॉन्टैक्ट्स को कॉल करने के लिए (SMS के लिए `SEND_SMS` परमिशन हटा दी गई है और सुरक्षित `ACTION_SENDTO` `smsto:` Intent का उपयोग किया गया है)।
- `BLUETOOTH_CONNECT` (API 31+) & `BLUETOOTH`/`BLUETOOTH_ADMIN` (`maxSdkVersion="30"`): ब्लूटूथ ऑन/ऑफ स्टेटस और कंट्रोल के लिए।
- `RECEIVE_BOOT_COMPLETED`: फ़ोन रीस्टार्ट होने पर यूज़र के चुने हुए मोड को रीस्टोर करने या नोटिफिकेशन दिखाने के लिए (`MokoBootReceiver`)।
- `BIND_ACCESSIBILITY_SERVICE` (`MokoAccessibilityService`): केवल यूज़र द्वारा Accessibility Settings से ऑन करने पर वॉइस क्लिक और स्क्रीन टेक्स्ट रीडिंग के लिए।
- `BIND_DEVICE_ADMIN` (`SuaDeviceAdminReceiver`): केवल यूज़र द्वारा ऑन करने पर "Lock Screen" वॉइस कमांड के लिए।

---

## 🧪 Testing & CI Verification

यूनिट और Robolectric टेस्ट चलाने के लिए:
```bash
gradle :app:testDebugUnitTest
```
Debug APK बिल्ड करने के लिए:
```bash
gradle assembleDebug
```
