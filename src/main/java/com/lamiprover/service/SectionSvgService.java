package com.lamiprover.service;

import com.lamiprover.model.Stackup;
import org.springframework.stereotype.Service;

/**
 * 对称叠层横截面 SVG（示意图，按几何比例映射；标注 w/t/h/s/阻焊）。
 */
@Service
public class SectionSvgService {

  public String render(Stackup s) {
    boolean stripline = s.traceType().stripline();
    boolean diff = s.traceType().differential();
    double w = s.traceWidth().metres();
    double t = s.copperThickness().metres();
    double h = s.dielectricHeight().metres();
    double sp = diff ? s.pairSpacingOrZero().metres() : 0.0;
    double mask = !stripline ? s.maskThicknessOrZero().metres() : 0.0;

    // 以 h 为基准缩放，整体放入 720x300 画布
    double worldW = (diff ? 2 * w + sp : w) + 2.2 * h;
    double scale = Math.min(620.0 / worldW, 120.0 / Math.max(h, t));
    int cx = 360;
    int copperH = Math.max(3, (int) Math.round(t * scale));
    int hPx = (int) Math.round(h * scale);
    int wPx = (int) Math.round(w * scale);
    int sPx = diff ? (int) Math.round(sp * scale) : 0;
    int maskPx = mask > 0 ? Math.max(3, (int) Math.round(mask * scale)) : 0;

    int planeY = 260;
    StringBuilder sb = new StringBuilder();
    sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='720' height='300' "
        + "viewBox='0 0 720 300' font-family='sans-serif' font-size='12'>");
    sb.append("<defs><marker id='a' markerWidth='8' markerHeight='8' refX='4' refY='4' ")
      .append("orient='auto'><path d='M0,0 L8,4 L0,8 z' fill='#c0392b'/></marker></defs>");
    sb.append("<rect width='720' height='300' fill='#fafafa'/>");

