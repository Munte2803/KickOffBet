package com.munte.KickOffBet.domain.dto.footballdata;

import com.munte.KickOffBet.domain.dto.api.response.MatchDto;

import java.util.List;

public record FdMatchList(

List<FdMatchDto> matches
) {
}
