import { fireEvent, render, screen } from "@testing-library/react-native";

import { DateField } from "./date-field";
import { TimeField } from "./time-field";

jest.mock("@react-native-community/datetimepicker", () => jest.requireActual("@/test/mocks").dateTimePickerMock);

describe("TimeField", () => {
  it("показывает выбранное время и сообщает новое в формате ЧЧ:ММ", async () => {
    const onChange = jest.fn();
    await render(<TimeField label="Время" value="08:15" onChange={onChange} />);

    const picker = screen.getByLabelText("Время");
    expect(picker.props.value.getHours()).toBe(8);
    expect(picker.props.value.getMinutes()).toBe(15);

    await fireEvent(picker, "change", {}, new Date(2026, 9, 18, 21, 5));
    expect(onChange).toHaveBeenCalledWith("21:05");
  });

  it("игнорирует закрытие без выбора", async () => {
    const onChange = jest.fn();
    await render(<TimeField label="Время" value="08:15" onChange={onChange} />);

    await fireEvent(screen.getByLabelText("Время"), "change", {}, undefined);

    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("DateField", () => {
  it("передаёт выбранную дату и ограничивает минимальную", async () => {
    const onChange = jest.fn();
    const minimum = new Date(2026, 9, 1);
    const picked = new Date(2026, 10, 1);
    await render(<DateField label="Дата" value={new Date(2026, 9, 18)} minimumDate={minimum} onChange={onChange} />);

    const picker = screen.getByLabelText("Дата");
    expect(picker.props.minimumDate).toBe(minimum);
    await fireEvent(picker, "change", {}, picked);

    expect(onChange).toHaveBeenCalledWith(picked);
  });

  it("игнорирует закрытие без выбора", async () => {
    const onChange = jest.fn();
    await render(<DateField label="Дата" value={new Date()} onChange={onChange} />);

    await fireEvent(screen.getByLabelText("Дата"), "change", {}, undefined);

    expect(onChange).not.toHaveBeenCalled();
  });
});
