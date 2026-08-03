import "@testing-library/jest-dom/vitest";

// jsdom lacks browser layout APIs that Ant Design (and a few widgets) call on mount.
// Use plain functions (not vi.fn) so per-test vi.resetAllMocks() does not clear them.
if (typeof window !== "undefined") {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });

  class ResizeObserverMock implements ResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }

  Object.defineProperty(window, "ResizeObserver", {
    writable: true,
    configurable: true,
    value: ResizeObserverMock,
  });

  // jsdom 30 resolves calc()/font-size more strictly and can throw inside getComputedStyle
  // for Ant Design token styles. Testing Library's getByRole → isInaccessible calls
  // getComputedStyle for display/visibility only; fall back so queries stay usable.
  const originalGetComputedStyle = window.getComputedStyle.bind(window);
  const fallbackComputedStyle = new Proxy(
    {
      getPropertyValue: () => "",
      item: () => "",
      length: 0,
      parentRule: null,
      cssText: "",
      setProperty: () => undefined,
      removeProperty: () => "",
    } as unknown as CSSStyleDeclaration,
    {
      get(target, prop, receiver) {
        if (prop in target) {
          return Reflect.get(target, prop, receiver);
        }
        return "";
      },
    },
  );
  window.getComputedStyle = ((elt: Element, pseudoElt?: string | null) => {
    try {
      return originalGetComputedStyle(elt, pseudoElt as string | undefined);
    } catch {
      return fallbackComputedStyle;
    }
  }) as typeof window.getComputedStyle;
}
