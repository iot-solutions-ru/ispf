export interface AuthConfig {
  mode: "local" | "oidc";
  localLoginEnabled?: boolean;
  oidc?: {
    issuer: string;
    clientId: string;
  };
}

const PKCE_STATE_KEY = "ispf-oidc-state";
const PKCE_VERIFIER_KEY = "ispf-oidc-verifier";

export async function fetchAuthConfig(): Promise<AuthConfig> {
  const response = await fetch("/api/v1/auth/config");
  if (!response.ok) {
    throw new Error(`Auth config failed: ${response.status}`);
  }
  return response.json() as Promise<AuthConfig>;
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

async function codeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

export async function startOidcLogin(config: AuthConfig): Promise<void> {
  if (!config.oidc) {
    throw new Error("OIDC is not configured");
  }
  const issuer = config.oidc.issuer.replace(/\/$/, "");
  const discovery = (await fetch(`${issuer}/.well-known/openid-configuration`).then((response) =>
    response.json()
  )) as { authorization_endpoint: string };
  const verifier = randomVerifier();
  const state = randomVerifier();
  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
  sessionStorage.setItem(PKCE_STATE_KEY, state);
  const redirectUri = `${window.location.origin}${window.location.pathname}`;
  const challenge = await codeChallenge(verifier);
  const url = new URL(discovery.authorization_endpoint);
  url.searchParams.set("client_id", config.oidc.clientId);
  url.searchParams.set("redirect_uri", redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid profile email");
  url.searchParams.set("state", state);
  url.searchParams.set("code_challenge", challenge);
  url.searchParams.set("code_challenge_method", "S256");
  window.location.assign(url.toString());
}

export async function completeOidcLogin(
  config: AuthConfig,
  code: string,
  state: string
): Promise<{ accessToken: string; principal: string; roles: string[] }> {
  const savedState = sessionStorage.getItem(PKCE_STATE_KEY);
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
  if (!savedState || savedState !== state || !verifier || !config.oidc) {
    throw new Error("OIDC state mismatch");
  }
  sessionStorage.removeItem(PKCE_STATE_KEY);
  sessionStorage.removeItem(PKCE_VERIFIER_KEY);

  const issuer = config.oidc.issuer.replace(/\/$/, "");
  const discovery = (await fetch(`${issuer}/.well-known/openid-configuration`).then((response) =>
    response.json()
  )) as { token_endpoint: string };
  const redirectUri = `${window.location.origin}${window.location.pathname}`;
  const tokenResponse = await fetch(discovery.token_endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: config.oidc.clientId,
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier,
    }),
  });
  if (!tokenResponse.ok) {
    throw new Error(await tokenResponse.text());
  }
  const tokens = (await tokenResponse.json()) as { access_token: string };
  const meResponse = await fetch("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${tokens.access_token}` },
  });
  if (!meResponse.ok) {
    throw new Error(await meResponse.text());
  }
  const me = (await meResponse.json()) as {
    principal?: string;
    roles?: string[];
  };
  return {
    accessToken: tokens.access_token,
    principal: me.principal ?? "oidc-user",
    roles: me.roles ?? [],
  };
}

export function clearOidcCallbackParams(): void {
  const url = new URL(window.location.href);
  url.searchParams.delete("code");
  url.searchParams.delete("state");
  url.searchParams.delete("session_state");
  url.searchParams.delete("iss");
  window.history.replaceState({}, "", url.toString());
}

export function readOidcCallback(): { code: string; state: string } | null {
  const params = new URLSearchParams(window.location.search);
  const code = params.get("code");
  const state = params.get("state");
  if (!code || !state) {
    return null;
  }
  return { code, state };
}
