# MMS package

Our own MMS implementation, replacing the vendored `android-smsmms` module and
its `com.google.android.mms.pdu_alt` / `com.android.mms` classes.

Kotlin, in the `:mms` Gradle module, package root
`com.anindra.messages.mms`.

---

## Why

The stack being replaced is a vendored fork of klinker's Apache-2.0 AOSP MMS
implementation, trimmed to the 11 classes one 300-line file uses. It carries
confirmed defects that no amount of testing around it can fix, plus a dead-code
burden and a dependency on `org.apache.http.legacy` declared
`required="false"` — so on a device lacking that optional library, the HTTP path
throws `NoClassDefFoundError`.

The full catalogue, with causes and fixes, is in
[07-anti-patterns.md](07-anti-patterns.md). It is the reason this package exists
and the reason it is shaped as it is.

## Documents

| # | Document | Covers |
|---|---|---|
| 01 | [architecture.md](architecture.md) | Layers, dependency rules, the transport seam |
| 02 | [pdu-wire-format.md](02-pdu-wire-format.md) | **The contract.** WSP primitives, headers, multipart |
| 03 | provider-contract.md | `content://mms` schema, columns, URI shapes |
| 04 | send-path.md | Outbound, step by step |
| 05 | receive-path.md | WAP push through to notification |
| 06 | network-apn-carrier.md | APN, MMSC, binding, carrier profiles |
| 07 | [anti-patterns.md](07-anti-patterns.md) | What we are fixing, and what would fix it again |
| 08 | migration.md | Cutting over from `android-smsmms` |
| 09 | testing.md | What is asserted, and where |

Read 02 before touching `pdu/`. It is the specification the code implements; the
tests pin the parts of it that round-tripping cannot catch.

## Scope

**In scope**

- PDU encode and decode for all nine supported message types
- SMIL generation and parsing
- Persistence against the telephony provider
- Attachment fitting to a carrier's size and dimension limits
- Retry policy, carried over from the existing `MmsRetry` semantics
- Two transports behind one interface: direct-to-MMSC, and the platform
  `SmsManager`

**Not in scope, deliberately**

- **Mbox, Forward, Delete and Cancel PDUs** (message types above 0x88). These are
  stored-only server operations. They parse to null rather than being
  half-supported.
- **DRM.** `android.drm` is deprecated and the stack is absent from Play-enabled
  devices. An OMA DRM part is passed through as opaque bytes.
- **Pre-KitKat behaviour.** minSdk is 29; every older branch is deleted rather
  than maintained.
- **Rewriting the platform's `com.android.mms` service.** It owns the radio
  transaction. We own the bytes.
- **Owning the Telephony provider schema.** We are a client of it.

## Design rules

These are the rules that keep the package coherent. Each exists because breaking
it produced a real bug.

**`pdu/` and `smil/` import nothing from `android.*`.** This is what lets the
whole codec run under plain JUnit with no Robolectric. It is the single most
valuable constraint in the package and the easiest to lose by accident — a
`Uri` in `PduPart` or an `android.util.Log` in the parser silently reintroduces a
test-runner dependency for code that needs none.

**A table's index position is wire format.** The constrained-media table and the
header-field codes are not internal details; renumbering them changes what we put
on the radio. Tests pin positions, not just round-trips.

**The parser returns null. It never throws and never loops.** Its input is
whatever a carrier pushed at us. See the hostile-input table in 02.

**No allocation from an unvalidated length.** Check against the bytes remaining
first. This is the check that stops a hostile push becoming an allocation flood.

**One rule per decision, stated once.** HTTP success, retry classification, image
sizing and carrier limits each have exactly one owner. A second implementation is
a second answer waiting to disagree.

**Both transports ship.** The seam exists so a carrier that behaves differently
on one path is a setting change, not a hotfix.

## Testing

```
./gradlew :mms:testDebugUnitTest
```

Plain JUnit 4 across the whole module — no Robolectric, no mockk. Anything
needing Android framework objects is written against an injectable seam.

Coverage that matters most, in order:

1. **Round-trip.** Compose a realistic Send-req, parse it back, assert every
   header and part survives. A codec can be self-consistent and still wrong.
2. **Pinned byte fixtures.** Exact expected octets for small PDUs, which catch
   drift that a round-trip cannot.
3. **The hostile-input table** from 02 §7.
4. **Provider write ordering** — parts before the message row.
5. **Routing and budgets** in the fitter.

Every change to this package ships with a JUnit test here **and** a
`scripts/test-*.sh` regression script, per the repo's hard rules.
