import type { ReactNode } from "react";

export const router = { push: jest.fn(), back: jest.fn(), replace: jest.fn() };

export const routerMock = {
  Link: ({ children }: { children: ReactNode }) => children,
  Stack: Object.assign(() => null, { Screen: () => null, Protected: () => null }),
  useRouter: () => router,
};

export const symbolsMock = { SymbolView: () => null };

export const sliderMock = {
  __esModule: true,
  default: (props: Record<string, unknown>) => {
    const { View } = jest.requireActual("react-native");
    const { createElement } = jest.requireActual("react");
    return createElement(View, props);
  },
};

export const dateTimePickerMock = {
  __esModule: true,
  default: (props: Record<string, unknown>) => {
    const { View } = jest.requireActual("react-native");
    const { createElement } = jest.requireActual("react");
    return createElement(View, props);
  },
};
