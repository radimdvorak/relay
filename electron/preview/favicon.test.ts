import type { Session } from "electron";
import { expect, it } from "vitest";
import { faviconData } from "./favicon";

const sessionServing = (responses: Record<string, () => Response>) =>
  ({
    fetch: async (url: string) => responses[url](),
  }) as unknown as Session;

it("stops reading an icon once it passes 1MB, instead of loading all of it", async () => {
  let served = 0;
  const huge = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (served >= 64 * 1024 * 1024) return controller.close();
      served += 64 * 1024;
      controller.enqueue(new Uint8Array(64 * 1024));
    },
  });
  const session = sessionServing({
    "http://localhost/huge.png": () =>
      new Response(huge, { headers: { "content-type": "image/png" } }),
    "http://localhost/small.png": () =>
      new Response(new Uint8Array([1, 2, 3]), {
        headers: { "content-type": "image/png" },
      }),
  });

  const icon = await faviconData(session, [
    "http://localhost/huge.png",
    "http://localhost/small.png",
  ]);

  expect(icon).toBe("data:image/png;base64,AQID");
  expect(served).toBeLessThan(2 * 1024 * 1024);
});
