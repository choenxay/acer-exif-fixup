/**
 * EXIF parser and corrector.
 * 
 * Roman Yepishev <rtg@rtg.in.ua>
 * This code is in public domain
 */
package net.lappyfamily.android.coconut;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import android.media.ExifInterface;
import android.os.Environment;
import android.util.Log;

public class ExifCorrector {
	private List<ExifTagInfo> fixupTags;
	private ByteOrder byteOrder;
	private static final String TAG = "ExifCorrector";
	private static final String EXIF_DATETIME_FORMAT = "yyyy:MM:dd HH:mm:ss";
	private static final String EXIF_DATE_FORMAT = "yyyy:MM:dd";

	public ExifCorrector() {
		fixupTags = new LinkedList<ExifTagInfo>();
		byteOrder = ByteOrder.BIG_ENDIAN;
	}

	private Date filenameToDate(String filename) throws Exception {
		// Filename is precious. We may want to start using MediaStore for this
		// info though.

		String filenameFormat = null;
		SimpleDateFormat df;
		if (filename.startsWith("IMG_")) {
			// IMG_20121008_085917.jpg
			// yyyyMMdd HHmmss
			filenameFormat = "'IMG_'yyyyMMdd'_'HHmmss'.jpg'";
		} else if (filename.startsWith("CameraZOOM-")) {
			// CameraZOOM-20121009164205013.jpg
			// yyyyMMddHHmmssSSS
			filenameFormat = "'CameraZOOM-'yyyyMMddHHmmssSSS'.jpg'";
		}

		if (filenameFormat == null) {
			throw new Exception("Unknown filename format");
		}

		// We are using default time zone
		df = new SimpleDateFormat(filenameFormat);
		Date imageDateTime = df.parse(filename);
		return imageDateTime;
	}

	private boolean hasProperDate(File imageFile, Date imageDate) throws Exception {
		boolean result = false;
		try {
			ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
			String exifDateString = exif.getAttribute(ExifInterface.TAG_DATETIME);
			
			SimpleDateFormat format = new SimpleDateFormat();
			format.setTimeZone(TimeZone.getDefault());
			
			if (exifDateString == null) {
				throw new Exception("No Exif.DateTime");
			}
			
			format.applyPattern(EXIF_DATETIME_FORMAT);
			String filenameDateString = format.format(imageDate);
			
			if (exifDateString.equals(filenameDateString)) {
				result = true;
			}
		} catch (IOException e) {
			// pass
		}
		return result;
	}

