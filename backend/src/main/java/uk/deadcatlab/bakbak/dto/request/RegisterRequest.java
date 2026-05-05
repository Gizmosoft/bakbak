package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/auth/register}.
 */
public record RegisterRequest(
	@NotBlank
	@Size(min = 3, max = 30)
	@Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username must be alphanumeric + underscore")
	String username,

	@NotBlank
	@Email
	@Size(max = 255)
	String email,

	@NotBlank
	@Size(min = 8, max = 100)
	String password,

	@Size(max = 100)
	String displayName,

	@NotNull
	@Past
	LocalDate dateOfBirth
) {}

