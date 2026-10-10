# MMS PDU wire format

The binary format is WSP (WAP-230) as profiled by OMA MMS Encapsulation
(3GPP TS 23.140). A PDU is:

```
<headers>  [<multipart body> if the type carries one]
```

Every header is a **field-code octet** immediately followed by its value. The
field code both names the field and determines how the value is encoded, so a
decoder must know the per-field type before it can read the value. That table
lives in `HeaderField.Kind`.

This document is the contract for `pdu/`. Where it disagrees with an
implementation, this document is right.

---

## 1. WSP primitives

Implemented in `Wsp.kt`. Encoding column is what `WspWriter` emits.

| Type | Encoding | Notes |
|---|---|---|
| Octet | one raw byte | |
| Short-integer | `0x80 or value`, low 7 bits | value must be 0..0x7F |
| Short-length | one byte, 0..30 | 31 (`LENGTH_QUOTE`) is reserved |
| Value-length | `<31` → one byte; else `0x1F` + uintvar | |
| Uintvar | 1–5 octets, 7 bits each, most significant group first, `0x80` set on all but the last | >5 octets is rejected |
| Long-integer | short-length octet + that many big-endian octets | minimal octets, max 8 |
| Integer-value | short-integer if it fits, else long-integer | the leading octet is the discriminator |
| Text-string | optional `0x7F` quote when the first byte is >0x7F, bytes, `0x00` | |
| Quoted-string | `0x22`, bytes, `0x00` | |
| Encoded-string-value | value-length, charset short-integer, text-string | see §5 |
| Constrained-media | short-integer index into `ContentTypes.TABLE` | else a text-string |

**Long-integer note.** `0` is one octet of `0x00`, *not* zero octets — a
zero-length long-integer is not a valid encoding.

**Uintvar note.** The first octet carries the most significant 7-bit group
(WAP-230 §3.1), so 128 is `0x81 0x00`, not `0x80 0x01`. A pair that disagrees
with the reference stack in the group order still round-trips in-house and is
then rejected by a carrier.

---

## 2. Message types

`X-Mms-Message-Type` (0x8C) is an **octet**. Only 0x80–0x88 are supported; the
Mbox/Forward/Delete/Cancel range above 0x88 parses to null.

| Value | Type |
|---|---|
| 0x80 | M-Send.req |
| 0x81 | M-Send.conf |
| 0x82 | M-Notification.ind |
| 0x83 | M-NotifyResp.ind |
| 0x84 | M-Retrieve.conf |
| 0x85 | M-Acknowledge.ind |
| 0x86 | M-Delivery.ind |
| 0x87 | M-ReadRec.ind |
| 0x88 | M-ReadOrig.ind |

## 3. Composing types

Only these are built outbound. Anything else composes to `null`.

**Header order.** Every other field may appear anywhere in the header block
(the composer emits the rest in ascending field-code order), but
`X-Mms-Content-Type` must be the **last** header: receivers — including the
production reference parser — stop reading headers at it, so a Content-Type
emitted first turns every remaining mandatory header into body bytes.

| Type | Required headers |
|---|---|
| M-Send.req | MMS-Version, Content-Type, From, Transaction-Id, and at least one of To/Cc/Bcc |
| M-NotifyResp.ind | MMS-Version, Status, Transaction-Id |
| M-Acknowledge.ind | MMS-Version, Transaction-Id |
| M-ReadRec.ind | MMS-Version, From, Message-Id, Read-Status, To |

Missing mandatory headers fail composition. A missing Transaction-Id is a
programming error, not a runtime condition.

## 4. Field encodings worth stating explicitly

Most fields follow `HeaderField.Kind` mechanically. These do not:

**MMS-Version (0x8D)** — octet field code, then a **short-integer**, not an
octet. `1.2` is `0x12` on the wire.

**From (0x89)** — two forms, chosen by whether an address is known:
```
no address:   0x89 <value-length 0x01> <0x81>
with address: 0x89 <value-length> <0x80> <encoded-string-value>
```
`0x81` is INSERT-ADDRESS-TOKEN and `0x80` is ADDRESS-PRESENT-TOKEN. The
no-address form is the single octet `0x81` — not a token string.

