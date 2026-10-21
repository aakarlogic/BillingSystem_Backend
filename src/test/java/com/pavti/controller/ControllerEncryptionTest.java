package com.pavti.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pavti.filter.ValidateEncryptedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

/**
 * In Health 360 a new API without [ValidateEncryptedRequest] silently went out unencrypted.
 * Here the build fails instead: every controller in com.pavti.controller must have
 * @ValidateEncryptedRequest on the class.
 */
class ControllerEncryptionTest {

    @Test
    void everyControllerRequiresEncryption() throws Exception {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> controllers = scan.findCandidateComponents("com.pavti.controller").stream().map(b -> b.getBeanClassName()).toList();

        assertFalse(controllers.isEmpty(), "no controllers found");
        for (String name : controllers) {
            assertTrue(Class.forName(name).isAnnotationPresent(ValidateEncryptedRequest.class),
                    name + " needs @ValidateEncryptedRequest (put it under @RequestMapping)");
        }
    }
}
