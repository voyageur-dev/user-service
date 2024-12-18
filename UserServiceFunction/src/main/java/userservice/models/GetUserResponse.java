package userservice.models;

import java.time.Instant;

public record GetUserResponse(String username,
                              String userStatus,
                              Instant userCreateDate,
                              Instant userLastModifiedDate) {
}
