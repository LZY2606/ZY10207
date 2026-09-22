package com.lamprover.service;

import com.lamprover.domain.Stackup;
import com.lamprover.persistence.StackupRepository;
import com.lamprover.persistence.StoredVersion;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StackupService {

    private final StackupRepository versions;

    public StackupService(StackupRepository versions) {
        this.versions = versions;
    }

    public StoredVersion save(Stackup stackup) {
        List<String> errors = stackup.validationErrors();
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return versions.saveNewVersion(stackup);
    }

    public StoredVersion require(String boardCode, Integer version) {
        StoredVersion v = versions.load(boardCode, version);
        if (v == null) {
            throw new NotFoundException("叠层版本不存在: " + boardCode
                    + (version == null ? "" : " v" + version));
        }
        return v;
    }

    public List<StackupRepository.VersionSummary> versions(String boardCode) {
        return versions.listVersions(boardCode);
    }

    public List<StackupRepository.VersionSummary> all() {
        return versions.listAll();
    }

    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }

        public List<String> errors() {
            return errors;
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
