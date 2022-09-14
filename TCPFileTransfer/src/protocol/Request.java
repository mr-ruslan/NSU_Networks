package protocol;

public abstract class Request {

    public abstract void writeTo(java.io.OutputStream stream) throws java.io.IOException;

    public static Request readFrom(java.io.InputStream stream) throws java.io.IOException {
        switch (StreamUtil.readInt(stream)) {
            case Upload.TAG:
                return Upload.readFrom(stream);
            default:
                throw new java.io.IOException("Unexpected tag value");
        }
    }

    public static class Upload extends Request {
        public static final int TAG = 1;

        private String fileName;
        private long fileSize;
        private byte[] hash;
        private int compression;

        public Upload(String fileName, long fileSize, byte[] hash, int compression) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.hash = hash;
            this.compression = compression;
        }

        public static Upload readFrom(java.io.InputStream stream) throws java.io.IOException {

            String fileName = StreamUtil.readString(stream);
            long fileSize = StreamUtil.readLong(stream);
            byte[] hash = StreamUtil.readBytes(stream,16);
            int compression = StreamUtil.readInt(stream);
            return new Upload(fileName, fileSize, hash, compression);
        }

        @Override
        public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
            StreamUtil.writeInt(stream, TAG);
            StreamUtil.writeString(stream, fileName);
            StreamUtil.writeLong(stream, fileSize);
            StreamUtil.writeBytes(stream, hash);
            StreamUtil.writeInt(stream, compression);

        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public byte[] getHash() {
            return hash;
        }

        public int getCompression() {
            return compression;
        }
    }


}
