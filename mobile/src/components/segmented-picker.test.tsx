import { fireEvent, render, screen } from "@testing-library/react-native";

import { SegmentedPicker } from "./segmented-picker";

const options = [
  { value: "a", label: "Первый" },
  { value: "b", label: "Второй" },
] as const;

describe("SegmentedPicker", () => {
  it("отмечает выбранный сегмент и сообщает о выборе другого", async () => {
    const onChange = jest.fn();
    await render(<SegmentedPicker accessibilityLabel="Выбор" options={options} value="a" onChange={onChange} />);

    expect(screen.getByRole("radio", { name: "Первый" })).toBeSelected();
    expect(screen.getByRole("radio", { name: "Второй" })).not.toBeSelected();

    await fireEvent.press(screen.getByRole("radio", { name: "Второй" }));

    expect(onChange).toHaveBeenCalledWith("b");
  });
});
