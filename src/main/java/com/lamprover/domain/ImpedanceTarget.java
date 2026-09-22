package com.lamprover.domain;

/** 验收目标阻抗带，单位欧姆。差分填差分阻抗，单端填单端阻抗。 */
public record ImpedanceTarget(double nominal, double lower, double upper) {

    public boolean contains(double zOhm) {
        return zOhm >= lower && zOhm <= upper;
    }

    public static ImpedanceTarget of(double nominal, double lower, double upper) {
        if (!(lower <= nominal && nominal <= upper)) {
            throw new IllegalArgumentException("目标带必须满足 lower <= nominal <= upper");
        }
        return new ImpedanceTarget(nominal, lower, upper);
    }
}
