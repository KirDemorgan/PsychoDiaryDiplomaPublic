import { renderHook, waitFor } from "@testing-library/react-native";

import { speechApi } from "@/api/speech-api";
import { createTestQueryClient, queryWrapper } from "@/test/query-wrapper";

import { speechConsentKey, useSpeechConsent, useUpdateSpeechConsent } from "./use-speech-consent";

jest.mock("@/api/speech-api", () => ({ speechApi: { consent: jest.fn(), updateConsent: jest.fn() } }));

const api = speechApi as jest.Mocked<typeof speechApi>;

describe("speech consent hooks", () => {
  beforeEach(() => jest.resetAllMocks());

  it("загружает согласие", async () => {
    api.consent.mockResolvedValue({ enabled: true, version: 1 } as never);
    const { result } = await renderHook(() => useSpeechConsent(), { wrapper: queryWrapper() });

    await waitFor(() => expect(result.current.data).toEqual({ enabled: true, version: 1 }));
  });

  it("после обновления кладёт ответ в кэш, а при ошибке перезагружает", async () => {
    api.updateConsent.mockResolvedValueOnce({ enabled: true, version: 2 } as never).mockRejectedValueOnce(new Error("conflict"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useUpdateSpeechConsent(), { wrapper: queryWrapper(client) });

    await result.current.mutateAsync({ version: 1, enabled: true });
    expect(client.getQueryData(speechConsentKey)).toEqual({ enabled: true, version: 2 });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["settings"] });

    await expect(result.current.mutateAsync({ version: 2, enabled: false })).rejects.toThrow("conflict");
    expect(invalidate).toHaveBeenCalledWith({ queryKey: speechConsentKey });
  });
});
