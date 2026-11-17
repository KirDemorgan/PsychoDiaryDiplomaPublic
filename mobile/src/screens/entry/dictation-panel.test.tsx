import { fireEvent, render, screen } from "@testing-library/react-native";

import { speechApi } from "@/api/speech-api";
import { queryWrapper } from "@/test/query-wrapper";

import { DictationPanel, formatDuration } from "./dictation-panel";

jest.mock("expo-audio", () => ({ AudioQuality: { MAX: 127 }, IOSOutputFormat: { LINEARPCM: "lpcm" } }));
jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/api/speech-api", () => ({ speechApi: { consent: jest.fn() } }));

const mockDictation = {
  phase: "idle" as "idle" | "recording" | "processing",
  error: null as string | null,
  seconds: 15,
  start: jest.fn(),
  stop: jest.fn(),
  cancel: jest.fn(),
};
jest.mock("@/features/voice/use-dictation", () => ({ useDictation: () => mockDictation }));

const api = speechApi as jest.Mocked<typeof speechApi>;

async function renderPanel(enabled: boolean) {
  api.consent.mockResolvedValue({ enabled, version: 1 } as never);
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <DictationPanel onTranscript={jest.fn()} />
    </Wrapper>,
  );
}

describe("formatDuration", () => {
  it.each([
    [0, "00:00"],
    [75, "01:15"],
    [120, "02:00"],
  ])("%i секунд → %s", (seconds, text) => {
    expect(formatDuration(seconds)).toBe(text);
  });
});

describe("DictationPanel", () => {
  beforeEach(() => {
    Object.assign(mockDictation, { phase: "idle", error: null, seconds: 15 });
    mockDictation.start.mockReset();
    mockDictation.stop.mockReset();
    mockDictation.cancel.mockReset();
  });

  it("при отсутствии согласия предлагает включить голосовой ввод", async () => {
    await renderPanel(false);

    expect(await screen.findByText("Голосовой ввод выключен.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Включить в настройках" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Надиктовать" })).toBeNull();
  });

  it("при согласии начинает диктовку", async () => {
    await renderPanel(true);

    await fireEvent.press(await screen.findByRole("button", { name: "Надиктовать" }));

    expect(mockDictation.start).toHaveBeenCalled();
  });

  it("во время записи показывает время и позволяет остановить", async () => {
    mockDictation.phase = "recording";
    await renderPanel(true);

    expect(await screen.findByText("Идёт запись 00:15 из 00:25")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Остановить и распознать" }));

    expect(mockDictation.stop).toHaveBeenCalled();
  });

  it("во время распознавания позволяет отменить", async () => {
    mockDictation.phase = "processing";
    await renderPanel(true);

    expect(await screen.findByText("Распознаю речь…")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Отменить" }));

    expect(mockDictation.cancel).toHaveBeenCalled();
  });

  it("показывает ошибку диктовки", async () => {
    mockDictation.error = "Нет доступа к микрофону.";
    await renderPanel(true);

    expect(await screen.findByText("Нет доступа к микрофону.")).toBeTruthy();
  });

  it("ничего не показывает, пока согласие загружается", async () => {
    api.consent.mockReturnValue(new Promise(() => undefined) as never);
    const Wrapper = queryWrapper();
    await render(
      <Wrapper>
        <DictationPanel onTranscript={jest.fn()} />
      </Wrapper>,
    );

    expect(screen.queryByRole("button")).toBeNull();
  });
});
