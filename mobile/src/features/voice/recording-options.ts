import { AudioQuality, IOSOutputFormat, type RecordingOptions } from "expo-audio";

export const MAX_RECORDING_SECONDS = 25;

export const WAV_RECORDING: RecordingOptions = {
  extension: ".wav",
  sampleRate: 16_000,
  numberOfChannels: 1,
  bitRate: 256_000,
  android: { extension: ".wav", outputFormat: "default", audioEncoder: "default" },
  ios: {
    extension: ".wav",
    sampleRate: 16_000,
    outputFormat: IOSOutputFormat.LINEARPCM,
    audioQuality: AudioQuality.MAX,
    linearPCMBitDepth: 16,
    linearPCMIsBigEndian: false,
    linearPCMIsFloat: false,
  },
  web: {},
};
