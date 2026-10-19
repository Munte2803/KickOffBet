package com.munte.KickOffBet.domain.dto.api.request;


import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


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

    // Jackson builds this request through the all-args constructor, which skips the field initializer
    // above: an omitted list arrives as null and is treated as empty
    public Set<UUID> getTeamIds() {
        if (teamIds == null) {
            teamIds = new HashSet<>();
        }
        return teamIds;
    }
}
