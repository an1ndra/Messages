# MMS anti-patterns

Every entry is a defect confirmed in a shipping MMS stack we are replacing, or a
trAP the code we wrote invites. Each is: the cause, the symptom it produces, and
what this package does instead.

The first section is the reason this package exists. The second is the reason it
is shaped the way it is.

---

## Part 1 — Defects in the stack being replaced

Sourced from `quik-sms/quik` (klinker's fork), `GrapheneOS/Messaging`, and
`FossifyOrg/Messages`. All three ship the same lineage.

### Silent data loss

**A duplicate-suppression check that can never return true.** The decisive
`return true;` is commented out, so every inbound notification is treated as new
and the cursor is closed twice.
→ Symptom: duplicate messages, and an `IllegalStateException` risk.
→ Here: dedup is a query the store owns, and it is unit-tested.

**A transfer helper that returns false on success.** The persist/transfer result
is hard-coded false, so the caller overwrites a good result with an I/O error.
→ Symptom: successfully downloaded MMS reported as failed, then retried forever.
→ Here: the HTTP result type separates `Success` from `Failure`; returning
  failure from a success path is not expressible.

**A failed-send update that matches nothing.** An in-source TODO admits the
`content://mms-sms/pending` update silently matches no rows.
→ Symptom: failed MMS are never retried, and the user sees a permanent failure.
→ Here: retry state is written through one function, and a test asserts the row
  exists afterwards.

### Acknowledgements never sent

**`sendAcknowledgeInd` commented out** in the download path, and a receive
receiver whose `getMmscInfoForReceptionAck()` returns null — which short-circuits
the whole notification task. Its own documentation admits carriers will
re-deliver without the ack.
→ Symptom: duplicate delivery, or a message that hangs as un-acknowledged.
→ Here: every terminal branch of the receive path answers the notification —
  retrieved, deferred, or expired. That is a structural requirement, tested.

### Logic that is simply wrong

- `RetryScheduler.getRetrieveStatus()` queries `RESPONSE_STATUS` where
  `RETRIEVE_STATUS` is meant, so its special case never fires.
- `TransactionState.setState` validates with `&&` where `||` is meant, so it
  never throws.
- `Content-Length`-style arithmetic in the part parser can go negative and is
  passed onward as a length.

→ Here: the parser rejects a negative computed header length outright, and that
  case is a named test.

### Security and robustness

- `grantUriPermission` is passed an **authority** where a **package name** is
  required. Nothing is ever granted.
- A file provider opens `new File(cacheDir, uri.getPath())` with **no path
  validation**.
- When the MMS network request fails, the client **silently substitutes a
  non-MMS network**.
- The HTTPS proxy selector **dereferences its proxy unconditionally**, so any
  proxyless HTTPS retrieve throws.
- Download temp files are named from `Math.abs(Random.nextLong())` — collisions
  overwrite each other, unlike the send path's UUID.

→ Here: network binding returns null rather than falling back; proxy is applied
  only when the profile has one; temp names are UUIDs.

### Dead weight

Roughly 60% of the module is unreachable: `TransactionService` is declared in the
manifest and **never started**, taking `SendTransaction`, `RetrieveTransaction`,
`RetryScheduler`, `HttpUtils` and `MmsMessageSender` with it. Alongside it sit
~12 hand-copied hidden framework classes with `UnsatisfiedLinkError` native
methods, an **OkHttp 2.5.0** dependency on `com.squareup.okhttp.internal.*`
internals, and `android.drm` code constructing a `DrmManagerClient` for a stack
removed from Play devices.

**`org.apache.http.legacy` is declared `required="false"`.** On a device without
it, every HTTP path throws `NoClassDefFoundError`. This is in our build today.

→ Here: none of it. OkHttp 4, no hidden-API copies, no DRM path, no optional
system library.

### Android rot

- The request path calls `wifi.setWifiEnabled(false/true)` around every
  transaction, clobbering the user's setting and silently failing on modern
  Android.
- `setMobileDataEnabled` is called around every send and receive; the source
  comments state it does not work.
- A `PendingIntent` built without a mutability flag throws
  `IllegalArgumentException` on Android 12+.
- `TelephonyManager.getDataState()`, used to decide auto-download, is deprecated
  since API 29.
- Every `SDK_INT < KITKAT` and `< LOLLIPOP` branch is dead at minSdk 29 but is
  still maintained and still wrong.

→ Here: no Wi-Fi toggling, no data-enable calls, every `PendingIntent` carries
  an explicit mutability flag, and there are no pre-KitKat branches.

### Two rules for the same thing

The library contains an Apache HTTP stack that requires exactly 200 and an OkHttp
stack that accepts any 2xx. Both are live paths for different configurations, so
"did the send succeed" has two answers in one codebase.

This is the exact failure the repo already documents for theme resolution, where
a report and the renderer disagreed. **A decision that appears twice will
eventually disagree.**

→ Here: `MmscHttpClient` states one success rule, in one place, with a test.

---

## Part 2 — Traps in the code we wrote

These are not from the old stack. They are places where a correct-looking
implementation still goes wrong.

**A nested block comment closes the wrong brace.** A literal `*/` inside a KDoc —
which happens naturally when documenting a wildcard MIME type — terminates the
inner comment and strands the outer one, because Kotlin block comments nest. The
file then fails to compile with an unrelated-looking error.
→ Never write `*/` or `/*` in a comment. Reference the constant.

**Setters and fallbacks with the same signature.** `fun octet(field: Int, value: Int)`
and `fun octet(field: Int, fallback: Int)` are distinct in Kotlin and identical
in the JVM. The module compiles clean in Kotlin and then fails to link.
→ Setters are `setX`, lookups are `xOrNull` / `xOr(fallback)`.

**Checking a field in the wrong type store.** `MMS-Version` is a *short-integer*
on the wire but is held as an octet. The mandatory-header check looked in the long
store and rejected every PDU.
→ The type store is derived from the declared `HeaderField.Kind`, never guessed.

**Decoding to a String and re-encoding.** Holding an `Encoded-string-value` as a
Kotlin `String` and re-encoding as UTF-8 on the way out silently rewrites every
non-ASCII message. Nothing fails; the recipient sees mojibake.
→ `EncodedStringValue` keeps raw bytes. `text` is a view, not the wire form.

**A quote byte counted in one place and not the other.** A text string whose first
byte exceeds 0x7F needs a leading `0x7F` quote. If `encodedLength()` omits it
while the writer emits it, every enclosing length is off by one.
→ The length is computed by the same rule that writes the bytes, and a test
  compares them.

**Peeking then re-reading.** `peekOctet()` followed by `index++` *and* a
`readShortInteger()` consumes two octets where one was meant, decoding every
constrained-media code as the wrong type — while still "working".
→ Peek to decide, then read exactly once.

**Treating 2xx and 200 as the same rule in two layers.** See above.

**A relative time token stored as an absolute time.** `Expiry` may arrive as a
relative token. Storing it in the long field makes an expiry of "0x81" look like
a date in 1970.
→ Relative tokens are held separately and resolved once, at parse time.

**An allocation sized by an unvalidated length.** The single highest-value check
in the parser: a forged `data-length` must be compared against the bytes
remaining *before* allocating, or a hostile push becomes an allocation flood.

**A silent fallback instead of a typed failure.** An APN that resolves to nothing
produces an empty MMSC, which surfaces much later as a `MalformedURLException` in
the HTTP client. The real problem — no APN — is never named.
→ Return a typed `INVALID_APN` and say so.

---

## Part 3 — Platform constraints, not bugs

Recorded so nobody spends time trying to "fix" them.

**`NET_CAPABILITY_MMS` binding generally requires being the default SMS app.** The
direct-MMSC transport is expected to work only in that configuration. This is a
platform policy, and it is why a second transport ships: when a carrier behaves
differently, it is a setting rather than a hotfix.

**The MMSC URL is not ours to resolve.** The platform resolves it from the
telephony APN database. Our own resolver reads the same provider table, which
access rules have progressively tightened.

**Response PDUs are addressed to the content-location, not always the MMSC.** Only
when the carrier sets `enabledNotifyWapMMSC`. Getting this wrong means the ack
goes nowhere and the carrier re-delivers.
