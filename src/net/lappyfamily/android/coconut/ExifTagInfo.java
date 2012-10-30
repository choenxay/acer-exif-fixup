/**
 * Simple Exif Tag Info storage
 * 
 * Roman Yepishev <rtg@rtg.in.ua>
 * This code is in public domain.
 */

package net.lappyfamily.android.coconut;

/*
 * I needed to store offsets somewhere so here's the class for that.
 * Also it hosts the IDs of the EXIF tags we are going to fix. 
 */
public class ExifTagInfo {
    public static final int EXIF_SUBIFD_TAG = 0x8769;
    public static final int GPS_SUBIFD_TAG = 0x8825;

    public static final int EXIF_DATETIME_ORIGINAL = 0x9003;
    public static final int EXIF_CREATEDATE = 0x9004;
    public static final int EXIF_GPS_TIMESTAMP = 0x0007;
    public static final int EXIF_GPS_DATESTAMP = 0x001d;

    private int tag_id;
    private long offset;
    private int components;

    public ExifTagInfo(int tag_id, long offset, int components) {
        this.tag_id = tag_id;
        this.offset = offset;
        this.components = components;
    }

    public int getTagId() {
        return this.tag_id;
    }

    public long getOffset() {
        return this.offset;
    }

    public int getComponents() {
        return this.components;
    }

}

