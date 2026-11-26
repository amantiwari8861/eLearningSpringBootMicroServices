package in.iamandroid.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Name is Required!") @Size(max = 100, message = "Name must be 100 characters or fewer") String name,
        @NotBlank @Email String email,

        @NotBlank @Size(min = 8, max = 72) String password
) {
}
