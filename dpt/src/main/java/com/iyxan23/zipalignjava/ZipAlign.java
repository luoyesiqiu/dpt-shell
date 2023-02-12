// Copyright (C) 2022 Iyxan23, All rights reserved.
// This file is licensed under the MIT license.
// Full license text is available on https://opensource.org/licenses/MIT.

package com.iyxan23.zipalignjava;

import com.macfaq.io.LittleEndianInputStream;
import com.macfaq.io.LittleEndianOutputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * A class that provides functions to align zips.
 *
 * @see ZipAlign#alignZip(RandomAccessFile, OutputStream)
 * @see ZipAlign#alignZip(RandomAccessFile, OutputStream, int)
 * @see ZipAlign#alignZip(InputStream, OutputStream, int)
 * @see ZipAlign#alignZip(InputStream, OutputStream)
 */
public class ZipAlign {
    /**
     * Aligns uncompressed data of the given zip file to 4-byte boundaries. This function takes a {@link RandomAccessFile}
     * object; to read an {@link InputStream}, check the function {@link ZipAlign#alignZip(InputStream, OutputStream)}
     * <b>(do note that it is substantially slower than using this function)</b><br/>
     * <br/>
     * Example:
     * <pre>
     *     // read-only RandomAccessFile
     *     RandomAccessFile zipFile = new RandomAccessFile("path/to/file.zip", "r");
     *     FileOutputStream zipOut = ...;
     *
     *     ZipAlign.alignZip(zipFile, zipOut, 4);
     * </pre>
     *
     * @param file A {@link RandomAccessFile} reference to the zip file.
     * @param out The output where the aligned version of the given zip will be streamed
     *
     * @throws IOException Will be thrown on IO errors
     * @throws InvalidZipException Will be thrown when the zip given is not valid.
     *
     * @see ZipAlign#alignZip(RandomAccessFile, OutputStream, int)
     * @see ZipAlign#alignZip(InputStream, OutputStream, int)
     * @see ZipAlign#alignZip(InputStream, OutputStream)
     */
    public static void alignZip(RandomAccessFile file, OutputStream out) throws IOException, InvalidZipException {
        alignZip(file, out, 4);
    }

    // Maximum size of an EOCD record
    private static final int maxEOCDLookup = 0xffff + 22;

