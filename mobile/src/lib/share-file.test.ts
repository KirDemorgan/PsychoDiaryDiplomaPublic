import { File } from "expo-file-system";
import * as Sharing from "expo-sharing";

import { PDF_FILE, SharingUnavailableError, ZIP_FILE, shareFile } from "./share-file";

const mockFile = { create: jest.fn(), write: jest.fn(), delete: jest.fn(), exists: true, uri: "file:///cache/r.pdf" };

jest.mock("expo-file-system", () => ({ File: jest.fn(), Paths: { cache: "cache-dir" } }));
jest.mock("expo-sharing", () => ({ isAvailableAsync: jest.fn(), shareAsync: jest.fn() }));

const sharing = Sharing as jest.Mocked<typeof Sharing>;

describe("shareFile", () => {
  beforeEach(() => {
    (File as unknown as jest.Mock).mockImplementation(() => mockFile);
    mockFile.create.mockReset();
    mockFile.write.mockReset().mockResolvedValue(undefined);
    mockFile.delete.mockReset();
    mockFile.exists = true;
    sharing.isAvailableAsync.mockReset().mockResolvedValue(true);
    sharing.shareAsync.mockReset().mockResolvedValue(undefined);
  });

  it("пишет PDF во временный файл, открывает системный Share и удаляет файл", async () => {
    await shareFile(new Uint8Array([1, 2, 3]).buffer, "report.pdf", PDF_FILE);

    expect(File).toHaveBeenCalledWith("cache-dir", "report.pdf");
    expect(mockFile.create).toHaveBeenCalledWith({ overwrite: true });
    expect(mockFile.write).toHaveBeenCalledWith(new Uint8Array([1, 2, 3]));
    expect(sharing.shareAsync).toHaveBeenCalledWith("file:///cache/r.pdf", {
      mimeType: "application/pdf",
      UTI: "com.adobe.pdf",
    });
    expect(mockFile.delete).toHaveBeenCalled();
  });

  it("удаляет временный файл, даже если отправка не удалась", async () => {
    sharing.shareAsync.mockRejectedValue(new Error("cancelled"));

    await expect(shareFile(new ArrayBuffer(1), "r.pdf", PDF_FILE)).rejects.toThrow("cancelled");

    expect(mockFile.delete).toHaveBeenCalled();
  });

  it("для архива передаёт тип ZIP", async () => {
    await shareFile(new ArrayBuffer(1), "data.zip", ZIP_FILE);

    expect(sharing.shareAsync).toHaveBeenCalledWith("file:///cache/r.pdf", {
      mimeType: "application/zip",
      UTI: "com.pkware.zip-archive",
    });
  });

  it("не пытается удалить уже отсутствующий файл", async () => {
    mockFile.exists = false;

    await shareFile(new ArrayBuffer(1), "r.pdf", PDF_FILE);

    expect(mockFile.delete).not.toHaveBeenCalled();
  });

  it("сообщает, если системная отправка недоступна, и ничего не создаёт", async () => {
    sharing.isAvailableAsync.mockResolvedValue(false);

    await expect(shareFile(new ArrayBuffer(1), "r.pdf", PDF_FILE)).rejects.toBeInstanceOf(SharingUnavailableError);

    expect(mockFile.create).not.toHaveBeenCalled();
  });
});
