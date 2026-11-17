import * as SecureStore from "expo-secure-store";

const REFRESH_TOKEN_KEY = "diary.refresh-token";

export interface TokenStorage {
  readRefreshToken(): Promise<string | null>;
  saveRefreshToken(token: string): Promise<void>;
  clear(): Promise<void>;
}

export const secureTokenStorage: TokenStorage = {
  readRefreshToken: () => SecureStore.getItemAsync(REFRESH_TOKEN_KEY),
  saveRefreshToken: (token) =>
    SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    }),
  clear: () => SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
};
