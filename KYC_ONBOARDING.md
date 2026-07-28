# KYC onboarding contract

Registration and successful OTP verification responses include:

- `kycStatus`: `NOT_SUBMITTED`, `PENDING`, `APPROVED`, or `REJECTED`
- `kycRequired`: `true` until KYC is approved
- `nextAction`: `COMPLETE_KYC`, `AWAIT_KYC_REVIEW`, or `CONTINUE`
- `redirectTo`: `/kyc` while KYC is required, otherwise `/dashboard`

After authentication, the frontend should navigate to `redirectTo`. It should also
navigate to `/kyc` when an outbound operation returns HTTP 403 with code
`KYC_REQUIRED`. The latest status and submitted documents are available from
`GET /api/v1/kyc`.
