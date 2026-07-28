# Nexa Admin Frontend Implementation

## Purpose

Implement a protected administration area for the Nexa frontend. The backend
already exposes administrator APIs for:

- User management
- KYC document review
- Dispute management
- Support ticket management
- Audit logs
- Administrative account deposits

Use the `/api/v1/...` endpoint versions throughout the frontend.

## Important backend prerequisites

### Administrator account

The database migration creates the `ROLE_ADMIN` role, but it does not create an
administrator user or expose an API for promoting a user.

An administrator account must be provisioned operationally before this frontend
can be tested. Do not add hard-coded admin credentials to the frontend.

### Authentication

The backend uses server-side sessions and MFA. Every API request must include
credentials:

```ts
fetch(url, {
  credentials: "include",
});
```

After OTP verification, request the current profile:

```http
GET /api/v1/profile
```

Only show and allow admin routes when:

```json
{
  "role": "ROLE_ADMIN"
}
```

Do not rely only on hidden navigation. Every admin route needs a role guard.

### CSRF protection

All authenticated state-changing requests require a CSRF token.

Fetch a token:

```http
GET /api/v1/auth/csrf
```

Example response:

```json
{
  "headerName": "X-XSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "csrf-token-value"
}
```

Send the returned token in the named header for `POST`, `PUT`, `PATCH`, and
`DELETE` requests:

```ts
fetch("/api/v1/admin/kyc/12/approve", {
  method: "POST",
  credentials: "include",
  headers: {
    "X-XSRF-TOKEN": csrfToken,
  },
});
```

Refresh the CSRF token after login and when a request indicates that the token
is no longer valid.

### Logout

```http
POST /api/v1/auth/logout
```

Send the CSRF header and session credentials. After success, clear frontend
authentication state and navigate to `/login`.

## Admin navigation

Create a compact, work-focused admin shell with these routes:

| Frontend route | Navigation label | Purpose |
| --- | --- | --- |
| `/admin` | Overview | Operational queues and shortcuts |
| `/admin/kyc` | KYC Review | Review pending identity documents |
| `/admin/users` | Users | Search users and unlock accounts |
| `/admin/disputes` | Disputes | Investigate and resolve disputes |
| `/admin/support` | Support | Manage customer support tickets |
| `/admin/audit` | Audit Log | Inspect system activity |
| `/admin/deposits` | Deposits | Optional administrative funding tool |

The shell should use a persistent desktop sidebar and a compact mobile drawer.
Include the signed-in administrator's name and a logout command. Do not expose
normal customer money-movement controls as admin navigation.

## Route behavior

Use this guard sequence for every `/admin/*` route:

1. If there is no authenticated session, navigate to `/login`.
2. Request `GET /api/v1/profile`.
3. If the response role is not `ROLE_ADMIN`, navigate to the normal dashboard
   or a dedicated access-denied page.
4. If the role is `ROLE_ADMIN`, render the admin route.

Handle these security responses globally:

| HTTP status | Code | Frontend behavior |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Clear local auth state and navigate to `/login` |
| `403` | `ACCESS_DENIED` | Show an access-denied message and leave the admin area |
| `423` | `ACCOUNT_LOCKED` | Show the locked-account message |

## Shared API error format

