import * as SecureStore from "expo-secure-store";

import { secureTokenStorage } from "./token-storage";

jest.mock("expo-secure-store", () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: "when-unlocked-this-device-only",
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

describe("secureTokenStorage", () => {
  it("сохраняет refresh-токен в защищённом хранилище только для этого устройства", async () => {
    await secureTokenStorage.saveRefreshToken("token");

    expect(SecureStore.setItemAsync).toHaveBeenCalledWith("diary.refresh-token", "token", {
      keychainAccessible: "when-unlocked-this-device-only",
    });
  });

  it("читает и удаляет токен", async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue("stored");

    await expect(secureTokenStorage.readRefreshToken()).resolves.toBe("stored");
    await secureTokenStorage.clear();

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith("diary.refresh-token");
  });
});
