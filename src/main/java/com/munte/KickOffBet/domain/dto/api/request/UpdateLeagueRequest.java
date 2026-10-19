package com.munte.KickOffBet.domain.dto.api.request;



import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateLeagueRequest {

    @NotBlank(message="Name is required")
    private String name;

    private Set<UUID> teamIds = new HashSet<>();

    // Jackson builds this request through the all-args constructor, which skips the field initializer
    // above: an omitted list arrives as null and is treated as empty
    public Set<UUID> getTeamIds() {
        if (teamIds == null) {
            teamIds = new HashSet<>();
        }
        return teamIds;
    }
}
