package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JAccreditation;
import io.jettra.server.autentification.entity.JCredential;
import io.jettra.server.autentification.entity.JRole;
import io.jettra.server.autentification.entity.JUser;
import java.time.Instant;
import java.util.*;

public class JettraSecurityDBInitializer {

    public static void initializeIfEmpty() {
        JRoleRepository roleRepo = new JRoleRepositoryImpl();
        JUserRepository userRepo = new JUserRepositoryImpl();
        JCredentialRepository credRepo = new JCredentialRepositoryImpl();
        JAccreditationRepository accRepo = new JAccreditationRepositoryImpl();

        System.out.println("[JettraSecurityDBInitializer] Verifying default security records (JUsers, JRole, JAccreditation)...");

        // 1. Create Roles if missing (ADMIN, MANAGER, DEMO)
        Map<String, JRole> defaultRolesMap = new HashMap<>();
        String[] defaultRoleNames = {"ADMIN", "MANAGER", "DEMO"};
        List<JRole> existingRoles = roleRepo.findAll();

        for (String roleName : defaultRoleNames) {
            Optional<JRole> foundRole = existingRoles.stream()
                .filter(r -> r.name() != null && r.name().equalsIgnoreCase(roleName))
                .findFirst();

            if (foundRole.isPresent()) {
                defaultRolesMap.put(roleName, foundRole.get());
            } else {
                JRole newRole = new JRole(UUID.nameUUIDFromBytes(roleName.getBytes()), roleName, true);
                roleRepo.save(newRole);
                defaultRolesMap.put(roleName, newRole);
                System.out.println("[JettraSecurityDBInitializer] Created default JRole: " + roleName);
            }
        }

        // 2. Create User admin if no users exist or admin user missing
        List<JUser> users = userRepo.findAll();
        JUser adminUser = users.stream()
            .filter(u -> "admin".equalsIgnoreCase(u.firstName()) || (u.email() != null && u.email().startsWith("admin")))
            .findFirst()
            .orElse(null);

        if (adminUser == null) {
            Set<JRole> adminRoles = new HashSet<>();
            adminRoles.add(defaultRolesMap.get("ADMIN"));

            adminUser = new JUser(
                UUID.nameUUIDFromBytes("admin".getBytes()),
                "admin",
                "admin",
                "admin@jettra.io",
                "1234567890",
                true,
                adminRoles
            );
            userRepo.save(adminUser);
            System.out.println("[JettraSecurityDBInitializer] Created default JUser: admin");
        }

        // 3. Create Credentials for user admin with password 'admin' encrypted if missing
        List<JCredential> credentials = credRepo.findAll();
        boolean adminCredExists = credentials.stream()
            .anyMatch(c -> "admin".equalsIgnoreCase(c.username()));

        if (!adminCredExists) {
            String encryptedPassword = hashPassword("admin");
            JCredential adminCredential = new JCredential(
                UUID.nameUUIDFromBytes("admin-cred".getBytes()),
                adminUser,
                "admin",
                encryptedPassword,
                true,
                Instant.now()
            );
            credRepo.save(adminCredential);
            System.out.println("[JettraSecurityDBInitializer] Created default JCredential for user: admin");
        }

        // 4. Create Default JAccreditation records if missing
        List<JAccreditation> existingAccs = accRepo.findAll();
        for (String roleName : defaultRoleNames) {
            JRole roleObj = defaultRolesMap.get(roleName);
            if (roleObj != null) {
                boolean accExists = existingAccs.stream()
                    .anyMatch(a -> a.jRole() != null && roleObj.id().equals(a.jRole().id()));

                if (!accExists) {
                    JAccreditation acc = new JAccreditation(
                        UUID.nameUUIDFromBytes(("acc-" + roleName).getBytes()),
                        roleObj,
                        roleName + "_FEATURE",
                        true
                    );
                    accRepo.save(acc);
                    System.out.println("[JettraSecurityDBInitializer] Created default JAccreditation for JRole: " + roleName);
                }
            }
        }

        System.out.println("[JettraSecurityDBInitializer] Default security records (JUsers, JRole, JAccreditation) verified and synchronized successfully.");
    }

    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error hashing password", ex);
        }
    }
}