    /**
     * Aligns uncompressed data of the given zip file to the specified byte boundaries. This function takes a {@link RandomAccessFile}
     * object; to read an {@link InputStream}, check the function {@link ZipAlign#alignZip(InputStream, OutputStream)}
     * <b>(do note that it is substantially slower than using this function)</b><br/>
     * <br/>
     * Example:
     * <pre>
     *     // read-only RandomAccessFile
     *     RandomAccessFile zipFile = new RandomAccessFile("path/to/file.zip", "r"); // read-only RandomAccessFile
     *     FileOutputStream zipOut = ...;
     *
     *     ZipAlign.alignZip(zipFile, zipOut, 4);
     * </pre>
     *
     * @param file A {@link RandomAccessFile} reference to the zip file.
     * @param out The output where the aligned version of the given zip will be streamed
     * @param alignment Alignment in bytes, usually 4
     *
     * @throws IOException Will be thrown on IO errors
     * @throws InvalidZipException Will be thrown when the zip given is not valid.
     *
     * @see ZipAlign#alignZip(RandomAccessFile, OutputStream)
     * @see ZipAlign#alignZip(InputStream, OutputStream, int)
     * @see ZipAlign#alignZip(InputStream, OutputStream)
     */
    public static void alignZip(RandomAccessFile file, OutputStream out, int alignment)
            throws IOException, InvalidZipException {

        // find the end of central directory
        long seekStart;
        int readAmount;
        final long fileLength = file.length();

        if (fileLength > maxEOCDLookup) {
            seekStart = fileLength - maxEOCDLookup;
            readAmount = maxEOCDLookup;
        } else {
            seekStart = 0;
            readAmount = (int) fileLength;
        }

        // find the signature
        file.seek(seekStart);

        int i;
        for (i = readAmount - 4; i >= 0; i--) {
            if (file.readByte() != 0x50) continue;
            file.seek(file.getFilePointer() - 1);
            if (file.readInt() == 0x504b0506) break; // EOCD signature (in big-endian)
        }

        if (i < 0)
            throw new InvalidZipException("No end-of-central-directory found");

        long eocdPosition = file.getFilePointer() - 4;

        // skip disk fields
        file.seek(eocdPosition + 10);

        byte[] buf = new byte[10]; // we're keeping the total entries (2B), central dir size (4B), and the offset (4B)
        file.read(buf);
        ByteBuffer eocdBuffer = ByteBuffer.wrap(buf)
                .order(ByteOrder.LITTLE_ENDIAN);

        // read em
        short totalEntries = eocdBuffer.getShort();
        int centralDirSize = eocdBuffer.getInt();
        int centralDirOffset = eocdBuffer.getInt();

        ArrayList<Alignment> neededAlignments = new ArrayList<>();
        ArrayList<FileOffsetShift> shifts = new ArrayList<>();

        // to keep track of how many bytes we've shifted through the whole file (because we're going to pad null bytes
        // to align)
        short shiftAmount = 0;

        file.seek(centralDirOffset);
        byte[] entry = new byte[46]; // not including the filename, extra field, and file comment
        ByteBuffer entryBuffer = ByteBuffer.wrap(entry)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (int ei = 0; ei < totalEntries; ei++) {
            final long entryStart = file.getFilePointer();
            file.read(entry);

            if (entryBuffer.getInt(0) != 0x02014b50)
                throw new InvalidZipException(
                        "assumed central directory entry at " + entryStart + " doesn't start with a signature"
                );

            short entry_fileNameLen = entryBuffer.getShort(28);
            short entry_extraFieldLen = entryBuffer.getShort(30);
            short entry_commentLen = entryBuffer.getShort(32);
            int fileOffset = entryBuffer.getInt(42);

            if (shiftAmount != 0)
                shifts.add(new FileOffsetShift(entryStart + 42, fileOffset + shiftAmount));

            // if this file is uncompressed, we align it
            if (entryBuffer.getShort(10) == 0) {
                // temporarily seek to the file header to calculate the alignment amount
                file.seek(fileOffset + 26); // skip all fields before filename length

                // read the filename & extra field length
                ByteBuffer lengths = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                file.read(lengths.array());
                short fileNameLen = lengths.getShort();
                short extraFieldLen = lengths.getShort();

                // calculate the amount of alignment needed
                long dataPos = fileOffset + 30 + fileNameLen + extraFieldLen + shiftAmount;
                short wrongOffset = (short) (dataPos % alignment);
                short alignAmount = wrongOffset == 0 ? 0 : (short) (alignment - wrongOffset);
                shiftAmount += alignAmount;

                // only align when alignAmount is not 0 (not already aligned)
                if (alignAmount != 0) {
                    // push it!
                    neededAlignments.add(new Alignment(
                            alignAmount,
                            fileOffset + 28,
                            (short) (extraFieldLen + alignAmount),
                            fileNameLen + extraFieldLen
                    ));
                }

                file.seek(entryStart + 46); // go back to our prev location
            }

            file.seek(file.getFilePointer() + entry_fileNameLen + entry_extraFieldLen + entry_commentLen);
        }

        // done analyzing! now we're going to stream the aligned zip
        file.seek(0);
        if (neededAlignments.size() == 0) {
            // there is no needed alignment, stream it all!
            byte[] buffer = new byte[8192];
            while (file.read(buffer) != -1) out.write(buffer);
            return;
        }

        // alignments needed! this aligns files to the defined boundaries by padding null bytes to the extra field
        for (Alignment al : neededAlignments) {
            if (al.extraFieldLenOffset != 0) {
                passBytes(file, out, al.extraFieldLenOffset - file.getFilePointer());
            }

            // write the changed extra field length (in little-endian)
            out.write(al.extraFieldLenValue & 0xFF);
            out.write((al.extraFieldLenValue >>> 8) & 0xFF);
            file.readShort(); // mirror the new position to the file

            passBytes(file, out, al.extraFieldExtensionOffset);

            byte[] padding = new byte[al.alignAmount];
            out.write(padding); // sneak in null bytes
            out.flush();
        }

        // the code below overrides the bytes that reference to other parts of the file that may be shifted
        // due to the fact that we're padding bytes to align uncompressed data

        // this changes the "file offset" defined in EOCD headers
        for (FileOffsetShift shift : shifts) {
            // write data before this
            passBytes(file, out, shift.eocdhPosition - file.getFilePointer());

            // write shifted file offset (in litte-endian)
            out.write(shift.shiftedFileOffset & 0xFF);
            out.write((shift.shiftedFileOffset >>> 8) & 0xFF);
            out.write((shift.shiftedFileOffset >>> 16) & 0xFF);
            out.write((shift.shiftedFileOffset >>> 24) & 0xFF);
            file.readInt(); // mirror the new position to the file
        }

        // after that we need to edit the EOCDR's "EOCDH start offset" field
        passBytes(file, out, eocdPosition + 0x10 - file.getFilePointer());
        int shiftedCDOffset = centralDirOffset + shiftAmount;

        out.write(shiftedCDOffset & 0xFF);
        out.write((shiftedCDOffset >>> 8) & 0xFF);
        out.write((shiftedCDOffset >>> 16) & 0xFF);
        out.write((shiftedCDOffset >>> 24) & 0xFF);
        file.readInt(); // mirror the new position change

        // write all that's left
        passBytes(file, out, file.length() - file.getFilePointer());
    }

