package helloworld;

import java.time.Instant;

public class UserResponse {
    private final String username;
    private final String userStatus;
    private final Instant userCreateDate;
    private final Instant userLastModifiedDate;

    public UserResponse(final String username,
                        final String userStatus,
                        final Instant userCreateDate,
                        final Instant userLastModifiedDate) {
        this.username = username;
        this.userStatus = userStatus;
        this.userCreateDate = userCreateDate;
        this.userLastModifiedDate = userLastModifiedDate;
    }
}
