package com.munte.KickOffBet.domain.dto.api.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTeamRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String shortName;

    @NotBlank
    @Size(min=1,max=5)
    private String tla;

    private Set<UUID> leagueIds= new HashSet<>();

}
