package com.ispf.server.application.git;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationGitFeatureBranchTest {

    @Test
    void acceptsIspfPrefixedBranch() {
        assertEquals("ispf/app-demo-1", ApplicationGitService.validateFeatureBranch("ispf/app-demo-1", "main"));
    }

    @Test
    void rejectsDefaultBranchCaseInsensitive() {
        assertThrows(
                ResponseStatusException.class,
                () -> ApplicationGitService.validateFeatureBranch("ispf/Main", "ispf/main")
        );
    }

    @Test
    void rejectsMissingIspfPrefix() {
        assertThrows(
                ResponseStatusException.class,
                () -> ApplicationGitService.validateFeatureBranch("feature/push", "main")
        );
    }

    @Test
    void rejectsRefsAndHeadsAliases() {
        assertThrows(
                ResponseStatusException.class,
                () -> ApplicationGitService.validateFeatureBranch("refs/heads/ispf/x", "main")
        );
        assertThrows(
                ResponseStatusException.class,
                () -> ApplicationGitService.validateFeatureBranch("heads/ispf/x", "main")
        );
        assertThrows(
                ResponseStatusException.class,
                () -> ApplicationGitService.validateFeatureBranch("HEAD", "main")
        );
    }
}
