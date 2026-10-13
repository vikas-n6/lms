import { keycloak } from "../auth/keycloak";

async function authHeader(): Promise<Record<string, string>> {
  await keycloak.updateToken(30).catch(() => keycloak.login());
  return { Authorization: `Bearer ${keycloak.token ?? ""}` };
}

/** GET a gateway API path with a fresh bearer token attached. */
export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path, { headers: await authHeader() });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}

/** Send a state-changing request (POST/PUT) with an Idempotency-Key. Returns the parsed body, or
 * null for empty 2xx responses. */
export async function apiSend<T>(
  method: "POST" | "PUT",
  path: string,
  body?: unknown,
): Promise<T | null> {
  const response = await fetch(path, {
    method,
    headers: {
      ...(await authHeader()),
      "Content-Type": "application/json",
      "Idempotency-Key": `ui-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : null;
}
