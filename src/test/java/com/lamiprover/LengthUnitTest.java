package com.lamiprover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lamiprover.unit.Length;
import com.lamiprover.unit.LengthUnit;
import org.junit.jupiter.api.Test;

class LengthUnitTest {

  @Test
  void convertsToSiWithoutGuessingUnit() {
    // 同一数值在不同单位下必须是不同的物理长度
    assertThat(Length.of(1, "mil").metres()).isEqualTo(25.4e-6);
    assertThat(Length.of(1, "um").metres()).isEqualTo(1e-6);
    assertThat(Length.of(1, "mm").metres()).isEqualTo(1e-3);
    assertThat(Length.of(1, "m").metres()).isEqualTo(1.0);
    // 100 mil 与 2.54 mm 物理相等（不同写法，同一 SI 值）
    assertThat(Length.of(100, "mil").metres())
        .isCloseTo(Length.of(2.54, "mm").metres(), org.assertj.core.data.Offset.offset(1e-15));
    // 100 um != 100 mil：数值相同但单位不同，绝不能按数值大小混淆
    assertThat(Length.of(100, "um").metres())
        .isLessThan(Length.of(100, "mil").metres() / 20.0);
  }

  @Test
  void rejectsUnknownOrMissingUnit() {
    assertThatThrownBy(() -> LengthUnit.fromSymbol("inch"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Length(1.0, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertsAcrossUnits() {
    assertThat(Length.of(7, "mil").to(LengthUnit.MM).value())
        .isCloseTo(0.1778, org.assertj.core.data.Offset.offset(1e-12));
    assertThat(Length.of(0.18, "mm").to(LengthUnit.UM).value())
        .isCloseTo(180.0, org.assertj.core.data.Offset.offset(1e-12));
  }
}
