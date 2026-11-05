import { describe, expect, it } from "vitest";
import { msysToWindows } from "./msys-path";

const temp = "C:\\Users\\lubom\\AppData\\Local\\Temp";

describe("msysToWindows", () => {
  it("puts /tmp in the user's temp folder", () => {
    expect(msysToWindows("/tmp/sheet_ab.png", temp)).toBe(
      `${temp}\\sheet_ab.png`,
    );

    expect(msysToWindows("/tmp/renders/a b.mp4", temp)).toBe(
      `${temp}\\renders\\a b.mp4`,
    );
  });

  it("reads a one-letter folder as a drive", () => {
    expect(msysToWindows("/c/Users/lubom/shot.png", temp)).toBe(
      "C:\\Users\\lubom\\shot.png",
    );

    expect(msysToWindows("/d/x.png", temp)).toBe("D:\\x.png");
  });

  it("leaves other paths alone", () => {
    expect(msysToWindows("C:\\Users\\shot.png", temp)).toBeNull();
    expect(msysToWindows("/tmpfiles/shot.png", temp)).toBeNull();
    expect(msysToWindows("/usr/share/shot.png", temp)).toBeNull();
    expect(msysToWindows("/Users/lubom/shot.png", temp)).toBeNull();
  });
});
