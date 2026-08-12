package com.sc2006group5.car_one_stop.service.auth;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("yukaiqiang2003@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Email Verification - OTP Code");
            message.setText("Your verification code is: " + otp + "\n\n" +
                    "This code will expire in 15 minutes.\n" +
                    "If you didn't request this, please ignore this email.");

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    public void sendResetLinkEmail(String toEmail, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("yukaiqiang2003@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Password Reset Link");
            message.setText("Your reset link is: caronestopmobile://reset-password?token=" + token + "\n\n" +
                    "This link will expire in 15 minutes.\n" +
                    "If you didn't request this, please ignore this email.");
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    public void sendResetOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("yukaiqiang2003@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Password Reset - OTP Code");
            message.setText("Your password reset code is: " + otp + "\n\n" +
                    "This code will expire in 15 minutes.\n" +
                    "If you didn't request this, please ignore this email.");

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }
}
