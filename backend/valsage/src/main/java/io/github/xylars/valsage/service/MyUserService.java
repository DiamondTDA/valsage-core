package io.github.xylars.valsage.service;

import io.github.xylars.valsage.dto.LoginRequest;
import io.github.xylars.valsage.dto.RegisterRequest;
import io.github.xylars.valsage.entity.MyUser;
import io.github.xylars.valsage.exception.EmailAlreadyExistsException;
import io.github.xylars.valsage.repo.MyUserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyUserService implements UserDetailsService {

    private final MyUserRepo myUserRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public MyUserService(MyUserRepo myUserRepo, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.myUserRepo = myUserRepo;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Fetch your user entity safely from the database using their email
        MyUser myUser = myUserRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        // 2. Return Spring Security's native User builder, forcing the email to be the principal identifier
        return org.springframework.security.core.userdetails.User.builder()
                .username(myUser.getEmail()) // <-- This guarantees authentication.getPrincipal().getUsername() is the email address string!
                .password(myUser.getPassword())
                .authorities(myUser.getAuthorities())
                .build();
    }

    @Transactional
    public void registerUser(RegisterRequest request) {

        if (myUserRepo.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email is already registered!");
        }

        MyUser myUser = new MyUser();

        myUser.setUsername(request.username());
        myUser.setEmail(request.email());
        String encodedPassword = passwordEncoder.encode(request.password());
        myUser.setPassword(encodedPassword);

        myUserRepo.save(myUser);
    }

}
