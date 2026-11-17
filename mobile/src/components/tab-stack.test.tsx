import { render } from "@testing-library/react-native";

import { TabStack } from "./tab-stack";

const mockStack = jest.fn((_props: unknown) => null);
const mockScreen = jest.fn((_props: unknown) => null);

jest.mock("expo-router/stack", () => ({
  Stack: Object.assign(({ children, ...props }: { children: React.ReactNode }) => (mockStack(props), children), {
    Screen: (props: unknown) => mockScreen(props),
  }),
}));

describe("TabStack", () => {
  it("скрывает системную шапку только на корневом экране вкладки: заголовок рисуется в контенте", async () => {
    await render(<TabStack title="Сегодня" />);

    const { screenOptions } = mockStack.mock.calls[0][0] as {
      screenOptions: (arg: { route: { name: string } }) => { headerShown: boolean };
    };
    expect(screenOptions({ route: { name: "index" } }).headerShown).toBe(false);
    expect(screenOptions({ route: { name: "profile" } }).headerShown).toBe(true);
    expect(mockScreen).toHaveBeenCalledWith(expect.objectContaining({ options: { title: "Сегодня" } }));
  });
});