**To / Cc / Bcc (0x97 / 0x82 / 0x81)** — repeating. Each occurrence is its own
field-code octet followed by its encoded-string-value, so a group message
repeats 0x97 once per recipient. These must never be collapsed into one value.

**Expiry (0x88) / Delivery-Time (0x87)** — value-length wrapping a token
followed by a long-integer:
```
0x88 <value-length> <0x81 relative-token> <long-integer seconds>
```
`0x80` is ABSOLUTE-TOKEN, `0x81` RELATIVE-TOKEN. Relative values are seconds
from now and must be resolved against the clock on decode.

**Message-Class (0x8A)** — either an octet (0x80–0x83) or a token string
(`personal`, `advertisement`, `informational`, `auto`). Try the octet form when
the leading octet is >= 0x80; otherwise rewind and read a text string.

**Content-Type (0x84)** — constrained-media code followed by zero or more
parameters (§6).

**Subject (0x96)** — encoded-string-value, so it carries its own charset.

## 5. Encoded-string-value

```
<value-length> <charset short-integer> <text-string>
```

The bytes inside the text-string are in the **declared** charset, not UTF-8.
`EncodedStringValue` keeps them raw for this reason: decoding to a Kotlin
`String` and re-encoding as UTF-8 would silently rewrite every non-ASCII message.
`text` is a decoded view only.

## 6. Multipart body

WSP multipart is **length-delimited, not MIME-boundary delimited**. There is no
boundary string anywhere in a PDU.

Only M-Send.req and M-Retrieve.conf carry a body.

```
Content-Type field:
  0x84 <value-length>
         <constrained-media multipart code>     (0x33 related, 0x0C mixed)
         [0x8A start  <text-string>]             first part's content-id, angle-bracketed
         [0x89 type   <text-string>]             first part's content-type

<uintvar entry-count>
repeat entry-count times:
  <uintvar header-length>
  <uintvar data-length>
  <part headers, exactly header-length octets>
  <part data,    exactly data-length octets>
```

The content-type of the message as a whole **must** be a multipart code; a body
under a non-multipart content type is a decode failure.

### Part headers

```
<value-length>
       <constrained-media or text-string content-type>
       [0x85 name    <text-string>]
       [0x81 charset <short-integer MIBenum>]

[0xC0 content-id         <quoted-string, angle brackets added if absent>]
[0x8E content-location   <text-string>]
[0xC8 transfer-encoding  <text-string>]
```

Name, filename and content-location are interchangeable; a part needs **at least
one** or it cannot be re-composed (which is what breaks forwarding). `0x85` is
emitted even when the value came from filename or content-location.

Part header codes, for reference:

| Code | Meaning |
|---|---|
| 0x81 | charset |
| 0x83 | content-type |
| 0x85 | name / filename / content-location |
| 0x86 | filename |
| 0x8E | content-location |
| 0x89 | content-type (in the message-level parameters) |
| 0x8A | start (in the message-level parameters) |
| 0xC0 | content-id |
| 0xC5 | content-disposition |
| 0xC8 | content-transfer-encoding |

## 7. Decoding hostile input

The parser's contract, from the fuzz vectors that define it:

**Return null. Never throw. Never loop. Never allocate from an unvalidated
length.**

Concretely, all of these must yield `null` rather than a crash, a hang, or an
exception:

| Input | Required behaviour |
|---|---|
| Constrained-media index past the table | decode to the wildcard |
| Constrained-media index at the last code | decode to that type, not the wildcard |
| Negative `header-length - bytesConsumedByContentType` | reject the part |
| `data-length` greater than the bytes remaining | reject **before** allocating |
| Oversized content-type value-length | abandon the structure |
| A `From` value-length with no following bytes | reject |
| Any truncated header, at any offset | reject |

A part with no name, filename, content-location or content-id is given a
synthesised name on decode, because carriers do send such parts and they must
still be forwardable.

`multipart.alternative` is flattened: the first child part replaces the wrapper.
Ordering follows the content-type `start` parameter when present.
