package com.nachtraben.soundstage.core.video;

import com.nachtraben.soundstage.core.TrackTime;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

public final class NowPlayingCardPainter {

    public static final int BARS = 48;

    public static final double MIN_ASPECT = 4.0 / 3.0;
    public static final double MAX_ASPECT = 21.0 / 9.0;

    private static final double MAX_ART_ASPECT = 16.0 / 9.0;
    private static final double MIN_TEXT_WIDTH = 0.62;

    private final int width;
    private final int height;
    private final Font titleFont;
    private final Font authorFont;
    private final Font smallFont;
    private final BufferedImage background;
    private final BufferedImage frame;
    private final int[] backgroundPixels;
    private final int[] framePixels;

    private Color barColour;
    private BufferedImage artImage;
    private int artWidth;
    private int artHeight;
    private List<String> titleLines = List.of();
    private String authorLine = "";

    public NowPlayingCardPainter(int width, int height, Font font) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException(width + "x" + height);
        this.width = width;
        this.height = height;
        this.titleFont = font.deriveFont(Font.PLAIN, height * 0.075f);
        this.authorFont = font.deriveFont(Font.PLAIN, height * 0.05f);
        this.smallFont = font.deriveFont(Font.PLAIN, height * 0.036f);
        this.background = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.backgroundPixels = ((DataBufferInt) background.getRaster().getDataBuffer()).getData();
        this.framePixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        setTrack("", "", null);
    }

    public static double cardAspect(int widthCells, int heightCells) {
        double aspect = widthCells / (double) Math.max(1, heightCells);
        return Math.max(MIN_ASPECT, Math.min(MAX_ASPECT, aspect));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void setTrack(String title, String author, BufferedImage artwork) {
        BufferedImage content = artwork;
        double artAspect = content == null ? 1.0
                : Math.max(1.0, Math.min(MAX_ART_ASPECT, content.getWidth() / (double) content.getHeight()));
        double room = width - 2.0 * margin() - gap() - height * MIN_TEXT_WIDTH;
        artHeight = (int) Math.round(Math.max(height * 0.2, Math.min(contentBottom() - margin(), room / artAspect)));
        artWidth = (int) Math.round(artHeight * artAspect);
        artImage = content == null ? null : smooth(cover(content, artWidth / (double) artHeight), artWidth, artHeight);

        Graphics2D g = background.createGraphics();
        try {
            hints(g);
            paintBackground(g, content);
            FontMetrics titleMetrics = g.getFontMetrics(titleFont);
            FontMetrics authorMetrics = g.getFontMetrics(authorFont);
            int maxWidth = Math.max(10, width - margin() - textX());
            titleLines = wrap(title == null ? "" : title, titleMetrics, maxWidth, 3);
            List<String> author1 = wrap(author == null ? "" : author, authorMetrics, maxWidth, 1);
            authorLine = author1.isEmpty() ? "" : author1.get(0);
        } finally {
            g.dispose();
        }
    }

    public int[] paint(double[] bars, long position, long duration, boolean stream, boolean paused) {
        System.arraycopy(backgroundPixels, 0, framePixels, 0, framePixels.length);
        Graphics2D g = frame.createGraphics();
        try {
            hints(g);
            paintBars(g, bars);
            paintArtwork(g);
            paintText(g, paused);
            paintProgress(g, position, duration, stream);
        } finally {
            g.dispose();
        }
        return framePixels;
    }

    private int margin() {
        return Math.round(height * 0.07f);
    }

    private int gap() {
        return Math.round(height * 0.06f);
    }

    private int artX() {
        return margin();
    }

    private int artY() {
        return margin() + Math.round((contentBottom() - margin() - artHeight) / 2f);
    }

    private int textX() {
        return artX() + artWidth + gap();
    }

    private int contentBottom() {
        return barsTop() - Math.round(height * 0.04f);
    }

    private int barsTop() {
        return Math.round(height * 0.55f);
    }

    private int barsBottom() {
        return Math.round(height * 0.82f);
    }

    private void paintBackground(Graphics2D g, BufferedImage content) {
        if (content != null) {
            BufferedImage blur = blurred(content, width / (double) height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(blur, 0, 0, width, height, null);
            g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 150), 0, height, new Color(0, 0, 0, 205)));
            g.fillRect(0, 0, width, height);
            barColour = tint(average(blur));
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(0x1B2735), 0, height, new Color(0x0B1016)));
            g.fillRect(0, 0, width, height);
            barColour = new Color(255, 255, 255, 110);
        }
    }

    private void paintBars(Graphics2D g, double[] bars) {
        int left = margin(), right = width - margin();
        float slot = (right - left) / (float) BARS;
        float barWidth = slot * 0.62f;
        int bottom = barsBottom();
        float maxHeight = bottom - barsTop();
        g.setColor(barColour);
        for (int i = 0; i < BARS; i++) {
            double h = bars != null && i < bars.length ? Math.max(0, Math.min(1, bars[i])) : 0;
            float barHeight = (float) Math.max(height * 0.004, h * maxHeight);
            float x = left + i * slot + (slot - barWidth) / 2f;
            g.fill(new RoundRectangle2D.Float(x, bottom - barHeight, barWidth, barHeight, barWidth * 0.5f, barWidth * 0.5f));
        }
    }

    private void paintArtwork(Graphics2D g) {
        int x = artX(), y = artY();
        float corner = height * 0.03f;
        Shape box = new RoundRectangle2D.Float(x, y, artWidth, artHeight, corner, corner);
        Shape clip = g.getClip();
        g.clip(box);
        if (artImage != null) {
            g.drawImage(artImage, x, y, null);
        } else {
            g.setColor(new Color(0x243244));
            g.fill(box);
            paintNote(g, x + (artWidth - artHeight) / 2, y, artHeight);
        }
        g.setClip(clip);
        g.setColor(new Color(255, 255, 255, 40));
        g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, artWidth - 1, artHeight - 1, corner, corner));
    }

    private void paintText(Graphics2D g, boolean paused) {
        FontMetrics titleMetrics = g.getFontMetrics(titleFont);
        FontMetrics authorMetrics = g.getFontMetrics(authorFont);
        FontMetrics smallMetrics = g.getFontMetrics(smallFont);
        int blockHeight = titleLines.size() * titleMetrics.getHeight()
                + (authorLine.isEmpty() ? 0 : authorMetrics.getHeight())
                + (paused ? smallMetrics.getHeight() : 0);
        int x = textX();
        int y = artY() + (artHeight - blockHeight) / 2;

        g.setColor(Color.WHITE);
        g.setFont(titleFont);
        for (String line : titleLines) {
            g.drawString(line, x, y + titleMetrics.getAscent());
            y += titleMetrics.getHeight();
        }
        if (!authorLine.isEmpty()) {
            g.setColor(new Color(255, 255, 255, 180));
            g.setFont(authorFont);
            g.drawString(authorLine, x, y + authorMetrics.getAscent());
            y += authorMetrics.getHeight();
        }
        if (paused) {
            g.setColor(new Color(255, 255, 255, 140));
            g.setFont(smallFont);
            g.drawString("Paused", x, y + smallMetrics.getAscent());
        }
    }

    private void paintProgress(Graphics2D g, long position, long duration, boolean stream) {
        int left = margin(), right = width - margin();
        int barY = Math.round(height * 0.865f);
        int barH = Math.max(2, Math.round(height * 0.012f));
        g.setColor(new Color(255, 255, 255, 64));
        g.fillRect(left, barY, right - left, barH);
        g.setFont(smallFont);
        FontMetrics metrics = g.getFontMetrics();
        int textY = barY + barH + Math.round(height * 0.02f) + metrics.getAscent();
        if (stream) {
            float dot = height * 0.024f;
            g.setColor(new Color(0xE5484D));
            g.fill(new Ellipse2D.Float(left, textY - metrics.getAscent() * 0.5f - dot * 0.6f, dot, dot));
            g.setColor(new Color(255, 255, 255, 210));
            g.drawString("LIVE", left + dot + height * 0.015f, textY);
            return;
        }
        double fraction = duration > 0 ? Math.max(0, Math.min(1, position / (double) duration)) : 0;
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRect(left, barY, (int) Math.round((right - left) * fraction), barH);
        g.setColor(new Color(255, 255, 255, 205));
        g.drawString(TrackTime.format(position, duration), left, textY);
        String total = TrackTime.format(duration, duration);
        g.drawString(total, right - metrics.stringWidth(total), textY);
    }

    private static void paintNote(Graphics2D g, int x, int y, int s) {
        g.setColor(new Color(255, 255, 255, 150));
        double headW = s * 0.22, headH = s * 0.16;
        double headX = x + s * 0.30, headY = y + s * 0.62;
        g.fill(new Ellipse2D.Double(headX, headY, headW, headH));
        double stemX = headX + headW - s * 0.045;
        g.fill(new Rectangle2D.Double(stemX, y + s * 0.22, s * 0.045, s * 0.47));
        Path2D.Double flag = new Path2D.Double();
        flag.moveTo(stemX + s * 0.045, y + s * 0.22);
        flag.curveTo(stemX + s * 0.22, y + s * 0.28, stemX + s * 0.24, y + s * 0.40, stemX + s * 0.14, y + s * 0.48);
        flag.curveTo(stemX + s * 0.17, y + s * 0.38, stemX + s * 0.12, y + s * 0.33, stemX + s * 0.045, y + s * 0.31);
        flag.closePath();
        g.fill(flag);
    }

    private static BufferedImage cover(BufferedImage image, double aspect) {
        int w = image.getWidth(), h = image.getHeight();
        if (w / (double) h > aspect) {
            int cw = Math.max(1, (int) Math.round(h * aspect));
            return image.getSubimage((w - cw) / 2, 0, cw, h);
        }
        int ch = Math.max(1, (int) Math.round(w / aspect));
        return image.getSubimage(0, (h - ch) / 2, w, ch);
    }

    private static BufferedImage smooth(BufferedImage image, int w, int h) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= w && current.getHeight() / 2 >= h) {
            current = scaled(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return scaled(current, w, h);
    }

    private static BufferedImage scaled(BufferedImage image, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, out.getWidth(), out.getHeight(), null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage blurred(BufferedImage content, double aspect) {
        int w = 64, h = Math.max(8, (int) Math.round(64 / aspect));
        BufferedImage small = smooth(cover(content, aspect), w, h);
        int[] pixels = small.getRGB(0, 0, w, h, null, 0, w);
        for (int pass = 0; pass < 3; pass++) {
            pixels = boxBlur(pixels, w, h, 3, true);
            pixels = boxBlur(pixels, w, h, 3, false);
        }
        small.setRGB(0, 0, w, h, pixels, 0, w);
        return small;
    }

    private static int[] boxBlur(int[] in, int w, int h, int r, boolean rows) {
        int[] out = new int[in.length];
        int lines = rows ? h : w, length = rows ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int a = 0, red = 0, green = 0, blue = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(length - 1, i + k));
                    int p = rows ? in[line * w + j] : in[j * w + line];
                    a += p >>> 24;
                    red += (p >> 16) & 0xFF;
                    green += (p >> 8) & 0xFF;
                    blue += p & 0xFF;
                }
                int n = 2 * r + 1;
                int argb = (a / n) << 24 | (red / n) << 16 | (green / n) << 8 | (blue / n);
                if (rows) out[line * w + i] = argb;
                else out[i * w + line] = argb;
            }
        }
        return out;
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static List<String> wrap(String text, FontMetrics metrics, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String rest = text.strip();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (metrics.stringWidth(rest) <= maxWidth) {
                lines.add(rest);
                rest = "";
                break;
            }
            int cut = rest.length();
            while (cut > 1 && metrics.stringWidth(rest.substring(0, cut)) > maxWidth) cut--;
            int space = rest.lastIndexOf(' ', cut);
            int end = space > 0 && lines.size() < maxLines - 1 ? space : cut;
            lines.add(rest.substring(0, end).strip());
            rest = rest.substring(end).strip();
        }
        if (!rest.isEmpty() && !lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            while (last.length() > 1 && metrics.stringWidth(last + "…") > maxWidth) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(lines.size() - 1, last.strip() + "…");
        }
        return lines;
    }

    private static Color average(BufferedImage image) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                n++;
            }
        }
        if (n == 0) return Color.WHITE;
        return new Color((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private static Color tint(Color average) {
        float[] hsb = Color.RGBtoHSB(average.getRed(), average.getGreen(), average.getBlue(), null);
        Color bright = Color.getHSBColor(hsb[0], Math.min(0.55f, hsb[1]), 0.95f);
        return new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), 150);
    }
}
