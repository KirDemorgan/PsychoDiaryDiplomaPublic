import { parseQuickAction, redirectIncomingPath, resolveSystemPath } from "./native-intent";

describe("resolveSystemPath", () => {
  it.each(["diary:///", "diary://", "diary:/"])("пустой путь схемы %s ведёт на корень", (path) => {
    expect(resolveSystemPath(path)).toBe("/");
  });

  it.each(["diary://sign-in", "/today", "diary:///settings/profile"])("обычный путь %s не меняется", (path) => {
    expect(resolveSystemPath(path)).toBe(path);
  });
});

describe("parseQuickAction", () => {
  it.each(["diary://entry/new", "diary:///entry/new", "/entry/new", "entry/new", "diary://entry/new/", "diary://entry/new?source=widget", "/entry/new#x"])(
    "узнаёт быструю запись в %s",
    (path) => {
      expect(parseQuickAction(path)).toBe("new-entry");
    },
  );

  it.each(["diary://", "/", "/today", "diary://entry/123", "diary://entry/new-copy", "/entry/new/extra", ""])(
    "не принимает %s за быструю запись",
    (path) => {
      expect(parseQuickAction(path)).toBeNull();
    },
  );
});

describe("redirectIncomingPath", () => {
  it("вошедшего пользователя ведёт сразу на новую запись", () => {
    const defer = jest.fn();

    expect(redirectIncomingPath("diary://entry/new", true, defer)).toBe("/entry/new");
    expect(defer).not.toHaveBeenCalled();
  });

  it("при холодном старте или без входа откладывает действие и ведёт на корень", () => {
    const defer = jest.fn();

    expect(redirectIncomingPath("diary://entry/new", false, defer)).toBe("/");
    expect(defer).toHaveBeenCalledWith("new-entry");
  });

  it("остальные пути обрабатывает как раньше", () => {
    const defer = jest.fn();

    expect(redirectIncomingPath("diary://", false, defer)).toBe("/");
    expect(redirectIncomingPath("/settings", true, defer)).toBe("/settings");
    expect(defer).not.toHaveBeenCalled();
  });
});
