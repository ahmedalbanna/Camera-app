# Play Data Safety — Al-Adasa (العدسة) v2.0

**Data collected: NONE by default.** All processing on-device.

| Category | Collected | Encrypted | Optional |
|---|---|---|---|
| Camera / Photos / Videos | On-device only, MediaStore `DCIM/Al-Adasa` | No (user files) | No |
| Microphone | On-device for video, not stored separately | No | No |
| Location | Only if user enables in Pro EXIF opt-in (off by default) | In transit if enabled | Yes |
| AI (Gemini) | Only if ai_consent && gemini_enabled && Wi-Fi + valid key; every call logged; no PII without consent | TLS | Yes |

**Permissions:** CAMERA (required), RECORD_AUDIO (required for video), INTERNET/ACCESS_NETWORK_STATE (optional, for Gemini Wi-Fi only), WRITE_EXTERNAL_STORAGE maxSdk 28.

**Data sharing:** No sharing with third parties. No analytics. No ads.

**Deletion:** User deletes via in-app viewer (MediaStore delete with RecoverableSecurityException consent) or system gallery.

For questions: ahmedalbanna966@gmail.com
