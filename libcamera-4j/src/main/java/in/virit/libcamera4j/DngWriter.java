package in.virit.libcamera4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Writes DNG (Digital Negative) files in pure Java.
 * DNG is a TIFF-based format with specific tags for raw camera data.
 */
public class DngWriter {

    // TIFF constants
    private static final short TIFF_LITTLE_ENDIAN = 0x4949; // "II"
    private static final short TIFF_MAGIC = 42;

    // TIFF tag types
    private static final short TYPE_BYTE = 1;
    private static final short TYPE_ASCII = 2;
    private static final short TYPE_SHORT = 3;
    private static final short TYPE_LONG = 4;
    private static final short TYPE_RATIONAL = 5;
    private static final short TYPE_SBYTE = 6;
    private static final short TYPE_UNDEFINED = 7;
    private static final short TYPE_SSHORT = 8;
    private static final short TYPE_SLONG = 9;
    private static final short TYPE_SRATIONAL = 10;
    private static final short TYPE_FLOAT = 11;
    private static final short TYPE_DOUBLE = 12;

    // Standard TIFF tags
    private static final short TAG_NEWSUBFILETYPE = 254;
    private static final short TAG_IMAGEWIDTH = 256;
    private static final short TAG_IMAGELENGTH = 257;
    private static final short TAG_BITSPERSAMPLE = 258;
    private static final short TAG_COMPRESSION = 259;
    private static final short TAG_PHOTOMETRIC = 262;
    private static final short TAG_MAKE = 271;
    private static final short TAG_MODEL = 272;
    private static final short TAG_STRIPOFFSETS = 273;
    private static final short TAG_ORIENTATION = 274;
    private static final short TAG_SAMPLESPERPIXEL = 277;
    private static final short TAG_ROWSPERSTRIP = 278;
    private static final short TAG_STRIPBYTECOUNTS = 279;
    private static final short TAG_PLANARCONFIG = 284;
    private static final short TAG_SOFTWARE = 305;
    private static final short TAG_DATETIME = 306;

    // CFA tags
    private static final short TAG_CFAREPEATPATTERNDIM = (short) 33421;
    private static final short TAG_CFAPATTERN = (short) 33422;

    // DNG-specific tags
    private static final short TAG_DNGVERSION = (short) 50706;
    private static final short TAG_DNGBACKWARDVERSION = (short) 50707;
    private static final short TAG_UNIQUECAMERAMODEL = (short) 50708;
    private static final short TAG_CFAPLANECOLOR = (short) 50710;
    private static final short TAG_CFALAYOUT = (short) 50711;
    private static final short TAG_BLACKLEVEL = (short) 50714;
    private static final short TAG_WHITELEVEL = (short) 50717;
    private static final short TAG_COLORMATRIX1 = (short) 50721;
    private static final short TAG_ASSHOTNEUTRAL = (short) 50728;
    private static final short TAG_CALIBRATIONILLUMINANT1 = (short) 50778;

    // Photometric interpretations
    private static final short PHOTOMETRIC_CFA = (short) 32803;

    // Bayer patterns (CFA values: 0=Red, 1=Green, 2=Blue)
    public enum BayerOrder {
        RGGB(new byte[]{0, 1, 1, 2}),
        GRBG(new byte[]{1, 0, 2, 1}),
        BGGR(new byte[]{2, 1, 1, 0}),
        GBRG(new byte[]{1, 2, 0, 1});

        final byte[] pattern;
        BayerOrder(byte[] pattern) { this.pattern = pattern; }
    }

    private final int width;
    private final int height;
    private final int bitDepth;
    private final BayerOrder bayerOrder;
    private final short[] imageData;

    private String make = "Raspberry Pi";
    private String model = "Camera";
    private String software = "libcamera4j";
    private double[] colorMatrix = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private double[] asShotNeutral = {1, 1, 1};
    private int[] blackLevel = {0, 0, 0, 0};
    private int whiteLevel = 1023;

    public DngWriter(int width, int height, int bitDepth, BayerOrder bayerOrder, short[] imageData) {
        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.bayerOrder = bayerOrder;
        this.imageData = imageData;
        this.whiteLevel = (1 << bitDepth) - 1;
    }

    public DngWriter setMake(String make) { this.make = make; return this; }
    public DngWriter setModel(String model) { this.model = model; return this; }
    public DngWriter setSoftware(String software) { this.software = software; return this; }
    public DngWriter setColorMatrix(double[] matrix) { this.colorMatrix = matrix; return this; }
    public DngWriter setAsShotNeutral(double[] neutral) { this.asShotNeutral = neutral; return this; }
    public DngWriter setBlackLevel(int[] levels) { this.blackLevel = levels; return this; }
    public DngWriter setWhiteLevel(int level) { this.whiteLevel = level; return this; }

