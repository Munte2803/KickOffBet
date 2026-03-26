package com.munte.KickOffBet.domain.dto.api.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateLeagueRequest {

    @NotBlank(message="Name is required")
    private String name;

    @NotBlank(message="Code is required")
    @Size(min=2, max=5, message="Code must be between 2 and 5 characters")
    private String code;

    private Set<UUID> teamIds=new HashSet<>();

}
