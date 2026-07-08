import { getAuthHeaders } from "../auth/session";

export interface MfaStatus {
  enabled: boolean;
  enrolled: boolean;
  enrollmentPending: boolean;
  enrollmentStartedAt: string | null;
}

export interface MfaEnrollmentStart {
  secret: string;
  otpauthUri: string;
  hint: string;
}

export interface MfaEnrollmentVerifyResult {
  enrolled: boolean;
  message: string;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchMfaStatus(): Promise<MfaStatus> {
  return fetch("/api/v1/security/mfa/status", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<MfaStatus>(response));
}

export function startMfaEnrollment(): Promise<MfaEnrollmentStart> {
  return fetch("/api/v1/security/mfa/enroll", {
    method: "POST",
    headers: getAuthHeaders(),
  }).then((response) => parseJson<MfaEnrollmentStart>(response));
}

export function verifyMfaEnrollment(code: string): Promise<MfaEnrollmentVerifyResult> {
  return fetch("/api/v1/security/mfa/verify", {
    method: "POST",
    headers: {
      ...getAuthHeaders(),
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ code }),
  }).then((response) => parseJson<MfaEnrollmentVerifyResult>(response));
}

export function cancelMfaEnrollment(): Promise<void> {
  return fetch("/api/v1/security/mfa/enroll", {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then((response) => {
    if (!response.ok) {
      return response.text().then((text) => {
        throw new Error(text || `Request failed: ${response.status}`);
      });
    }
  });
}
