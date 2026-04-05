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

}
