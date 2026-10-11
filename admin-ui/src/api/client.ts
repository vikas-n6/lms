import { keycloak } from "../auth/keycloak";

/** Fetch a gateway API path with a fresh bearer token attached. */
export async function apiGet<T>(path: string): Promise<T> {
  await keycloak.updateToken(30).catch(() => keycloak.login());
  const response = await fetch(path, {
    headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}