    /**
     * Unpacks 10-bit packed Bayer data to 16-bit.
     */
    public static short[] unpack10bit(byte[] packed, int width, int height, int stride) {
        short[] unpacked = new short[width * height];

        for (int row = 0; row < height; row++) {
            int rowOffset = row * stride;
            int dstRowOffset = row * width;

            for (int col = 0; col < width; col += 4) {
                int srcIdx = rowOffset + (col * 5) / 4;

                int b0 = packed[srcIdx] & 0xFF;
                int b1 = packed[srcIdx + 1] & 0xFF;
                int b2 = packed[srcIdx + 2] & 0xFF;
                int b3 = packed[srcIdx + 3] & 0xFF;
                int b4 = packed[srcIdx + 4] & 0xFF;

                unpacked[dstRowOffset + col] = (short)((b0 << 2) | ((b4 >> 0) & 0x03));
                if (col + 1 < width) unpacked[dstRowOffset + col + 1] = (short)((b1 << 2) | ((b4 >> 2) & 0x03));
                if (col + 2 < width) unpacked[dstRowOffset + col + 2] = (short)((b2 << 2) | ((b4 >> 4) & 0x03));
                if (col + 3 < width) unpacked[dstRowOffset + col + 3] = (short)((b3 << 2) | ((b4 >> 6) & 0x03));
            }
        }

        return unpacked;
    }

    /**
     * Detects Bayer order from pixel format string.
     */
    public static BayerOrder detectBayerOrder(String formatStr) {
        if (formatStr.startsWith("BG") || formatStr.contains("BGGR")) return BayerOrder.BGGR;
        if (formatStr.startsWith("GB") || formatStr.contains("GBRG")) return BayerOrder.GBRG;
        if (formatStr.startsWith("RG") || formatStr.contains("RGGB")) return BayerOrder.RGGB;
        if (formatStr.startsWith("GR") || formatStr.contains("GRBG")) return BayerOrder.GRBG;
        return BayerOrder.BGGR;
    }

    public void write(Path path) throws IOException {
        try (OutputStream os = Files.newOutputStream(path)) {
            write(os);
        }
    }

    public void write(OutputStream os) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // We'll build everything in memory first to calculate correct offsets
        List<IfdEntry> entries = new ArrayList<>();
        ByteArrayOutputStream extraData = new ByteArrayOutputStream();

        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"));

        // Calculate where IFD starts (after 8-byte header)
        int ifdOffset = 8;

        // We need to know IFD size to calculate extra data offset
        // Each IFD entry is 12 bytes, plus 2 bytes for count, plus 4 bytes for next IFD pointer
        // We'll have approximately 20 entries
        int estimatedEntries = 25;
        int ifdSize = 2 + estimatedEntries * 12 + 4;
        int extraDataOffset = ifdOffset + ifdSize;

        // Add tags - values <= 4 bytes go inline, larger values go to extra data
        addLongTag(entries, TAG_NEWSUBFILETYPE, 0);  // Full resolution image (MUST be LONG type)
        addLongTag(entries, TAG_IMAGEWIDTH, width);
        addLongTag(entries, TAG_IMAGELENGTH, height);
        addShortTag(entries, TAG_BITSPERSAMPLE, 16);
        addShortTag(entries, TAG_COMPRESSION, 1);  // No compression
        addShortTag(entries, TAG_PHOTOMETRIC, PHOTOMETRIC_CFA);
        addShortTag(entries, TAG_ORIENTATION, 1);  // Top-left
        addShortTag(entries, TAG_SAMPLESPERPIXEL, 1);
        addLongTag(entries, TAG_ROWSPERSTRIP, height);
        addShortTag(entries, TAG_PLANARCONFIG, 1);  // Chunky

        // String tags
        addStringTag(entries, extraData, extraDataOffset, TAG_MAKE, make);
        addStringTag(entries, extraData, extraDataOffset, TAG_MODEL, model);
        addStringTag(entries, extraData, extraDataOffset, TAG_SOFTWARE, software);
        addStringTag(entries, extraData, extraDataOffset, TAG_DATETIME, dateTime);
        addStringTag(entries, extraData, extraDataOffset, TAG_UNIQUECAMERAMODEL, make + " " + model);

