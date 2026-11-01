import { useEffect, useState } from "react";

/**
 * The current time, refreshed every `interval` ms. It keeps ticking while the
 * window is unfocused: the sidebar's spinners keep turning then, and a clock
 * frozen beside one reads as a hung thread.
 */
export function useNow(interval: number, ticking = true) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    if (!ticking) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), interval);
    return () => clearInterval(timer);
  }, [interval, ticking]);
  return now;
}
