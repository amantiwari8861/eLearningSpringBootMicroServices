package in.iamandroid.order.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProgressRequest(

        @NotNull UUID courseId,

        @NotNull Boolean completed
) {
}
