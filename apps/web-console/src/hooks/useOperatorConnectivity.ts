import { useCallback, useEffect, useState } from "react";

export function useOperatorConnectivity(onReconnect?: () => void) {
  const [online, setOnline] = useState(
    typeof navigator !== "undefined" ? navigator.onLine : true
  );
  const [reconnecting, setReconnecting] = useState(false);

  const handleReconnect = useCallback(() => {
    if (!onReconnect) {
      return;
    }
    setReconnecting(true);
    try {
      onReconnect();
    } finally {
      window.setTimeout(() => setReconnecting(false), 800);
    }
  }, [onReconnect]);

  useEffect(() => {
    const onOnline = () => {
      setOnline(true);
      handleReconnect();
    };
    const onOffline = () => setOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, [handleReconnect]);

  return {
    online,
    offline: !online,
    reconnecting,
    showStaleBanner: !online,
  };
}
