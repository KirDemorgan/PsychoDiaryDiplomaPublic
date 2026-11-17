import { File, Paths } from "expo-file-system";
import * as Sharing from "expo-sharing";

export class SharingUnavailableError extends Error {
  constructor() {
    super("Отправка файлов недоступна на этом устройстве");
    this.name = "SharingUnavailableError";
  }
}

export interface SharedFileType {
  mimeType: string;
  uti: string;
}

export const PDF_FILE: SharedFileType = { mimeType: "application/pdf", uti: "com.adobe.pdf" };
export const ZIP_FILE: SharedFileType = { mimeType: "application/zip", uti: "com.pkware.zip-archive" };

export async function shareFile(bytes: ArrayBuffer, fileName: string, type: SharedFileType): Promise<void> {
  if (!(await Sharing.isAvailableAsync())) {
    throw new SharingUnavailableError();
  }
  const file = new File(Paths.cache, fileName);
  file.create({ overwrite: true });
  try {
    await file.write(new Uint8Array(bytes));
    await Sharing.shareAsync(file.uri, { mimeType: type.mimeType, UTI: type.uti });
  } finally {
    if (file.exists) {
      file.delete();
    }
  }
}
