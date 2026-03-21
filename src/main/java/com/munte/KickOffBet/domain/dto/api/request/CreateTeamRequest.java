package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String shortName;

    @NotBlank
    @Size(min=1,max=5)
    private String tla;

    @URL
    private String crestUrl;


    private Set<UUID> leagueIds= new HashSet<>();
}
