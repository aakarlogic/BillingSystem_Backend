package com.pavti.config;

import com.pavti.common.PasswordHelper;
import com.pavti.common.TextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the first platform admin on start-up when none exists yet.
 * The password comes from PAVTI_ADMIN_PASSWORD (never from the code) and is stored as BCrypt.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final JdbcTemplate jdbc;
    private final PasswordHelper passwords;
    private final AppProperties props;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordHelper passwords, AppProperties props) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer admins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id = u.role_id WHERE r.role_code = 'SUPER_ADMIN'", Integer.class);
        if (admins != null && admins > 0) return;
        String password = props.bootstrap().adminPassword();
        if (password == null || password.length() < 8) {
            log.warn("No platform admin yet. Set PAVTI_ADMIN_PASSWORD (8+ characters) and restart to create one.");
            return;
        }
        String mobile = TextHelper.mobile(props.bootstrap().adminMobile());
        jdbc.update("CALL sp_user_save(NULL, NULL, 'SUPER_ADMIN', ?, ?, '', ?, NULL)",
                props.bootstrap().adminName(), mobile, passwords.hash(password));
        jdbc.update("UPDATE users SET must_change_password = 0, is_mobile_verified = 1 WHERE mobile_no = ?", mobile);
        log.info("Platform admin created for mobile {}", mobile);
    }
}