    if (stripline) {
      // 下参考平面
      plane(sb, planeY);
      // 介质（下 h + 上 h 对称）
      int traceBottom = planeY - hPx;
      int traceY = traceBottom - copperH;
      sb.append("<rect x='60' y='").append(traceY)
          .append("' width='600' height='").append(2 * hPx + copperH)
          .append("' fill='#d9e8f5' stroke='#9bb8d4'/>");
      drawTraces(sb, cx, traceY, copperH, wPx, sPx, diff);
      // 上参考平面（对称，等距 h）
      plane(sb, traceY - hPx);
      dim(sb, cx - wPx / 2 - 34, planeY, cx - wPx / 2 - 34, traceBottom,
          "h=" + fmt(s.dielectricHeight()), true);
      dim(sb, cx - wPx / 2 - 34, traceY - hPx, cx - wPx / 2 - 34, traceY,
          "h=" + fmt(s.dielectricHeight()), true);
      widthDims(sb, cx, traceBottom, copperH, wPx, sPx, diff, s);
      label(sb, 36, traceY - hPx - 6, "上参考平面 GND/PWR");
      label(sb, 36, planeY + 18, "下参考平面 GND/PWR");
      label(sb, 600, (traceY + planeY) / 2, "Dk=" + s.dielectricConstant());
    } else {
      plane(sb, planeY);
      int traceBottom = planeY - hPx;
      int traceY = traceBottom - copperH;
      // 介质
      sb.append("<rect x='60' y='").append(traceY)
          .append("' width='600' height='").append(hPx + copperH)
          .append("' fill='#d9e8f5' stroke='#9bb8d4'/>");
      drawTraces(sb, cx, traceY, copperH, wPx, sPx, diff);
      // 阻焊覆盖层（覆盖走线上方与两侧）
      if (maskPx > 0) {
        int top = traceY - maskPx;
        sb.append("<rect x='60' y='").append(top)
            .append("' width='600' height='").append(hPx + copperH + maskPx - (top - traceY))
            .append("' fill='#7bbf6a' fill-opacity='0.28' stroke='#4e8a42'/>");
        dim(sb, cx + wPx / 2 + (diff ? sPx / 2 : 0) + 26, traceY,
            cx + wPx / 2 + (diff ? sPx / 2 : 0) + 26, top,
            "阻焊 " + fmt(s.maskThicknessOrZero()), true);
        label(sb, 600, top - 6, "阻焊 maskDk=" + s.solderMaskDk());
      } else {
        label(sb, 600, traceY - 8, "裸铜·空气侧");
      }
      dim(sb, cx - wPx / 2 - 34, planeY, cx - wPx / 2 - 34, traceBottom,
          "h=" + fmt(s.dielectricHeight()), true);
      widthDims(sb, cx, traceBottom, copperH, wPx, sPx, diff, s);
      label(sb, 36, planeY + 18, "参考平面 GND/PWR");
      label(sb, 600, (traceY + planeY) / 2, "Dk=" + s.dielectricConstant());
    }
    sb.append("<text x='360' y='292' text-anchor='middle' fill='#555'>")
        .append(s.traceType().label())
        .append(" · 材料版本 ").append(escape(s.materialVersion()))
        .append("</text>");
    sb.append("</svg>");
    return sb.toString();
  }

  private static void drawTraces(StringBuilder sb, int cx, int y, int h,
                                 int wPx, int sPx, boolean diff) {
    if (diff) {
      int total = 2 * wPx + sPx;
      int x1 = cx - total / 2;
      copper(sb, x1, y, wPx, h);
      copper(sb, x1 + wPx + sPx, y, wPx, h);
    } else {
      copper(sb, cx - wPx / 2, y, wPx, h);
    }
  }

  private static void copper(StringBuilder sb, int x, int y, int w, int h) {
    sb.append("<rect x='").append(x).append("' y='").append(y)
        .append("' width='").append(w).append("' height='").append(h)
        .append("' fill='#c98a2e' stroke='#8a5a12'/>");
  }

  private static void plane(StringBuilder sb, int y) {
    sb.append("<rect x='60' y='").append(y)
        .append("' width='600' height='10' fill='#888' stroke='#555'/>");
    for (int x = 70; x < 650; x += 18) {
      sb.append("<line x1='").append(x).append("' y1='").append(y)
          .append("' x2='").append(x - 8).append("' y2='").append(y + 10)
          .append("' stroke='#666'/>");
    }
  }

  private static void widthDims(StringBuilder sb, int cx, int traceBottom, int copperH,
                                int wPx, int sPx, boolean diff, Stackup s) {
    int dimY = traceBottom + copperH + 22;
    if (diff) {
      int total = 2 * wPx + sPx;
      int x1 = cx - total / 2;
      hdim(sb, x1, x1 + wPx, dimY, "w=" + fmt(s.traceWidth()));
      hdim(sb, x1 + wPx + sPx, x1 + total, dimY, "w=" + fmt(s.traceWidth()));
      hdim(sb, x1 + wPx, x1 + wPx + sPx, dimY + 18, "s=" + fmt(s.pairSpacingOrZero()));
    } else {
      hdim(sb, cx - wPx / 2, cx + wPx / 2, dimY, "w=" + fmt(s.traceWidth()));
    }
    hdim(sb, cx - wPx / 2 - 70, cx - wPx / 2 - 70 + Math.max(8, copperH * 2),
        dimY, "t=" + fmt(s.copperThickness()));
  }

  private static void dim(StringBuilder sb, int x, int y1, int x2, int y2,
                          String text, boolean vertical) {
    sb.append("<line x1='").append(x).append("' y1='").append(y1)
        .append("' x2='").append(x2).append("' y2='").append(y2)
        .append("' stroke='#c0392b' stroke-dasharray='3,2'/>");
    int tx = vertical ? x - 6 : (x + x2) / 2;
    int ty = vertical ? (y1 + y2) / 2 : y1 - 4;
    sb.append("<text x='").append(tx).append("' y='").append(ty)
        .append("' fill='#c0392b' text-anchor='end'>").append(escape(text)).append("</text>");
  }

  private static void hdim(StringBuilder sb, int x1, int x2, int y, String text) {
    sb.append("<line x1='").append(x1).append("' y1='").append(y)
        .append("' x2='").append(x2).append("' y2='").append(y)
        .append("' stroke='#c0392b' marker-start='url(#a)' marker-end='url(#a)'/>");
    sb.append("<text x='").append((x1 + x2) / 2).append("' y='").append(y + 14)
        .append("' fill='#c0392b' text-anchor='middle'>").append(escape(text)).append("</text>");
  }

  private static void label(StringBuilder sb, int x, int y, String text) {
    sb.append("<text x='").append(x).append("' y='").append(y)
        .append("' fill='#333'>").append(escape(text)).append("</text>");
  }

  private static String fmt(com.lamiprover.unit.Length length) {
    return length.value() + length.unit().symbol();
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
