import Keycloak from "keycloak-js";

// Single Keycloak instance for the SPA. Authorization Code + PKCE against the lms realm.
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8180",
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? "lms",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "admin-ui",
});

export async function initKeycloak(): Promise<boolean> {
  return keycloak.init({
    onLoad: "login-required",
    pkceMethod: "S256",
    checkLoginIframe: false,
  });
}
