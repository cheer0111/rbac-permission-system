package cheer.service;

import cheer.dto.LoginDTO;

public interface AuthService {
    String login(LoginDTO dto);
}
