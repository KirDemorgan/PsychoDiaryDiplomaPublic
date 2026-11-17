import { formatEtag } from "./versions";

describe("formatEtag", () => {
  it("оформляет версию как сильный ETag сервера", () => {
    expect(formatEtag(0)).toBe('"v0"');
    expect(formatEtag(12)).toBe('"v12"');
  });
});
