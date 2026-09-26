# android-smsmms (vendored)

Trims klinker's MMS stack to the subset this app actually uses.

## Origin

- Upstream: `git@github.com:quik-sms/quik.git`, directory `android-smsmms/`
  (klinker's fork of the AOSP MMS implementation; `Transaction.java` and
  `PduComposer.java` carry Apache-2.0 headers, the wrapper files GPLv3 — this
  project is GPLv3, so both are compatible).
- Why vendored at all: the MMS PDU types (`com.google.android.mms.pdu_alt.*`,
  `com.android.mms.smil.SmilHelper`, `SmilXmlSerializer`) are AOSP-internal and
  absent from the public SDK, so an `m_SendReq` PDU cannot be built with
  framework calls. `SmsManager` only exposes `sendMultimediaMessage`, which
  takes an already-composed PDU. quik and Fossify Messages both ship this same
  stack.

## What is kept

Only the PDU codec + SMIL generation the app calls from
`app/src/main/java/com/anindra/messages/sms/MmsComposer.kt`:

- `com/google/android/mms/pdu_alt/**` — `SendReq`, `PduBody`, `PduPart`,
  `PduComposer`, `PduPersister`, `EncodedStringValue`, `CharacterSets`, …
- `com/google/android/mms/ContentType`, `.../smil/SmilHelper`
- `com/android/mms/dom/smil/**` + `layout/` + `logs/` (SMIL serialization deps)
- `android/**`, `org/**` support classes these reference
- `com/klinker/android/send_message/Settings` (one constant used by
  `PduPersister`) and `com/android/mms/service_alt/SubscriptionIdChecker`

## What was removed and why

- `res/` — 597 per-carrier `mms_config.xml` MCC/MNC profiles, read only by the
  MMS transaction service.
- `com/android/mms/transaction`, `service_alt` (except `SubscriptionIdChecker`),
  `util/DownloadManager`, `logs`/`layout` consumers, `MmsConfig` — the MMS
  network/transaction service. The framework's `SmsManager` already performs the
  transfer, so none of this is reachable from this app.
- `com/klinker/android/send_message/{Utils,Transaction,…}` — klinker's wrapper.
  `Utils` was the only thing the PDU code needed, and it dragged the whole
  network stack in; `SendReq.prepareFromAddress` now does the same MSISDN lookup
  inline (see the comment on `myPhoneNumberForSubscription`).
- The `okhttp` dependencies (only the removed service stack used them).
- Their `MmsFileProvider` from the library manifest — the app serves the composed
  PDU through its own FileProvider instead.

**This is the one local modification to upstream code**; keep the comment in
`SendReq.java` if re-vendoring.
