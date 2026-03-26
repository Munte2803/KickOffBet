package com.munte.KickOffBet.domain.dto.api.request;


import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateLeagueRequest {

    @NotBlank(message="Name is required")
    private String name;

    private Set<UUID> teamIds = new HashSet<>();

}