Controller errors generally use:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "One or more request fields are invalid",
  "status": 400,
  "path": "/api/v1/example",
  "timestamp": "2026-07-28T12:00:00Z",
  "fieldErrors": {
    "reason": "must not be blank"
  },
  "details": {}
}
```

Show `message` in the page or dialog. Map `fieldErrors` to their corresponding
form controls. Do not display raw stack traces or replace actionable API
messages with a generic error.

## Overview page

There is no dedicated admin dashboard endpoint. Build the overview from these
existing requests:

```http
GET /api/v1/admin/users
GET /api/v1/admin/kyc
GET /api/v1/admin/disputes?status=OPEN&page=0&size=5
GET /api/v1/admin/support/tickets?page=0&size=5
GET /api/v1/admin/audit?limit=10
```

Display:

- Pending KYC count
- Locked user count
- Open dispute count from the dispute page metadata
- Recent support tickets
- Recent audit activity

Each section must link to its full management page. If one request fails, keep
the other overview sections usable and show a retry action only for the failed
section.

## Users page

### List users

```http
GET /api/v1/admin/users
```

Response:

```json
[
  {
    "id": 8,
    "username": "alex",
    "email": "alex@example.com",
    "fullName": "Alex Morgan",
    "phoneNumber": "+15551234567",
    "address": "Customer address",
    "role": "ROLE_USER",
    "kycStatus": "PENDING",
    "enabled": true,
    "locked": false,
    "createdAt": "2026-07-28T10:30:00"
  }
]
```

The page should provide:

- Search by username, full name, email, or user ID
- Filters for role, KYC status, enabled status, and locked status
- Columns for user, contact, role, KYC status, account status, and created date
- A user detail drawer or dialog
- An unlock command only when `locked` is `true`

The backend returns all users without pagination. Filtering and pagination on
this page must currently be client-side.

### Unlock a user

```http
PUT /api/v1/admin/users/{userId}/unlock
```

Success response:

```json
{
  "message": "User unlocked"
}
```

After success, update the row to `locked: false` or refetch the list.

The backend does not currently support disabling users, deleting users,
changing roles, resetting passwords, or editing customer profiles from this
page. Do not render controls for unsupported operations.

## KYC review page

KYC is required before users can send money. A submitted document has status
`PENDING` until an administrator approves or rejects it.

### Pending queue

```http
GET /api/v1/admin/kyc
```

Response:

```json
[
  {
    "id": 12,
    "userId": 8,
    "filename": "stored-document-name.pdf",
    "contentType": "application/pdf",
    "status": "PENDING",
    "rejectionReason": null,
    "uploadedAt": "2026-07-28T18:15:00Z",
    "reviewedAt": null
  }
]
```

This endpoint returns pending documents only. Join `userId` with the users
response when the interface needs a customer name or email.

Show:

- Customer name, username, email, and user ID
- Document type based on `contentType`
- Upload date
- Current status
- View document action
- Approve action
- Reject action

### View or download a document

```http
GET /api/v1/admin/kyc/{documentId}/document
```

Fetch the response with `credentials: "include"` and handle it as a `Blob`.
Preview PDF, JPEG, and PNG documents in a secure modal or new browser tab.
Always provide a download action. Revoke temporary object URLs when the preview
closes.

Do not place approval buttons over the document preview. Keep the decision
controls in a stable action bar beside or below the preview.

### Approve KYC

```http
POST /api/v1/admin/kyc/{documentId}/approve
```

No request body is required.

Require confirmation before approval. On success:

- Show a success notification
- Remove the document from the pending queue
- Refresh the corresponding user's KYC status
- Close the review view or move to the next pending document

Approval changes the user's KYC status to `APPROVED`, which enables protected
banking operations without requiring the user to log out.

### Reject KYC

```http
POST /api/v1/admin/kyc/{documentId}/reject
Content-Type: application/json

