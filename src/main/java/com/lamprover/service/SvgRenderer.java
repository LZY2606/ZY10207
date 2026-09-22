package com.lamprover.service;

import com.lamprover.domain.CopperRole;
import com.lamprover.domain.Layer;
import com.lamprover.domain.Stackup;
import com.lamprover.domain.Trace;
import com.lamprover.domain.Unit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 服务端 SVG 横截面：厚度按真实比例（最小可视高度保底），
 * 信号铜层画出走线（仅高亮走线着色，避免重叠）。
 */
@Component
public class SvgRenderer {

    private static final int WIDTH = 960;
    private static final int MARGIN_X = 210;
    private static final int MARGIN_Y = 24;
    private static final int MIN_PX = 7;

    public String render(Stackup stackup, String highlightedTrace) {
        List<Layer> layers = stackup.layers();
        int innerW = WIDTH - MARGIN_X - 40;
        double total = layers.stream().mapToDouble(l -> l.thickness().si()).sum();
        double[] raw = new double[layers.size()];
        double rawSum = 0;
        for (int i = 0; i < layers.size(); i++) {
            raw[i] = Math.max(MIN_PX, innerW * 0 + 640 * layers.get(i).thickness().si() / total);
            rawSum += raw[i];
        }
        double[] px = raw;
        int height = (int) Math.round(rawSum) + 2 * MARGIN_Y;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d' viewBox='0 0 %d %d' font-family='-apple-system,PingFang SC,sans-serif'>",
                WIDTH, height, WIDTH, height));
        sb.append("<rect width='100%' height='100%' fill='#ffffff'/>");

        double y = MARGIN_Y;
        for (int i = 0; i < layers.size(); i++) {
            Layer l = layers.get(i);
            double h = px[i];
            sb.append(String.format(Locale.ROOT,
                    "<rect x='%d' y='%.1f' width='%d' height='%.1f' fill='%s' stroke='#3b4252' stroke-width='0.7'/>",
                    MARGIN_X, y, innerW, h, fill(l)));

            String label = layerLabel(l);
            double labelY = h < 14 ? y - 3 : y + h / 2;
            String anchor = h < 14 ? "end" : "middle";
            sb.append(String.format(Locale.ROOT,
                    "<text x='%d' y='%.1f' font-size='11' fill='#222' text-anchor='end' dominant-baseline='middle'>%s</text>",
                    MARGIN_X - 8, h < 14 ? y + h / 2 : labelY, label));
            if (h < 14) {
                sb.append(String.format(Locale.ROOT,
                        "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke='#999' stroke-width='0.6'/>",
                        MARGIN_X - 4, y + h / 2, MARGIN_X, y + h / 2));
            }

            if (l.isCopper() && l.copperRole() == CopperRole.SIGNAL) {
                drawTrace(sb, stackup, l, y, h, highlightedTrace, total, innerW);
            }
            y += h;
        }
        if (highlightedTrace != null) {
            sb.append(String.format(Locale.ROOT,
                    "<text x='%d' y='14' font-size='12' fill='#d83b01'>高亮走线：%s（红色）</text>",
                    MARGIN_X, escape(highlightedTrace)));
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private void drawTrace(StringBuilder sb, Stackup stackup, Layer copper,
                           double y, double bandH, String highlightedTrace,
                           double totalThickness, int innerW) {
        Trace shown = null;
        if (highlightedTrace != null) {
            for (Trace t : stackup.traces()) {
                if (t.name().equals(highlightedTrace)
                        && t.signalLayerName().equals(copper.name())) {
                    shown = t;
                    break;
                }
            }
        } else {
            for (Trace t : stackup.traces()) {
                if (t.signalLayerName().equals(copper.name())) {
                    shown = t;
                    break;
                }
            }
        }
        if (shown == null) {
            return;
        }
        boolean hl = shown.name().equals(highlightedTrace);
        String color = hl ? "#d83b01" : "#8a6d1f";
        double traceH = Math.min(Math.max(3, bandH * 0.55), 9);
        double ty = y + (bandH - traceH) / 2;
        double scale = (innerW - 120) / totalThickness;
        double wPx = Math.max(8, shown.width().si() * scale);
        double cx = MARGIN_X + innerW / 2.0;
        if (shown.type() == com.lamprover.domain.TraceType.DIFFERENTIAL && shown.spacing() != null) {
            double sPx = Math.max(4, shown.spacing().si() * scale);
            rect(sb, cx - sPx / 2 - wPx, ty, wPx, traceH, color);
            rect(sb, cx + sPx / 2, ty, wPx, traceH, color);
        } else {
            rect(sb, cx - wPx / 2, ty, wPx, traceH, color);
        }
    }

    private void rect(StringBuilder sb, double x, double y, double w, double h, String fill) {
        sb.append(String.format(Locale.ROOT,
                "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s' stroke='#5a3d00' stroke-width='0.6'/>",
                x, y, w, h, fill));
    }

    private String layerLabel(Layer l) {
        StringBuilder sb = new StringBuilder(escape(l.name()));
        sb.append(" · ").append(String.format(Locale.ROOT, "%.4g", l.thickness().value()))
                .append(' ').append(unitLabel(l.thickness().unit()));
        if (l.material() != null) {
            sb.append(" · ").append(escape(l.material()));
        }
        if (l.dk() != null) {
            sb.append(String.format(Locale.ROOT, " · Dk %.2f", l.dk().value()));
        }
        if (l.isCopper()) {
            sb.append(l.copperRole() == CopperRole.REFERENCE_PLANE ? " · 参考平面" : " · 信号");
        }
        return sb.toString();
    }

    private String fill(Layer l) {
        return switch (l.kind()) {
            case SOLDER_MASK -> "#7bbf6a";
            case COPPER -> l.copperRole() == CopperRole.REFERENCE_PLANE ? "#c98f2d" : "#e3c26e";
            case DIELECTRIC -> l.name().toUpperCase(Locale.ROOT).contains("CORE")
                    ? "#cfe3f5" : "#eaf2fb";
        };
    }

    private String unitLabel(Unit u) {
        return switch (u) {
            case UM -> "µm";
            case MM -> "mm";
            case MIL -> "mil";
            case OHM -> "Ω";
            case ONE -> "";
        };
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
