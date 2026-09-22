package com.lamiprover.tol;

/**
 * 固定种子确定性随机数：Numerical Recipes LCG + Box–Muller。
 * 不依赖 java.util.Random 的实现版本，保证 fixture 可跨机器重放。
 */
public final class DeterministicRng {

  private static final long A = 6364136223846793005L;
  private static final long C = 1442695040888963407L;
  private static final long MASK = (1L << 53) - 1;

  private long state;
  private boolean haveSpare;
  private double spare;

  public DeterministicRng(long seed) {
    this.state = seed == 0 ? 0xDEADBEEFCAFEBABEL : seed;
    // 预热，避免低位短周期
    for (int i = 0; i < 16; i++) {
      advance();
    }
  }

  private long advance() {
    state = state * A + C;
    return state;
  }

  /** (0,1) 均匀分布。 */
  public double nextUnit() {
    haveSpare = false;
    double u = ((advance() >>> 11) & MASK) / (double) (1L << 53);
    return u * (1.0 - 2.0e-16) + 1.0e-16;
  }

  /** ±3σ 截断正态（返回值裁剪到 [-3,3]）。 */
  public double nextGaussianClipped() {
    double z;
    if (haveSpare) {
      z = spare;
      haveSpare = false;
    } else {
      double u = nextUnit();
      double v = ((advance() >>> 11) & MASK) / (double) (1L << 53);
      v = v * (1.0 - 2.0e-16) + 1.0e-16;
      double mag = Math.sqrt(-2.0 * Math.log(u));
      z = mag * Math.cos(2.0 * Math.PI * v);
      spare = mag * Math.sin(2.0 * Math.PI * v);
      haveSpare = true;
    }
    return Math.max(-3.0, Math.min(3.0, z));
  }
}