    private static class Alignment {
        public short alignAmount;
        public long extraFieldLenOffset;
        public short extraFieldLenValue;
        public int extraFieldExtensionOffset;

        public Alignment(short alignAmount, long extraFieldLenOffset, short extraFieldLenValue,
                         int extraFieldExtensionOffset) {
            this.alignAmount = alignAmount;
            this.extraFieldLenOffset = extraFieldLenOffset;
            this.extraFieldLenValue = extraFieldLenValue;
            this.extraFieldExtensionOffset = extraFieldExtensionOffset;
        }

        @Override
        public String toString() {
            return "Alignment{" +
                    "alignAmount=" + alignAmount +
                    ", extraFieldLenOffset=" + extraFieldLenOffset +
                    ", extraFieldLenValue=" + extraFieldLenValue +
                    ", extraFieldExtensionOffset=" + extraFieldExtensionOffset +
                    '}';
        }
    }

    private static class FileOffsetShift {
        public long eocdhPosition;
        public int shiftedFileOffset;

        public FileOffsetShift(long eocdhPosition, int shiftedFileOffset) {
            this.eocdhPosition = eocdhPosition;
            this.shiftedFileOffset = shiftedFileOffset;
        }

        @Override
        public String toString() {
            return "FileOffsetShift{" +
                    "eocdhPosition=" + eocdhPosition +
                    ", shiftedFileOffset=" + shiftedFileOffset +
                    '}';
        }
    }

    /**
     * Aligns the zip from the given input stream and outputs it to the given output stream with 4 byte alignment.<br/>
     * <br/>
     * <b>It is highly recommended to use {@link ZipAlign#alignZip(RandomAccessFile, OutputStream)} instead whenever
     * possible, this function is exponentially slower compared to the one mentioned.</b><br/>
     * <br/>
     * <b>NOTE: This function assumes that the given input stream is a valid zip file and skims manually through bytes.
     * It is advised to first verify the zip before passing it to this function.</b><br/>
     * <br/>
     * Example usage:
     * <pre>
     *     FileInputStream zipIn = ...;
     *     FileOutputStream zipOut = ...;
     *
     *     ZipAlign.alignZip(zipIn, zipOut);
     * </pre>
     *
     * @param zipIn The zip input stream
     * @param zipOut The zip output stream
     * @see ZipAlign#alignZip(InputStream, OutputStream, int)
     */
    public static void alignZip(InputStream zipIn, OutputStream zipOut) throws IOException {
        alignZip(zipIn, zipOut, 4);
    }

