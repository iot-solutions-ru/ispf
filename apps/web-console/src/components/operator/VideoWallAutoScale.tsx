import { useLayoutEffect, useRef, type ReactNode } from "react";

interface VideoWallAutoScaleProps {
  children: ReactNode;
}

/**
 * Fit dashboard content into a video-wall cell via CSS transform scale (BL-148).
 */
export default function VideoWallAutoScale({ children }: VideoWallAutoScaleProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const host = hostRef.current;
    const content = contentRef.current;
    if (!host || !content) {
      return;
    }

    const applyScale = () => {
      const hostW = host.clientWidth;
      const hostH = host.clientHeight;
      if (hostW <= 0 || hostH <= 0) {
        return;
      }
      content.style.width = `${hostW}px`;
      content.style.height = `${hostH}px`;
      content.style.transform = "none";
      const contentW = Math.max(content.scrollWidth, hostW);
      const contentH = Math.max(content.scrollHeight, hostH);
      const scale = Math.min(hostW / contentW, hostH / contentH, 1);
      content.style.transformOrigin = "top left";
      content.style.transform = scale < 0.999 ? `scale(${scale})` : "none";
      content.dataset.videoWallScale = scale.toFixed(3);
    };

    applyScale();
    const ro = new ResizeObserver(applyScale);
    ro.observe(host);
    ro.observe(content);
    return () => ro.disconnect();
  }, []);

  return (
    <div ref={hostRef} className="operator-video-wall-autoscale" data-testid="video-wall-autoscale">
      <div ref={contentRef} className="operator-video-wall-autoscale-content">
        {children}
      </div>
    </div>
  );
}
