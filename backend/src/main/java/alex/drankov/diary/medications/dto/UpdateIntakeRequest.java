package alex.drankov.diary.medications.dto;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeStatus;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;

public final class UpdateIntakeRequest {
    @Getter
    private IntakeStatus status;
    @Getter
    private Instant actualAt;
    @Getter
    private BigDecimal actualDose;
    @Getter
    private DoseUnit unit;
    private boolean statusPresent;
    private boolean actualAtPresent;
    private boolean actualDosePresent;
    private boolean unitPresent;

    @JsonSetter
    public void setStatus(IntakeStatus value) {
        status = value;
        statusPresent = true;
    }

    @JsonSetter
    public void setActualAt(Instant value) {
        actualAt = value;
        actualAtPresent = true;
    }

    @JsonSetter
    public void setActualDose(BigDecimal value) {
        actualDose = value;
        actualDosePresent = true;
    }

    @JsonSetter
    public void setUnit(DoseUnit value) {
        unit = value;
        unitPresent = true;
    }

    public boolean hasStatus() {
        return statusPresent;
    }
    public boolean hasActualAt() {
        return actualAtPresent;
    }
    public boolean hasActualDose() {
        return actualDosePresent;
    }
    public boolean hasUnit() {
        return unitPresent;
    }

    public void validate() {
        if (!statusPresent && !actualAtPresent && !actualDosePresent && !unitPresent) {
            throw ApiException.invalid("Изменение не содержит полей");
        }
    }
}