    /**
     * Aligns the zip from the given input stream and outputs it to the given output stream.<br/>
     * <br/>
     * <b>It is highly recommended to use {@link ZipAlign#alignZip(RandomAccessFile, OutputStream)} instead whenever
     * possible, this function is exponentially slower compared to the one mentioned.</b><br/>
     * <br/>
     * <b>NOTE: This function assumes that the given input stream is a valid zip file and skims manually through bytes.
     * It is advised to first verify the zip before passing it to this function.</b><br/>
     * <br/>
     * Example usage:
     * <pre>
     *     FileInputStream zipIn = ...;
     *     FileOutputStream zipOut = ...;
     *
     *     ZipAlign.alignZip(zipIn, zipOut, 4);
     * </pre>
     *
     * @param zipIn The zip input stream
     * @param zipOut The zip output stream
     * @param alignment Alignment in bytes, usually 4
     * @see ZipAlign#alignZip(InputStream, OutputStream)
     */
    public static void alignZip(InputStream zipIn, OutputStream zipOut, int alignment) throws IOException {
        LittleEndianInputStream in = new LittleEndianInputStream(zipIn);
        LittleEndianOutputStream out = new LittleEndianOutputStream(zipOut);
        ArrayList<Integer> fileOffsets = new ArrayList<>();

        // todo: handle zip64 asdjlkajdoijdlkasjd

        // source: https://en.wikipedia.org/wiki/ZIP_(file_format)#Structure
        // better source: https://users.cs.jmu.edu/buchhofp/forensics/formats/pkzip.html
        // the real source: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT

        int header = in.readInt();

        // starts with local file header signature
        while (header == 0x04034b50) {
            fileOffsets.add(out.bytesWritten());
            out.writeInt(0x04034b50);

            passBytes(in, out, 2);

            short generalPurposeFlag = in.readShort();
            out.writeShort(generalPurposeFlag);

            // data descriptor is used if the 3rd bit is 1
            boolean hasDataDescriptor = (generalPurposeFlag & 0x8) == 0x8;

            short compressionMethod = in.readShort();
            out.writeShort(compressionMethod);
            // 0 is when there is no compression done
            boolean shouldAlign = compressionMethod == 0;

            passBytes(in, out, 8);

            int compressedSize = in.readInt();
            out.writeInt(compressedSize);
            passBytes(in, out, 4);

            short fileNameLen = in.readShort();
            out.writeShort(fileNameLen);

            short extraFieldLen = in.readShort();

            // we're going to extend this extra field (if the data is uncompressed) so that the data will align into
            // the specified alignment boundaries (usually 4 bytes)
            int dataStartPoint = out.bytesWritten() + 2 + fileNameLen + extraFieldLen;
            int wrongOffset = dataStartPoint % alignment;
            int paddingSize = wrongOffset == 0 ? 0 : alignment - wrongOffset;

            if (shouldAlign) {
                out.writeShort(extraFieldLen + paddingSize);
            } else {
                out.writeShort(extraFieldLen);
            }

            passBytes(in, out, fileNameLen);
            passBytes(in, out, extraFieldLen);

            if (shouldAlign && paddingSize != 0) {
                // pad the extra field with null bytes
                byte[] padding = new byte[paddingSize];
                out.write(padding);
            }

            // if there isn't any data descriptor we can just pass the data right away
            if (!hasDataDescriptor) {
                passBytes(in, out, compressedSize);

                out.flush();
                header = in.readInt();

                continue;
            }

            // we have a data descriptor

            // fixme: pkware's spec 4.3.9.3 - although crazy rare, it is possible for data descriptors to not have
            //        a header before it. It's very tricky to implement it so I prefer to do it later.

            byte[] buffer = new byte[4];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            // loop until we stumble upon 0x08074b50
            byte cur;
            do {
                cur = in.readByte();
                out.write(cur);

                if (byteBuffer.position() == buffer.length - 1) {
                    // the buffer is full, so we shift all of it to the left
                    buffer[0] = buffer[1];
                    buffer[1] = buffer[2];
                    buffer[2] = buffer[3];
                    // then put our byte on the last index
                    byteBuffer.put(buffer.length - 1, cur);
                } else {
                    byteBuffer.put(cur);
                }
            } while (byteBuffer.getInt(0) != 0x08074b50);

            // we skip all the data descriptor lol, we don't need it
            passBytes(in, out, 12);

            out.flush();

            // todo: zip64
            // if (zip64) passBytes(in, outStream, 20);

            // next should be a new header
            header = in.readInt();
        }

        int centralDirectoryPosition = out.bytesWritten();
        int fileOffsetIndex = 0;

        // we're at the central directory
        while (header == 0x02014b50) {
            out.writeInt(0x02014b50);
            int fileOffset = fileOffsets.get(fileOffsetIndex);

            passBytes(in, out, 24);

            short fileNameLen = in.readShort();
            out.writeShort(fileNameLen);

            short extraFieldLen = in.readShort();
            out.writeShort(extraFieldLen);

            short fileCommentLen = in.readShort();
            out.writeShort(fileCommentLen);

            passBytes(in, out, 8);

            // offset of local header
            in.readInt();
            out.writeInt(fileOffset);

            passBytes(in, out, fileNameLen);
            passBytes(in, out, extraFieldLen);
            passBytes(in, out, fileCommentLen);

            out.flush();
            fileOffsetIndex++;

            header = in.readInt();
        }

        if (header != 0x06054b50)
            throw new IOException("No end of central directory record header, there is something wrong");

        // end of central directory record
        out.writeInt(0x06054b50);
        passBytes(in, out, 12);

        // offset of where central directory starts
        in.readInt();
        out.writeInt(centralDirectoryPosition);

        short commentLen = in.readShort();
        out.writeShort(commentLen);

        passBytes(in, out, commentLen);
    }

    private static void passBytes(RandomAccessFile raf, OutputStream out, long len) throws IOException {
        byte[] buffer = new byte[8162];

        long left;
        for (left = len; left > 8162; left -= 8162) {
            raf.read(buffer);
            out.write(buffer);
        }

        if (left != 0) {
            buffer = new byte[(int) left];
            raf.read(buffer);
            out.write(buffer);
        }

        out.flush();
    }

    /**
     * Passes a specified length of bytes from an {@link InputStream} to an {@link OutputStream}
     * @param in The input stream
     * @param out The output stream
     * @param len The length of how many bytes to be passed
     */
    private static void passBytes(InputStream in, OutputStream out, int len) throws IOException {
        byte[] data = new byte[len];
        if (in.read(data) == -1) throw new IOException("Reached EOF when passing bytes");
        out.write(data);
    }
}
