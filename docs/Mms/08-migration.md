# 08 — Migration from `android-smsmms`

`android-smsmms` (the vendored klinker/AOSP stack) is gone. The app sends MMS
through the `:mms` module.

## What changed

| Before | After |
|---|---|
| `MmsComposer` built a `SendReq` with `pdu_alt`, persisted it with `PduPersister`, composed it with the vendored `PduComposer` | `MmsSender` reads the attachment and calls `Mms.send` (fit, build, persist, compose, hand to the platform transport) |
| `SmsSupport.sendMms` built the `PendingIntent` itself (`MMS_SENT`, extras: app message id, outbox uri, PDU file) | `SystemMmsTransport` builds it (`SEND_SENT`, extras: transaction id, subscription id, PDU file name) |
| Result matched to the app message by an extra | `MmsPendingSends` keeps `tr_id -> app message id`, written before the PDU is handed over |
| `MmsImageSizing` (app) | `fit/ImageSizing` (`:mms`), single owner |

## Send flow

1. `SmsSupport.sendMms` -> `MmsSender.send` reads the attachment (bounded).
2. `Mms.send` fits it to the carrier profile, builds the `M-Send.req`, persists
   the outbox row, composes the bytes and calls `SystemMmsTransport.send`.
3. Between persist and hand-over `MmsDiagnostics.sendStarted` fires with the
   outbox row; `MmsSender` stores `tr_id -> message id` there.
4. The platform answers with `SEND_SENT`; `SmsStatusReceiver` finds the outbox
   row by `tr_id`, moves it to Sent/Failed, deletes the PDU file and settles the
   app message.

## Not changed

Receiving (`MmsReceiver`, `MmsDownloader`, `MmsDownloadReceiver`) never used
the vendored stack and is untouched. Moving it onto `Mms.receive` is a separate step.

## Removed

`:android-smsmms`, its Gradle wiring, the ProGuard keep rules, the CodeQL
`paths-ignore`, `MmsComposer`, the app's `MmsImageSizing` and its test, and
`ComposerParserInteropTest` (it compared the new codec with the vendored one).
