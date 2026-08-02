package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;

/** Evaluates exact, comparator, caret, and tilde SemVer constraints. */
final class VersionConstraint {

    private VersionConstraint() {
    }

    static boolean matches(String constraint, String candidate) {
        if ("*".equals(constraint)) {
            return true;
        }
        SemanticVersion version = SemanticVersion.parse(candidate);
        String normalized = constraint.trim();
        if (normalized.startsWith("^")) {
            SemanticVersion lower = SemanticVersion.parse(normalized.substring(1));
            SemanticVersion upper = caretUpper(lower);
            return version.compareTo(lower) >= 0 && version.compareTo(upper) < 0;
        }
        if (normalized.startsWith("~")) {
            SemanticVersion lower = SemanticVersion.parse(normalized.substring(1));
            SemanticVersion upper = new SemanticVersion(
                    lower.major(), lower.minor() + 1, 0, java.util.List.of());
            return version.compareTo(lower) >= 0 && version.compareTo(upper) < 0;
        }
        if (!normalized.contains(" ") && !startsWithComparator(normalized)) {
            return candidate.equals(normalized);
        }
        for (String token : normalized.split("\\s+")) {
            if (!matchesComparator(token, version)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesComparator(String token, SemanticVersion version) {
        String operator;
        if (token.startsWith(">=") || token.startsWith("<=")) {
            operator = token.substring(0, 2);
        } else if (token.startsWith(">") || token.startsWith("<") || token.startsWith("=")) {
            operator = token.substring(0, 1);
        } else {
            throw new PluginRuntimeException("Invalid semantic version comparator: " + token);
        }
        SemanticVersion bound = SemanticVersion.parse(token.substring(operator.length()));
        int comparison = version.compareTo(bound);
        return switch (operator) {
            case ">=" -> comparison >= 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            case "=" -> comparison == 0;
            default -> false;
        };
    }

    private static SemanticVersion caretUpper(SemanticVersion lower) {
        if (lower.major() > 0) {
            return new SemanticVersion(lower.major() + 1, 0, 0, java.util.List.of());
        }
        if (lower.minor() > 0) {
            return new SemanticVersion(0, lower.minor() + 1, 0, java.util.List.of());
        }
        return new SemanticVersion(0, 0, lower.patch() + 1, java.util.List.of());
    }

    private static boolean startsWithComparator(String value) {
        return value.startsWith(">") || value.startsWith("<") || value.startsWith("=");
    }
}
