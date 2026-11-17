package alex.drankov.diary.medications.dto;

import alex.drankov.diary.common.api.ApiException;

import com.fasterxml.jackson.annotation.JsonSetter;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;

public class UpdateMedicationRequest {
    @Getter
    private String name;

    @Getter
    private String form;

    @Schema(hidden = true)
    private boolean namePresent;

    @Schema(hidden = true)
    private boolean formPresent;

    @JsonSetter("name")
    public void setName(String value) {
        name = value;
        namePresent = true;
    }

    @JsonSetter("form")
    public void setForm(String value) {
        form = value;
        formPresent = true;
    }

    public boolean hasName() {
        return namePresent;
    }

    public boolean hasForm() {
        return formPresent;
    }

    public void validate() {
        if (!namePresent && !formPresent) {
            throw ApiException.invalid("Передайте изменяемые поля препарата");
        }
    }
}
