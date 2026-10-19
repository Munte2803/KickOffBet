package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private Set<UUID> leagueIds= new HashSet<>();

    // Jackson builds this request through the all-args constructor, which skips the field initializer
    // above: an omitted list arrives as null and is treated as empty
    public Set<UUID> getLeagueIds() {
        if (leagueIds == null) {
            leagueIds = new HashSet<>();
        }
        return leagueIds;
    }
}
