package alex.drankov.diary.users.entity;

import alex.drankov.diary.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String timeZone;

    @Column(nullable = false)
    private long dataRevision;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    public User(
            String email,
            String passwordHash,
            String displayName,
            String timeZone
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.timeZone = timeZone;
    }

    public void changeProfile(String name, String zone) {
        displayName = name;
        timeZone = zone;
    }

    public void advanceDataRevision() {
        dataRevision++;
    }
}
