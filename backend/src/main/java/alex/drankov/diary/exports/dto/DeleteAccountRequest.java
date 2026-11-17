package alex.drankov.diary.exports.dto;



public record DeleteAccountRequest(String currentPassword, Boolean confirm) {
    @Override
    public String toString() {
        return "DeleteAccountRequest[redacted]";
    }
}