        // DNG version (4 bytes, fits inline)
        addByteTag(entries, TAG_DNGVERSION, new byte[]{1, 4, 0, 0});
        addByteTag(entries, TAG_DNGBACKWARDVERSION, new byte[]{1, 1, 0, 0});

        // CFA pattern (TIFF-EP style)
        addShortArrayTag(entries, TAG_CFAREPEATPATTERNDIM, new short[]{2, 2});
        addByteTag(entries, TAG_CFAPATTERN, bayerOrder.pattern);

        // DNG CFA tags (required for DNG)
        addByteTag(entries, TAG_CFAPLANECOLOR, new byte[]{0, 1, 2});  // R, G, B plane mapping
        addShortTag(entries, TAG_CFALAYOUT, 1);  // Rectangular layout

        // Color calibration
        addSRationalArrayTag(entries, extraData, extraDataOffset, TAG_COLORMATRIX1, colorMatrix);
        addRationalArrayTag(entries, extraData, extraDataOffset, TAG_ASSHOTNEUTRAL, asShotNeutral);  // Unsigned
        addShortTag(entries, TAG_CALIBRATIONILLUMINANT1, 21);  // D65

        // Black/white levels (as rationals per DNG spec)
        addRationalArrayTag(entries, extraData, extraDataOffset, TAG_BLACKLEVEL,
            new double[]{blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]});
        addLongArrayTag(entries, extraData, extraDataOffset, TAG_WHITELEVEL, new int[]{whiteLevel});

        // Calculate strip offset and count (will be filled in after we know exact positions)
        int imageDataSize = width * height * 2;

        // Add strip tags (placeholders, will update values below)
        IfdEntry stripOffsetsEntry = new IfdEntry(TAG_STRIPOFFSETS, TYPE_LONG, 1, 0);
        IfdEntry stripByteCountsEntry = new IfdEntry(TAG_STRIPBYTECOUNTS, TYPE_LONG, 1, imageDataSize);
        entries.add(stripOffsetsEntry);
        entries.add(stripByteCountsEntry);

        // Sort entries by tag number FIRST (TIFF requirement)
        entries.sort(Comparator.comparingInt(e -> e.tag & 0xFFFF));

        // Recalculate IFD size with actual entry count
        ifdSize = 2 + entries.size() * 12 + 4;
        extraDataOffset = ifdOffset + ifdSize;

        // Update all extra data offsets with proper alignment (now in sorted order)
        int currentOffset = extraDataOffset;
        for (IfdEntry entry : entries) {
            if (entry.extraData != null) {
                // Align RATIONAL/SRATIONAL/LONG data to 4-byte boundary
                if (entry.type == TYPE_RATIONAL || entry.type == TYPE_SRATIONAL || entry.type == TYPE_LONG) {
                    currentOffset = (currentOffset + 3) & ~3;
                } else if (entry.type == TYPE_SHORT) {
                    currentOffset = (currentOffset + 1) & ~1;  // 2-byte align
                }
                entry.offset = currentOffset;
                currentOffset += entry.extraData.length;
            }
        }

        // Align strip offset to 4-byte boundary
        int stripOffset = (currentOffset + 3) & ~3;
        stripOffsetsEntry.value = stripOffset;  // Update the placeholder

        // Now write everything
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

        // TIFF header
        buf.putShort(TIFF_LITTLE_ENDIAN);
        buf.putShort(TIFF_MAGIC);
        buf.putInt(ifdOffset);
        os.write(buf.array());

        // IFD
        buf = ByteBuffer.allocate(2 + entries.size() * 12 + 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) entries.size());
        for (IfdEntry entry : entries) {
            buf.putShort(entry.tag);
            buf.putShort(entry.type);
            buf.putInt(entry.count);
            if (entry.extraData != null) {
                buf.putInt(entry.offset);
            } else {
                buf.putInt(entry.value);
            }
        }
        buf.putInt(0);  // No next IFD
        os.write(buf.array());

        // Extra data with padding for alignment
        int bytesWritten = 0;
        for (IfdEntry entry : entries) {
            if (entry.extraData != null) {
                // Add padding to reach the aligned offset
                int paddingNeeded = entry.offset - (extraDataOffset + bytesWritten);
                for (int i = 0; i < paddingNeeded; i++) {
                    os.write(0);
                    bytesWritten++;
                }
                os.write(entry.extraData);
                bytesWritten += entry.extraData.length;
            }
        }

        // Add padding before image data
        int paddingToStrip = stripOffset - (extraDataOffset + bytesWritten);
        for (int i = 0; i < paddingToStrip; i++) {
            os.write(0);
        }

        // Image data (16-bit little-endian) - write in chunks to avoid huge buffer
        int chunkSize = 65536;  // 64KB chunks
        ByteBuffer pixelBuf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN);
        int pixelIdx = 0;
        while (pixelIdx < imageData.length) {
            pixelBuf.clear();
            int pixelsInChunk = Math.min((chunkSize / 2), imageData.length - pixelIdx);
            for (int i = 0; i < pixelsInChunk; i++) {
                pixelBuf.putShort(imageData[pixelIdx++]);
            }
            os.write(pixelBuf.array(), 0, pixelsInChunk * 2);
        }
    }

    private void addShortTag(List<IfdEntry> entries, short tag, int value) {
        // Mask to 16 bits to avoid sign extension issues for values like 32803
        entries.add(new IfdEntry(tag, TYPE_SHORT, 1, value & 0xFFFF));
    }

    private void addLongTag(List<IfdEntry> entries, short tag, int value) {
        entries.add(new IfdEntry(tag, TYPE_LONG, 1, value));
    }

    private void addByteTag(List<IfdEntry> entries, short tag, byte[] data) {
        if (data.length <= 4) {
            int value = 0;
            for (int i = 0; i < data.length; i++) {
                value |= (data[i] & 0xFF) << (i * 8);
            }
            entries.add(new IfdEntry(tag, TYPE_BYTE, data.length, value));
        } else {
            entries.add(new IfdEntry(tag, TYPE_BYTE, data.length, data));
        }
    }

    private void addShortArrayTag(List<IfdEntry> entries, short tag, short[] data) {
        if (data.length <= 2) {
            int value = 0;
            for (int i = 0; i < data.length; i++) {
                value |= (data[i] & 0xFFFF) << (i * 16);
            }
            entries.add(new IfdEntry(tag, TYPE_SHORT, data.length, value));
        } else {
            ByteBuffer buf = ByteBuffer.allocate(data.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : data) buf.putShort(s);
            entries.add(new IfdEntry(tag, TYPE_SHORT, data.length, buf.array()));
        }
    }

    private void addStringTag(List<IfdEntry> entries, ByteArrayOutputStream extraData, int baseOffset, short tag, String value) {
        byte[] bytes = (value + "\0").getBytes();
        if (bytes.length <= 4) {
            int val = 0;
            for (int i = 0; i < bytes.length; i++) {
                val |= (bytes[i] & 0xFF) << (i * 8);
            }
            entries.add(new IfdEntry(tag, TYPE_ASCII, bytes.length, val));
        } else {
            entries.add(new IfdEntry(tag, TYPE_ASCII, bytes.length, bytes));
        }
    }

    private void addLongArrayTag(List<IfdEntry> entries, ByteArrayOutputStream extraData, int baseOffset, short tag, int[] values) {
        if (values.length == 1) {
            entries.add(new IfdEntry(tag, TYPE_LONG, 1, values[0]));
        } else {
            ByteBuffer buf = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int v : values) buf.putInt(v);
            entries.add(new IfdEntry(tag, TYPE_LONG, values.length, buf.array()));
        }
    }

    private void addRationalArrayTag(List<IfdEntry> entries, ByteArrayOutputStream extraData, int baseOffset, short tag, double[] values) {
        ByteBuffer buf = ByteBuffer.allocate(values.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : values) {
            int numerator = (int) Math.round(v * 10000);
            int denominator = 10000;
            buf.putInt(numerator);
            buf.putInt(denominator);
        }
        entries.add(new IfdEntry(tag, TYPE_RATIONAL, values.length, buf.array()));
    }

    private void addSRationalArrayTag(List<IfdEntry> entries, ByteArrayOutputStream extraData, int baseOffset, short tag, double[] values) {
        ByteBuffer buf = ByteBuffer.allocate(values.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : values) {
            int numerator = (int) Math.round(v * 10000);
            int denominator = 10000;
            buf.putInt(numerator);
            buf.putInt(denominator);
        }
        entries.add(new IfdEntry(tag, TYPE_SRATIONAL, values.length, buf.array()));
    }

    private static class IfdEntry {
        final short tag;
        final short type;
        final int count;
        int value;      // For inline values
        byte[] extraData;  // For values > 4 bytes
        int offset;     // Offset where extraData will be written

        IfdEntry(short tag, short type, int count, int value) {
            this.tag = tag;
            this.type = type;
            this.count = count;
            this.value = value;
        }

        IfdEntry(short tag, short type, int count, byte[] data) {
            this.tag = tag;
            this.type = type;
            this.count = count;
            this.extraData = data;
        }
    }
}
