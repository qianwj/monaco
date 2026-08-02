package cn.elvis.monaco.plugin.runtime.lifecycle;

import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;

import java.util.List;

/** Minimal SemVer precedence implementation used for dependency ranges. */
record SemanticVersion(int major, int minor, int patch, List<String> prerelease) implements Comparable<SemanticVersion> {

    static SemanticVersion parse(String value) {
        if (value == null) {
            throw new PluginRuntimeException("Semantic version must not be null");
        }
        String withoutBuild = value.split("\\+", 2)[0];
        String[] releaseAndPre = withoutBuild.split("-", 2);
        String[] core = releaseAndPre[0].split("\\.", -1);
        if (core.length != 3) {
            throw new PluginRuntimeException("Invalid semantic version: " + value);
        }
        try {
            return new SemanticVersion(
                    Integer.parseInt(core[0]),
                    Integer.parseInt(core[1]),
                    Integer.parseInt(core[2]),
                    releaseAndPre.length == 1 ? List.of() : List.of(releaseAndPre[1].split("\\.")));
        } catch (NumberFormatException exception) {
            throw new PluginRuntimeException("Invalid semantic version: " + value, exception);
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int compared = Integer.compare(major, other.major);
        if (compared == 0) {
            compared = Integer.compare(minor, other.minor);
        }
        if (compared == 0) {
            compared = Integer.compare(patch, other.patch);
        }
        if (compared != 0) {
            return compared;
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return prerelease.isEmpty() == other.prerelease.isEmpty()
                    ? 0
                    : (prerelease.isEmpty() ? 1 : -1);
        }
        int common = Math.min(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < common; index++) {
            compared = compareIdentifier(prerelease.get(index), other.prerelease.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return new java.math.BigInteger(left).compareTo(new java.math.BigInteger(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }
}
