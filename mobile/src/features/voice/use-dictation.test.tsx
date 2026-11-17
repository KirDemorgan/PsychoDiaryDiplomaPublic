import { act, renderHook, waitFor } from "@testing-library/react-native";
import * as Audio from "expo-audio";

import { ApiError } from "@/api/errors";
import { speechApi } from "@/api/speech-api";

import { MAX_RECORDING_SECONDS, WAV_RECORDING } from "./recording-options";
import { MICROPHONE_DENIED, useDictation } from "./use-dictation";

const mockRecorder = {
  prepareToRecordAsync: jest.fn(),
  record: jest.fn(),
  stop: jest.fn(),
  uri: "file:///cache/dictation.wav" as string | null,
};

jest.mock("expo-audio", () => ({
  useAudioRecorder: jest.fn(() => mockRecorder),
  useAudioRecorderState: jest.fn(() => ({ durationMillis: 12_900 })),
  requestRecordingPermissionsAsync: jest.fn(),
  setAudioModeAsync: jest.fn(),
  AudioQuality: { MAX: 127 },
  IOSOutputFormat: { LINEARPCM: "lpcm" },
}));
let mockUuid = 0;
jest.mock("expo-crypto", () => ({ randomUUID: () => `uuid-${++mockUuid}` }));
jest.mock("@/api/speech-api", () => ({ speechApi: { transcribe: jest.fn(), get: jest.fn(), remove: jest.fn() } }));

const audio = Audio as jest.Mocked<typeof Audio>;
const api = speechApi as jest.Mocked<typeof speechApi>;

const succeeded = { id: "t1", status: "SUCCEEDED", transcript: "Привет, мир" } as never;

describe("useDictation", () => {
  beforeEach(() => {
    mockUuid = 0;
    mockRecorder.prepareToRecordAsync.mockReset().mockResolvedValue(undefined);
    mockRecorder.record.mockReset();
    mockRecorder.stop.mockReset().mockResolvedValue(undefined);
    mockRecorder.uri = "file:///cache/dictation.wav";
    audio.requestRecordingPermissionsAsync.mockReset().mockResolvedValue({ granted: true } as never);
    audio.setAudioModeAsync.mockReset().mockResolvedValue(undefined);
    api.transcribe.mockReset().mockResolvedValue(succeeded);
    api.get.mockReset();
    api.remove.mockReset().mockResolvedValue(undefined);
  });

  it("записывает WAV 16 кГц моно, останавливает запись и передаёт расшифровку", async () => {
    const onTranscript = jest.fn();
    const { result } = await renderHook(() => useDictation(onTranscript));

    await act(async () => result.current.start());
    expect(result.current.phase).toBe("recording");
    expect(result.current.seconds).toBe(12);
    expect(audio.useAudioRecorder).toHaveBeenCalledWith(WAV_RECORDING);
    expect(audio.setAudioModeAsync).toHaveBeenCalledWith({ allowsRecording: true, playsInSilentMode: true });
    expect(mockRecorder.record).toHaveBeenCalled();

    await act(async () => result.current.stop());

    expect(onTranscript).toHaveBeenCalledWith("Привет, мир");
    expect(api.transcribe).toHaveBeenCalledWith("file:///cache/dictation.wav", "uuid-1");
    expect(result.current.phase).toBe("idle");
    expect(result.current.error).toBeNull();
  });

  it("описывает запись как 16 кГц моно PCM 16 бит без плавающей точки", () => {
    expect(WAV_RECORDING).toMatchObject({
      extension: ".wav",
      sampleRate: 16_000,
      numberOfChannels: 1,
      ios: { outputFormat: "lpcm", linearPCMBitDepth: 16, linearPCMIsFloat: false, linearPCMIsBigEndian: false },
    });
  });

  it("без доступа к микрофону не начинает запись", async () => {
    audio.requestRecordingPermissionsAsync.mockResolvedValue({ granted: false } as never);
    const { result } = await renderHook(() => useDictation(jest.fn()));

    await act(async () => result.current.start());

    expect(result.current.error).toBe(MICROPHONE_DENIED);
    expect(result.current.phase).toBe("idle");
    expect(mockRecorder.record).not.toHaveBeenCalled();
  });

  it("сообщает, если запись не удалось начать", async () => {
    mockRecorder.prepareToRecordAsync.mockRejectedValue(new Error("busy"));
    const { result } = await renderHook(() => useDictation(jest.fn()));

    await act(async () => result.current.start());

    expect(result.current.error).toMatch(/Не удалось начать запись/);
    expect(result.current.phase).toBe("idle");
  });

  it("сообщает, если файла записи нет", async () => {
    mockRecorder.uri = null;
    const { result } = await renderHook(() => useDictation(jest.fn()));
    await act(async () => result.current.start());

    await act(async () => result.current.stop());

    expect(result.current.error).toMatch(/Запись не получилась/);
    expect(api.transcribe).not.toHaveBeenCalled();
  });

  it("если сервис распознавания не настроен (503), показывает понятное сообщение", async () => {
    api.transcribe.mockRejectedValue(new ApiError(503, "SPEECH_UNAVAILABLE", "x"));
    const onTranscript = jest.fn();
    const { result } = await renderHook(() => useDictation(onTranscript));
    await act(async () => result.current.start());

    await act(async () => result.current.stop());

    expect(result.current.error).toBe("Сервис распознавания речи не настроен.");
    expect(onTranscript).not.toHaveBeenCalled();
  });

  it("показывает сообщения сервера и ошибки связи", async () => {
    api.transcribe.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    const { result } = await renderHook(() => useDictation(jest.fn()));
    await act(async () => result.current.start());

    await act(async () => result.current.stop());

    expect(result.current.error).toMatch(/Нет связи с сервером/);
  });

  it("автоматически останавливает запись по достижении лимита", async () => {
    jest.useFakeTimers();
    const onTranscript = jest.fn();
    const { result } = await renderHook(() => useDictation(onTranscript));
    await act(async () => result.current.start());

    await act(async () => {
      jest.advanceTimersByTime(MAX_RECORDING_SECONDS * 1000);
    });
    jest.useRealTimers();

    await waitFor(() => expect(onTranscript).toHaveBeenCalledWith("Привет, мир"));
    expect(mockRecorder.stop).toHaveBeenCalledTimes(1);
  });

  it("отмена распознавания останавливает ожидание", async () => {
    let resolveFirst: (value: never) => void = () => undefined;
    api.transcribe.mockReturnValue(new Promise((resolve) => (resolveFirst = resolve as never)));
    const { result } = await renderHook(() => useDictation(jest.fn()));
    await act(async () => result.current.start());

    let stopping: Promise<void> = Promise.resolve();
    await act(async () => {
      stopping = result.current.stop();
    });
    expect(result.current.phase).toBe("processing");
    await act(async () => result.current.cancel());
    await act(async () => {
      resolveFirst({ id: "t1", status: "QUEUED" } as never);
      await stopping;
    });

    expect(result.current.error).toMatch(/отменено/);
    expect(result.current.phase).toBe("idle");
  });

  it("отмена без активного распознавания безопасна", async () => {
    const { result } = await renderHook(() => useDictation(jest.fn()));

    await act(async () => result.current.cancel());

    expect(result.current.phase).toBe("idle");
  });
});
