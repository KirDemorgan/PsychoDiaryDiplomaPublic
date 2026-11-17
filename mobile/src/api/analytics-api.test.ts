import { createAnalyticsApi } from "./analytics-api";
import type { HttpClient } from "./http-client";

describe("analyticsApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createAnalyticsApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockClear());

  it("запрашивает обзор за период в часовом поясе", async () => {
    await api.overview("2026-10-12", "2026-10-18", "Asia/Yekaterinburg");

    expect(request).toHaveBeenCalledWith(
      "/analytics/overview?from=2026-10-12&to=2026-10-18&timeZone=Asia%2FYekaterinburg",
    );
  });

  it("запрашивает сравнение дней по тегу и показателю", async () => {
    await api.comparison("2026-10-12", "2026-10-18", "UTC", "SLEEP_DEFICIT", "ANXIETY");

    expect(request).toHaveBeenCalledWith(
      "/analytics/tag-comparisons?from=2026-10-12&to=2026-10-18&timeZone=UTC&tagCode=SLEEP_DEFICIT&metric=ANXIETY",
    );
  });
});
