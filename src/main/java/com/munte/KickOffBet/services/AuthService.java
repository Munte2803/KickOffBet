package com.munte.KickOffBet.services;

import com.munte.KickOffBet.domain.dto.api.request.EmailRequest;
import com.munte.KickOffBet.domain.dto.api.request.LoginRequest;
import com.munte.KickOffBet.domain.dto.api.request.RegisterRequest;
import com.munte.KickOffBet.domain.dto.api.request.ResetPasswordRequest;
import com.munte.KickOffBet.domain.dto.api.response.AuthDto;
import com.munte.KickOffBet.domain.entity.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    AuthDto register(RegisterRequest request, MultipartFile idCard);


    AuthDto registerAdmin(RegisterRequest request);

    AuthDto login(LoginRequest request);

    User getCurrentUser();

    void confirmEmail(String token);

    void resendVerificationEmail(EmailRequest email);

    void forgotPassword(EmailRequest email);

    void resetPassword(String token, ResetPasswordRequest request);

}