{
  "reason": "The submitted identification document has expired."
}
```

The rejection reason is required. Use a modal with a multiline input and
disable submission while the reason is blank.

On success, remove the document from the pending queue and show a confirmation.
The user status becomes `REJECTED`, allowing the customer-facing KYC page to
request a replacement document.

### KYC page states

Implement:

- Loading skeleton
- Empty queue with a simple "No pending KYC reviews" state
- Document loading and preview failure
- Approving state
- Rejecting state
- Per-item API error
- Queue refresh

Prevent duplicate decisions by disabling both decision controls while either
request is running.

## Disputes page

### List disputes

```http
GET /api/v1/admin/disputes?status=OPEN&page=0&size=20
```

`status` is optional. Valid values:

- `OPEN`
- `UNDER_REVIEW`
- `EVIDENCE_REQUESTED`
- `RESOLVED_CUSTOMER`
- `RESOLVED_MERCHANT`
- `WITHDRAWN`

The response is a Spring `Page` object:

```json
{
  "content": [],
  "number": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

The backend limits `size` to 100.

Show:

- Case reference
- Customer name and ID
- Transaction reference
- Reason
- Amount and currency
- Status
- Provisional credit indicator
- Created and updated dates

### Dispute detail

```http
GET /api/v1/admin/disputes/{disputeId}
```

Response fields:

```json
{
  "id": 31,
  "caseReference": "DSP-ABC123456789",
  "userId": 8,
  "userName": "Alex Morgan",
  "transactionId": 90,
  "transactionReference": "TX-REFERENCE",
  "reason": "UNAUTHORIZED",
  "description": "Customer description",
  "amount": 120.50,
  "currency": "USD",
  "status": "OPEN",
  "provisionalCreditGranted": false,
  "provisionalCreditTransactionId": null,
  "clawbackTransactionId": null,
  "resolutionNote": null,
  "resolvedAt": null,
  "createdAt": "2026-07-28T18:15:00Z",
  "updatedAt": "2026-07-28T18:15:00Z"
}
```

### Update investigation status

```http
PATCH /api/v1/admin/disputes/{disputeId}
Content-Type: application/json

{
  "status": "UNDER_REVIEW",
  "note": "Transaction evidence is being reviewed."
}
```

Use this endpoint for investigation updates. Do not use it to select
`RESOLVED_CUSTOMER` or `RESOLVED_MERCHANT`; the backend requires the resolve
endpoint for those outcomes.

The note is optional and has a maximum length of 2,000 characters.

### Grant provisional credit

```http
POST /api/v1/admin/disputes/{disputeId}/provisional-credit
```

No request body is required.

This operation moves money and must use a high-friction confirmation dialog
showing:

- Customer
- Case reference
- Amount
- Currency
- Source transaction

Only enable it while the dispute is open and
`provisionalCreditGranted` is `false`.

### Resolve a dispute

```http
POST /api/v1/admin/disputes/{disputeId}/resolve
Content-Type: application/json

{
  "inFavourOfCustomer": true,
  "resolutionNote": "The transaction was confirmed as unauthorized."
}
```

The outcome and resolution note are required.

Resolution behavior:

| Outcome | Backend result |
| --- | --- |
| Customer | Status becomes `RESOLVED_CUSTOMER`; the customer receives a refund if provisional credit was not already granted |
| Merchant | Status becomes `RESOLVED_MERCHANT`; any provisional credit is reclaimed |

The merchant outcome can fail with `409 CONFLICT` if a required clawback cannot
be completed. Keep the dispute open in the UI and display the backend message.

Closed and withdrawn disputes must be read-only.

## Support page

### List tickets

```http
GET /api/v1/admin/support/tickets?page=0&size=20
```

The response is a Spring `Page` object. List responses contain
`messages: []`; request the ticket detail before displaying the conversation.

Ticket fields:

```json
{
  "id": 45,
  "userId": 8,
  "userName": "Alex Morgan",
  "subject": "Transfer question",
  "category": "TRANSACTION",
  "priority": "NORMAL",
  "status": "OPEN",
  "resolution": null,
  "assignedAdminId": null,
  "assignedAdminName": null,
  "messageCount": 2,
  "messages": [],
  "createdAt": "2026-07-28T18:15:00Z",
  "updatedAt": "2026-07-28T18:20:00Z"
}
```

Valid priorities:

- `LOW`
- `NORMAL`
- `HIGH`
- `URGENT`

Valid statuses:

- `OPEN`
- `IN_PROGRESS`
- `WAITING_FOR_CUSTOMER`
- `RESOLVED`
- `CLOSED`

Valid categories:

- `ACCOUNT`
- `TRANSACTION`
- `CARD`
- `KYC`
- `LOAN`
- `DISPUTE`
- `TECHNICAL`
- `OTHER`

### Ticket detail

```http
GET /api/v1/admin/support/tickets/{ticketId}
```

Render messages chronologically. Message fields include:

```json
{
  "id": 101,
  "authorUserId": 8,
  "authorName": "Alex Morgan",
  "fromSupport": false,
  "internalNote": false,
  "body": "Message text",
  "createdAt": "2026-07-28T18:20:00Z"
}
```

Internal notes are included for administrators and must be visually distinct
from messages visible to the customer.

### Reply or add an internal note

```http
POST /api/v1/admin/support/tickets/{ticketId}/messages
Content-Type: application/json

{
  "body": "We are reviewing this issue.",
  "internalNote": false
}
```

Use a clear toggle between:

- Customer reply: `internalNote: false`
- Internal note: `internalNote: true`

A customer reply to an `OPEN` ticket changes it to `IN_PROGRESS` and notifies
the customer. An internal note does not notify the customer.

Disable customer replies for `RESOLVED` and `CLOSED` tickets unless the ticket
is first moved back to an active status.

### Update ticket

```http
PATCH /api/v1/admin/support/tickets/{ticketId}
Content-Type: application/json

{
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assignedAdminId": 2,
  "resolution": "Optional resolution summary"
}
```

All fields are optional. Obtain administrator IDs from
`GET /api/v1/admin/users` and filter the response to `role === "ROLE_ADMIN"`.

When moving a ticket to `RESOLVED` or `CLOSED`, request a resolution summary in
the frontend even though the backend does not currently require it.

## Audit log page

```http
GET /api/v1/admin/audit
```

Optional query parameters:

| Parameter | Format | Meaning |
| --- | --- | --- |
| `userId` | Integer | Only events for one user |
| `action` | String | Only events matching an action |
| `startDate` | `YYYY-MM-DD` | Start of date range |
| `endDate` | `YYYY-MM-DD` | End of date range |
| `limit` | Integer | Maximum returned rows |

Response:

```json
[
  {
    "id": 300,
    "userId": 8,
    "action": "DISPUTE_RESOLVED",
    "details": "{disputeId=31, outcome=RESOLVED_CUSTOMER}",
    "timestamp": "2026-07-28T18:30:00",
    "ipAddress": null
  }
]
```

The `details` field is currently a display string, not guaranteed JSON. Render
it as preformatted text and do not pass it to `JSON.parse`.

Provide filters, a reset command, refresh, and a dense chronological table.
The endpoint is not paginated, so use `limit` to prevent unnecessarily large
responses.

## Administrative deposits

The backend exposes:

```http
POST /api/v1/transactions/deposit
Content-Type: application/json
Idempotency-Key: unique-client-generated-value

{
  "accountId": 25,
  "amount": 100.00,
  "description": "Approved account adjustment",
  "category": "ADMIN_ADJUSTMENT"
}
```

This endpoint requires `ROLE_ADMIN`. `accountId` and an amount of at least
`0.01` are required. The destination customer's KYC status must be `APPROVED`.

Generate and retain one idempotency key per attempted deposit so retrying a
timed-out request does not create a duplicate credit.

Require a confirmation dialog containing the account ID, amount, description,
and category.

### Current limitation

The current admin user response does not include customer account IDs, and
there is no admin endpoint for listing another user's accounts. A polished
deposit workflow cannot discover the destination account from the existing
admin APIs.

Until the backend adds an admin customer-account lookup endpoint, either:

- Hide the deposit page, or
- Treat it as an advanced tool requiring a known numeric account ID

Do not confuse `userId` with `accountId`.

## Loading, mutation, and empty states

Every admin page must implement:

- Initial loading state
- Empty state
- Request failure with retry
- Mutation-in-progress state
- Success confirmation
- Prevention of duplicate submissions
- Refresh after a successful mutation

Keep table dimensions stable while loading. Use dialogs for destructive or
money-moving decisions and drawers or full pages for record details.

## Recommended client data types

```ts
type Role = "ROLE_USER" | "ROLE_ADMIN";
type KycStatus = "NOT_SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED";

type DisputeStatus =
  | "OPEN"
  | "UNDER_REVIEW"
  | "EVIDENCE_REQUESTED"
  | "RESOLVED_CUSTOMER"
  | "RESOLVED_MERCHANT"
  | "WITHDRAWN";

type TicketPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

type TicketStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "WAITING_FOR_CUSTOMER"
  | "RESOLVED"
  | "CLOSED";
```

Treat timestamps without a trailing `Z` as backend-local date-time values.
Timestamps ending in `Z` are UTC instants.

## Acceptance criteria

- Non-admin users cannot open any `/admin/*` route.
- Direct navigation to an admin URL performs a server-backed role check.
- Session cookies are included with every API request.
- CSRF tokens are sent for every authenticated mutation.
- Pending KYC documents can be viewed, approved, and rejected.
- KYC rejection requires a reason.
- Locked users can be unlocked.
- Disputes can be filtered, reviewed, updated, provisionally credited, and
  resolved with confirmation.
- Support tickets can be reviewed, assigned, prioritized, answered, annotated
  internally, resolved, and closed.
- Audit logs can be filtered by user, action, and date.
- API validation messages appear beside the relevant controls.
- Duplicate submissions are prevented.
- Unsupported backend actions are not shown as working controls.
- The interface works at desktop and mobile widths without overlapping text,
  tables, dialogs, or action bars.