	public boolean correct(File imageFile) {
		Log.d(TAG, "Processing " + imageFile.getAbsolutePath());

		/* Cleanup */
		fixupTags.clear();
		byteOrder = ByteOrder.BIG_ENDIAN;

		Date imageDateTime;
		RandomAccessFile imageData;
		String logPrefix = imageFile.getAbsolutePath() + ": ";

		String state = Environment.getExternalStorageState();

		if (!Environment.MEDIA_MOUNTED.equals(state)) {
			return false;
		}

		try {
			imageDateTime = filenameToDate(imageFile.getName());
		} catch (Exception e) {
			Log.w(TAG, logPrefix + "Unknown or wrong format");
			return false;
		}

		try {
			if (hasProperDate(imageFile, imageDateTime)) {
				Log.d(TAG, logPrefix + "EXIF Date is correct");
				return false;
			}
		} catch (Exception e) {
			Log.w(TAG, "Error while reading exif: " + e);
			return false;
		}

		try {
			imageData = new RandomAccessFile(imageFile, "rw");
		} catch (FileNotFoundException e) {
			Log.w(TAG, logPrefix + "File not found");
			return false;
		}

		int ch = 0;
		boolean exif_found = false;

		// 1. Scanning
		try {
			while ((ch = imageData.read()) != -1) {
				if (ch == 0xff) {
					ch = imageData.read();

					if (ch == 0xe1) {
						imageData.skipBytes(2);
						exif_found = true;
						break;
					}
				}
			}
		} catch (IOException e) {
			Log.e(TAG, logPrefix + "Error while reading", e);
		}

		if (!exif_found) {
			Log.i(TAG, logPrefix + "EXIF not found, nothing to do");
			return false;
		}

		// 2. Extracting offsets
		try {
			collectExifOffsets(imageData);
		} catch (Exception e) {
			Log.e(TAG, "Error while collecting EXIF offsets", e);
			return false;
		}
		
		if (fixupTags.size() == 0) {
			Log.d(TAG, logPrefix + "No EXIF offsets collected, nothing to do");
			return false;
		}

		// 3. Patching
		try {
			patchExif(imageData, imageDateTime);
		} catch (IOException e) {
			Log.e(TAG, "Error while patching", e);
			return false;
		}

		try {
			imageData.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// 4. Changing last modification date
		try {
			imageFile.setLastModified(imageDateTime.getTime());
		} catch (Exception e) {
			e.printStackTrace();
		}

		Log.d(TAG, "Finished processing " + imageFile.getAbsolutePath());
		return true;
	}

	private void collectExifOffsets(RandomAccessFile imageData)
			throws Exception {
		imageData.skipBytes(6);
		long tiff_offset = imageData.getFilePointer();
		
		byte[] buf2 = new byte[2];
		byte[] buf4 = new byte[4];
		byte[] buf12 = new byte[12];
		
		imageData.read(buf2);
		if (buf2[0] == 0x49 && buf2[1] == 0x49) {
			// Intel align, little endian
			// Looks like exif is already broken by the library
			this.byteOrder = ByteOrder.LITTLE_ENDIAN;
		}
		
		imageData.skipBytes(2);
		imageData.read(buf4);
		ByteBuffer bb = ByteBuffer.wrap(buf4);
		bb.order(this.byteOrder);

		long local_ifd_offset = bb.getInt();

		List<Long> ifd_offsets = new LinkedList<Long>();
		ifd_offsets.add(local_ifd_offset);

		for (int ifd = 0; ifd < ifd_offsets.size(); ifd++) {
			long ifd_offset = ifd_offsets.get(ifd);

			imageData.seek(tiff_offset + ifd_offset);
			// Number of entries in IFD
			imageData.read(buf2);
			bb = ByteBuffer.wrap(buf2);
			bb.order(this.byteOrder);
			int ifd_entries = bb.getShort();

			for (int entry = 0; entry < ifd_entries; entry++) {
				imageData.read(buf12);
				bb = ByteBuffer.wrap(buf12);
				bb.order(this.byteOrder);

				// EXIF Tag: All primitives are signed in Java
				int tag_id = bb.getShort() & 0x0000ffff;
				// skipping format (short)
				bb.position(bb.position() + 2);

				// Components (if > 4 - then that's offset from tiff_offset)
				int no_of_components = bb.getInt();
				long value = bb.getInt();

				if (tag_id == ExifTagInfo.EXIF_SUBIFD_TAG
						|| tag_id == ExifTagInfo.GPS_SUBIFD_TAG) {
					// Will process this sub IFD on next iteration
					ifd_offsets.add(value);
				}
				
				switch (tag_id) {
				case ExifTagInfo.EXIF_DATETIME_ORIGINAL:
				case ExifTagInfo.EXIF_CREATEDATE:
				case ExifTagInfo.EXIF_GPS_TIMESTAMP:
				case ExifTagInfo.EXIF_GPS_DATESTAMP:
					// We use absolute offset to simplify calculations;
					fixupTags.add(new ExifTagInfo(tag_id, tiff_offset + value,
							no_of_components));
					break;
				}
			}
		}

	}

	private void patchExif(RandomAccessFile imageData, Date imageDateTime)
			throws IOException {
		/* We have all the data we need and can patch */
		for (ExifTagInfo e : fixupTags) {
			long offset = e.getOffset();

			SimpleDateFormat format = new SimpleDateFormat();
			format.setTimeZone(TimeZone.getDefault());

			int tag_id = e.getTagId();
			byte[] result = null;

			switch (tag_id) {
			case ExifTagInfo.EXIF_DATETIME_ORIGINAL:
			case ExifTagInfo.EXIF_CREATEDATE:
				format.applyPattern(EXIF_DATETIME_FORMAT);
				result = format.format(imageDateTime).getBytes();
				break;
			case ExifTagInfo.EXIF_GPS_DATESTAMP:
				// black magic - we need to convert local time
				// imageDateTime to UTC
				format.applyPattern(EXIF_DATE_FORMAT);
				format.setTimeZone(TimeZone.getTimeZone("UTC"));
				result = format.format(imageDateTime).getBytes();
				break;
			case ExifTagInfo.EXIF_GPS_TIMESTAMP:
				Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
				c.setTime(imageDateTime);

				int hours = c.get(Calendar.HOUR_OF_DAY);
				int minutes = c.get(Calendar.MINUTE);
				int seconds = c.get(Calendar.SECOND);

				// GPS Time Stamp is Rational (12/1 0/1 0/1)
				result = new byte[8 * 3];
				ByteBuffer bb = ByteBuffer.wrap(result);
				bb.order(this.byteOrder);
				bb.putInt((hours << 4) + 1);
				bb.putInt((minutes << 4) + 1);
				bb.putInt((seconds << 4) + 1);
			}

			imageData.seek(offset);
			imageData.write(result);
			Log.d(TAG, String.format("Updated tag 0x%04x", tag_id));
		}

	}
}
