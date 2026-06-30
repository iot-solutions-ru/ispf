import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { ObjectWebSocketCacheBridge } from "./hooks/ObjectWebSocketCacheBridge";
import { UserTimeZoneProvider } from "./context/UserTimeZoneContext";
import { i18nReady } from "./i18n";
import { initThemeOnDocument } from "./themeInit";
import "./styles.css";

initThemeOnDocument();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 300_000,
    },
  },
});

void i18nReady
  .catch((error) => {
    console.error("i18n init failed, rendering with defaults", error);
  })
  .finally(() => {
    createRoot(document.getElementById("root")!).render(
      <StrictMode>
        <BrowserRouter>
          <QueryClientProvider client={queryClient}>
            <UserTimeZoneProvider>
              <ObjectWebSocketCacheBridge />
              <App />
            </UserTimeZoneProvider>
          </QueryClientProvider>
        </BrowserRouter>
      </StrictMode>
    );
  });
