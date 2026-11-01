import { compress } from "./screenshot-compress";

self.onmessage = async (event: MessageEvent<Blob>) => {
  try {
    self.postMessage({ blob: await compress(event.data) });
  } catch (error) {
    self.postMessage({
      error:
        error instanceof Error ? error.message : "Could not compress screenshot.",
    });
  }
};
